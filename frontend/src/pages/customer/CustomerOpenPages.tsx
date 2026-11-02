import { useCallback, useEffect, useMemo, useState } from 'react'
import { App, Button, Card, Form, Input, Select, Space, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { PlusOutlined } from '@ant-design/icons'
import { useNavigate, useParams } from 'react-router-dom'
import { customerOpenApi, workflowApi, type CustomerOpen } from '../../api'
import { ApiError } from '../../api/http'
import { useAuth } from '../../auth/AuthContext'
import { can } from '../../auth/permissions'
import { useFormOverlay, useFormOverlayFooter } from '../../ui/CreateDrawer'
import { askQfSubmit } from '../../ui/QfSubmitConfirm'
import { FormFlowButton } from '../../workflow/FormFlowButton'
import { WorkflowBar } from '../../workflow/WorkflowBar'
import { useWorkflowRuntime } from '../../workflow/formAccess'

const PATH = '/customer-opens'
const MODULE = 'CUSTOMER_APPLY'
const LEVELS = ['普通', '重要', '战略']

export function CustomerOpenListPage() {
  const { message } = App.useApp()
  const { user: me } = useAuth()
  const navigate = useNavigate()
  const canCreate = can(me, MODULE, 'create')
  const [loading, setLoading] = useState(false)
  const [rows, setRows] = useState<CustomerOpen[]>([])

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setRows(await customerOpenApi.list())
    } catch (e) {
      message.error(e instanceof ApiError ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }, [message])

  useEffect(() => {
    void load()
  }, [load])

  const columns: ColumnsType<CustomerOpen> = [
    { title: '单号', dataIndex: 'code', width: 150 },
    { title: '公司名称', dataIndex: 'companyName' },
    { title: '简称', dataIndex: 'shortName', width: 140 },
    { title: '联系人', dataIndex: 'contactName', width: 120, render: (value) => value || '—' },
    {
      title: '状态',
      width: 120,
      render: (_, row) => (
        <Tag color={row.settled ? 'green' : 'orange'}>{row.settled ? '已建档' : row.workflowStatusLabel || '草稿'}</Tag>
      ),
    },
  ]

  return (
    <Card
      title="新建客户"
      extra={
        <Space>
          <FormFlowButton module={MODULE} />
          {canCreate ? (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate(`${PATH}/new`)}>
              新建客户
            </Button>
          ) : null}
        </Space>
      }
    >
      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={rows}
        onRow={(row) => ({ onClick: () => navigate(`${PATH}/${row.id}`) })}
      />
    </Card>
  )
}

export function CustomerOpenFormPage() {
  const { message } = App.useApp()
  const { id } = useParams()
  const recordId = id ? Number(id) : undefined
  const editing = recordId != null && !Number.isNaN(recordId)
  const navigate = useNavigate()
  const [form] = Form.useForm()
  const [saving, setSaving] = useState(false)
  const [settled, setSettled] = useState(false)
  const { runtime, reload } = useWorkflowRuntime(MODULE, editing ? recordId : undefined)
  const overlay = useFormOverlay()

  useEffect(() => {
    if (!editing || recordId == null) return
    customerOpenApi
      .get(recordId)
      .then((row) => {
        setSettled(row.settled)
        form.setFieldsValue(row)
      })
      .catch((e) => message.error(e instanceof ApiError ? e.message : '加载失败'))
  }, [editing, recordId, form, message])

  const save = async () => {
    const values = await form.validateFields()
    if (editing && recordId != null) {
      await customerOpenApi.update(recordId, values)
      return recordId
    }
    const created = await customerOpenApi.create(values)
    return created.id
  }

  const overlayFooter = useMemo(() => {
    if (!overlay?.active || settled) return null
    return (
      <Space>
        <Button onClick={overlay.close}>取消</Button>
        <Button
          type="primary"
          loading={saving}
          onClick={() => {
            setSaving(true)
            void (async () => {
              const savedId = await save()
              const comment = await askQfSubmit({
                nextNodeName: runtime?.nextNodeName || runtime?.currentNodeName,
                assignees: runtime?.currentAssignees,
              })
              if (comment == null || !savedId) return
              await workflowApi.advance(MODULE, savedId, comment)
              message.success('已提交')
              overlay.close()
            })()
              .catch((e) => message.error(e instanceof ApiError ? e.message : '提交失败'))
              .finally(() => setSaving(false))
          }}
        >
          提交
        </Button>
      </Space>
    )
  }, [overlay, saving, settled, message, runtime, form, editing, recordId])
  useFormOverlayFooter(overlayFooter)

  const body = (
    <>
      {editing && recordId ? (
        <WorkflowBar
          module={MODULE}
          bizId={recordId}
          runtime={runtime}
          hideAdvance={Boolean(overlay?.active)}
          onSaveDraft={async () => {
            await save()
          }}
          onReloaded={() => reload()}
        />
      ) : null}
      <Form form={form} layout="vertical" disabled={settled}>
        <Form.Item name="companyName" label="公司名称" rules={[{ required: true, message: '请填写公司名称' }]}>
          <Input placeholder="与通讯录企业名称一致，不能重复" maxLength={80} />
        </Form.Item>
        <Form.Item name="shortName" label="客户简称">
          <Input placeholder="不填则使用公司名称" maxLength={50} />
        </Form.Item>
        <Form.Item name="level" label="客户等级" initialValue="普通">
          <Select options={LEVELS.map((item) => ({ value: item, label: item }))} />
        </Form.Item>
        <Form.Item name="contactName" label="联系人">
          <Input maxLength={50} />
        </Form.Item>
        <Form.Item name="contactPhone" label="联系电话">
          <Input maxLength={30} />
        </Form.Item>
        <Form.Item name="address" label="单位地址">
          <Input maxLength={200} />
        </Form.Item>
      </Form>
      {settled ? <div style={{ color: '#64748b' }}>审核通过后已写入客户资料，并在通讯录企业架构中新增了该公司。</div> : null}
    </>
  )

  if (overlay?.active) return body
  return (
    <Card title="新建客户" extra={<Button onClick={() => navigate(PATH)}>返回</Button>}>
      {body}
    </Card>
  )
}
