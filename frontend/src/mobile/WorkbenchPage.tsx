import { useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { HomeDashboard } from '../apps/HomeDashboard'
import { useAuth } from '../auth/AuthContext'
import { visiblePackages } from '../apps/catalog'
import { greeting, openFromMini } from './workbench'
import { useBranding } from '../branding/BrandingContext'

export function WorkbenchPage() {
  const { user } = useAuth()
  const { branding } = useBranding()
  const navigate = useNavigate()
  const packs = useMemo(() => visiblePackages(user), [user])

  return (
    <>
      <section className="mini-hero">
        <div style={{ fontSize: 13, opacity: 0.9 }}>
          {greeting()}，{user?.displayName}
        </div>
        <h1>{branding.name || '工作台'}</h1>
      </section>
      <HomeDashboard
        onOpenInbox={(type, overdue) => navigate(`/m/inbox?type=${type}${overdue ? '&overdue=1' : ''}`)}
        onOpenForm={(path) => openFromMini(path, navigate)}
      />
      {packs.map((pack) => (
        <section className="mini-section" key={pack.key}>
          <div className="mini-section-head">{pack.label}</div>
          {pack.groups.map((group) => (
            <div key={group.key}>
              <div className="mini-section-head" style={{ fontWeight: 400, color: '#86909c' }}>{group.label}</div>
              <div className="mini-grid">
                {group.forms.map((item) => (
                  <button type="button" className="mini-app" key={item.path} onClick={() => openFromMini(item.path, navigate)}>
                    <span className="mini-app-icon tone-indigo">{item.label.slice(0, 1)}</span>
                    <span>{item.label}</span>
                  </button>
                ))}
              </div>
            </div>
          ))}
        </section>
      ))}
    </>
  )
}
