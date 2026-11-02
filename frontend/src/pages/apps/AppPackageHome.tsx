import { useEffect, useMemo } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import { defaultPackageKey, packageByKey, visibleGroups, visiblePackages, writeLastPackage } from '../../apps/catalog'

export function AppPackageHome() {
  const { key } = useParams()
  const { user } = useAuth()
  const navigate = useNavigate()
  const packs = visiblePackages(user)
  const packKey = packs.some((item) => item.key === key) ? key : defaultPackageKey(user)
  const pack = packageByKey(packKey)
  const first = useMemo(() => (pack ? visibleGroups(user, pack.groups)[0]?.forms[0] : undefined), [pack, user])

  useEffect(() => {
    if (packKey && key !== packKey) {
      writeLastPackage(packKey)
      navigate(`/apps/${packKey}`, { replace: true })
      return
    }
    if (pack) writeLastPackage(pack.key)
    if (first) navigate(first.path, { replace: true })
  }, [key, pack, packKey, first, navigate])

  if (first) return null
  return <div className="qf-contacts-empty">当前角色没有可看的表单</div>
}
