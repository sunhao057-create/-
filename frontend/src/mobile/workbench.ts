import type { FormModule } from '../types'
import { can } from '../auth/permissions'
import type { User } from '../types'
import { visiblePackages } from '../apps/catalog'

export type MiniTone = 'blue' | 'violet' | 'emerald' | 'amber' | 'rose' | 'slate' | 'cyan' | 'indigo'

export type MiniAppItem = {
  module: FormModule | string
  label: string
  path: string
  tone: MiniTone
}

export type MiniAppGroup = { key: string; title: string; items: MiniAppItem[] }

export function workbenchGroups(user: User | null): MiniAppGroup[] {
  return visiblePackages(user).map((pack) => ({
    key: pack.key,
    title: pack.label,
    items: pack.groups.flatMap((group) =>
      group.forms
        .filter((item) => can(user, item.module, 'view'))
        .map((item) => ({
          module: item.module,
          label: item.label,
          path: item.path,
          tone: 'indigo' as MiniTone,
        })),
    ),
  }))
}

export function toMiniDest(path: string) {
  if (!path || path === '/') return '/m'
  if (path.startsWith('/m')) return path
  return `/m/app${path.startsWith('/') ? path : `/${path}`}`
}

export function openFromMini(path: string, go?: (to: string) => void) {
  sessionStorage.setItem('fromMini', '1')
  const dest = toMiniDest(path)
  if (go) go(dest)
  else window.location.assign(dest)
}

export function greeting() {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
}
