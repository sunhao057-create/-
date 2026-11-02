import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { App, Dropdown, Empty, Space } from 'antd'
import {
  AppstoreOutlined,
  CalendarOutlined,
  ClusterOutlined,
  FieldTimeOutlined,
  PlusOutlined,
  ProjectOutlined,
  TableOutlined,
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { formViewApi } from '../api'
import { ApiError } from '../api/http'
import { useAuth } from '../auth/AuthContext'
import { can } from '../auth/permissions'
import { FormViewConfig, visibilityOf, type ConfigTab, type ViewDraft } from './FormViewConfig'
import type { DataViewDef, DataViewField, DataViewHeight, DataViewRange, DataViewType } from '../types'

const TYPE_META: Array<{ key: DataViewType; label: string; icon: ReactNode }> = [
  { key: 'TABLE', label: '表格视图', icon: <TableOutlined /> },
  { key: 'CARD', label: '卡片视图', icon: <AppstoreOutlined /> },
  { key: 'BOARD', label: '看板视图', icon: <ProjectOutlined /> },
  { key: 'GANTT', label: '甘特视图', icon: <FieldTimeOutlined /> },
  { key: 'TREE', label: '层级视图', icon: <ClusterOutlined /> },
  { key: 'CALENDAR', label: '日历视图', icon: <CalendarOutlined /> },
]

type Draft = ViewDraft

function typeLabel(type: DataViewType) {
  return TYPE_META.find((item) => item.key === type)?.label || '表格视图'
}

function blankDraft(type: DataViewType, fields: DataViewField[]): Draft {
  return {
    name: typeLabel(type),
    type,
    remark: '',
    pinTop: false,
    visibility: 'ALL',
    dataRange: 'ALL',
    rowHeight: 'MEDIUM',
    sortField: undefined,
    sortDir: 'DESC',
    searchEnabled: true,
    queryEnabled: true,
    fields: fields.map((item) => item.key),
    extra: {
      visibilityMode: 'ALL',
      memberScope: 'ALL_MEMBERS',
      remarkEnabled: false,
      allowDetail: true,
      allowFlowChart: false,
      allowRobot: false,
      allowUrge: false,
      allowComment: false,
      allowPrint: false,
      viewButton: '查看',
      detailButton: '查看',
      listMainButtons: [],
      listMoreButtons: [],
    },
  }
}

function cellText(value: unknown): string {
  if (value == null || value === '') return '—'
  if (Array.isArray(value)) return value.map(cellText).filter((item) => item !== '—').join('、') || '—'
  if (typeof value === 'object') {
    const obj = value as Record<string, unknown>
    return cellText(obj.name ?? obj.label ?? obj.title ?? obj.value ?? '')
  }
  if (typeof value === 'boolean') return value ? '是' : '否'
  return String(value)
}

function doneStatus(label?: unknown) {
  const text = String(label || '')
  return /完成|结束|归档|通过|关闭/.test(text)
}

function valueMentionsAccount(value: unknown, user: { id: number; username: string }, seen = new Set<object>()): boolean {
  if (value == null || value === '') return false
  if (typeof value === 'object') {
    if (seen.has(value)) return false
    seen.add(value)
    const list = Array.isArray(value) ? value : Object.values(value)
    return list.some((item) => valueMentionsAccount(item, user, seen))
  }
  const text = String(value).trim()
  if (text === String(user.id)) return true
  const account = user.username.trim()
  return account.length > 0 && text.includes(account)
}

function rowMentionsAccount<T>(
  row: T,
  user: { id: number; username: string },
  fields: DataViewField[],
  read: (row: T, key: string) => unknown,
) {
  const extra = (row as unknown as { extra?: unknown }).extra
  if (valueMentionsAccount(extra, user)) return true
  return fields.some((field) => valueMentionsAccount(read(row, field.key), user))
}

export function viewColumns<T>(columns: ColumnsType<T>, fieldKeys: string[]): ColumnsType<T> {
  if (!fieldKeys.length) return columns
  return columns.filter((col) => {
    const item = col as { key?: string; dataIndex?: string }
    const key = String(item.key || item.dataIndex || '')
    return key === 'actions' || fieldKeys.includes(key)
  })
}

export function rowHeightPx(height?: DataViewHeight) {
  if (height === 'COMPACT') return 36
  if (height === 'TALL') return 64
  return 48
}

type RowLike = { id: number }

type Props<T extends RowLike> = {
  module: string
  fields: DataViewField[]
  rows: T[]
  loading?: boolean
  extra?: ReactNode
  filters?: ReactNode
  onOpen: (row: T) => void
  getValue?: (row: T, key: string) => unknown
  statusKey?: string
  creatorNameKey?: string
  dateKey?: string
  onRange?: (range: DataViewRange) => void
  renderTable: (ctx: { rows: T[]; fieldKeys: string[]; rowHeight: number }) => ReactNode
}

export function FormViewWorkbench<T extends RowLike>({
  module,
  fields,
  rows,
  loading,
  extra,
  filters,
  onOpen,
  getValue,
  statusKey = 'workflowStatusLabel',
  creatorNameKey = 'creatorName',
  dateKey = 'createdAt',
  onRange,
  renderTable,
}: Props<T>) {
  const { message, modal } = App.useApp()
  const { user: me } = useAuth()
  const canManage = can(me, module, 'manageView')
  const [views, setViews] = useState<DataViewDef[]>([])
  const [currentId, setCurrentId] = useState(0)
  const [range, setRange] = useState<DataViewRange | null>(null)
  const [editor, setEditor] = useState<{ id?: number; draft: Draft } | null>(null)
  const [section, setSection] = useState<ConfigTab>('basic')
  const [saving, setSaving] = useState(false)
  const [previewing, setPreviewing] = useState(false)

  const read =
    getValue ||
    ((row: T, key: string) => {
      const record = row as unknown as Record<string, unknown>
      const extraMap = record.extra as Record<string, unknown> | undefined
      return record[key] ?? extraMap?.[key]
    })

  const loadViews = async (selectId?: number) => {
    if (!module) return
    try {
      const list = await formViewApi.list(module)
      setViews(list)
      setCurrentId((prev) => {
        if (selectId != null && list.some((item) => item.id === selectId)) return selectId
        if (list.some((item) => item.id === prev)) return prev
        return list[0]?.id ?? 0
      })
    } catch (e) {
      message.error(e instanceof ApiError ? e.message : '加载视图失败')
    }
  }

  useEffect(() => {
    void loadViews()
  }, [module])

  const current = views.find((item) => item.id === currentId) || views[0]
  const active = editor?.draft || current
  const fieldKeys = active?.fields?.length ? active.fields : fields.map((item) => item.key)
  const activeRange = range || active?.dataRange || 'ALL'

  useEffect(() => {
    onRange?.(activeRange)
  }, [activeRange, onRange])

  const visibleRows = useMemo(() => {
    const mine = me?.displayName || ''
    return rows.filter((row) => {
      const creator = String(read(row, creatorNameKey) || '')
      if (activeRange === 'RELATED') return me ? rowMentionsAccount(row, me, fields, read) : false
      if (activeRange === 'MINE' || activeRange === 'STARTED') return !mine || creator === mine
      if (activeRange === 'DONE') return doneStatus(read(row, statusKey))
      return true
    })
  }, [rows, activeRange, me, creatorNameKey, statusKey, read, fields])

  const sortedRows = useMemo(() => {
    const field = active?.sortField
    if (!field) return visibleRows
    const dir = active.sortDir === 'ASC' ? 1 : -1
    return [...visibleRows].sort((a, b) => String(read(a, field)).localeCompare(String(read(b, field)), 'zh') * dir)
  }, [visibleRows, active, read])

  const startCreate = (type: DataViewType) => {
    if (!canManage) {
      message.warning('当前角色没有创建视图的权限')
      return
    }
    setSection('basic')
    setPreviewing(false)
    setEditor({ draft: blankDraft(type, fields) })
  }

  const startEdit = (view: DataViewDef) => {
    if (!canManage || view.builtin) return
    setSection('basic')
    setPreviewing(false)
    setEditor({
      id: view.id,
      draft: {
        name: view.name,
        type: view.type,
        remark: view.remark || '',
        pinTop: view.pinTop,
        visibility: view.visibility,
        dataRange: view.dataRange,
        rowHeight: view.rowHeight,
        sortField: view.sortField || undefined,
        sortDir: view.sortDir || 'DESC',
        searchEnabled: view.searchEnabled,
        queryEnabled: view.queryEnabled,
        fields: view.fields?.length ? view.fields : fields.map((item) => item.key),
        extra: {
          memberScope: view.visibility === 'ADMIN' ? 'ADMIN' : view.visibility === 'SELF' ? 'INVITE' : 'ALL_MEMBERS',
          remarkEnabled: Boolean(view.remark),
          ...(view.extra || {}),
          visibilityMode: String(view.extra?.visibilityMode || (view.visibility === 'ALL' ? 'ALL' : 'PART')),
        },
      },
    })
  }

  const patchDraft = (partial: Partial<Draft>) => {
    setEditor((currentEditor) => (currentEditor ? { ...currentEditor, draft: { ...currentEditor.draft, ...partial } } : currentEditor))
  }

  const save = async () => {
    if (!editor) return
    if (!editor.draft.name.trim()) {
      message.warning('请填写视图标题')
      return
    }
    setSaving(true)
    try {
      const payload = { ...editor.draft, visibility: visibilityOf(editor.draft) }
      const saved = editor.id
        ? await formViewApi.update(module, editor.id, payload)
        : await formViewApi.create(module, payload)
      message.success(editor.id ? '视图已更新' : '视图已创建')
      setEditor(null)
      await loadViews(saved.id)
    } catch (e) {
      message.error(e instanceof ApiError ? e.message : '保存视图失败')
    } finally {
      setSaving(false)
    }
  }

  const removeView = (view: DataViewDef) => {
    if (!canManage || view.builtin) return
    modal.confirm({
      title: '删除视图',
      content: `确认删除「${view.name}」？`,
      okText: '删除',
      okButtonProps: { danger: true },
      onOk: async () => {
        await formViewApi.remove(module, view.id)
        message.success('已删除')
        if (currentId === view.id) setCurrentId(0)
        await loadViews()
      },
    })
  }

  const tableBody = renderBody(
    active?.type || 'TABLE',
    sortedRows,
    fieldKeys,
    active?.rowHeight,
    onOpen,
    read,
    statusKey,
    dateKey,
    creatorNameKey,
    fields,
    renderTable,
  )
  const emptyPreview = (
    <div className="qf-view-preview">
      <div className="qf-view-empty">
        <span className="qf-view-empty-icon" />
        <p>暂无数据</p>
      </div>
    </div>
  )
  const body = editor ? (previewing ? tableBody : emptyPreview) : tableBody

  return (
    <div className="qf-view-workbench">
      <div className="qf-view-toolbar">
        <div className="qf-view-tabs">
          {views.map((view) => {
            const tab = (
              <button
                key={view.id}
                type="button"
                className={`qf-view-tab${currentId === view.id && !editor ? ' is-active' : ''}`}
                onClick={() => {
                  setEditor(null)
                  setCurrentId(view.id)
                  setRange(null)
                }}
              >
                {view.pinTop ? '📌 ' : ''}
                {view.name}
              </button>
            )
            if (!canManage || view.builtin) return tab
            return (
              <Dropdown
                key={view.id}
                trigger={['contextMenu']}
                menu={{
                  items: [
                    { key: 'edit', label: '编辑', onClick: () => startEdit(view) },
                    { key: 'delete', label: '删除', danger: true, onClick: () => removeView(view) },
                  ],
                }}
              >
                {tab}
              </Dropdown>
            )
          })}
          {canManage ? (
            <Dropdown
              menu={{
                items: TYPE_META.map((item) => ({
                  key: item.key,
                  icon: item.icon,
                  label: item.label,
                  onClick: () => startCreate(item.key),
                })),
              }}
            >
              <button type="button" className="qf-view-tab qf-view-add" aria-label="新建视图">
                <PlusOutlined />
              </button>
            </Dropdown>
          ) : null}
        </div>
        {editor ? null : (
          <Space size={8} wrap>
            {(['DONE', 'STARTED', 'ALL', 'MINE', 'RELATED'] as DataViewRange[]).map((item) => (
              <button
                key={item}
                type="button"
                className={`qf-view-chip${activeRange === item ? ' is-active' : ''}`}
                onClick={() => setRange(item)}
              >
                {{ ALL: '全部数据', MINE: '我的数据', RELATED: '关于自己的数据', STARTED: '我发起的', DONE: '已办视图' }[item]}
              </button>
            ))}
          </Space>
        )}
      </div>
      {editor ? null : (
        <div className="qf-view-tools">
          <div>{active?.searchEnabled === false ? null : filters}</div>
          <div>{extra}</div>
        </div>
      )}
      <div className={`qf-view-body${editor ? ' is-editing' : ''}`}>
        {body}
        {editor ? (
          <FormViewConfig
            draft={editor.draft}
            fields={fields}
            section={section}
            saving={saving}
            isEdit={Boolean(editor.id)}
            onSection={setSection}
            onChange={patchDraft}
            onPreview={() => setPreviewing(true)}
            onSave={() => void save()}
            onClose={() => {
              setEditor(null)
              setPreviewing(false)
            }}
          />
        ) : null}
      </div>
      {loading ? <div className="qf-view-loading">加载中…</div> : null}
    </div>
  )
}

function renderBody<T extends RowLike>(
  type: DataViewType,
  rows: T[],
  fieldKeys: string[],
  height: DataViewHeight | undefined,
  onOpen: (row: T) => void,
  read: (row: T, key: string) => unknown,
  statusKey: string,
  dateKey: string,
  creatorNameKey: string,
  fields: DataViewField[],
  renderTable: Props<T>['renderTable'],
) {
  if (type === 'TABLE') {
    return renderTable({ rows, fieldKeys, rowHeight: rowHeightPx(height) })
  }
  if (!rows.length) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无数据" />
  }
  if (type === 'CARD') {
    return (
      <div className="qf-view-cards">
        {rows.map((row) => (
          <button key={row.id} type="button" className="qf-view-card" onClick={() => onOpen(row)}>
            <strong>{cellText(read(row, fieldKeys[0] || 'title'))}</strong>
            {fieldKeys.slice(1, 4).map((key) => (
              <p key={key}>
                {fields.find((item) => item.key === key)?.title || key}：{cellText(read(row, key))}
              </p>
            ))}
          </button>
        ))}
      </div>
    )
  }
  if (type === 'BOARD') {
    const groups = groupBy(rows, (row) => String(read(row, statusKey) || '未启动'))
    return (
      <div className="qf-view-board">
        {Object.entries(groups).map(([name, items]) => (
          <div key={name} className="qf-view-lane">
            <header>
              {name} <em>{items.length}</em>
            </header>
            {items.map((row) => (
              <button key={row.id} type="button" onClick={() => onOpen(row)}>
                {cellText(read(row, fieldKeys[0] || 'title'))}
              </button>
            ))}
          </div>
        ))}
      </div>
    )
  }
  if (type === 'CALENDAR') {
    const groups = groupBy(rows, (row) => String(read(row, dateKey) || '').slice(0, 10) || '未填日期')
    return (
      <div className="qf-view-calendar">
        {Object.entries(groups).map(([day, items]) => (
          <div key={day}>
            <strong>{day}</strong>
            {items.map((row) => (
              <button key={row.id} type="button" onClick={() => onOpen(row)}>
                {cellText(read(row, fieldKeys[0] || 'title'))}
              </button>
            ))}
          </div>
        ))}
      </div>
    )
  }
  if (type === 'TREE') {
    const groups = groupBy(rows, (row) => String(read(row, creatorNameKey) || '未分组'))
    return (
      <div className="qf-view-tree">
        {Object.entries(groups).map(([name, items]) => (
          <details key={name} open>
            <summary>
              {name}（{items.length}）
            </summary>
            {items.map((row) => (
              <button key={row.id} type="button" onClick={() => onOpen(row)}>
                {cellText(read(row, fieldKeys[0] || 'title'))}
              </button>
            ))}
          </details>
        ))}
      </div>
    )
  }
  return (
    <div className="qf-view-gantt">
      {rows.map((row) => (
        <button key={row.id} type="button" onClick={() => onOpen(row)}>
          <span>{cellText(read(row, fieldKeys[0] || 'title'))}</span>
          <i>{cellText(read(row, dateKey))}</i>
        </button>
      ))}
    </div>
  )
}

function groupBy<T>(rows: T[], keyOf: (row: T) => string) {
  const result: Record<string, T[]> = {}
  for (const row of rows) {
    const key = keyOf(row) || '未分组'
    if (!result[key]) result[key] = []
    result[key].push(row)
  }
  return result
}
