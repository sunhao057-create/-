import { useEffect, useMemo, useRef, useState } from 'react'
import {
  App,
  Button,
  Card,
  Dropdown,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Radio,
  Segmented,
  Select,
  Space,
  Tag,
  Tooltip,
  Typography,
} from 'antd'
import {
  ApartmentOutlined,
  ArrowLeftOutlined,
  CheckCircleOutlined,
  DeleteOutlined,
  FlagOutlined,
  MailOutlined,
  MinusOutlined,
  PlusOutlined,
} from '@ant-design/icons'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { lookupApi, workflowApi } from '../api'
import { ApiError } from '../api/http'
import { useAuth } from '../auth/AuthContext'
import { can } from '../auth/permissions'
import { isRemovedSidebarModule } from '../qingflow/businessCatalog'
import { FORM_MODULES } from '../role/modules'
import type {
  FieldMode,
  FormFieldMeta,
  Staff,
  Workflow,
  WorkflowCondition,
  WorkflowEdge,
  WorkflowNode,
} from '../types'
import { PreviewTable } from '../ui/PreviewTable'
import { EntityDrawer } from '../ui/CreateDrawer'
import { RowActions } from '../ui/RowActions'
import {
  belowLastNode,
  canvasSize,
  conditionOffset,
  emptyGraph,
  layoutVertical,
  looksHorizontal,
  nodeSize,
  PAD_X,
  PAD_Y,
  verticalEdgeGeometry,
} from '../workflow/canvas'
import { statusLabel } from '../utils/format'

type FormOption = { value: string; label: string }

function nativeFormOptions(): FormOption[] {
  return FORM_MODULES.filter(
    (item) =>
      item.hasCreate &&
      item.hasDataScope &&
      !item.moduleLabel.includes('隐藏') &&
      !isRemovedSidebarModule(item.module),
  ).map((item) => ({ value: item.module, label: item.moduleLabel }))
}

function bindFormOptions(): FormOption[] {
  return nativeFormOptions()
}

function bindFormLabel(code: string, options: FormOption[]) {
  return options.find((item) => item.value === code)?.label || statusLabel(code)
}

const ZOOM_MIN = 0.4
const ZOOM_MAX = 2

function clampZoom(value: number) {
  return Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, Math.round(value * 100) / 100))
}

const OPS = [
  { value: 'EQ', label: '等于' },
  { value: 'NE', label: '不等于' },
  { value: 'CONTAINS', label: '包含' },
  { value: 'GT', label: '大于' },
  { value: 'GTE', label: '大于等于' },
  { value: 'LT', label: '小于' },
  { value: 'LTE', label: '小于等于' },
  { value: 'EMPTY', label: '为空' },
  { value: 'NOT_EMPTY', label: '不为空' },
]

function nodeTypeLabel(type: string) {
  if (type === 'START') return '开始'
  if (type === 'END') return '结束'
  if (type === 'CC') return '抄送'
  if (type === 'BRANCH') return '分支'
  if (type === 'CONDITION') return '分支条件'
  if (type === 'TASK') return '办理'
  return '审批'
}

function nodeKind(type: string) {
  if (type === 'START') return 'start'
  if (type === 'END') return 'end'
  if (type === 'CC') return 'cc'
  if (type === 'BRANCH') return 'branch'
  if (type === 'CONDITION') return 'condition'
  return 'approval'
}

function assigneeCaption(node: WorkflowNode, staff: Staff[], fields: FormFieldMeta[]) {
  if (node.type !== 'APPROVAL' && node.type !== 'CC' && node.type !== 'TASK') return ''
  if ((node.assigneeType || 'STARTER') === 'STARTER') return '由发起人办理'
  if (node.assigneeType === 'ORG_SUPERVISOR') return '由部门主管审批'
  if (node.assigneeType === 'ORG_CHAIN') {
    const levels = node.assigneeLevels
    return levels && levels > 0 ? `逐级审批（向上 ${levels} 级）` : '逐级审批（直到最高部门）'
  }
  if (node.assigneeType === 'FORM_FIELD') {
    const label = fields.find((item) => item.key === node.assigneeField)?.label
    return label ? `按「${label}」办理` : '按表单成员办理'
  }
  const names = (node.assigneeUserIds || [])
    .map((id) => staff.find((item) => item.id === id)?.displayName)
    .filter(Boolean)
  return names.length ? names.join('、') : '指定人员办理'
}

function opLabel(op?: string) {
  return OPS.find((item) => item.value === op)?.label || op || '等于'
}

function conditionCaption(conditions: WorkflowCondition[] | undefined, fields: FormFieldMeta[], isDefault?: boolean) {
  const rows = (conditions || []).filter((item) => item.field)
  if (!rows.length) return isDefault ? '其他情况（默认）' : '未设置判断条件'
  return rows
    .map((item, index) => {
      const label = fields.find((field) => field.key === item.field)?.label || item.field
      const prev = rows[index - 1]
      const join = index === 0 ? '' : (prev?.group || 0) === (item.group || 0) ? ' 且 ' : ' 或 '
      const valuePart = needsCompareValue(item.op) ? ` ${item.value || ''}` : ''
      return `${join}${label} ${opLabel(item.op)}${valuePart}`
    })
    .join('')
}

function needsCompareValue(op?: string) {
  return op !== 'EMPTY' && op !== 'NOT_EMPTY'
}

function edgeCaption(edge: WorkflowEdge, fields: FormFieldMeta[] = []) {
  if (edge.name) return edge.name
  return conditionCaption(edge.conditions, fields, edge.isDefault)
}

function graphPayload(nodes: WorkflowNode[], edges: WorkflowEdge[]) {
  return {
    nodes,
    edges: edges.map((edge) => ({
      from: edge.from,
      to: edge.to,
      name: edge.name,
      isDefault: !!edge.isDefault,
      conditions: edge.conditions || [],
    })),
  }
}

export function WorkflowsPage() {
  const { id } = useParams()
  if (id) return <Designer id={Number(id)} />
  return <List />
}

function List() {
  const { message } = App.useApp()
  const { user: me } = useAuth()
  const navigate = useNavigate()
  const canView = can(me, 'WORKFLOW', 'view')
  const canEdit = can(me, 'WORKFLOW', 'edit')
  const canDelete = can(me, 'WORKFLOW', 'delete')
  const [loading, setLoading] = useState(false)
  const [rows, setRows] = useState<Workflow[]>([])
  const [creating, setCreating] = useState(false)
  const [saving, setSaving] = useState(false)
  const [keyword, setKeyword] = useState('')
  const [form] = Form.useForm<{ name: string; code: string; formModule: string }>()
  const formOptions = useMemo(() => bindFormOptions(), [])

  const load = async () => {
    setLoading(true)
    try {
      setRows(await workflowApi.list())
    } catch (e) {
      message.error(e instanceof ApiError ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [])

  const filtered = useMemo(() => {
    const q = keyword.trim().toLowerCase()
    if (!q) return rows
    return rows.filter((row) => {
      const hay = [row.name, row.code, row.formModuleLabel, row.formModule, row.published ? '已发布' : '草稿']
        .filter(Boolean)
        .join(' ')
        .toLowerCase()
      return hay.includes(q)
    })
  }, [rows, keyword])

  return (
    <Card>
      <div className="wf-list-head">
        <div>
          <h3>流程设计</h3>
          <p>为项目、付款申请、客户、供应商配置审批路径，点击流程进入画布。</p>
        </div>
        <Space wrap>
          <Input.Search
            allowClear
            placeholder="搜索流程名称 / 绑定表单 / 状态"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onSearch={setKeyword}
            style={{ width: 280, maxWidth: '100%' }}
          />
          {canEdit ? (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                form.resetFields()
                form.setFieldsValue({ formModule: 'PROJECT' })
                setCreating(true)
              }}
            >
              新建流程
            </Button>
          ) : null}
        </Space>
      </div>
      <PreviewTable
        rowKey="id"
        loading={loading}
        dataSource={filtered}
        pagination={false}
        previewTitle={(row) => row.name || '流程详情'}
        previewLoad={(row) => workflowApi.get(row.id)}
        previewLink={(row) => ({ label: '打开设计', path: `/workflows/${row.id}` })}
        scroll={{ x: 720 }}
        columns={[
          { title: '流程名称', dataIndex: 'name', ellipsis: true },
          {
            title: '绑定表单',
            dataIndex: 'formModuleLabel',
            width: 260,
            render: (_, row) => bindFormLabel(row.formModule, formOptions) || row.formModuleLabel,
          },
          {
            title: '状态',
            width: 100,
            render: (_, row) => (row.published ? <Tag color="success">已发布</Tag> : <Tag>草稿</Tag>),
          },
          {
            title: '操作',
            width: 240,
            render: (_, row) => (
              <RowActions
                canView={canView}
                canEdit={canEdit}
                canDelete={canDelete && !row.published}
                deleteTitle="确认删除该流程？"
                onEdit={() => navigate(`/workflows/${row.id}`)}
                extra={
                  canEdit && !row.published ? (
                    <Button
                      size="small"
                      type="link"
                      onClick={async () => {
                        try {
                          await workflowApi.publish(row.id)
                          message.success('已发布')
                          await load()
                        } catch (e) {
                          message.error(e instanceof ApiError ? e.message : '发布失败')
                        }
                      }}
                    >
                      发布
                    </Button>
                  ) : canEdit && row.published ? (
                    <Popconfirm
                      title="取消发布后，新单据将不再走该流程"
                      okText="确定"
                      cancelText="取消"
                      onConfirm={async () => {
                        try {
                          await workflowApi.unpublish(row.id)
                          message.success('已取消发布')
                          await load()
                        } catch (e) {
                          message.error(e instanceof ApiError ? e.message : '操作失败')
                        }
                      }}
                    >
                      <Button size="small" type="link">
                        取消发布
                      </Button>
                    </Popconfirm>
                  ) : null
                }
                onDelete={async () => {
                  await workflowApi.remove(row.id)
                  message.success('已删除')
                  await load()
                }}
              />
            ),
          },
        ]}
      />
      <EntityDrawer
        title="新建流程"
        open={creating}
        confirmLoading={saving}
        okText="创建并设计"
        cancelText="取消"
        onClose={() => setCreating(false)}
        onOk={() => form.submit()}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={async (values) => {
            setSaving(true)
            try {
              const created = await workflowApi.create({
                ...values,
                code: `LC${Date.now()}`,
                graph: emptyGraph(),
              })
              message.success('已创建，开始设计')
              setCreating(false)
              navigate(`/workflows/${created.id}`)
            } catch (e) {
              message.error(e instanceof ApiError ? e.message : '创建失败')
            } finally {
              setSaving(false)
            }
          }}
        >
          <Form.Item name="name" label="流程名称" rules={[{ required: true, message: '请填写名称' }]}>
            <Input placeholder="例如 项目立项流程" />
          </Form.Item>
          <Form.Item name="formModule" label="绑定表单" rules={[{ required: true }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={formOptions}
              onChange={(value) => {
                const label = bindFormLabel(value, formOptions)
                const current = String(form.getFieldValue('name') || '')
                if (label && (!current || current.endsWith('流程'))) {
                  form.setFieldValue('name', `${label}流程`)
                }
              }}
            />
          </Form.Item>
        </Form>
      </EntityDrawer>
    </Card>
  )
}

function Designer({ id }: { id: number }) {
  const { message } = App.useApp()
  const { user: me } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const fromPath = (location.state as { fromPath?: string } | null)?.fromPath
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [name, setName] = useState('')
  const [code, setCode] = useState('')
  const [formModule, setFormModule] = useState('PROJECT')
  const canEdit = can(me, 'WORKFLOW', 'edit') || can(me, formModule, 'flow')
  const [formModuleLabel, setFormModuleLabel] = useState('')
  const [formOptions, setFormOptions] = useState<FormOption[]>(nativeFormOptions())
  const designerOptions = useMemo(() => {
    if (!formModule || formOptions.some((item) => item.value === formModule)) return formOptions
    return [{ value: formModule, label: formModuleLabel || statusLabel(formModule) }, ...formOptions]
  }, [formModule, formModuleLabel, formOptions])
  const [published, setPublished] = useState(false)
  const [nodes, setNodes] = useState<WorkflowNode[]>([])
  const [edges, setEdges] = useState<WorkflowEdge[]>([])
  const [selectedKey, setSelectedKey] = useState<string | null>(null)
  const [hoveredEdge, setHoveredEdge] = useState<string | null>(null)
  const [insertMenuEdge, setInsertMenuEdge] = useState<string | null>(null)
  const [linkFrom, setLinkFrom] = useState<string | null>(null)
  const [fields, setFields] = useState<FormFieldMeta[]>([])
  const [staff, setStaff] = useState<Staff[]>([])
  const [editingEdge, setEditingEdge] = useState<{ from: string; to: string } | null>(null)
  const drag = useRef<{ key: string; dx: number; dy: number } | null>(null)
  const pan = useRef<{ x: number; y: number; ox: number; oy: number } | null>(null)
  const hoverClear = useRef<ReturnType<typeof setTimeout> | null>(null)
  const canvasRef = useRef<HTMLDivElement>(null)
  const zoomRef = useRef(1)
  const panRef = useRef({ x: 0, y: 0 })
  const [zoom, setZoom] = useState(1)
  const [panOffset, setPanOffset] = useState({ x: 0, y: 0 })
  const [panning, setPanning] = useState(false)

  const setCanvasZoom = (next: number, origin?: { x: number; y: number }) => {
    const clamped = clampZoom(next)
    const prev = zoomRef.current
    if (clamped === prev) return
    const canvas = canvasRef.current
    if (canvas && origin) {
      const rect = canvas.getBoundingClientRect()
      const mx = origin.x - rect.left
      const my = origin.y - rect.top
      const gx = (mx - panRef.current.x) / prev
      const gy = (my - panRef.current.y) / prev
      const nextPan = { x: mx - gx * clamped, y: my - gy * clamped }
      panRef.current = nextPan
      setPanOffset(nextPan)
    }
    zoomRef.current = clamped
    setZoom(clamped)
  }

  const enterEdge = (eid: string) => {
    if (hoverClear.current) {
      clearTimeout(hoverClear.current)
      hoverClear.current = null
    }
    setHoveredEdge(eid)
  }

  const leaveEdge = (eid: string) => {
    if (hoverClear.current) clearTimeout(hoverClear.current)
    hoverClear.current = setTimeout(() => {
      setHoveredEdge((cur) => (cur === eid ? null : cur))
      hoverClear.current = null
    }, 160)
  }
  const selected = nodes.find((item) => item.key === selectedKey) || null
  const memberFields = fields.filter((item) => item.member)
  const formFields = fields.filter((item) => !item.key.startsWith('_action_'))
  const actionFields = fields.filter((item) => item.key.startsWith('_action_'))
  const outgoing = edges.filter((item) => item.from === selected?.key)
  const blankCondition = (group = 0): WorkflowCondition => ({
    field: formFields[0]?.key || '',
    op: 'EQ',
    value: '',
    group,
  })
  const editing = editingEdge
    ? edges.find((item) => item.from === editingEdge.from && item.to === editingEdge.to)
    : null

  const incomingBranchEdge = (condKey: string) =>
    edges.find((item) => item.to === condKey && nodes.find((node) => node.key === item.from)?.type === 'BRANCH')
    || edges.find((item) => item.to === condKey)

  const addConditionPath = (branchKey: string) => {
    const branch = nodes.find((item) => item.key === branchKey)
    if (!branch) return
    const existing = edges.filter((item) => item.from === branchKey)
    const stamp = Date.now()
    const key = `cond${stamp}`
    const index = existing.length
    const pos = conditionOffset(branch, index)
    setNodes((prev) => [
      ...prev,
      {
        key,
        name: `分支条件${index + 1}`,
        type: 'CONDITION',
        x: pos.x,
        y: pos.y,
        fields: {},
      },
    ])
    setEdges((prev) => [
      ...prev,
      {
        from: branchKey,
        to: key,
        name: `分支条件${index + 1}`,
        isDefault: existing.length > 0 && existing.every((item) => !item.isDefault),
        conditions: existing.length === 0 ? [blankCondition()] : [],
      },
    ])
    setSelectedKey(key)
  }

  const addPath = (from: string, to: string) => {
    setEdges((prev) => {
      if (prev.some((item) => item.from === from && item.to === to)) return prev
      const existing = prev.filter((item) => item.from === from)
      const first = existing.length === 0
      const fromNode = nodes.find((item) => item.key === from)
      const isBranch = fromNode?.type === 'BRANCH'
      const isCondition = fromNode?.type === 'CONDITION'
      return [
        ...prev,
        {
          from,
          to,
          name: first ? '默认' : '',
          isDefault: first || isCondition,
          conditions: isBranch && !first && !isCondition ? [blankCondition()] : [],
        },
      ]
    })
  }

  const load = async () => {
    setLoading(true)
    try {
      const detail = await workflowApi.get(id)
      setName(detail.name)
      setCode(detail.code)
      setFormModule(detail.formModule)
      setFormModuleLabel(detail.formModuleLabel || '')
      setPublished(detail.published)
      const loaded = detail.nodes || []
      const loadedEdges = detail.edges || []
      const placed = looksHorizontal(loaded) ? layoutVertical(loaded, loadedEdges) : loaded
      const minX = placed.reduce((min, item) => Math.min(min, item.x), 96)
      const shift = minX < 88 ? 88 - minX : 0
      setNodes(shift ? placed.map((item) => ({ ...item, x: item.x + shift })) : placed)
      setEdges(loadedEdges)
      zoomRef.current = 1
      setZoom(1)
      panRef.current = { x: 0, y: 0 }
      setPanOffset({ x: 0, y: 0 })
      setFields(await workflowApi.formFields(detail.formModule))
      setSelectedKey(detail.nodes.find((item) => item.type === 'APPROVAL' || item.type === 'TASK')?.key || detail.nodes[0]?.key || null)
      try {
        setStaff(await lookupApi.staff())
      } catch {
        setStaff([])
      }
      setFormOptions(bindFormOptions())
    } catch (e) {
      message.error(e instanceof ApiError ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [id])

  useEffect(() => {
    if (loading) return
    const el = canvasRef.current
    if (!el) return
    const onWheel = (event: WheelEvent) => {
      event.preventDefault()
      const factor = event.deltaY < 0 ? 1.1 : 1 / 1.1
      setCanvasZoom(zoomRef.current * factor, { x: event.clientX, y: event.clientY })
    }
    el.addEventListener('wheel', onWheel, { passive: false })
    return () => el.removeEventListener('wheel', onWheel)
  }, [loading])

  useEffect(() => {
    if (!formModule) return
    let cancelled = false
    void workflowApi
      .formFields(formModule)
      .then((next) => {
        if (cancelled) return
        setFields(next)
        if (next.length === 0) return
        const fieldKeys = new Set(next.map((item) => item.key))
        setNodes((prev) =>
          prev.map((item) => {
            if (item.assigneeType !== 'FORM_FIELD') return item
            if (item.assigneeField && fieldKeys.has(item.assigneeField)) return item
            return { ...item, assigneeType: 'STARTER', assigneeField: undefined }
          }),
        )
      })
      .catch(() => {
        if (!cancelled) setFields([])
      })
    return () => {
      cancelled = true
    }
  }, [formModule])

  const save = async (publish = false) => {
    for (const node of nodes) {
      if (node.type !== 'APPROVAL' && node.type !== 'CC' && node.type !== 'TASK') continue
      if (node.assigneeType === 'USER' && !(node.assigneeUserIds || []).some((id) => id > 0)) {
        message.error(`${node.name} 请选择办理人`)
        return
      }
      if (node.assigneeType === 'FORM_FIELD' && !memberFields.some((item) => item.key === node.assigneeField)) {
        message.error(`${node.name} 请选择表单中的成员字段`)
        return
      }
    }
    setSaving(true)
    try {
      await workflowApi.update(id, { name, code, formModule, graph: graphPayload(nodes, edges) })
      if (publish) {
        await workflowApi.publish(id)
        setPublished(true)
        message.success('已保存并发布')
      } else {
        message.success('已保存')
      }
    } catch (e) {
      message.error(e instanceof ApiError ? e.message : '保存失败')
    } finally {
      setSaving(false)
    }
  }

  const insertOnEdge = (from: string, to: string, type: 'APPROVAL' | 'CC' | 'BRANCH') => {
    const fromNode = nodes.find((item) => item.key === from)
    const toNode = nodes.find((item) => item.key === to)
    const old = edges.find((item) => item.from === from && item.to === to)
    if (!fromNode || !toNode || !old) return
    if (fromNode.type === 'BRANCH') {
      message.warning('分支节点请用「添加条件分支」，不能在分支连线上直接插入')
      return
    }
    const stamp = Date.now()
    const rest = edges.filter((item) => !(item.from === from && item.to === to))
    const keep = {
      name: old.name,
      isDefault: !!old.isDefault,
      conditions: old.conditions || [],
    }
    if (type === 'BRANCH') {
      const key = `branch${stamp}`
      const c1 = `cond${stamp}a`
      const c2 = `cond${stamp}b`
      const nextNodes: WorkflowNode[] = [
        ...nodes,
        { key, name: `分支${nodes.length}`, type: 'BRANCH', x: fromNode.x, y: fromNode.y, fields: {} },
        { key: c1, name: '分支条件1', type: 'CONDITION', x: fromNode.x, y: fromNode.y, fields: {} },
        { key: c2, name: '分支条件2', type: 'CONDITION', x: fromNode.x, y: fromNode.y, fields: {} },
      ]
      const nextEdges: WorkflowEdge[] = [
        ...rest,
        { from, to: key, ...keep },
        { from: key, to: c1, name: '分支条件1', isDefault: false, conditions: [blankCondition(0)] },
        { from: key, to: c2, name: '分支条件2', isDefault: true, conditions: [] },
        { from: c1, to, isDefault: true, conditions: [] },
        { from: c2, to, isDefault: true, conditions: [] },
      ]
      setNodes(layoutVertical(nextNodes, nextEdges))
      setEdges(nextEdges)
      setSelectedKey(c1)
      return
    }
    const key = `${type.toLowerCase()}${stamp}`
    const names = { APPROVAL: '审批', CC: '抄送' }
    const nextNodes: WorkflowNode[] = [
      ...nodes,
      {
        key,
        name: `${names[type]}${nodes.length}`,
        type,
        x: fromNode.x,
        y: fromNode.y,
        fields: {},
        assigneeType: 'STARTER',
        assigneeUserIds: [],
      },
    ]
    const nextEdges: WorkflowEdge[] = [
      ...rest,
      { from, to: key, ...keep },
      { from: key, to, isDefault: true, conditions: [] },
    ]
    setNodes(layoutVertical(nextNodes, nextEdges))
    setEdges(nextEdges)
    setSelectedKey(key)
  }

  const addNode = (type: 'APPROVAL' | 'CC' | 'BRANCH' | 'END') => {
    if (
      type !== 'END' &&
      selected &&
      selected.type !== 'BRANCH' &&
      selected.type !== 'END'
    ) {
      const outgoingEdges = edges.filter((item) => item.from === selected.key)
      if (outgoingEdges.length === 1) {
        insertOnEdge(outgoingEdges[0].from, outgoingEdges[0].to, type)
        return
      }
    }
    const names = { APPROVAL: '审批', CC: '抄送', BRANCH: '分支', END: '结束' }
    const stamp = Date.now()
    const key = `${type.toLowerCase()}${stamp}`
    const pos = belowLastNode(nodes, type)
    if (type === 'BRANCH') {
      const c1 = `cond${stamp}a`
      const c2 = `cond${stamp}b`
      const branchNode = { ...pos, type: 'BRANCH' as const }
      const first = conditionOffset(branchNode, 0, 2)
      const second = conditionOffset(branchNode, 1, 2)
      setNodes((prev) => [
        ...prev,
        { key, name: `分支${prev.length}`, type: 'BRANCH', x: pos.x, y: pos.y, fields: {} },
        { key: c1, name: '分支条件1', type: 'CONDITION', x: first.x, y: first.y, fields: {} },
        { key: c2, name: '分支条件2', type: 'CONDITION', x: second.x, y: second.y, fields: {} },
      ])
      setEdges((prev) => [
        ...prev,
        { from: key, to: c1, name: '分支条件1', isDefault: false, conditions: [blankCondition(0)] },
        { from: key, to: c2, name: '分支条件2', isDefault: true, conditions: [] },
      ])
      setSelectedKey(c1)
      setEditingEdge({ from: key, to: c1 })
      return
    }
    setNodes((prev) => [
      ...prev,
      {
        key,
        name: `${names[type]}${prev.length}`,
        type,
        x: pos.x,
        y: pos.y,
        fields: {},
        assigneeType: type === 'APPROVAL' || type === 'CC' ? 'STARTER' : undefined,
        assigneeUserIds: [],
      },
    ])
    setSelectedKey(key)
  }

  const removeNode = () => {
    if (!selected || selected.type === 'START') {
      message.warning('开始节点不能删除')
      return
    }
    const extra =
      selected.type === 'BRANCH'
        ? edges.filter((item) => item.from === selected.key).map((item) => item.to)
        : []
    const drop = new Set([selected.key, ...extra.filter((key) => nodes.find((item) => item.key === key)?.type === 'CONDITION')])
    setNodes((prev) => prev.filter((item) => !drop.has(item.key)))
    setEdges((prev) => prev.filter((item) => !drop.has(item.from) && !drop.has(item.to)))
    setSelectedKey(null)
  }

  const setFieldMode = (field: string, mode: FieldMode) => {
    if (!selected) return
    setNodes((prev) =>
      prev.map((item) =>
        item.key === selected.key ? { ...item, fields: { ...(item.fields || {}), [field]: mode } } : item,
      ),
    )
  }

  const lines = useMemo(
    () =>
      edges
        .map((edge) => {
          const from = nodes.find((item) => item.key === edge.from)
          const to = nodes.find((item) => item.key === edge.to)
          if (!from || !to) return null
          return {
            ...edge,
            ...verticalEdgeGeometry(from, to),
          }
        })
        .filter(Boolean) as Array<WorkflowEdge & { d: string; labelX: number; labelY: number }>,
    [edges, nodes],
  )
  const board = canvasSize(nodes)

  return (
    <Card className="wf-page-card" loading={loading}>
      <div className="wf-toolbar">
        <div className="wf-toolbar-left">
          <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate(fromPath || '/workflows')}>
            返回
          </Button>
          <Input
            className="wf-name-input"
            variant="borderless"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="流程名称"
            disabled={!canEdit}
          />
          <Select
            className="wf-form-module-label"
            variant="borderless"
            value={formModule}
            options={designerOptions}
            showSearch
            optionFilterProp="label"
            disabled={!canEdit}
            style={{ minWidth: 200, maxWidth: 360 }}
            onChange={(value) => {
              const nextLabel = bindFormLabel(value, formOptions)
              const prevLabel = formModuleLabel || bindFormLabel(formModule, formOptions)
              setFormModule(value)
              setFormModuleLabel(nextLabel)
              if (nextLabel && (!name || name === `${prevLabel}流程` || name === prevLabel)) {
                setName(`${nextLabel}流程`)
              }
            }}
          />
          {published ? <Tag color="success">已发布</Tag> : <Tag>草稿</Tag>}
        </div>
        {canEdit ? (
          <div className="wf-toolbar-right">
            <Button loading={saving} onClick={() => void save(false)}>
              保存
            </Button>
            <Button type="primary" loading={saving} onClick={() => void save(true)}>
              保存并发布
            </Button>
          </div>
        ) : null}
      </div>
      <div className="wf-designer">
        <div
          ref={canvasRef}
          className={`wf-canvas${linkFrom ? ' is-linking' : ''}${panning ? ' is-panning' : ''}`}
          onMouseDown={(event) => {
            if (event.button !== 0 || linkFrom) return
            const hit = (event.target as HTMLElement).closest(
              '.wf-node, .wf-palette, .wf-zoom, .wf-edge-plus, .wf-link-hint',
            )
            if (hit) return
            pan.current = {
              x: event.clientX,
              y: event.clientY,
              ox: panRef.current.x,
              oy: panRef.current.y,
            }
            setPanning(true)
            event.preventDefault()
          }}
          onMouseMove={(event) => {
            if (pan.current) {
              const next = {
                x: pan.current.ox + (event.clientX - pan.current.x),
                y: pan.current.oy + (event.clientY - pan.current.y),
              }
              panRef.current = next
              setPanOffset(next)
              return
            }
            if (!drag.current) return
            const graph = event.currentTarget.querySelector('.wf-canvas-graph')
            if (!(graph instanceof HTMLElement)) return
            const scale = zoomRef.current
            const rect = graph.getBoundingClientRect()
            const x = (event.clientX - rect.left) / scale - drag.current.dx
            const y = (event.clientY - rect.top) / scale - drag.current.dy
            setNodes((prev) =>
            prev.map((item) =>
              item.key === drag.current?.key
                ? { ...item, x: Math.max(PAD_X / 2, x), y: Math.max(PAD_Y, y) }
                : item,
            ),
            )
          }}
          onMouseUp={() => {
            drag.current = null
            if (pan.current) {
              pan.current = null
              setPanning(false)
            }
          }}
          onMouseLeave={() => {
            drag.current = null
            if (pan.current) {
              pan.current = null
              setPanning(false)
            }
          }}
        >
          {canEdit ? (
            <div className="wf-palette">
              <Tooltip title="添加审批（选中节点后会插到该节点后面）" placement="right">
                <button type="button" className="wf-palette-item" onClick={() => addNode('APPROVAL')}>
                  <CheckCircleOutlined />
                  审批
                </button>
              </Tooltip>
              <Tooltip title="添加抄送（选中节点后会插到该节点后面）" placement="right">
                <button type="button" className="wf-palette-item" onClick={() => addNode('CC')}>
                  <MailOutlined />
                  抄送
                </button>
              </Tooltip>
              <Tooltip title="添加分支（选中节点后会插到该节点后面）" placement="right">
                <button type="button" className="wf-palette-item" onClick={() => addNode('BRANCH')}>
                  <ApartmentOutlined />
                  分支
                </button>
              </Tooltip>
              <Tooltip title="添加结束" placement="right">
                <button type="button" className="wf-palette-item" onClick={() => addNode('END')}>
                  <FlagOutlined />
                  结束
                </button>
              </Tooltip>
            </div>
          ) : null}
          {linkFrom ? <div className="wf-link-hint">请点击要连接的目标节点</div> : null}
          <div className="wf-zoom">
            <button type="button" className="wf-zoom-btn" onClick={() => setCanvasZoom(zoomRef.current - 0.1)} title="缩小">
              <MinusOutlined />
            </button>
            <button type="button" className="wf-zoom-value" onClick={() => setCanvasZoom(1)} title="重置为 100%">
              {Math.round(zoom * 100)}%
            </button>
            <button type="button" className="wf-zoom-btn" onClick={() => setCanvasZoom(zoomRef.current + 0.1)} title="放大">
              <PlusOutlined />
            </button>
          </div>
          <div className="wf-canvas-inner">
            <div
              className="wf-canvas-stage"
              style={{
                width: board.width * zoom,
                height: board.height * zoom,
                transform: `translate(${panOffset.x}px, ${panOffset.y}px)`,
              }}
            >
            <div className="wf-canvas-graph" style={{ width: board.width, height: board.height, transform: `scale(${zoom})` }}>
            <svg className="wf-svg" width={board.width} height={board.height}>
              <defs>
                <marker id="arrow" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto">
                  <path d="M0,0 L0,6 L8,3 z" fill="#bfbfbf" />
                </marker>
                <marker id="arrow-muted" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto">
                  <path d="M0,0 L0,6 L8,3 z" fill="#8c8c8c" />
                </marker>
                <marker id="arrow-on" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto">
                  <path d="M0,0 L0,6 L8,3 z" fill="var(--app-primary)" />
                </marker>
              </defs>
              {lines.map((line) => {
                const toNode = nodes.find((item) => item.key === line.to)
                const fromNode = nodes.find((item) => item.key === line.from)
                const eid = `${line.from}>${line.to}`
                const edgeOn = hoveredEdge === eid || insertMenuEdge === eid
                const hideLabel =
                  edgeOn ||
                  toNode?.type === 'CONDITION' ||
                  fromNode?.type === 'CONDITION' ||
                  fromNode?.type === 'START' ||
                  edges.filter((item) => item.from === line.from).length < 2
                const dashed = toNode?.type === 'CONDITION'
                return (
                  <g
                    key={eid}
                    className={`wf-edge-g${edgeOn ? ' is-on' : ''}`}
                    onMouseEnter={() => enterEdge(eid)}
                    onMouseLeave={() => leaveEdge(eid)}
                  >
                    <path className="wf-edge-hit" d={line.d} fill="none" />
                    <path
                      className="wf-edge-line"
                      d={line.d}
                      fill="none"
                      stroke={edgeOn ? 'var(--app-primary)' : dashed ? '#8c8c8c' : '#bfbfbf'}
                      strokeWidth={edgeOn ? 2.2 : 1.8}
                      strokeLinecap="round"
                      strokeDasharray={dashed ? '5 4' : undefined}
                      strokeLinejoin="round"
                      markerEnd={edgeOn ? 'url(#arrow-on)' : dashed ? 'url(#arrow-muted)' : 'url(#arrow)'}
                    />
                    {hideLabel ? null : (
                      <text x={line.labelX} y={line.labelY - 10} fill="#8c8c8c" fontSize="11" textAnchor="middle">
                        {edgeCaption(line, fields)}
                      </text>
                    )}
                  </g>
                )
              })}
            </svg>
            {canEdit && !linkFrom
              ? lines.map((line) => {
                  const fromNode = nodes.find((item) => item.key === line.from)
                  if (!fromNode || fromNode.type === 'BRANCH' || fromNode.type === 'END') return null
                  const eid = `${line.from}>${line.to}`
                  const show = hoveredEdge === eid || insertMenuEdge === eid
                  return (
                    <Dropdown
                      key={`${eid}-plus`}
                      trigger={['click']}
                      open={insertMenuEdge === eid}
                      onOpenChange={(open) => {
                        setInsertMenuEdge(open ? eid : null)
                        if (open) enterEdge(eid)
                      }}
                      getPopupContainer={() => document.body}
                      menu={{
                        items: [
                          { key: 'APPROVAL', label: '插入审批', icon: <CheckCircleOutlined /> },
                          { key: 'CC', label: '插入抄送', icon: <MailOutlined /> },
                          { key: 'BRANCH', label: '插入分支', icon: <ApartmentOutlined /> },
                        ],
                        onClick: ({ key }) => {
                          insertOnEdge(line.from, line.to, key as 'APPROVAL' | 'CC' | 'BRANCH')
                          setInsertMenuEdge(null)
                        },
                      }}
                    >
                      <button
                        type="button"
                        className={`wf-edge-plus${show ? ' is-on' : ''}`}
                        title="插入节点"
                        style={{ left: line.labelX, top: line.labelY }}
                        onMouseEnter={() => enterEdge(eid)}
                        onMouseLeave={() => leaveEdge(eid)}
                        onMouseDown={(event) => event.stopPropagation()}
                        onClick={(event) => event.stopPropagation()}
                      >
                        <PlusOutlined />
                      </button>
                    </Dropdown>
                  )
                })
              : null}
            {nodes.map((node) => {
              const kind = nodeKind(node.type)
              const active = node.key === selectedKey
              const size = nodeSize(node.type)
              const incoming = incomingBranchEdge(node.key)
              const sub =
                node.type === 'CONDITION'
                  ? conditionCaption(incoming?.conditions, fields, incoming?.isDefault)
                  : node.type === 'BRANCH' && !edges.some((edge) => edge.from === node.key)
                    ? '未设置路径'
                    : assigneeCaption(node, staff, fields)
              return (
                <div
                  key={node.key}
                  className={`wf-node wf-node--${kind}${active ? ' is-active' : ''}${linkFrom === node.key ? ' is-source' : ''}`}
                  onMouseDown={(event) => {
                    event.stopPropagation()
                    const graph = event.currentTarget.closest('.wf-canvas-graph')
                    if (!(graph instanceof HTMLElement)) return
                    if (canEdit) {
                      const scale = zoomRef.current
                      const rect = graph.getBoundingClientRect()
                      drag.current = {
                        key: node.key,
                        dx: (event.clientX - rect.left) / scale - node.x,
                        dy: (event.clientY - rect.top) / scale - node.y,
                      }
                    }
                    setSelectedKey(node.key)
                    if (canEdit && linkFrom && linkFrom !== node.key) {
                      addPath(linkFrom, node.key)
                      setLinkFrom(null)
                    }
                  }}
                  onDoubleClick={() => {
                    if (!canEdit) return
                    if (node.type === 'CONDITION' && incoming) {
                      setEditingEdge({ from: incoming.from, to: incoming.to })
                    }
                  }}
                  style={{ left: node.x, top: node.y, width: size.w, height: size.h }}
                >
                  {node.type !== 'START' ? <span className="wf-port wf-port-in" /> : null}
                  {node.type !== 'END' ? <span className="wf-port wf-port-out" /> : null}
                  {node.name === nodeTypeLabel(node.type) ? null : (
                    <div className="wf-node-type">{nodeTypeLabel(node.type)}</div>
                  )}
                  <div className="wf-node-name">{node.name}</div>
                  {sub ? <div className="wf-node-sub">{sub}</div> : null}
                </div>
              )
            })}
            </div>
            </div>
          </div>
        </div>
        <aside className="wf-inspector">
          {selected ? (
            <>
              <div className="wf-inspector-kicker">{nodeTypeLabel(selected.type)}</div>
              <h4 className="wf-inspector-title">节点设置</h4>
              <Input
                value={selected.name}
                onChange={(e) =>
                  setNodes((prev) => prev.map((item) => (item.key === selected.key ? { ...item, name: e.target.value } : item)))
                }
                placeholder="节点名称"
                disabled={!canEdit}
              />
              {canEdit ? (
              <div style={{ marginTop: 12 }}>
                <Space wrap>
                  {selected.type !== 'BRANCH' ? (
                    <Button size="small" onClick={() => setLinkFrom(selected.key)}>
                      {linkFrom === selected.key ? '请点击目标节点' : '连到其他节点'}
                    </Button>
                  ) : (
                    <Button size="small" onClick={() => addConditionPath(selected.key)}>
                      添加条件分支
                    </Button>
                  )}
                  {selected.type !== 'START' ? (
                    <Button size="small" danger onClick={removeNode}>
                      删除节点
                    </Button>
                  ) : null}
                </Space>
              </div>
              ) : null}
              {selected.type === 'APPROVAL' || selected.type === 'CC' || selected.type === 'TASK' ? (
                <div className="wf-inspector-section">
                  <h5>办理人</h5>
                  <Radio.Group
                    value={selected.assigneeType || 'STARTER'}
                    disabled={!canEdit}
                    onChange={(e) =>
                      setNodes((prev) =>
                        prev.map((item) => (item.key === selected.key ? { ...item, assigneeType: e.target.value } : item)),
                      )
                    }
                    options={[
                      { label: '发起人', value: 'STARTER' },
                      { label: '指定人员', value: 'USER' },
                      ...(memberFields.length ? [{ label: '表单成员', value: 'FORM_FIELD' }] : []),
                      { label: '部门主管', value: 'ORG_SUPERVISOR' },
                      { label: '逐级审批', value: 'ORG_CHAIN' },
                    ]}
                  />
                  <p className="wf-inspector-help" style={{ marginTop: 8 }}>
                    {(selected.assigneeType || 'STARTER') === 'ORG_SUPERVISOR'
                      ? '按发起人所在部门找主管。发起人自己是主管时，提交给上一级部门主管。'
                      : selected.assigneeType === 'ORG_CHAIN'
                        ? '从发起人的直属主管开始，一级一级往上。不填或填 0 表示批到最高部门。'
                        : null}
                  </p>
                  {selected.assigneeType === 'ORG_CHAIN' ? (
                    <InputNumber
                      min={0}
                      max={20}
                      precision={0}
                      style={{ width: '100%', marginTop: 8 }}
                      placeholder="向上级数，0 表示到最高部门"
                      value={selected.assigneeLevels ?? 0}
                      disabled={!canEdit}
                      onChange={(value) =>
                        setNodes((prev) =>
                          prev.map((item) =>
                            item.key === selected.key ? { ...item, assigneeLevels: value == null ? 0 : Number(value) } : item,
                          ),
                        )
                      }
                    />
                  ) : null}
                  {(selected.assigneeType || 'STARTER') === 'USER' ? (
                    <Select
                      mode="multiple"
                      style={{ width: '100%', marginTop: 10 }}
                      placeholder="选择人员"
                      value={selected.assigneeUserIds || []}
                      options={staff.map((item) => ({ value: item.id, label: item.displayName }))}
                      disabled={!canEdit}
                      onChange={(value) =>
                        setNodes((prev) =>
                          prev.map((item) => (item.key === selected.key ? { ...item, assigneeUserIds: value } : item)),
                        )
                      }
                    />
                  ) : null}
                  {selected.assigneeType === 'FORM_FIELD' ? (
                    memberFields.length ? (
                    <Select
                      style={{ width: '100%', marginTop: 10 }}
                      placeholder="选择成员字段"
                      value={selected.assigneeField}
                      options={memberFields.map((item) => ({ value: item.key, label: item.label }))}
                      disabled={!canEdit}
                      onChange={(value) =>
                        setNodes((prev) =>
                          prev.map((item) => (item.key === selected.key ? { ...item, assigneeField: value } : item)),
                        )
                      }
                    />
                    ) : (
                      <p className="wf-inspector-help">当前表单没有成员字段，请改用发起人或指定人员。</p>
                    )
                  ) : null}
                </div>
              ) : null}
              {selected.type === 'APPROVAL' || selected.type === 'TASK' ? (
                <div className="wf-inspector-section">
                  <h5>超时</h5>
                  <p className="wf-inspector-help">超过此时限仍未办理，待办和单据上将标记已超时。不填或填 0 表示不超时。</p>
                  <Space wrap style={{ marginTop: 8 }}>
                    <InputNumber
                      min={0}
                      max={999}
                      precision={0}
                      placeholder="时长"
                      disabled={!canEdit}
                      value={selected.timeoutValue ?? 0}
                      onChange={(value) =>
                        setNodes((prev) =>
                          prev.map((item) =>
                            item.key === selected.key
                              ? { ...item, timeoutValue: value == null ? 0 : Number(value) }
                              : item,
                          ),
                        )
                      }
                    />
                    <Select
                      style={{ width: 100 }}
                      disabled={!canEdit}
                      value={selected.timeoutUnit || 'HOUR'}
                      options={[
                        { value: 'HOUR', label: '小时' },
                        { value: 'DAY', label: '天' },
                      ]}
                      onChange={(value) =>
                        setNodes((prev) =>
                          prev.map((item) => (item.key === selected.key ? { ...item, timeoutUnit: value } : item)),
                        )
                      }
                    />
                  </Space>
                  <h5 style={{ marginTop: 16 }}>超时提醒人</h5>
                  <p className="wf-inspector-help">超过时限仍未办理时提醒这些人，提醒会出现在他们的「抄送我」列表。</p>
                  <Select
                    mode="multiple"
                    allowClear
                    style={{ width: '100%', marginTop: 8 }}
                    placeholder="选择提醒人"
                    disabled={!canEdit}
                    value={selected.remindees || []}
                    options={staff.map((item) => ({ value: item.id, label: item.displayName }))}
                    onChange={(value) =>
                      setNodes((prev) =>
                        prev.map((item) => (item.key === selected.key ? { ...item, remindees: value } : item)),
                      )
                    }
                  />
                  <h5 style={{ marginTop: 16 }}>升级提醒</h5>
                  <p className="wf-inspector-help">超时后再过此时限，改提醒升级人员；不填或填 0 表示不升级。</p>
                  <Space wrap style={{ marginTop: 8 }}>
                    <InputNumber
                      min={0}
                      max={999}
                      precision={0}
                      placeholder="时长"
                      disabled={!canEdit}
                      value={selected.escalateAfterValue ?? 0}
                      onChange={(value) =>
                        setNodes((prev) =>
                          prev.map((item) =>
                            item.key === selected.key
                              ? { ...item, escalateAfterValue: value == null ? 0 : Number(value) }
                              : item,
                          ),
                        )
                      }
                    />
                    <Select
                      style={{ width: 100 }}
                      disabled={!canEdit}
                      value={selected.escalateAfterUnit || 'HOUR'}
                      options={[
                        { value: 'HOUR', label: '小时' },
                        { value: 'DAY', label: '天' },
                      ]}
                      onChange={(value) =>
                        setNodes((prev) =>
                          prev.map((item) =>
                            item.key === selected.key ? { ...item, escalateAfterUnit: value } : item,
                          ),
                        )
                      }
                    />
                  </Space>
                  <Select
                    mode="multiple"
                    allowClear
                    style={{ width: '100%', marginTop: 8 }}
                    placeholder="选择升级提醒人"
                    disabled={!canEdit}
                    value={selected.escalateRemindees || []}
                    options={staff.map((item) => ({ value: item.id, label: item.displayName }))}
                    onChange={(value) =>
                      setNodes((prev) =>
                        prev.map((item) =>
                          item.key === selected.key ? { ...item, escalateRemindees: value } : item,
                        ),
                      )
                    }
                  />
                </div>
              ) : null}
              {selected.type === 'BRANCH' ? (
                <div className="wf-inspector-section">
                  <h5>分支条件</h5>
                  <p className="wf-inspector-help">
                    添加条件卡片后，双击卡片或点「设置判断条件」。都不满足时走默认路径。
                  </p>
                  {outgoing.length === 0 ? <p className="wf-inspector-help">还没有条件分支，请先添加。</p> : null}
                  {outgoing.map((edge) => {
                    const target = nodes.find((item) => item.key === edge.to)
                    return (
                      <div className="wf-path-card" key={`${edge.from}-${edge.to}`}>
                        <strong>{target?.name || edge.to}</strong>
                        <p>{conditionCaption(edge.conditions, fields, edge.isDefault)}</p>
                        {canEdit ? (
                        <Space wrap>
                          <Button size="small" onClick={() => setEditingEdge({ from: edge.from, to: edge.to })}>
                            设置判断条件
                          </Button>
                          {target?.type === 'CONDITION' ? (
                            <Button size="small" onClick={() => setSelectedKey(target.key)}>
                              去连接后续节点
                            </Button>
                          ) : null}
                          <Button
                            size="small"
                            danger
                            onClick={() => {
                              setEdges((prev) => prev.filter((item) => !(item.from === edge.from && item.to === edge.to)))
                              if (target?.type === 'CONDITION') {
                                setNodes((prev) => prev.filter((item) => item.key !== target.key))
                                setEdges((prev) => prev.filter((item) => item.from !== target.key && item.to !== target.key))
                              }
                            }}
                          >
                            删除
                          </Button>
                        </Space>
                        ) : null}
                      </div>
                    )
                  })}
                </div>
              ) : null}
              {selected.type === 'CONDITION' ? (
                <div className="wf-inspector-section">
                  <h5>判断条件</h5>
                  <p className="wf-inspector-help">数据按是否满足条件进入不同分支，不会被过滤或改写。</p>
                  <div className="wf-condition-box">
                    {conditionCaption(
                      incomingBranchEdge(selected.key)?.conditions,
                      fields,
                      incomingBranchEdge(selected.key)?.isDefault,
                    )}
                  </div>
                  {outgoing.length === 0 && canEdit ? (
                    <p className="wf-inspector-help">请先点「连到其他节点」，再点后续审批或结束节点。</p>
                  ) : null}
                  {canEdit ? (
                  <Button
                    type="primary"
                    style={{ marginTop: 12 }}
                    onClick={() => {
                      const edge = incomingBranchEdge(selected.key)
                      if (edge) setEditingEdge({ from: edge.from, to: edge.to })
                    }}
                  >
                    设置判断条件
                  </Button>
                  ) : null}
                </div>
              ) : null}
              {selected.type === 'END' || selected.type === 'BRANCH' || selected.type === 'CONDITION' || selected.type === 'CC' ? (
                selected.type === 'END' ? (
                  <p className="wf-inspector-help">结束节点不配置表单字段，仍可控制下方操作按钮。</p>
                ) : selected.type === 'CONDITION' ? (
                  <p className="wf-inspector-help">条件卡片不配置表单字段权限，请连到后续审批节点后再设置。</p>
                ) : selected.type === 'CC' ? (
                  <p className="wf-inspector-help">抄送会自动流转，不停留在当前步骤，无需配置表单字段权限。</p>
                ) : null
              ) : (
                <div className="wf-inspector-section">
                  <h5>表单字段控制</h5>
                  {selected.type === 'START' ? (
                    <p className="wf-inspector-help">开始节点的字段权限用于新建和发起单据。办理中的步骤请在对应审批节点上设置。</p>
                  ) : null}
                  {formFields.map((field) => {
                    const mode = (selected.fields?.[field.key] as FieldMode) || 'EDITABLE'
                    return (
                      <div className={`wf-field-row${field.parentLabel ? ' is-child' : ''}`} key={field.key}>
                        <span>
                          {field.parentLabel ? (
                            <>
                              <small>{field.parentLabel}</small>
                              {field.label}
                            </>
                          ) : (
                            field.label
                          )}
                        </span>
                        <Segmented
                          size="small"
                          value={mode}
                          disabled={!canEdit}
                          onChange={(value) => setFieldMode(field.key, value as FieldMode)}
                          options={[
                            { label: '隐藏', value: 'HIDDEN' },
                            { label: '可见', value: 'VISIBLE' },
                            { label: '可编辑', value: 'EDITABLE' },
                          ]}
                        />
                      </div>
                    )
                  })}
                </div>
              )}
              <div className="wf-inspector-section">
                <h5>操作按钮</h5>
                <p className="wf-inspector-help">任意节点都可控制取消、暂存、提交、删除是否出现。结束节点默认全部隐藏。</p>
                {actionFields.map((field) => {
                  const hidden = ((selected.fields?.[field.key] as FieldMode) || (selected.type === 'END' ? 'HIDDEN' : 'EDITABLE')) === 'HIDDEN'
                  return (
                    <div className="wf-field-row" key={field.key}>
                      <span>{field.label}</span>
                      <Segmented
                        size="small"
                        value={hidden ? 'HIDDEN' : 'VISIBLE'}
                        disabled={!canEdit}
                        onChange={(value) => setFieldMode(field.key, value as FieldMode)}
                        options={[
                          { label: '隐藏', value: 'HIDDEN' },
                          { label: '显示', value: 'VISIBLE' },
                        ]}
                      />
                    </div>
                  )
                })}
              </div>
            </>
          ) : (
            <div className="wf-inspector-empty">
              先在画布里点一个节点。然后在这里设置：谁来办、字段能不能改、按钮显不显示。
            </div>
          )}
        </aside>
      </div>
      <ConditionModal
        open={!!editing}
        title={editing ? nodes.find((item) => item.key === editing.to)?.name || '分支条件' : '分支条件'}
        fields={formFields}
        value={editing?.conditions || []}
        isDefault={!!editing?.isDefault}
        onCancel={() => setEditingEdge(null)}
        onOk={(conditions, isDefault) => {
          if (!editingEdge) return
          const from = editingEdge.from
          const to = editingEdge.to
          setEdges((prev) =>
            prev.map((item) => {
              if (item.from === from && item.to === to) {
                return { ...item, isDefault, conditions: isDefault ? [] : conditions }
              }
              if (isDefault && item.from === from) {
                return { ...item, isDefault: false }
              }
              return item
            }),
          )
          setEditingEdge(null)
        }}
      />
    </Card>
  )
}

function ConditionModal({
  open,
  title,
  fields,
  value,
  isDefault,
  onCancel,
  onOk,
}: {
  open: boolean
  title: string
  fields: FormFieldMeta[]
  value: WorkflowCondition[]
  isDefault: boolean
  onCancel: () => void
  onOk: (conditions: WorkflowCondition[], isDefault: boolean) => void
}) {
  const [rows, setRows] = useState<WorkflowCondition[]>([])
  const [fallback, setFallback] = useState(false)

  useEffect(() => {
    if (!open) return
    setFallback(isDefault && !value.filter((item) => item.field).length)
    setRows(value.length ? value.map((item) => ({ ...item, group: item.group || 0 })) : [{ field: fields[0]?.key || '', op: 'EQ', value: '', group: 0 }])
  }, [open, value, isDefault, fields])

  const addRow = (orGroup: boolean) => {
    const last = rows[rows.length - 1]
    const group = orGroup ? (last?.group || 0) + 1 : last?.group || 0
    setFallback(false)
    setRows((prev) => [...prev, { field: fields[0]?.key || '', op: 'EQ', value: '', group }])
  }

  return (
    <Modal
      title={title}
      open={open}
      onCancel={onCancel}
      destroyOnHidden
      footer={
        <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
          <Button onClick={onCancel}>取消</Button>
          <Button type="primary" onClick={() => onOk(rows.filter((item) => item.field), fallback)}>
            确认
          </Button>
        </Space>
      }
    >
      <Typography.Paragraph type="secondary" style={{ marginBottom: 8 }}>
        数据按是否满足条件进入不同分支。点「且条件」表示同时满足，点「或条件」表示满足其中一组即可。
      </Typography.Paragraph>
      {fallback ? (
        <Typography.Text type="secondary">当前为默认路径，不判断条件。取消勾选后可设置字段判断。</Typography.Text>
      ) : (
        rows.map((row, index) => {
          const prev = rows[index - 1]
          const showOr = index > 0 && (prev?.group || 0) !== (row.group || 0)
          return (
            <div key={index} style={{ marginBottom: 8 }}>
              {index > 0 ? (
                <Tag style={{ marginBottom: 6 }}>{showOr ? '或' : '且'}</Tag>
              ) : null}
              <Space wrap>
                <Select
                  style={{ width: 150 }}
                  placeholder="判断字段"
                  value={row.field || undefined}
                  options={fields.map((item) => ({ value: item.key, label: item.label }))}
                  onChange={(field) => setRows((prev) => prev.map((item, i) => (i === index ? { ...item, field } : item)))}
                />
                <Select
                  style={{ width: 110 }}
                  value={row.op || 'EQ'}
                  options={OPS}
                  onChange={(op) => setRows((prev) => prev.map((item, i) => (i === index ? { ...item, op } : item)))}
                />
                {needsCompareValue(row.op) ? (
                  <Input
                    style={{ width: 120 }}
                    placeholder="值，例如 是"
                    value={row.value}
                    onChange={(e) => setRows((prev) => prev.map((item, i) => (i === index ? { ...item, value: e.target.value } : item)))}
                  />
                ) : null}
                <Button
                  type="text"
                  danger
                  icon={<DeleteOutlined />}
                  onClick={() => setRows((prev) => prev.filter((_, i) => i !== index))}
                />
              </Space>
            </div>
          )
        })
      )}
      <Space style={{ marginTop: 8 }}>
        <Button icon={<PlusOutlined />} onClick={() => addRow(false)}>
          且条件
        </Button>
        <Button icon={<PlusOutlined />} onClick={() => addRow(true)}>
          或条件
        </Button>
        <Button
          onClick={() => {
            setFallback(true)
            setRows([])
          }}
        >
          设为默认路径
        </Button>
      </Space>
    </Modal>
  )
}
