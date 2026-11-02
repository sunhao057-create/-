import { useCallback, useEffect, useMemo, useState } from 'react'
import { App, Button, Card, Col, Form, Input, Row, Space, Tag } from 'antd'
import { useNavigate, useParams } from 'react-router-dom'
import { techApi, userApi, workflowApi, type TechSchema, type TechSchemaField } from '../../api'
import { ApiError } from '../../api/http'
import { useAuth } from '../../auth/AuthContext'
import { can } from '../../auth/permissions'
import { isWideField, QfFieldControl, QfSubtable } from '../../tech/QfFields'
import { techKindBySlug } from '../../tech/catalog'
import { useFormOverlay, useFormOverlayFooter } from '../../ui/CreateDrawer'
import { askQfSubmit } from '../../ui/QfSubmitConfirm'
import { AccessItem, useWorkflowRuntime } from '../../workflow/formAccess'
import { WorkflowBar } from '../../workflow/WorkflowBar'

type FormValues = Record<string, unknown>

function toDateInput(value: unknown, datetime: boolean) {
  if (value == null || value === '') return undefined
  const text = String(value).trim()
  if (datetime) return text.replace(' ', 'T').slice(0, 16)
  return text.slice(0, 10)
}

function fromDateInput(value: unknown, datetime: boolean) {
  if (value == null || value === '') return undefined
  const text = String(value)
  if (datetime) return text.includes('T') ? text.replace('T', ' ') + (text.length <= 16 ? ':00' : '') : text
  return text
}

function convertFieldValue(field: TechSchemaField, value: unknown, toInput: boolean): unknown {
  if (field.type === 'date' || field.type === 'datetime') {
    return toInput ? toDateInput(value, field.type === 'datetime') : fromDateInput(value, field.type === 'datetime')
  }
  if (field.type === 'member' && value != null && value !== '') return String(value)
  if (field.type === 'subtable' && Array.isArray(value)) {
    return value.map((row) => {
      const next: Record<string, unknown> = { ...(row as Record<string, unknown>) }
      for (const sub of field.subfields) {
        if (sub.key in next) next[sub.key] = convertFieldValue(sub, next[sub.key], toInput)
      }
      return next
    })
  }
  return value
}

type PendingFile = { fieldKey: string; files: File[] }

function takePendingFiles(field: TechSchemaField, value: unknown): { stored: unknown; pending: File[] } {
  if ((field.type !== 'attachment' && field.type !== 'image') || !Array.isArray(value)) {
    return { stored: value, pending: [] }
  }
  const pending: File[] = []
  const stored = value
    .map((item) => {
      const row = item as Record<string, unknown>
      const file = row.originFileObj as File | undefined
      if (file) pending.push(file)
      if (row.id || row.url) {
        return { id: row.id, name: row.name ?? row.originalName, originalName: row.originalName ?? row.name, url: row.url }
      }
      return null
    })
    .filter(Boolean)
  return { stored, pending }
}

export function TechFormPage() {
  const { message } = App.useApp()
  const { slug = '', id } = useParams()
  const navigate = useNavigate()
  const { user: me } = useAuth()
  const kind = techKindBySlug(slug)
  const module = kind?.module || ''
  const recordId = id ? Number(id) : undefined
  const editing = recordId != null && !Number.isNaN(recordId)
  const allowed = editing ? can(me, module, 'edit') || can(me, module, 'view') : can(me, module, 'create')

  const [form] = Form.useForm<FormValues>()
  const [schema, setSchema] = useState<TechSchema | null>(null)
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [users, setUsers] = useState<{ value: string; label: string }[]>([])
  const [title, setTitle] = useState('')
  const [statusLabel, setStatusLabel] = useState('')
  const { runtime, reload } = useWorkflowRuntime(module, editing ? recordId : undefined)
  const overlay = useFormOverlay()

  const resultKey = schema?.fields.find((field) => field.name === '办理结果')?.key
  const abortKey = schema?.fields.find((field) => field.name === '中止原因')?.key
  const result = Form.useWatch(resultKey || '__none__', form)

  useEffect(() => {
    if (!slug) return
    techApi
      .schema(slug)
      .then(setSchema)
      .catch((e) => message.error(e instanceof ApiError ? e.message : '加载表单结构失败'))
  }, [slug, message])

  useEffect(() => {
    userApi
      .list({ status: 'ENABLED', page: 1, size: 200 })
      .then((page) =>
        setUsers(
          (page.items || []).map((item) => ({
            value: String(item.id),
            label: item.displayName || item.username,
          })),
        ),
      )
      .catch(() => setUsers([]))
  }, [])

  const loadRecord = useCallback(async () => {
    if (!slug || !editing || recordId == null || !schema) return
    setLoading(true)
    try {
      const record = await techApi.get(slug, recordId)
      setTitle(record.title)
      setStatusLabel(record.workflowStatusLabel || '')
      const extra = record.extra || {}
      const values: FormValues = {}
      if (extra._customerId != null) values._customerId = extra._customerId
      for (const field of schema.fields) {
        let value = extra[field.key]
        if ((field.type === 'attachment' || field.type === 'image') && !value) {
          value = (record.files || []).filter((file) => (file as { fieldKey?: string }).fieldKey === field.key)
        }
        values[field.key] = convertFieldValue(field, value, true)
      }
      form.setFieldsValue(values)
    } catch (e) {
      message.error(e instanceof ApiError ? e.message : '加载记录失败')
    } finally {
      setLoading(false)
    }
  }, [slug, editing, recordId, schema, form, message])

  useEffect(() => {
    if (schema) void loadRecord()
  }, [schema, loadRecord])

  const visibleFields = useMemo(
    () =>
      (schema?.fields || []).filter((field) => !(abortKey && field.key === abortKey && result !== '用户中止')),
    [schema, abortKey, result],
  )
  const namedFields = useMemo(() => visibleFields.map((field) => ({ key: field.key, name: field.name })), [visibleFields])
  const sections = useMemo(() => {
    const groups: { title: string; fields: TechSchemaField[] }[] = []
    for (const field of visibleFields) {
      const heading = field.section || '表单信息'
      const last = groups[groups.length - 1]
      if (last && last.title === heading) last.fields.push(field)
      else groups.push({ title: heading, fields: [field] })
    }
    return groups
  }, [visibleFields])

  const collectExtra = async () => {
    const values = await form.validateFields()
    const extra: Record<string, unknown> = {}
    const pending: PendingFile[] = []
    for (const field of visibleFields) {
      if (field.type === 'subtable') {
        const rows = Array.isArray(values[field.key]) ? (values[field.key] as Record<string, unknown>[]) : []
        extra[field.key] = rows.map((row) => {
          const next: Record<string, unknown> = {}
          for (const sub of field.subfields) {
            const converted = convertFieldValue(sub, row[sub.key], false)
            if (converted !== undefined && converted !== '') next[sub.key] = converted
          }
          return next
        })
        continue
      }
      const taken = takePendingFiles(field, values[field.key])
      if (taken.pending.length) pending.push({ fieldKey: field.key, files: taken.pending })
      let value = field.type === 'attachment' || field.type === 'image' ? taken.stored : convertFieldValue(field, values[field.key], false)
      if (value !== undefined && value !== '') extra[field.key] = value
    }
    if (values._customerId) extra._customerId = values._customerId
    return { extra, pending }
  }

  const saveRecord = async () => {
    if (!slug) return
    const { extra, pending } = await collectExtra()
    let savedId = recordId
    if (editing && recordId != null) {
      await techApi.update(slug, recordId, extra)
    } else {
      const created = await techApi.create(slug, extra)
      savedId = created.id
    }
    if (savedId) {
      for (const item of pending) {
        await techApi.uploadFiles(slug, savedId, item.files, item.fieldKey)
      }
    }
    return savedId
  }

  const finishSubmit = async (savedId: number, comment?: string) => {
    try {
      await workflowApi.advance(module, savedId, comment)
      message.success('已提交')
    } catch (err) {
      if (err instanceof ApiError && (err.message.includes('尚未发起') || err.message.includes('不由你处理'))) {
        message.success('已暂存')
      } else {
        throw err
      }
    }
  }

  const overlayFooter = useMemo(() => {
    if (!overlay?.active) return null
    return (
      <Space>
        <Button onClick={overlay.close}>取消</Button>
        <Button
          loading={saving}
          onClick={() => {
            setSaving(true)
            void saveRecord()
              .then(() => {
                message.success('已暂存')
                overlay.close()
              })
              .catch((e) => message.error(e instanceof ApiError ? e.message : '保存失败'))
              .finally(() => setSaving(false))
          }}
        >
          暂存
        </Button>
        <Button
          type="primary"
          loading={saving}
          onClick={() => {
            setSaving(true)
            void (async () => {
              const savedId = await saveRecord()
              if (!savedId) return
              const comment = await askQfSubmit({
                nextNodeName: runtime?.nextNodeName || runtime?.currentNodeName,
                assignees: runtime?.currentAssignees,
              })
              if (comment == null) return
              await finishSubmit(savedId, comment)
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
  }, [overlay, saving, message, module, runtime])
  useFormOverlayFooter(overlayFooter)

  if (!kind) return <Card>未知模块</Card>
  if (!allowed) return <Card>无权限</Card>

  const formBody = (
    <>
      {editing && recordId ? (
        <WorkflowBar
          module={module}
          bizId={recordId}
          runtime={runtime}
          hideAdvance={Boolean(overlay?.active)}
          onSaveDraft={saveRecord}
          onReloaded={async () => {
            await reload()
            await loadRecord()
          }}
        />
      ) : null}
      <Form form={form} layout="vertical">
        <Form.Item name="_customerId" hidden>
          <Input />
        </Form.Item>
        {sections.map((section) => (
          <div key={section.title} style={{ marginBottom: 16 }}>
            <div className="qf-submit-section-title">{section.title}</div>
            <Row gutter={16}>
              {section.fields.map((field) => (
                <Col key={field.key} span={isWideField(field) ? 24 : Math.min(24, Math.max(6, field.span || 12))}>
                  {field.type === 'subtable' ? (
                    <AccessItem runtime={runtime} field={field.key} label={field.name}>
                      <QfSubtable field={field} users={users} fields={namedFields} />
                    </AccessItem>
                  ) : (
                    <AccessItem
                      runtime={runtime}
                      field={field.key}
                      name={field.key}
                      label={field.name}
                      rules={field.required ? [{ required: true, message: `请填写${field.name}` }] : undefined}
                    >
                      <QfFieldControl field={field} users={users} fields={namedFields} />
                    </AccessItem>
                  )}
                </Col>
              ))}
            </Row>
          </div>
        ))}
      </Form>
    </>
  )

  if (overlay?.active) return formBody

  return (
    <Card
      loading={loading && !schema}
      title={editing ? title || kind.label : kind.label}
      extra={
        <Space>
          {statusLabel ? <Tag color="orange">{statusLabel}</Tag> : null}
          <Button onClick={() => navigate(`/tech/${slug}`)}>返回列表</Button>
        </Space>
      }
    >
      {formBody}
    </Card>
  )
}
