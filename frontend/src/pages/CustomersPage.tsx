import { useEffect, useRef, useState } from 'react'
import {
  App,
  Button,
  Card,
  Checkbox,
  Col,
  Divider,
  Form,
  Input,
  Row,
  Select,
  Space,
} from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { customerApi, workflowApi } from '../api'
import { ApiError } from '../api/http'
import type { Customer, CustomerPayload } from '../types'
import { useAuth } from '../auth/AuthContext'
import { can } from '../auth/permissions'
import { LIST_EXPORT_HINT, ListExportButton, useListSelection } from '../export/ListExportButton'
import { AccessItem, fieldMode, hasEditableField, isEditable, useWorkflowRuntime } from '../workflow/formAccess'
import { FormFlowButton } from '../workflow/FormFlowButton'
import { WorkflowBar } from '../workflow/WorkflowBar'
import { WorkflowFormButtons } from '../workflow/WorkflowFormButtons'
import { FormViewWorkbench, viewColumns } from '../ui/FormViewWorkbench'
import { PreviewHint, PreviewTable } from '../ui/PreviewTable'
import { EntityDrawer } from '../ui/CreateDrawer'
import { askQfSubmit } from '../ui/QfSubmitConfirm'
import { RowActions } from '../ui/RowActions'
import { ImportModal } from '../import/ImportModal'
import { BankAccountLineList, ContactLineList } from '../ui/PartyLineLists'

const LEVELS = ['一般重要', '重要', '非常重要']

type FormValues = CustomerPayload

export function CustomersPage() {
  const { message } = App.useApp()
  const { user: me } = useAuth()
  const navigate = useNavigate()
  const canCreate = can(me, 'CUSTOMER', 'create')
  const canView = can(me, 'CUSTOMER', 'view')
  const canEdit = can(me, 'CUSTOMER', 'edit')
  const canDelete = can(me, 'CUSTOMER', 'delete')
  const canExport = can(me, 'CUSTOMER', 'importExport')
  const [form] = Form.useForm<FormValues>()
  const [keyword, setKeyword] = useState('')
  const [publicFilter, setPublicFilter] = useState('ALL')
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const submitMode = useRef<'draft' | 'submit'>('draft')
  const [rows, setRows] = useState<Customer[]>([])
  const [open, setOpen] = useState(false)
  const [importOpen, setImportOpen] = useState(false)
  const [editing, setEditing] = useState<Customer | null>(null)
  const formWrite = Boolean(editing) ? canEdit : canCreate
  const { selectedRowKeys, rowSelection } = useListSelection()
  const [searchParams] = useSearchParams()
  const { runtime, reload } = useWorkflowRuntime('CUSTOMER', editing?.id)

  const load = async (q = keyword) => {
    setLoading(true)
    try {
      setRows(await customerApi.list(q || undefined, publicFilter))
    } catch (e) {
      message.error(e instanceof ApiError ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load('')
  }, [])

  useEffect(() => {
    const id = Number(searchParams.get('id') || 0)
    if (id) void openEdit({ id } as Customer)
  }, [searchParams])

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({
      level: '一般重要',
      contacts: [],
      bankAccounts: [],
      isPublic: false,
    })
    setOpen(true)
  }

  const openEdit = async (record: Customer) => {
    try {
      const detail = await customerApi.get(record.id)
      setEditing(detail)
      form.setFieldsValue({
        shortName: detail.shortName,
        companyName: detail.companyName,
        code: detail.code,
        level: detail.level,
        contacts: detail.contacts ?? [],
        bankAccounts: detail.bankAccounts ?? [],
        isPublic: !!detail.isPublic,
      })
      setOpen(true)
    } catch (e) {
      message.error(e instanceof ApiError ? e.message : '加载客户失败')
    }
  }

  const viewFields = [
    { key: 'shortName', title: '客户简称' },
    { key: 'companyName', title: '公司名称' },
    { key: 'code', title: '客户编码' },
    { key: 'level', title: '等级' },
    { key: 'isPublic', title: '公开' },
  ]

  const columns: ColumnsType<Customer> = [
    { title: '客户简称', dataIndex: 'shortName', key: 'shortName', width: 140 },
    { title: '公司名称', dataIndex: 'companyName', key: 'companyName', ellipsis: true },
    { title: '客户编码', dataIndex: 'code', key: 'code', width: 110 },
    { title: '等级', dataIndex: 'level', key: 'level', width: 110 },
    {
      title: '公开',
      dataIndex: 'isPublic',
      key: 'isPublic',
      width: 80,
      render: (value: boolean) => (value ? '已公开' : '未公开'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 160,
      render: (_, record) => (
        <RowActions
          canView={canView}
          canEdit={canEdit}
          canDelete={canDelete}
          deleteTitle="确认删除该客户？"
          onView={() => navigate(`/customers/${record.id}`)}
          onEdit={() => void openEdit(record)}
          onDelete={async () => {
            await customerApi.remove(record.id)
            message.success('已删除')
            await load()
          }}
        />
      ),
    },
  ]

  return (
    <Card className="qf-list-card">
      <FormViewWorkbench<Customer>
        module="CUSTOMER"
        fields={viewFields}
        rows={rows}
        loading={loading}
        onOpen={(record) => void openEdit(record)}
        filters={
          <Space wrap>
            <Input.Search
              allowClear
              placeholder="搜索简称 / 公司 / 编码"
              onSearch={(value) => {
                setKeyword(value)
                void load(value)
              }}
              style={{ width: 280 }}
            />
            <Select
              value={publicFilter}
              style={{ width: 120 }}
              options={[
                { value: 'ALL', label: '全部' },
                { value: 'YES', label: '已公开' },
                { value: 'NO', label: '未公开' },
              ]}
              onChange={(value) => {
                setPublicFilter(value)
                void (async () => {
                  setLoading(true)
                  try {
                    setRows(await customerApi.list(keyword || undefined, value))
                  } catch (e) {
                    message.error(e instanceof ApiError ? e.message : '加载失败')
                  } finally {
                    setLoading(false)
                  }
                })()
              }}
            />
          </Space>
        }
        extra={
          <Space>
            <FormFlowButton module="CUSTOMER" />
            <ListExportButton
              module="CUSTOMER"
              selectedRowKeys={selectedRowKeys}
              query={{ keyword: keyword || undefined, publicFilter }}
              fallbackName="客户列表.xlsx"
            />
            {canExport ? <Button onClick={() => setImportOpen(true)}>导入</Button> : null}
            {canCreate ? (
              <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                提交数据
              </Button>
            ) : null}
          </Space>
        }
        renderTable={({ rows: viewRows, fieldKeys }) => (
          <>
            <PreviewHint>{LIST_EXPORT_HINT}</PreviewHint>
            <PreviewTable
              rowKey="id"
              columns={viewColumns(columns, fieldKeys)}
              dataSource={viewRows}
              rowSelection={rowSelection}
              pagination={false}
              scroll={{ x: 800 }}
              previewTitle={(record) => record.shortName || '客户详情'}
              previewLoad={(record) => customerApi.get(record.id)}
              previewLink={(record) => ({ label: '打开客户', path: `/customers/${record.id}` })}
            />
          </>
        )}
      />

      <EntityDrawer
        variant="qingflow"
        title="客户信息"
        open={open}
        onClose={() => setOpen(false)}
        footer={
          <WorkflowFormButtons
              appearance="qingflow"
              runtime={runtime}
              loading={saving}
              disabled={Boolean(editing) && !hasEditableField(runtime, canEdit)}
              onCancel={() => setOpen(false)}
              onDraft={() => {
                submitMode.current = 'draft'
                form.submit()
              }}
              onSubmit={() => {
                submitMode.current = 'submit'
                form.submit()
              }}
            />
        }
      >
        {editing ? (
          <WorkflowBar module="CUSTOMER" bizId={editing.id} runtime={runtime} hideAdvance onReloaded={reload} />
        ) : null}
        <Form
          form={form}
          layout="vertical"
          onFinish={async (values) => {
            setSaving(true)
            try {
              const payload: CustomerPayload = {
                shortName: values.shortName,
                companyName: values.companyName,
                code: values.code,
                level: values.level,
                contacts: (values.contacts ?? []).map((item) => ({
                  ...item,
                  primaryContact: !!item.primaryContact,
                })),
                  bankAccounts: (values.bankAccounts ?? []).map((item) => ({
                  ...item,
                  primaryAccount: !!item.primaryAccount,
                })),
                isPublic: !!values.isPublic,
              }
              const saved = editing
                ? await customerApi.update(editing.id, payload)
                : await customerApi.create(payload)
              if (submitMode.current === 'submit') {
                const comment = await askQfSubmit()
                if (comment == null) return
                try {
                  await workflowApi.advance('CUSTOMER', saved.id, comment)
                  message.success('已提交')
                } catch (err) {
                  if (err instanceof ApiError && (err.message.includes('尚未发起') || err.message.includes('不由你处理'))) {
                    message.success(editing ? '已暂存' : '已创建')
                  } else {
                    throw err
                  }
                }
              } else {
                message.success('已暂存，未提交')
              }
              setOpen(false)
              await load()
            } catch (e) {
              message.error(e instanceof ApiError ? e.message : '保存失败')
            } finally {
              setSaving(false)
            }
          }}
        >
          <Row gutter={12}>
            <Col xs={24} md={12}>
              <AccessItem runtime={runtime} field="shortName" name="shortName" label="客户简称" rules={[{ required: true, message: '请填写简称' }]}>
                <Input placeholder="例如 苏州冠礼" />
              </AccessItem>
            </Col>
            <Col xs={24} md={12}>
              <AccessItem runtime={runtime} field="code" name="code" label="客户编码" rules={[{ required: true, message: '请填写编码' }]}>
                <Input placeholder="例如 133" />
              </AccessItem>
            </Col>
            <Col xs={24} md={16}>
              <AccessItem runtime={runtime} field="companyName" name="companyName" label="公司名称" rules={[{ required: true, message: '请填写公司名称' }]}>
                <Input placeholder="营业执照上的公司全称" />
              </AccessItem>
            </Col>
            <Col xs={24} md={8}>
              <AccessItem runtime={runtime} field="level" name="level" label="客户等级" rules={[{ required: true, message: '请选择等级' }]}>
                <Select options={LEVELS.map((value) => ({ value, label: value }))} />
              </AccessItem>
            </Col>
            <Col xs={24}>
              <Form.Item name="isPublic" valuePropName="checked">
                <Checkbox>允许公开</Checkbox>
              </Form.Item>
            </Col>
          </Row>

          {fieldMode(runtime, 'contacts', formWrite) !== 'HIDDEN' ? (
            <>
          <Divider>联系人</Divider>
          <ContactLineList editable={isEditable(runtime, 'contacts', formWrite)} />
            </>
          ) : null}

          {fieldMode(runtime, 'bankAccounts', formWrite) !== 'HIDDEN' ? (
            <>
          <Divider>银行账户</Divider>
          <BankAccountLineList editable={isEditable(runtime, 'bankAccounts', formWrite)} />
            </>
          ) : null}
        </Form>
      </EntityDrawer>
      <ImportModal
        open={importOpen}
        module="CUSTOMER"
        title="导入客户"
        onClose={() => setImportOpen(false)}
        onImported={() => void load()}
      />
    </Card>
  )
}
