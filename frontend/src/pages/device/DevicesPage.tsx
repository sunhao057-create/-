import { useEffect, useMemo, useState, type Key } from 'react'
import { App, Button, Card, Form, Image, Input, Modal, Select, Space, Switch, Tag, TreeSelect, Upload } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { ExportOutlined, PlusOutlined, QrcodeOutlined, SearchOutlined } from '@ant-design/icons'
import { deviceApi, userApi } from '../../api'
import { ApiError } from '../../api/http'
import type { Device, OrgDepartment, User } from '../../types'
import { useAuth } from '../../auth/AuthContext'
import { can } from '../../auth/permissions'
import { buildDeptTree, deptPath } from '../../org/deptTree'
import { FormViewWorkbench, viewColumns } from '../../ui/FormViewWorkbench'
import { PreviewHint, PreviewTable } from '../../ui/PreviewTable'
import { EntityDrawer } from '../../ui/CreateDrawer'
import { RowActions } from '../../ui/RowActions'
import { saveBlob } from '../../utils/download'
import { FormFlowButton } from '../../workflow/FormFlowButton'

function filenameFromDisposition(header: string, fallback: string) {
  const utf = header.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf?.[1]) return decodeURIComponent(utf[1])
  const plain = header.match(/filename="?([^";]+)"?/i)
  return plain?.[1] || fallback
}

type DeviceFilters = {
  departmentId?: number
  enabled?: boolean
  code?: string
  name?: string
  model?: string
  location?: string
}

function cleanFilters(values: DeviceFilters): DeviceFilters {
  const text = (value?: string) => {
    const next = value?.trim()
    return next ? next : undefined
  }
  return {
    departmentId: values.departmentId,
    enabled: values.enabled,
    code: text(values.code),
    name: text(values.name),
    model: text(values.model),
    location: text(values.location),
  }
}

export function DevicesPage() {
  const { message } = App.useApp()
  const { user: me } = useAuth()
  const canCreate = can(me, 'DEVICE', 'create')
  const canView = can(me, 'DEVICE', 'view')
  const canEdit = can(me, 'DEVICE', 'edit')
  const canDelete = can(me, 'DEVICE', 'delete')
  const canExport = can(me, 'DEVICE', 'importExport')
  const [form] = Form.useForm()
  const [filterForm] = Form.useForm<DeviceFilters>()
  const [rows, setRows] = useState<Device[]>([])
  const [depts, setDepts] = useState<OrgDepartment[]>([])
  const [managers, setManagers] = useState<User[]>([])
  const [filters, setFilters] = useState<DeviceFilters>({})
  const [loading, setLoading] = useState(false)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Device | null>(null)
  const [saving, setSaving] = useState(false)
  const [exporting, setExporting] = useState(false)
  const [selectedRowKeys, setSelectedRowKeys] = useState<Key[]>([])
  const [pageSize, setPageSize] = useState(20)
  const [photoFile, setPhotoFile] = useState<File | null>(null)
  const [photoPreview, setPhotoPreview] = useState<string | null>(null)
  const [removePhoto, setRemovePhoto] = useState(false)
  const [qrOpen, setQrOpen] = useState(false)
  const [qrDevice, setQrDevice] = useState<Device | null>(null)
  const [qrUrl, setQrUrl] = useState<string | null>(null)
  const deptTree = useMemo(() => buildDeptTree(depts), [depts])

  const load = async (query: DeviceFilters = filters) => {
    setLoading(true)
    try {
      const [devices, departments] = await Promise.all([deviceApi.list(query), deviceApi.departments()])
      setRows(devices)
      setDepts(departments)
      userApi.list({ status: 'ENABLED', page: 1, size: 500 }).then((people) => setManagers(people.items || [])).catch(() => setManagers([]))
    } catch (e) {
      message.error(e instanceof ApiError ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [])

  useEffect(() => {
    return () => {
      if (qrUrl) URL.revokeObjectURL(qrUrl)
    }
  }, [qrUrl])

  const resetPhoto = (device?: Device | null) => {
    setPhotoFile(null)
    setRemovePhoto(false)
    setPhotoPreview(device?.photoUrl || null)
  }

  const openQr = async (record: Device) => {
    try {
      if (qrUrl) URL.revokeObjectURL(qrUrl)
      const blob = await deviceApi.qrcode(record.id)
      setQrDevice(record)
      setQrUrl(URL.createObjectURL(blob))
      setQrOpen(true)
    } catch (e) {
      message.error(e instanceof ApiError ? e.message : '生成二维码失败')
    }
  }

  const viewFields = [
    { key: 'photoUrl', title: '照片' },
    { key: 'code', title: '编码' },
    { key: 'name', title: '设备名称' },
    { key: 'model', title: '型号' },
    { key: 'location', title: '放置位置' },
    { key: 'departmentName', title: '所属部门' },
    { key: 'managerName', title: '客户经理' },
    { key: 'statusLabel', title: '状态' },
    { key: 'enabled', title: '启用' },
  ]

  const columns: ColumnsType<Device> = [
    {
      title: '照片',
      dataIndex: 'photoUrl',
      key: 'photoUrl',
      width: 72,
      render: (url: string | null | undefined) =>
        url ? <Image src={url} width={40} height={40} style={{ objectFit: 'cover', borderRadius: 4 }} /> : '—',
    },
    { title: '编码', dataIndex: 'code', key: 'code', width: 150 },
    { title: '设备名称', dataIndex: 'name', key: 'name', width: 160 },
    { title: '型号', dataIndex: 'model', key: 'model', width: 120 },
    { title: '放置位置', dataIndex: 'location', key: 'location', ellipsis: true },
    {
      title: '所属部门',
      key: 'departmentName',
      width: 180,
      ellipsis: true,
      render: (_, record) => deptPath(depts, record.departmentId) || record.departmentName,
    },
    { title: '客户经理', dataIndex: 'managerName', key: 'managerName', width: 110, render: (value: string | null | undefined) => value || '—' },
    {
      title: '状态',
      dataIndex: 'statusLabel',
      key: 'statusLabel',
      width: 90,
      render: (label: string, record) => (
        <Tag color={record.status === 'IN_USE' ? 'green' : record.status === 'SCRAPPED' ? 'default' : 'orange'}>{label}</Tag>
      ),
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      key: 'enabled',
      width: 80,
      render: (value: boolean) => <Tag color={value ? 'green' : 'default'}>{value ? '启用' : '停用'}</Tag>,
    },
    {
      title: '操作',
      key: 'actions',
      width: 240,
      render: (_, record) => (
        <RowActions
          canView={canView && record.status !== 'SCRAPPED'}
          canEdit={canEdit && record.status !== 'SCRAPPED'}
          canDelete={canDelete}
          deleteTitle="确认删除该设备？"
          extra={
            <Button size="small" icon={<QrcodeOutlined />} onClick={() => void openQr(record)}>
              二维码
            </Button>
          }
          onEdit={() => {
            setEditing(record)
            form.setFieldsValue(record)
            resetPhoto(record)
            setOpen(true)
          }}
          onDelete={async () => {
            try {
              await deviceApi.remove(record.id)
              message.success('已删除')
              await load()
            } catch (e) {
              message.error(e instanceof ApiError ? e.message : '删除失败')
            }
          }}
        />
      ),
    },
  ]

  const openDevice = (record: Device) => {
    if (!canView) return
    setEditing(record)
    form.setFieldsValue(record)
    resetPhoto(record)
    setOpen(true)
  }

  return (
    <Card>
      <FormViewWorkbench<Device>
        module="DEVICE"
        fields={viewFields}
        rows={rows}
        loading={loading}
        onOpen={openDevice}
        filters={
          <Form
            form={filterForm}
            layout="inline"
            style={{ rowGap: 12 }}
            onFinish={async (values) => {
              const next = cleanFilters(values)
              setFilters(next)
              setSelectedRowKeys([])
              await load(next)
            }}
          >
            <Form.Item name="departmentId" label="所属部门">
              <TreeSelect
                allowClear
                showSearch
                treeDefaultExpandAll
                treeLine
                treeNodeFilterProp="title"
                placeholder="全部部门"
                treeData={deptTree}
                style={{ width: 220 }}
              />
            </Form.Item>
            <Form.Item name="enabled" label="是否启用">
              <Select
                allowClear
                placeholder="全部"
                style={{ width: 120 }}
                options={[
                  { value: true, label: '启用' },
                  { value: false, label: '停用' },
                ]}
              />
            </Form.Item>
            <Form.Item name="code" label="设备编码">
              <Input allowClear placeholder="编码" style={{ width: 160 }} />
            </Form.Item>
            <Form.Item name="name" label="设备名称">
              <Input allowClear placeholder="名称" style={{ width: 160 }} />
            </Form.Item>
            <Form.Item name="model" label="设备型号">
              <Input allowClear placeholder="型号" style={{ width: 140 }} />
            </Form.Item>
            <Form.Item name="location" label="存放位置">
              <Input allowClear placeholder="位置" style={{ width: 160 }} />
            </Form.Item>
            <Form.Item>
              <Space>
                <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
                  查询
                </Button>
                <Button
                  onClick={async () => {
                    filterForm.resetFields()
                    setFilters({})
                    setSelectedRowKeys([])
                    await load({})
                  }}
                >
                  重置
                </Button>
              </Space>
            </Form.Item>
          </Form>
        }
        extra={
          <Space>
            {canExport ? (
              <Button
                icon={<ExportOutlined />}
                loading={exporting}
                onClick={async () => {
                  setExporting(true)
                  try {
                    const ids = selectedRowKeys.map((key) => Number(key)).filter((id) => Number.isFinite(id))
                    const { blob, disposition } = await deviceApi.exportWorkbook(ids.length ? { ids } : filters)
                    saveBlob(blob, filenameFromDisposition(disposition, '设备台账.xlsx'))
                    if (ids.length) message.success(`已导出 ${ids.length} 台设备`)
                    else message.success('台账已导出')
                  } catch (e) {
                    message.error(e instanceof ApiError ? e.message : '导出失败')
                  } finally {
                    setExporting(false)
                  }
                }}
              >
                {selectedRowKeys.length ? `导出已选 (${selectedRowKeys.length})` : '导出台账'}
              </Button>
            ) : null}
            <FormFlowButton module="DEVICE" />
            {canCreate ? (
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => {
                  setEditing(null)
                  form.resetFields()
                  form.setFieldsValue({ enabled: true })
                  resetPhoto(null)
                  setOpen(true)
                }}
              >
                提交数据
              </Button>
            ) : null}
          </Space>
        }
        renderTable={({ rows: viewRows, fieldKeys }) => (
          <>
            <PreviewHint>
              可按部门、启用状态、编码、名称、型号、存放位置筛选。勾选后导出选中数据；未勾选则导出当前筛选结果。导出的 Excel 会内嵌设备照片和二维码，数据量大时请稍候。
            </PreviewHint>
            <PreviewTable
              rowKey="id"
              columns={viewColumns(columns, fieldKeys)}
              dataSource={viewRows}
              pagination={{
                pageSize,
                showSizeChanger: true,
                pageSizeOptions: [20, 50, 100, 200, 500, 1000],
                onChange: (_page, size) => {
                  if (size && size !== pageSize) setPageSize(size)
                },
                showTotal: (total) => `共 ${total} 台`,
              }}
              onRow={(record) => ({
                onClick: () => openDevice(record),
              })}
              rowSelection={{
                selectedRowKeys,
                onChange: setSelectedRowKeys,
                preserveSelectedRowKeys: true,
              }}
            />
          </>
        )}
      />
      <EntityDrawer
        variant="qingflow"
        title="设备信息"
        open={open}
        confirmLoading={saving}
        onClose={() => setOpen(false)}
        onOk={editing ? (canEdit ? () => form.submit() : undefined) : canCreate ? () => form.submit() : undefined}
        okButtonProps={{ style: (editing ? canEdit : canCreate) ? undefined : { display: 'none' } }}
        okText="提交"
        cancelText={editing && !canEdit ? '关闭' : '取消'}
      >
        <Form
          form={form}
          layout="vertical"
          disabled={Boolean(editing) ? !canEdit : !canCreate}
          onFinish={async (values) => {
            setSaving(true)
            try {
              const saved = editing ? await deviceApi.update(editing.id, values) : await deviceApi.create(values)
              if (photoFile) await deviceApi.uploadPhoto(saved.id, photoFile)
              else if (editing && removePhoto && editing.photoUrl) await deviceApi.removePhoto(saved.id)
              message.success('已保存')
              setOpen(false)
              await load()
            } catch (e) {
              message.error(e instanceof ApiError ? e.message : '保存失败')
            } finally {
              setSaving(false)
            }
          }}
        >
          <Form.Item name="name" label="设备名称" rules={[{ required: true, message: '请填写设备名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="model" label="型号">
            <Input />
          </Form.Item>
          <Form.Item name="brand" label="厂家 / 品牌">
            <Input placeholder="例如 岛津" />
          </Form.Item>
          <Form.Item name="serialNo" label="序列号">
            <Input />
          </Form.Item>
          <Form.Item name="location" label="放置位置" rules={[{ required: true, message: '请填写放置位置' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="departmentId" label="所属部门" rules={[{ required: true, message: '请选择所属部门' }]}>
            <TreeSelect treeDefaultExpandAll treeLine placeholder="选择部门" treeData={deptTree} />
          </Form.Item>
          <Form.Item name="managerId" label="客户经理">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="选择客户经理"
              options={managers.map((item) => ({ value: item.id, label: item.displayName || item.username }))}
            />
          </Form.Item>
          <Form.Item label="设备图片">
            <Upload
              accept="image/png,image/jpeg,image/webp"
              maxCount={1}
              showUploadList={false}
              beforeUpload={(file) => {
                setPhotoFile(file)
                setRemovePhoto(false)
                setPhotoPreview(URL.createObjectURL(file))
                return false
              }}
            >
              <Button>上传图片</Button>
            </Upload>
            {photoPreview ? (
              <div style={{ marginTop: 8 }}>
                <Image src={photoPreview} width={96} height={96} style={{ objectFit: 'cover', borderRadius: 6 }} />
                <Button
                  type="link"
                  onClick={() => {
                    setPhotoFile(null)
                    setPhotoPreview(null)
                    setRemovePhoto(true)
                  }}
                >
                  移除
                </Button>
              </div>
            ) : (
              <div style={{ color: 'var(--app-text-secondary)', marginTop: 8 }}>支持 jpg / png / webp，单张不超过 8MB</div>
            )}
          </Form.Item>
          <Form.Item name="remark" label="说明">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </EntityDrawer>
      <Modal
        title={qrDevice ? `${qrDevice.name} 二维码` : '设备二维码'}
        open={qrOpen}
        onCancel={() => setQrOpen(false)}
        footer={
          <Space>
            <Button
              type="primary"
              onClick={() => {
                if (!qrUrl || !qrDevice) return
                const link = document.createElement('a')
                link.href = qrUrl
                link.download = `${qrDevice.code}-二维码.png`
                link.click()
              }}
            >
              下载二维码
            </Button>
            <Button onClick={() => setQrOpen(false)}>关闭</Button>
          </Space>
        }
      >
        {qrUrl ? (
          <div style={{ textAlign: 'center' }}>
            <img src={qrUrl} alt="设备二维码" width={240} height={240} />
            <div style={{ marginTop: 8, color: 'var(--app-text-secondary)' }}>
              扫码可查看编码、名称、位置和所属部门
            </div>
          </div>
        ) : null}
      </Modal>
    </Card>
  )
}
