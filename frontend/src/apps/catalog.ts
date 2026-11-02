import type { FormModule, User } from '../types'
import { can } from '../auth/permissions'

export type AppForm = {
  module: FormModule | string
  label: string
  path: string
  createPath?: string
}

export type AppGroup = {
  key: string
  label: string
  forms: AppForm[]
}

export type AppPackage = {
  key: string
  label: string
  groups: AppGroup[]
}

export const APP_PACKAGES: AppPackage[] = [
  {
    key: 'device',
    label: '设备管理',
    groups: [
      {
        key: 'ledger',
        label: '设备台账',
        forms: [{ module: 'DEVICE', label: '设备信息', path: '/devices' }],
      },
      {
        key: 'orders',
        label: '设备工单',
        forms: [
          { module: 'DEVICE_REPAIR', label: '设备维修工单', path: '/devices/repairs', createPath: '/devices/repairs/new' },
          { module: 'DEVICE_MAINT', label: '维保服务记录', path: '/devices/maint-records', createPath: '/devices/maint-records/new' },
          { module: 'DEVICE_MOVE', label: '设备搬迁工单', path: '/devices/moves', createPath: '/devices/moves/new' },
          { module: 'DEVICE_SCRAP', label: '设备报废工单', path: '/devices/scraps', createPath: '/devices/scraps/new' },
        ],
      },
    ],
  },
  {
    key: 'customer',
    label: '客户',
    groups: [
      {
        key: 'open',
        label: '客户申请',
        forms: [
          { module: 'CUSTOMER_APPLY', label: '新建客户', path: '/customer-opens', createPath: '/customer-opens/new' },
          { module: 'CUSTOMER_ACCOUNT', label: '新建客户账号', path: '/customer-accounts', createPath: '/customer-accounts/new' },
        ],
      },
    ],
  },
  {
    key: 'mini',
    label: '客户小程序',
    groups: [
      {
        key: 'content',
        label: '小程序内容',
        forms: [
          { module: 'MINI_BANNER', label: '轮播图', path: '/portal/banners', createPath: '/portal/banners/new' },
          { module: 'MINI_KNOWLEDGE', label: '知识库', path: '/portal/knowledge', createPath: '/portal/knowledge/new' },
          { module: 'MINI_COMPANY', label: '公司介绍', path: '/portal/company', createPath: '/portal/company/new' },
          { module: 'MINI_SERVICE', label: '服务内容', path: '/portal/service', createPath: '/portal/service/new' },
        ],
      },
    ],
  },
]

const LAST_PACKAGE = 'qf-last-package'

export function formVisible(user: User | null | undefined, form: AppForm) {
  return can(user, form.module, 'view')
}

export function visibleForms(user: User | null | undefined, forms: AppForm[]) {
  return forms.filter((item) => formVisible(user, item))
}

export function visibleGroups(user: User | null | undefined, groups: AppGroup[]) {
  return groups
    .map((group) => ({ ...group, forms: visibleForms(user, group.forms) }))
    .filter((group) => group.forms.length > 0)
}

export function visiblePackages(user: User | null | undefined) {
  return APP_PACKAGES.map((item) => ({ ...item, groups: visibleGroups(user, item.groups) })).filter(
    (item) => item.groups.length > 0,
  )
}

export function allVisibleForms(user: User | null | undefined) {
  return visiblePackages(user).flatMap((item) => item.groups.flatMap((group) => group.forms))
}

export function packageByKey(key?: string | null) {
  return APP_PACKAGES.find((item) => item.key === key) || null
}

export function packageOfPath(pathname: string) {
  const path = pathname.replace(/^\/m\/app/, '') || '/'
  if (path.startsWith('/apps/tech/board')) return packageByKey('tech')
  return (
    APP_PACKAGES.find((item) =>
      item.groups.some((group) => group.forms.some((form) => path === form.path || path.startsWith(`${form.path}/`))),
    ) || null
  )
}

export function formOfPath(pathname: string) {
  const path = pathname.replace(/^\/m\/app/, '') || '/'
  for (const item of APP_PACKAGES) {
    for (const group of item.groups) {
      const form = group.forms.find((entry) => path === entry.path || path.startsWith(`${entry.path}/`))
      if (form) return form
    }
  }
  return null
}

export function readLastPackage() {
  try {
    return sessionStorage.getItem(LAST_PACKAGE)
  } catch {
    return null
  }
}

export function writeLastPackage(key: string) {
  try {
    sessionStorage.setItem(LAST_PACKAGE, key)
  } catch {
    /* ignore */
  }
}

export function defaultPackageKey(user: User | null | undefined) {
  const packs = visiblePackages(user)
  const last = readLastPackage()
  if (last && packs.some((item) => item.key === last)) return last
  return packs[0]?.key || 'customer'
}
