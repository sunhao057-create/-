import { useEffect, useMemo, useState } from 'react'
import { CaretRightOutlined, HeartOutlined, PlusOutlined } from '@ant-design/icons'
import { useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { can } from '../auth/permissions'
import { AppMark } from './AppMark'
import {
  defaultPackageKey,
  formOfPath,
  packageByKey,
  packageOfPath,
  visibleGroups,
  writeLastPackage,
} from './catalog'
import { isFavorite, toggleFavorite } from './favorites'

export function PackageSider({ packageKey }: { packageKey?: string | null }) {
  const { user } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const current = packageOfPath(location.pathname)
  const key = packageKey || current?.key || defaultPackageKey(user)
  const pack = packageByKey(key)
  const groups = useMemo(() => (pack ? visibleGroups(user, pack.groups) : []), [pack, user])
  const activeForm = formOfPath(location.pathname)
  const groupKeys = groups.map((item) => item.key).join('|')
  const [trackedGroups, setTrackedGroups] = useState(groupKeys)
  const [openGroups, setOpenGroups] = useState<string[]>(() => groups.map((item) => item.key))
  const [, bumpFav] = useState(0)

  if (trackedGroups !== groupKeys) {
    setTrackedGroups(groupKeys)
    setOpenGroups(groups.map((item) => item.key))
  }

  useEffect(() => {
    if (pack) writeLastPackage(pack.key)
  }, [pack])

  return (
    <aside className="qf-pack">
      {pack ? (
        <div className="qf-pack-name">
          <AppMark label={pack.label} seed={pack.key} size="sm" />
          <span>{pack.label}</span>
        </div>
      ) : null}
      <div className="qf-pack-groups">
        {groups.map((group) => {
          const open = openGroups.includes(group.key)
          return (
            <div key={group.key} className="qf-pack-group">
              <button
                type="button"
                className="qf-pack-group-title"
                onClick={() =>
                  setOpenGroups((keys) =>
                    keys.includes(group.key) ? keys.filter((item) => item !== group.key) : [...keys, group.key],
                  )
                }
              >
                <CaretRightOutlined className={open ? 'is-open' : ''} />
                <span>{group.label}</span>
              </button>
              {open
                ? group.forms.map((form) => (
                    <div key={form.path} className={`qf-pack-form${activeForm?.path === form.path ? ' is-active' : ''}`}>
                      <button type="button" className="qf-pack-form-name" onClick={() => navigate(form.path)}>
                        <AppMark label={form.label} seed={form.path} size="sm" />
                        <span>{form.label}</span>
                      </button>
                      <span className="qf-pack-form-ops">
                        {form.createPath && can(user, form.module, 'create') ? (
                          <button type="button" title="新建" onClick={() => navigate(form.createPath!)}>
                            <PlusOutlined />
                          </button>
                        ) : null}
                        <button
                          type="button"
                          title="常用"
                          className={isFavorite(form.path) ? 'is-on' : ''}
                          onClick={() => {
                            toggleFavorite(form.path)
                            bumpFav((value) => value + 1)
                          }}
                        >
                          <HeartOutlined />
                        </button>
                      </span>
                    </div>
                  ))
                : null}
            </div>
          )
        })}
      </div>
    </aside>
  )
}
