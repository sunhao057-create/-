import { useEffect, useMemo, useRef, useState } from 'react'
import { App, Button, Card, Descriptions, Form, Input, Popconfirm, Select, Space, Tag, TreeSelect, Upload } from 'antd'
import { InboxOutlined } from '@ant-design/icons'
import type { UploadFile } from 'antd/es/upload/interface'
import { useNavigate, useParams } from 'react-router-dom'
import { deviceApi, workflowApi, type CustomerPick } from '../../api'
import { ApiError } from '../../api/http'
import { useFormOverlay, useFormOverlayFooter } from '../../ui/CreateDrawer'
import type { Device, DeviceWorkOrder, FormModule, OrgDepartment } from '../../types'
import { useAuth } from '../../auth/AuthContext'
import { can } from '../../auth/permissions'
import { buildDeptTree } from '../../org/deptTree'
import { ACTION_CANCEL, ACTION_DELETE, ACTION_DRAFT, AccessItem, actionShown, saveActionLabel, useWorkflowRuntime } from '../../workflow/formAccess'
import { WorkflowBar } from '../../workflow/WorkflowBar'
import type { DeviceOrderKind } from './DeviceWorkOrderListPage'
import { DateInput } from '../../ui/DateInput'
import { askQfSubmit } from '../../ui/QfSubmitConfirm'

const META: Record<
  DeviceOrderKind,
  { module: FormModule; title: string; path: string; reasonLabel: string }
> = {
  repairs: { module: 'DEVICE_REPAIR', title: '设备维修工单', path: '/devices/repairs', reasonLabel: '维修事由' },
  moves: { module: 'DEVICE_MOVE', title: '设备搬迁工单', path: '/devices/moves', reasonLabel: '搬迁事由' },
  scraps: { module: 'DEVICE_SCRAP', title: '设备报废工单', path: '/devices/scraps', reasonLabel: '报废事由' },
}

export function DeviceWorkOrderFormPage({ kind }: { kind: DeviceOrderKind }) {
  const meta = META[kind]
  const { message } = App.useApp()
  const { user: me } = useAuth()
  const navigate = useNavigate()
  const { id } = useParams()
  const bizId = id ? Number(id) : null
  const editing = bizId != null && Number.isFinite(bizId) && bizId > 0
  const canDelete = can(me, meta.module, 'delete')
  const canEdit = can(me, meta.module, 'edit')
  const [form] = Form.useForm()
  const [devices, setDevices] = useState<Device[]>([])
  const [customers, setCustomers] = useState<CustomerPick[]>([])
  const [depts, setDepts] = useState<OrgDepartment[]>([])
  const [detail, setDetail] = useState<DeviceWorkOrder | null>(null)
  const [fileList, setFileList] = useState<UploadFile[]>([])
  const [submitting, setSubmitting] = useState(false)
  const { runtime, reload } = useWorkflowRuntime(meta.module, editing ? bizId ?? undefined : undefined)
  const lockCore = !!detail?.applied && !canEdit
  const deptTree = useMemo(() => buildDeptTree(depts), [depts])
  const overlay = useFormOverlay()
  const submitMode = useRef<'draft' | 'submit'>('draft')
  const overlayFooter = useMemo(() => {
    if (!overlay?.active) return null
    return (
      <Space>
        <Button onClick={overlay.close}>取消</Button>
        <Button
          loading={submitting}
          disabled={lockCore}
          onClick={() => {
            submitMode.current = 'draft'
            form.submit()
          }}
        >
          暂存
        </Button>
        <Button
          type="primary"
          loading={submitting}
          disabled={lockCore}
          onClick={() => {
            submitMode.current = 'submit'
            form.submit()
          }}
        >
          提交
        </Button>
      </Space>
    )
  }, [overlay, submitting, lockCore, form])
  useFormOverlayFooter(overlayFooter)

  useEffect(() => {
    deviceApi
      .list()
      .then((rows) =>
        setDevices(
          rows.filter(
            (item) =>
              item.id === detail?.deviceId || (item.enabled && item.status === 'IN_USE') || editing,
          ),
        ),
      )
      .catch((e) => message.error(e instanceof ApiError ? e.message : '加载设备失败'))
    if (kind === 'moves') {
      deviceApi.departments().then(setDepts).catch(() => setDepts([]))
    }
    if (kind === 'repairs') {
      deviceApi.repairCustomers().then(setCustomers).catch(() => setCustomers([]))
    }
  }, [message, detail?.deviceId, editing, kind])

  useEffect(() => {
    if (!editing || !bizId) return
    void deviceApi
      .order(kind, bizId)
      .then((next) => {
        setDetail(next)
        form.setFieldsValue(next)
        setFileList(
          (next.files || []).map((file) => ({
            uid: String(file.id),
            name: file.originalName,
            status: 'done' as const,
            url: file.url,
          })),
        )
      })
      .catch((e) => message.error(e instanceof ApiError ? e.message : '加载失败'))
  }, [editing, bizId, form, message, kind])

  return (
    <Card
      title={editing ? meta.title : `新建${meta.title}`}
      extra={
        <Space>
          {detail?.code ? <Tag>{detail.code}</Tag> : <Tag>保存后自动生成编码</Tag>}
          {detail?.applied ? <Tag color="green">已办结</Tag> : null}
          {detail?.workflowStatusLabel ? <Tag color="orange">{detail.workflowStatusLabel}</Tag> : null}
        </Space>
      }
    >
      {editing && bizId ? (
        <WorkflowBar
          module={meta.module}
          bizId={bizId}
          runtime={runtime}
          onReloaded={async () => {
            await reload()
            if (bizId) {
              const next = await deviceApi.order(kind, bizId)
              setDetail(next)
              setFileList(
                (next.files || []).map((file) => ({
                  uid: String(file.id),
                  name: file.originalName,
                  status: 'done' as const,
                  url: file.url,
                })),
              )
            }
          }}
        />
      ) : null}
      <Form
        form={form}
        layout="vertical"
        onFinish={async (values) => {
          setSubmitting(true)
          try {
            const payload = {
              deviceId: values.deviceId,
              reason: values.reason,
              applyDate: values.applyDate,
              targetLocation: values.targetLocation,
              targetDepartmentId: values.targetDepartmentId,
              clientCustomerId: values.clientCustomerId,
              serviceCustomerId: values.serviceCustomerId,
            }
            const saved =
              editing && bizId
                ? await deviceApi.updateOrder(kind, bizId, payload)
                : await deviceApi.createOrder(kind, payload)
            const pending = fileList.map((file) => file.originFileObj).filter(Boolean) as File[]
            const withFiles = pending.length ? await deviceApi.uploadOrderFiles(kind, saved.id, pending) : saved
            if (overlay?.active && submitMode.current === 'submit') {
              const comment = await askQfSubmit()
              if (comment == null) return
              try {
                await workflowApi.advance(meta.module, withFiles.id, comment)
                message.success('已提交')
              } catch (err) {
                if (err instanceof ApiError && (err.message.includes('尚未发起') || err.message.includes('不由你处理'))) {
                  message.success('已暂存')
                } else {
                  throw err
                }
              }
              overlay.close()
              return
            }
            if (overlay?.active) {
              message.success('已暂存')
              overlay.close()
              return
            }
            message.success(editing ? '工单已保存' : '工单已提交')
            navigate(`${meta.path}/${withFiles.id}`)
            setDetail(withFiles)
            setFileList(
              (withFiles.files || []).map((file) => ({
                uid: String(file.id),
                name: file.originalName,
                status: 'done' as const,
                url: file.url,
              })),
            )
          } catch (e) {
            message.error(e instanceof ApiError ? e.message : '保存失败')
          } finally {
            setSubmitting(false)
          }
        }}
      >
        <AccessItem runtime={runtime} field="deviceId" name="deviceId" label="设备" rules={[{ required: true, message: '请选择设备' }]}>
          <Select
            disabled={lockCore}
            showSearch
            optionFilterProp="label"
            options={devices.map((item) => ({
              value: item.id,
              label: `${item.name}（${item.code} · ${item.location}）`,
            }))}
          />
        </AccessItem>
        <AccessItem runtime={runtime} field="applyDate" name="applyDate" label="申请日期" rules={[{ required: true, message: '请选择日期' }]}>
          <DateInput disabled={lockCore} />
        </AccessItem>
        {kind === 'moves' ? (
          <>
            <AccessItem
              runtime={runtime}
              field="targetLocation"
              name="targetLocation"
              label="搬迁后位置"
              rules={[{ required: true, message: '请填写搬迁后位置' }]}
            >
              <Input disabled={lockCore} />
            </AccessItem>
            <AccessItem runtime={runtime} field="targetDepartmentId" name="targetDepartmentId" label="搬迁后部门">
              <TreeSelect allowClear disabled={lockCore} treeDefaultExpandAll treeLine placeholder="可选，不填则仍属原部门" treeData={deptTree} />
            </AccessItem>
          </>
        ) : null}
        {kind === 'repairs' ? (
          <>
            <AccessItem
              runtime={runtime}
              field="clientCustomerId"
              name="clientCustomerId"
              label="委托客户（结算）"
              extra="维修费用向这家客户结算。"
              rules={[{ required: true, message: '请选择委托客户' }]}
            >
              <Select
                disabled={lockCore}
                showSearch
                optionFilterProp="label"
                placeholder="选择结算客户"
                options={customers.map((item) => ({ value: item.id, label: item.companyName }))}
              />
            </AccessItem>
            <AccessItem
              runtime={runtime}
              field="serviceCustomerId"
              name="serviceCustomerId"
              label="服务客户"
              extra="实际维修的客户。不填则与委托客户相同。A 委托我们给 B 维修时，这里选 B，结算仍是 A。"
            >
              <Select
                allowClear
                disabled={lockCore}
                showSearch
                optionFilterProp="label"
                placeholder="不填则与委托客户相同"
                options={customers.map((item) => ({ value: item.id, label: item.companyName }))}
              />
            </AccessItem>
          </>
        ) : null}
        <AccessItem runtime={runtime} field="reason" name="reason" label={meta.reasonLabel} rules={[{ required: true, message: '请填写事由' }]}>
          <Input.TextArea rows={3} maxLength={500} disabled={lockCore} />
        </AccessItem>
        {kind === 'repairs' && detail?.customerName ? (
          <Descriptions size="small" column={1} bordered title="报修登记">
            <Descriptions.Item label="工单等级">{detail.grade || '—'}</Descriptions.Item>
            <Descriptions.Item label="客户名称">{detail.customerName}</Descriptions.Item>
            <Descriptions.Item label="单位地址">{detail.customerAddress || '—'}</Descriptions.Item>
            <Descriptions.Item label="联系人">{detail.contactName || '—'}</Descriptions.Item>
            <Descriptions.Item label="联系电话">{detail.contactPhone || '—'}</Descriptions.Item>
            <Descriptions.Item label="仪器名称">{detail.instrumentName || '—'}</Descriptions.Item>
            <Descriptions.Item label="仪器型号">{detail.instrumentModel || '—'}</Descriptions.Item>
            <Descriptions.Item label="仪器厂家或品牌">{detail.brand || '—'}</Descriptions.Item>
            <Descriptions.Item label="故障现象">{detail.fault || '—'}</Descriptions.Item>
            <Descriptions.Item label="委托单位">{detail.clientOrg || '—'}</Descriptions.Item>
            <Descriptions.Item label="客户经理">{detail.accountManager || '—'}</Descriptions.Item>
            <Descriptions.Item label="备注">{detail.intakeRemark || '—'}</Descriptions.Item>
          </Descriptions>
        ) : null}
        <AccessItem runtime={runtime} field="attachments" name="files" label="附件">
          <Upload.Dragger
            multiple
            disabled={lockCore}
            fileList={fileList}
            beforeUpload={() => false}
            onChange={({ fileList: next }) => setFileList(next)}
            onRemove={async (file) => {
              const fileId = Number(file.uid)
              if (editing && bizId && Number.isFinite(fileId) && !file.originFileObj) {
                try {
                  const next = await deviceApi.removeOrderFile(kind, bizId, fileId)
                  setDetail(next)
                } catch (e) {
                  message.error(e instanceof ApiError ? e.message : '删除附件失败')
                  return false
                }
              }
              return true
            }}
          >
            <p className="ant-upload-drag-icon">
              <InboxOutlined />
            </p>
            <p>点击或拖拽上传文件</p>
            <p style={{ color: 'var(--app-text-secondary)' }}>支持文档、图片或压缩包，单个不超过 20MB</p>
          </Upload.Dragger>
        </AccessItem>
        {overlay?.active ? null : (
        <Space>
          {actionShown(runtime, ACTION_DRAFT) && (canEdit || !editing) ? (
            <Button type="primary" htmlType="submit" loading={submitting} disabled={lockCore}>
              {saveActionLabel(runtime)}
            </Button>
          ) : null}
          {actionShown(runtime, ACTION_CANCEL) ? (
            <Button onClick={() => navigate(meta.path)}>返回</Button>
          ) : null}
          {actionShown(runtime, ACTION_DELETE) && editing && canDelete && bizId ? (
            <Popconfirm
              title={`确认删除该${meta.title}？`}
              onConfirm={async () => {
                try {
                  await deviceApi.removeOrder(kind, bizId)
                  message.success('已删除')
                  navigate(meta.path)
                } catch (e) {
                  message.error(e instanceof ApiError ? e.message : '删除失败')
                }
              }}
            >
              <Button danger>删除</Button>
            </Popconfirm>
          ) : null}
        </Space>
        )}
      </Form>
    </Card>
  )
}
