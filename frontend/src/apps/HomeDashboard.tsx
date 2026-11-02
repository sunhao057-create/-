import { useEffect, useMemo, useState } from 'react'
import { App } from 'antd'
import { CheckCircleOutlined, HeartOutlined, SendOutlined } from '@ant-design/icons'
import { inboxApi } from '../api'
import { ApiError } from '../api/http'
import { useAuth } from '../auth/AuthContext'
import type { InboxItem } from '../types'
import { allVisibleForms } from './catalog'
import { listFavorites, toggleFavorite } from './favorites'

function soon(row: InboxItem) {
  if (row.overdue || !row.dueAt) return false
  const due = new Date(row.dueAt).getTime()
  return due > Date.now() && due - Date.now() <= 48 * 3600 * 1000
}

type Stat = { key: string; label: string; value: number; tone?: 'warn' | 'urge'; type: string; overdue?: boolean }

export function HomeDashboard({
  onOpenInbox,
  onOpenForm,
}: {
  onOpenInbox: (type: string, overdue?: boolean) => void
  onOpenForm: (path: string) => void
}) {
  const { message } = App.useApp()
  const { user } = useAuth()
  const [todo, setTodo] = useState<InboxItem[]>([])
  const [started, setStarted] = useState<InboxItem[]>([])
  const [finished, setFinished] = useState<InboxItem[]>([])
  const [draft, setDraft] = useState<InboxItem[]>([])
  const [done, setDone] = useState<InboxItem[]>([])
  const [favTick, setFavTick] = useState(0)
  const forms = useMemo(() => allVisibleForms(user), [user])
  const favorites = useMemo(
    () => forms.filter((item) => listFavorites().includes(item.path)),
    [forms, favTick],
  )

  useEffect(() => {
    Promise.all([
      inboxApi.list('TODO'),
      inboxApi.list('STARTED'),
      inboxApi.list('FINISHED'),
      inboxApi.list('DRAFT'),
      inboxApi.list('DONE'),
    ])
      .then(([todos, running, complete, drafts, handled]) => {
        setTodo(todos)
        setStarted(running)
        setFinished(complete)
        setDraft(drafts)
        setDone(handled)
      })
      .catch((e) => message.error(e instanceof ApiError ? e.message : '加载失败'))
  }, [message])

  const todoStats: Stat[] = [
    { key: 'all', label: '全部待办', value: todo.length, type: 'TODO' },
    { key: 'late', label: '已超时', value: todo.filter((item) => item.overdue).length, tone: 'warn', type: 'TODO', overdue: true },
    { key: 'soon', label: '即将超时', value: todo.filter(soon).length, type: 'TODO' },
    { key: 'urge', label: '催办', value: 0, tone: 'urge', type: 'TODO' },
  ]
  const startStats: Stat[] = [
    { key: 'run', label: '进行中', value: started.length, type: 'STARTED' },
    { key: 'ok', label: '已完成', value: finished.length, type: 'FINISHED' },
    { key: 'draft', label: '草稿箱', value: draft.length, type: 'DRAFT' },
    { key: 'done', label: '我的已办', value: done.length, type: 'DONE' },
  ]

  return (
    <div className="qf-home">
      <div className="qf-home-stats">
        <section className="qf-home-card">
          <h3>
            <CheckCircleOutlined /> 我的待办
          </h3>
          <div className="qf-home-metrics">
            {todoStats.map((item) => (
              <button key={item.key} type="button" className={item.tone ? `is-${item.tone}` : ''} onClick={() => onOpenInbox(item.type, item.overdue)}>
                <b>{item.value}</b>
                <span>{item.label}</span>
              </button>
            ))}
          </div>
        </section>
        <section className="qf-home-card">
          <h3>
            <SendOutlined /> 我发起的
          </h3>
          <div className="qf-home-metrics">
            {startStats.map((item) => (
              <button key={item.key} type="button" onClick={() => onOpenInbox(item.type)}>
                <b>{item.value}</b>
                <span>{item.label}</span>
              </button>
            ))}
          </div>
        </section>
      </div>
      <section className="qf-home-fav">
        <h3>常用</h3>
        {favorites.length ? (
          <div className="qf-home-fav-list">
            {favorites.map((item) => (
              <button key={item.path} type="button" className="qf-home-fav-item" onClick={() => onOpenForm(item.path)}>
                <span>{item.label}</span>
                <HeartOutlined
                  className="is-on"
                  onClick={(e) => {
                    e.stopPropagation()
                    toggleFavorite(item.path)
                    setFavTick((value) => value + 1)
                  }}
                />
              </button>
            ))}
          </div>
        ) : (
          <div className="qf-home-empty">
            <div className="qf-home-robot" aria-hidden>
              <i />
            </div>
            <strong>添加常用</strong>
            <p>点击♡即可添加为常用</p>
          </div>
        )}
      </section>
    </div>
  )
}

export function inboxPathOf(row: InboxItem) {
  return bizPath(String(row.formModule), row.bizId)
}
