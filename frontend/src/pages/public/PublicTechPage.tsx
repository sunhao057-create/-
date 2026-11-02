import { useCallback, useEffect, useMemo, useState } from 'react'
import { App, Button, Card, Col, DatePicker, Descriptions, Form, Input, InputNumber, Row, Select, Space, Table, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../../api/http'
import { publicApi, type PublicTech, type PublicTechSchema, type PublicTechSchemaField } from '../../api/public'
import { PublicClosed, usePublicConfig } from '../../layouts/PublicLayout'
import { techKindBySlug } from '../../tech/catalog'
import { CustomerLookup, isCustomerLinkField } from '../../ui/CustomerLookup'
import type { PublicPageMode } from './PublicRecordsPage'

function loadPublicCustomers() {
  return publicApi.customers()
}

function cellText(value: unknown): string {
  if (value == null) return ''
  if (Array.isArray(value)) return value.map(cellText).filter(Boolean).join('、')
  if (typeof value === 'object') {
    const obj = value as Record<string, unknown>
    return cellText(obj.name ?? obj.label ?? obj.title ?? obj.value ?? '')
  }
  return String(value)
}

function FieldInput({
  field,
  fields,
}: {
  field: PublicTechSchemaField
  fields: { key: string; name: string }[]
}) {
  if (isCustomerLinkField(field.name) && field.type !== 'member') {
    return (
      <CustomerLookup
        fields={fields}
        loadCustomers={loadPublicCustomers}
        placeholder={`请选择${field.name}`}
      />
    )
  }
  if (field.options && field.options.length > 0) {
    return <Select allowClear options={field.options.map((o) => ({ value: o, label: o }))} placeholder={`请选择${field.name}`} />
  }
  switch (field.type) {
    case 'textarea':
      return <Input.TextArea rows={3} placeholder={`请输入${field.name}`} />
    case 'number':
      return <InputNumber style={{ width: '100%' }} placeholder={`请输入${field.name}`} />
    case 'date':
      return <DatePicker style={{ width: '100%' }} />
    case 'datetime':
      return <DatePicker showTime style={{ width: '100%' }} />
    default:
      return <Input placeholder={`请输入${field.name}`} />
  }
}

export function PublicTechPage({ mode = 'list' }: { mode?: PublicPageMode }) {
  const { message } = App.useApp()
  const navigate = useNavigate()
  const { slug = '', id } = useParams()
  const config = usePublicConfig()
  const kind = techKindBySlug(slug)
  const module = kind?.module || ''
  const flags = config.modules.find((item) => item.module === module)?.flags ?? {
    publicAccess: false,
    display: false,
    submit: false,
    query: false,
    export: false,
  }
  const base = `/public/tech/${slug}`

  const [form] = Form.useForm<Record<string, unknown>>()
  const [schema, setSchema] = useState<PublicTechSchema | null>(null)
  const [rows, setRows] = useState<PublicTech[]>([])
  const [detail, setDetail] = useState<PublicTech | null>(null)
  const [missing, setMissing] = useState('')
  const [loading, setLoading] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  const canList = flags.publicAccess && (flags.display || flags.query)
  const canCreate = flags.publicAccess && flags.submit
  const namedFields = useMemo(
    () =>
      (schema?.fields || [])
        .filter((item) => item.subfields.length === 0)
        .map((item) => ({ key: item.key, name: item.name })),
    [schema],
  )

  useEffect(() => {
    if (!slug) return
    publicApi
      .techSchema(slug)
      .then(setSchema)
      .catch(() => setSchema(null))
  }, [slug])

  const loadList = useCallback(
    async (q?: string) => {
      if (!canList) return
      if (!flags.display && !String(q || '').trim()) {
        setRows([])
        return
      }
      setLoading(true)
      try {
        setRows(await publicApi.techList(slug, q))
      } catch (e) {
        message.error(e instanceof ApiError ? e.message : '加载失败')
      } finally {
        setLoading(false)
      }
    },
    [slug, canList, flags.display, message],
  )

  useEffect(() => {
    if (mode === 'list') void loadList(flags.display ? undefined : '')
    if (mode === 'query') setRows([])
  }, [mode, loadList, flags.display])

  useEffect(() => {
    if (mode !== 'detail') {
      setDetail(null)
      setMissing('')
      return
    }
    const recordId = Number(id)
    if (!recordId) {
      setMissing('内容不存在')
      return
    }
    publicApi
      .tech(slug, recordId)
      .then(setDetail)
      .catch((e) => setMissing(e instanceof ApiError ? e.message : '内容不存在'))
  }, [slug, mode, id])

  const onSubmit = async () => {
    try {
      const values = await form.validateFields()
      const extra: Record<string, unknown> = {}
      for (const field of schema?.fields || []) {
        let value = values[field.key]
        if (value != null && (field.type === 'date' || field.type === 'datetime') && dayjs.isDayjs(value)) {
          value = field.type === 'datetime' ? value.format('YYYY-MM-DD HH:mm:ss') : value.format('YYYY-MM-DD')
        }
        if (value !== undefined && value !== '') extra[field.key] = value
      }
      if (values._customerId) extra._customerId = values._customerId
      const payload = new FormData()
      payload.append('extra', JSON.stringify(extra))
      setSubmitting(true)
      const created = await publicApi.submitTech(slug, payload)
      message.success('提交成功')
      navigate(`${base}/${created.id}`, { replace: true })
    } catch (e) {
      if (e instanceof ApiError) message.error(e.message)
    } finally {
      setSubmitting(false)
    }
  }

  if (!kind) return <PublicClosed text="未知模块" />
  if (!flags.publicAccess) return <PublicClosed />
  if (mode === 'create' && !canCreate) return <PublicClosed text="该表单未开放填报" />
  if ((mode === 'list' || mode === 'query' || mode === 'detail') && !canList) return <PublicClosed />

  if (mode === 'detail') {
    if (missing) return <PublicClosed text={missing} />
    if (!detail) return null
    return (
      <Card
        title={detail.title || detail.code}
        extra={<Button onClick={() => navigate(base)}>返回列表</Button>}
      >
        <Descriptions column={2} bordered size="small">
          <Descriptions.Item label="编号">{detail.code}</Descriptions.Item>
          <Descriptions.Item label="类型">{detail.kindLabel}</Descriptions.Item>
          {(schema?.fields || [])
            .filter((f) => f.subfields.length === 0)
            .map((f) => (
              <Descriptions.Item key={f.key} label={f.name}>
                {cellText(detail.extra?.[f.key]) || '—'}
              </Descriptions.Item>
            ))}
        </Descriptions>
      </Card>
    )
  }

  if (mode === 'create') {
    return (
      <Card
        title={`填报${kind.label}`}
        extra={
          <Space>
            <Button onClick={() => navigate(base)}>返回列表</Button>
            <Button type="primary" loading={submitting} onClick={() => void onSubmit()}>
              提交
            </Button>
          </Space>
        }
      >
        <Form form={form} layout="vertical">
          <Form.Item name="_customerId" hidden>
            <Input />
          </Form.Item>
          <Row gutter={16}>
            {(schema?.fields || [])
              .filter((f) => f.subfields.length === 0)
              .map((field) => (
                <Col key={field.key} span={Math.min(24, Math.max(6, field.span || 12))}>
                  <Form.Item
                    name={field.key}
                    label={field.name}
                    rules={field.required ? [{ required: true, message: `请填写${field.name}` }] : undefined}
                  >
                    <FieldInput field={field} fields={namedFields} />
                  </Form.Item>
                </Col>
              ))}
          </Row>
        </Form>
      </Card>
    )
  }

  const columns: ColumnsType<PublicTech> = [
    { title: '编号', dataIndex: 'code', width: 160 },
    { title: '标题', dataIndex: 'title', ellipsis: true },
    { title: '类型', dataIndex: 'kindLabel', width: 180 },
    {
      title: '操作',
      key: 'actions',
      width: 100,
      render: (_: unknown, record) => (
        <Button type="link" size="small" onClick={() => navigate(`${base}/${record.id}`)}>
          查看
        </Button>
      ),
    },
  ]

  return (
    <Card
      title={mode === 'query' ? `查询${kind.label}` : `${kind.label}列表`}
      extra={
        canCreate ? (
          <Button type="primary" onClick={() => navigate(`${base}/new`)}>
            填报
          </Button>
        ) : null
      }
    >
      {flags.query || flags.display ? (
        <Space wrap style={{ marginBottom: 16 }}>
          <Input.Search
            allowClear
            placeholder="搜索编号 / 标题"
            onSearch={(value) => void loadList(value)}
            style={{ width: 320 }}
          />
          {flags.query && !flags.display && mode !== 'query' ? (
            <Typography.Text type="secondary">请输入关键字后查询</Typography.Text>
          ) : null}
        </Space>
      ) : null}
      <Table<PublicTech>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={false}
        locale={{ emptyText: '暂无数据' }}
        onRow={(record) => ({ onClick: () => navigate(`${base}/${record.id}`), style: { cursor: 'pointer' } })}
      />
    </Card>
  )
}
