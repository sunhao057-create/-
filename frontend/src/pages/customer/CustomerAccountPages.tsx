import { useCallback, useEffect, useMemo, useState } from 'react'
import { App, Button, Card, Form, Input, Select, Space, Table, Tag, TreeSelect } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { PlusOutlined } from '@ant-design/icons'
import { useNavigate, useParams } from 'react-router-dom'
import { customerAccountApi, workflowApi, type CustomerAccountOpen, type CustomerPick } from '../../api'
import { ApiError } from '../../api/http'
import { useAuth } from '../../auth/AuthContext'
import { can } from '../../auth/permissions'
import { useFormOverlay, useFormOverlayFooter } from '../../ui/CreateDrawer'
import { askQfSubmit } from '../../ui/QfSubmitConfirm'
import { FormFlowButton } from '../../workflow/FormFlowButton'
import { WorkflowBar } from '../../workflow/WorkflowBar'
import { buildDeptTree } from '../../org/deptTree'
import type { OrgDepartment } from '../../types'
import { AccessItem, useWorkflowRuntime } from '../../workflow/formAccess'

const PATH = '/customer-accounts'
const MODULE = 'CUSTOMER_ACCOUNT'

export function CustomerAccountListPage() {
  const { message } = App.useApp()
  const { user: me } = useAuth()
  const navigate = useNavigate()
  const canCreate = can(me, MODULE, 'create')
  const [loading, setLoading] = useState(false)
  const [rows, setRows] = useState<CustomerAccountOpen[]>([])

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setRows(await customerAccountApi.list())
    } catch (e) {
      message.error(e instanceof ApiError ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }, [message])

  useEffect(() => {
    void load()
  }, [load])

  const columns: ColumnsType<CustomerAccountOpen> = [
    { title: '单号', dataIndex: 'code', width: 150 },
    { title: '公司', dataIndex: 'companyName' },
    { title: '部门', dataIndex: 'departmentName', width: 140, render: (value) => value || '—' },
    { title: '姓名', dataIndex: 'displayName', width: 120 },
    { title: '登录账号', dataIndex: 'username', width: 140 },
    {
      title: '账号类型',
      width: 110,
      render: (_, row) => (row.accountType === 'EXTERNAL' ? '外部' : row.accountType === 'INTERNAL' ? '内部' : '待审核'),
    },
    { title: '角色', dataIndex: 'roleName', width: 140, render: (value) => value || '—' },
    {
      title: '状态',
      width: 110,
      render: (_, row) => <Tag color={row.settled ? 'green' : 'orange'}>{row.settled ? '已开通' : row.workflowStatusLabel || '草稿'}</Tag>,
    },
  ]

  return (
    <Card
      title="新建客户账号"
      extra={
        <Space>
          <FormFlowButton module={MODULE} />
          {canCreate ? (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate(`${PATH}/new`)}>
              新建账号
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

export function CustomerAccountFormPage() {
  const { message } = App.useApp()
  const { id } = useParams()
  const recordId = id ? Number(id) : undefined
  const editing = recordId != null && !Number.isNaN(recordId)
  const navigate = useNavigate()
  const [form] = Form.useForm()
  const [saving, setSaving] = useState(false)
  const [settled, setSettled] = useState(false)
  const [password, setPassword] = useState<string | null>(null)
  const [customers, setCustomers] = useState<CustomerPick[]>([])
  const [roles, setRoles] = useState<{ code: string; name: string }[]>([])
  const [departments, setDepartments] = useState<OrgDepartment[]>([])
  const customerId = Form.useWatch('customerId', form)
  const { runtime, reload } = useWorkflowRuntime(MODULE, editing ? recordId : undefined)
  const overlay = useFormOverlay()

  useEffect(() => {
    customerAccountApi.customers().then(setCustomers).catch(() => setCustomers([]))
    customerAccountApi.roles().then(setRoles).catch(() => setRoles([]))
  }, [])

  useEffect(() => {
    if (!customerId) {
      setDepartments([])
      return
    }
    customerAccountApi
      .departments(Number(customerId))
      .then((rows) =>
        setDepartments(
          rows.map((item) => ({
            id: item.id,
            name: item.name,
            parentId: item.parentId,
            sortOrder: 0,
            memberCount: 0,
            leaderCount: 0,
          })),
        ),
      )
      .catch(() => setDepartments([]))
  }, [customerId])

  useEffect(() => {
    if (!editing || recordId == null) return
    customerAccountApi
      .get(recordId)
      .then((row) => {
        setSettled(row.settled)
        setPassword(row.initialPassword || null)
        form.setFieldsValue(row)
      })
      .catch((e) => message.error(e instanceof ApiError ? e.message : '加载失败'))
  }, [editing, recordId, form, message])

  const save = async () => {
    const values = await form.validateFields()
    if (editing && recordId != null) {
      await customerAccountApi.update(recordId, values)
      return recordId
    }
    const created = await customerAccountApi.create(values)
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
        <Form.Item name="customerId" label="所属客户" rules={[{ required: true, message: '请选择所属客户' }]}>
          <Select
            showSearch
            optionFilterProp="label"
            placeholder="需已完成新建客户"
            options={customers.map((item) => ({ value: item.id, label: item.companyName }))}
            onChange={() => form.setFieldValue('departmentId', undefined)}
          />
        </Form.Item>
        <AccessItem runtime={runtime} field="departmentId" name="departmentId" label="所属部门" extra="只能选这家公司下面的部门。审核通过后，账号挂在这个部门。">
          <TreeSelect
            allowClear
            showSearch
            treeDefaultExpandAll
            treeNodeFilterProp="title"
            placeholder={customerId ? '选择公司下的部门' : '请先选择所属客户'}
            disabled={!customerId || settled}
            treeData={buildDeptTree(departments)}
          />
        </AccessItem>
        <Form.Item name="displayName" label="姓名" rules={[{ required: true, message: '请填写姓名' }]}>
          <Input maxLength={50} />
        </Form.Item>
        <Form.Item
          name="username"
          label="登录账号"
          rules={[{ required: true, message: '请填写登录账号' }, { pattern: /^[a-zA-Z0-9_]{4,32}$/, message: '4-32 位字母、数字或下划线' }]}
        >
          <Input maxLength={32} />
        </Form.Item>
        <Form.Item name="phone" label="手机号">
          <Input maxLength={30} />
        </Form.Item>
        <Form.Item name="accountType" label="账号类型" extra="审核时指定。外部账号进入客户小程序，内部账号进入工作台。">
          <Select
            allowClear
            placeholder="审核时选择"
            options={[
              { value: 'EXTERNAL', label: '外部账号' },
              { value: 'INTERNAL', label: '内部账号' },
            ]}
          />
        </Form.Item>
        <Form.Item name="roleCode" label="角色" extra="审核时指定，权限跟这个角色走。">
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            placeholder="审核时选择"
            options={roles.map((item) => ({ value: item.code, label: item.name }))}
          />
        </Form.Item>
      </Form>
      {settled && password ? <div style={{ color: '#64748b' }}>账号已开通，初始密码 {password}。首次登录需要修改密码。</div> : null}
    </>
  )

  if (overlay?.active) return body
  return (
    <Card title="新建客户账号" extra={<Button onClick={() => navigate(PATH)}>返回</Button>}>
      {body}
    </Card>
  )
}
