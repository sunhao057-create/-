import { useEffect, useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { AppstoreOutlined, InboxOutlined, UserOutlined } from '@ant-design/icons'
import { inboxApi } from '../api'
import { useAuth } from '../auth/AuthContext'
import { useBranding } from '../branding/BrandingContext'
import { markMpWebView } from './mpWebView'
import './mini.css'

const TABS = [
  { key: '/m', label: '工作台', icon: <AppstoreOutlined /> },
  { key: '/m/inbox', label: '我的待办', icon: <InboxOutlined /> },
  { key: '/m/me', label: '我的信息', icon: <UserOutlined /> },
]

export function MiniLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const { branding } = useBranding()
  const { user } = useAuth()
  const [todoCount, setTodoCount] = useState(0)

  const embed = location.pathname.startsWith('/m/app')

  useEffect(() => {
    markMpWebView()
    sessionStorage.setItem('fromMini', '1')
  }, [])

  useEffect(() => {
    void inboxApi
      .list('TODO')
      .then((rows) => setTodoCount(rows.length))
      .catch(() => setTodoCount(0))
    const timer = window.setInterval(() => {
      void inboxApi
        .list('TODO')
        .then((rows) => setTodoCount(rows.length))
        .catch(() => undefined)
    }, 60000)
    return () => window.clearInterval(timer)
  }, [location.pathname])

  return (
    <div className="mini-shell">
      <div className="mini-phone">
        <div className="mini-status">
          {embed ? (
            <button type="button" className="mini-back" onClick={() => navigate('/m')}>
              返回工作台
            </button>
          ) : (
            <span>{branding.name || '一体化管理'}</span>
          )}
          <span>{user?.displayName}</span>
        </div>
        <div className="mini-body">
          <Outlet />
        </div>
        <nav className="mini-tabs">
          {TABS.map((tab) => {
            const on = tab.key === '/m'
              ? location.pathname === '/m' || location.pathname.startsWith('/m/devices')
              : location.pathname.startsWith(tab.key)
            return (
              <button
                key={tab.key}
                type="button"
                className={on ? 'mini-tab is-on' : 'mini-tab'}
                onClick={() => navigate(tab.key)}
              >
                <span className="mini-tab-icon">
                  {tab.icon}
                  {tab.key === '/m/inbox' && todoCount > 0 ? (
                    <span className="mini-tab-badge">{todoCount > 99 ? '99+' : todoCount}</span>
                  ) : null}
                </span>
                {tab.label}
              </button>
            )
          })}
        </nav>
      </div>
    </div>
  )
}
