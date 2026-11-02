import { useEffect, useMemo, useRef, useState } from 'react'
import { App, Button, Card, Col, Form, Row, Select, Space, Tag } from 'antd'
import { useNavigate, useParams } from 'react-router-dom'
import { maintApi, userApi, workflowApi, type MaintServiceOption, type TechSchema, type TechSchemaField } from '../../api'
import { ApiError } from '../../api/http'
import { useAuth } from '../../auth/AuthContext'
import { can } from '../../auth/permissions'
import { isWideField, QfFieldControl, QfSubtable } from '../../tech/QfFields'
import { useFormOverlay, useFormOverlayFooter } from '../../ui/CreateDrawer'
import { askQfSubmit } from '../../ui/QfSubmitConfirm'
import { AccessItem, useWorkflowRuntime } from '../../workflow/formAccess'
import { WorkflowBar } from '../../workflow/WorkflowBar'

const PATH = '/devices/maint-records'
const MODULE = 'DEVICE_MAINT'

type FormValues = Record<string, unknown>
type PendingFile = { fieldKey: string; files: File[] }

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
      for (const sub of field.subfields || []) {
        if (sub.key in next) next[sub.key] = convertFieldValue(sub, next[sub.key], toInput)
      }
      return next
    })
  }
  return value
}

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

export function MaintRecordFormPage() {
  const { message } = App.useApp()
  const { user: me } = useAuth()
  const navigate = useNavigate()
  const { id } = useParams()
  const recordId = id ? Number(id) : undefined
  const editing = recordId != null && !Number.isNaN(recordId)
  const allowed = editing ? can(me, MODULE, 'edit') || can(me, MODULE, 'view') : can(me, MODULE, 'create')
  const [form] = Form.useForm<FormValues>()
  const [services, setServices] = useState<MaintServiceOption[]>([])
  const [service, setService] = useState<string>()
  const [schema, setSchema] = useState<TechSchema | null>(null)
  const [users, setUsers] = useState<{ value: string; label: string }[]>([])
  const [title, setTitle] = useState('')
  const [statusLabel, setStatusLabel] = useState('')
  const [saving, setSaving] = useState(false)
  const pendingExtra = useRef<Record<string, unknown> | null>(null)
  const { runtime, reload } = useWorkflowRuntime(MODULE, editing ? recordId : undefined)
  const overlay = useFormOverlay()

  useEffect(() => {
    maintApi
      .services()
      .then(setServices)
      .catch((e) => message.error(e instanceof ApiError ? e.message : '加载维保服务失败'))
    userApi
      .list({ status: 'ENABLED', page: 1, size: 200 })
      .then((page) =>
        setUsers((page.items || []).map((item) => ({ value: String(item.id), label: item.displayName || item.username }))),
      )
      .catch(() => setUsers([]))
  }, [message])

  useEffect(() => {
    if (!service) {
      setSchema(null)
      return
    }
    let cancelled = false
    maintApi
      .schema(service)
      .then((next) => {
        if (!cancelled) setSchema(next)
      })
      .catch((e) => message.error(e instanceof ApiError ? e.message : '加载表单字段失败'))
    return () => {
      cancelled = true
    }
  }, [service, message])

  useEffect(() => {
    if (!editing || recordId == null) return
    maintApi
      .get(recordId)
      .then((record) => {
        setTitle(record.title)
        setStatusLabel(record.workflowStatusLabel || '')
        setService(record.service)
        form.setFieldValue('service', record.service)
        const extra = { ...(record.extra || {}) }
        for (const file of record.files || []) {
          const key = (file as { fieldKey?: string }).fieldKey
          if (!key) continue
          const list = Array.isArray(extra[key]) ? (extra[key] as unknown[]) : []
          extra[key] = [...list, file]
        }
        pendingExtra.current = extra
      })
      .catch((e) => message.error(e instanceof ApiError ? e.message : '加载记录失败'))
  }, [editing, recordId, form, message])

  useEffect(() => {
    if (!schema || !pendingExtra.current) return
    const extra = pendingExtra.current
    const values: FormValues = {}
    for (const field of schema.fields) {
      values[field.key] = convertFieldValue(field, extra[field.key], true)
    }
    form.setFieldsValue(values)
    pendingExtra.current = null
  }, [schema, form])

  const sections = useMemo(() => {
    const groups: { title: string; fields: TechSchemaField[] }[] = []
    for (const field of schema?.fields || []) {
      const heading = field.section || '表单信息'
      const last = groups[groups.length - 1]
      if (last && last.title === heading) last.fields.push(field)
      else groups.push({ title: heading, fields: [field] })
    }
    return groups
  }, [schema])

  const namedFields = useMemo(
    () => (schema?.fields || []).map((field) => ({ key: field.key, name: field.name })),
    [schema],
  )

  const collectExtra = async () => {
    const values = await form.validateFields()
    const extra: Record<string, unknown> = { service: values.service }
    const pending: PendingFile[] = []
    for (const field of schema?.fields || []) {
      if (field.type === 'subtable') {
        const rows = Array.isArray(values[field.key]) ? (values[field.key] as Record<string, unknown>[]) : []
        extra[field.key] = rows.map((row) => {
          const next: Record<string, unknown> = {}
          for (const sub of field.subfields || []) {
            const converted = convertFieldValue(sub, row[sub.key], false)
            if (converted !== undefined && converted !== '') next[sub.key] = converted
          }
          return next
        })
        continue
      }
      const taken = takePendingFiles(field, values[field.key])
      if (taken.pending.length) pending.push({ fieldKey: field.key, files: taken.pending })
      const value = field.type === 'attachment' || field.type === 'image' ? taken.stored : convertFieldValue(field, values[field.key], false)
      if (value !== undefined && value !== '') extra[field.key] = value
    }
    return { extra, pending }
  }

  const saveRecord = async () => {
    const { extra, pending } = await collectExtra()
    let savedId = recordId
    if (editing && recordId != null) {
      await maintApi.update(recordId, extra)
    } else {
      const created = await maintApi.create(extra)
      savedId = created.id
    }
    if (savedId) {
      for (const item of pending) {
        await maintApi.uploadFiles(savedId, item.files, item.fieldKey)
      }
    }
    return savedId
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
              try {
                await workflowApi.advance(MODULE, savedId, comment)
                message.success('已提交')
              } catch (err) {
                if (err instanceof ApiError && (err.message.includes('尚未发起') || err.message.includes('不由你处理'))) {
                  message.success('已暂存')
                } else {
                  throw err
                }
              }
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
  }, [overlay, saving, message, runtime, schema, form, editing, recordId])
  useFormOverlayFooter(overlayFooter)

  if (!allowed) return <Card>无权限</Card>

  const formBody = (
    <>
      {editing && recordId ? (
        <WorkflowBar
          module={MODULE}
          bizId={recordId}
          runtime={runtime}
          hideAdvance={Boolean(overlay?.active)}
          onSaveDraft={async () => {
            await saveRecord()
          }}
          onReloaded={async () => {
            await reload()
          }}
        />
      ) : null}
      <Form form={form} layout="vertical">
        <div className="qf-submit-section-title">维保服务</div>
        <Form.Item name="service" label="维保服务" rules={[{ required: true, message: '请选择维保服务' }]}>
          <Select
            showSearch
            optionFilterProp="label"
            placeholder="请选择要记录的维保服务"
            disabled={editing}
            options={services.map((item) => ({ value: item.slug, label: item.label }))}
            onChange={(value) => setService(value)}
          />
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
                      <QfFieldControl field={field} users={users} fields={namedFields} freeText />
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
      title={editing ? title || '维保服务记录' : '维保服务记录'}
      extra={
        <Space>
          {statusLabel ? <Tag color="orange">{statusLabel}</Tag> : null}
          <Button onClick={() => navigate(PATH)}>返回列表</Button>
        </Space>
      }
    >
      {formBody}
    </Card>
  )
}
