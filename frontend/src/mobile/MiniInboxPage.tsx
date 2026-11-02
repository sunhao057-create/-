import { useEffect, useRef, useState } from 'react'
import { App, Empty, Spin, Switch } from 'antd'
import { inboxApi } from '../api'
import { ApiError } from '../api/http'
import type { InboxItem } from '../types'
import { formatDateTime, statusLabel } from '../utils/format'
import { bizPath } from '../workflow/WorkflowBar'
import { openFromMini } from './workbench'
import { useNavigate, useSearchParams } from 'react-router-dom'

const TABS = [
  { key: 'TODO', label: '待我处理' },
  { key: 'STARTED', label: '进行中' },
  { key: 'FINISHED', label: '已完成' },
  { key: 'DONE', label: '我已处理' },
  { key: 'DRAFT', label: '草稿' },
  { key: 'RETURNED', label: '退回' },
  { key: 'CC', label: '抄送' },
]

const TAB_KEYS = new Set(TABS.map((item) => item.key))

export function MiniInboxPage() {
  const { message } = App.useApp()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const type = TAB_KEYS.has(params.get('type') || '') ? String(params.get('type')) : 'TODO'
  const overdueOnly = params.get('overdue') === '1'
  const setType = (next: string) => navigate(`/m/inbox?type=${next}`, { replace: true })
  const setOverdueOnly = (next: boolean) => navigate(`/m/inbox?type=${type}${next ? '&overdue=1' : ''}`, { replace: true })
  const [loading, setLoading] = useState(false)
  const [rows, setRows] = useState<InboxItem[]>([])
  const [pulling, setPulling] = useState(false)
  const startY = useRef(0)

  const load = async (next = type, overdue = overdueOnly) => {
    setLoading(true)
    try {
      const filterOverdue = overdue && (next === 'TODO' || next === 'RETURNED')
      setRows(await inboxApi.list(next, filterOverdue))
    } catch (e) {
      message.error(e instanceof ApiError ? e.message : '加载失败')
    } finally {
      setLoading(false)
      setPulling(false)
    }
  }

  useEffect(() => {
    void load(type, overdueOnly)
  }, [type, overdueOnly])

  return (
    <div
      onTouchStart={(event) => {
        startY.current = event.touches[0]?.clientY || 0
      }}
      onTouchEnd={(event) => {
        const y = event.changedTouches[0]?.clientY || 0
        if (y - startY.current > 72) {
          setPulling(true)
          void load()
        }
      }}
    >
      <div className={pulling ? 'mini-refresh is-on' : 'mini-refresh'}>正在刷新…</div>
      <h2 style={{ margin: '4px 4px 8px', fontSize: 22 }}>我的待办</h2>
      <div className="mini-chips">
        {TABS.map((tab) => (
          <button
            key={tab.key}
            type="button"
            className={type === tab.key ? 'mini-chip is-on' : 'mini-chip'}
            onClick={() => setType(tab.key)}
          >
            {tab.label}
          </button>
        ))}
      </div>
      {type === 'TODO' || type === 'RETURNED' ? (
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, margin: '0 4px 12px', fontSize: 13, color: '#64748b' }}>
          只看已超时
          <Switch size="small" checked={overdueOnly} onChange={setOverdueOnly} />
        </div>
      ) : null}
      {loading && !rows.length ? (
        <div className="mini-empty">
          <Spin />
        </div>
      ) : rows.length === 0 ? (
        <div className="mini-empty">
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="这一栏暂时没有单据" />
        </div>
      ) : (
        rows.map((row) => (
          <button type="button" className="mini-todo" key={row.id} onClick={() => openFromMini(bizPath(String(row.formModule), row.bizId), navigate)}>
            <h3>{row.title || row.formModuleLabel}</h3>
            <p>
              {row.formModuleLabel}
              {row.nodeName ? ` · ${row.nodeName}` : ''}
              {row.comment ? ` · ${row.comment}` : ''}
            </p>
            <div className="mini-todo-meta">
              <span className="mini-pill">{statusLabel(row.status)}</span>
              {row.overdue ? <span className="mini-pill warn">已超时</span> : null}
              <span className="mini-pill">{formatDateTime(row.createdAt)}</span>
            </div>
          </button>
        ))
      )}
    </div>
  )
}
