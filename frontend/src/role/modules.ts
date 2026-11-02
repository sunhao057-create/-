import type { FormModule, FormPermission } from '../types'

export const FORM_MODULES: Array<
  Pick<FormPermission, 'module' | 'moduleLabel' | 'hasDataScope' | 'hasCreate' | 'hasEdit' | 'hasDelete' | 'hasImport'>
> = [
  { module: 'DEVICE', moduleLabel: '设备信息', hasDataScope: true, hasCreate: true, hasImport: false },
  { module: 'DEVICE_REPAIR', moduleLabel: '设备维修工单', hasDataScope: true, hasCreate: true, hasImport: false },
  { module: 'DEVICE_MAINT', moduleLabel: '维保服务记录', hasDataScope: true, hasCreate: true, hasImport: false },
  { module: 'DEVICE_MOVE', moduleLabel: '设备搬迁工单', hasDataScope: true, hasCreate: true, hasImport: false },
  { module: 'DEVICE_SCRAP', moduleLabel: '设备报废工单', hasDataScope: true, hasCreate: true, hasImport: false },
  { module: 'MINI_BANNER', moduleLabel: '小程序轮播图', hasDataScope: false, hasCreate: true, hasImport: false },
  { module: 'MINI_KNOWLEDGE', moduleLabel: '知识库', hasDataScope: false, hasCreate: true, hasImport: false },
  { module: 'MINI_COMPANY', moduleLabel: '公司介绍', hasDataScope: false, hasCreate: true, hasImport: false },
  { module: 'MINI_SERVICE', moduleLabel: '服务内容', hasDataScope: false, hasCreate: true, hasImport: false },
  { module: 'CUSTOMER_APPLY', moduleLabel: '新建客户', hasDataScope: true, hasCreate: true, hasImport: false },
  { module: 'CUSTOMER_ACCOUNT', moduleLabel: '新建客户账号', hasDataScope: true, hasCreate: true, hasImport: false },
  { module: 'ORG', moduleLabel: '组织架构', hasDataScope: false, hasCreate: true, hasImport: false },
  { module: 'USER', moduleLabel: '账号管理', hasDataScope: false, hasCreate: true, hasImport: true },
  { module: 'ROLE', moduleLabel: '角色管理', hasDataScope: false, hasCreate: true, hasImport: false },
  { module: 'WORKFLOW', moduleLabel: '流程引擎', hasDataScope: false, hasCreate: false, hasImport: false },
  { module: 'BRANDING', moduleLabel: '系统名称/图标', hasDataScope: false, hasCreate: false, hasImport: false },
  { module: 'THEME', moduleLabel: '主题风格', hasDataScope: false, hasCreate: false, hasImport: false },
]

export type PermissionGroupNode = {
  key: string
  label: string
  modules?: FormModule[]
  children?: PermissionGroupNode[]
}

/** 与侧栏菜单层级、顺序一致（不含首页、个人中心）。 */
export const PERMISSION_GROUPS: PermissionGroupNode[] = [
  {
    key: 'grp-device',
    label: '设备管理',
    modules: ['DEVICE', 'DEVICE_REPAIR', 'DEVICE_MAINT', 'DEVICE_MOVE', 'DEVICE_SCRAP'],
  },
  {
    key: 'grp-mini',
    label: '客户小程序',
    modules: ['MINI_BANNER', 'MINI_KNOWLEDGE', 'MINI_COMPANY', 'MINI_SERVICE'],
  },
  {
    key: 'grp-customer',
    label: '客户',
    modules: ['CUSTOMER_APPLY', 'CUSTOMER_ACCOUNT'],
  },
  {
    key: 'grp-settings',
    label: '系统设置',
    modules: ['ORG', 'ROLE', 'WORKFLOW', 'USER', 'THEME', 'BRANDING'],
  },
]

export function collectPermissionModules(nodes: PermissionGroupNode[] = PERMISSION_GROUPS): FormModule[] {
  const out: FormModule[] = []
  const walk = (list: PermissionGroupNode[]) => {
    for (const node of list) {
      if (node.modules?.length) out.push(...node.modules)
      if (node.children?.length) walk(node.children)
    }
  }
  walk(nodes)
  return out
}

export function emptyPermissions(): FormPermission[] {
  return FORM_MODULES.map((item) => ({
    module: item.module,
    moduleLabel: item.moduleLabel,
    view: false,
    create: false,
    edit: false,
    delete: false,
    importExport: false,
    flow: false,
    manageView: false,
    dataScope: 'ALL',
    hasDataScope: item.hasDataScope,
    hasCreate: item.hasCreate,
    hasEdit: item.hasEdit !== false,
    hasDelete: item.hasDelete !== false,
    hasImport: item.hasImport !== false,
    hasFlow: Boolean(item.hasCreate && item.hasDataScope),
    hasManageView: Boolean(item.hasCreate && item.hasDataScope),
    publicAccess: false,
    publicDisplay: false,
    publicSubmit: false,
    publicQuery: false,
    publicExport: false,
    hasPublicAccess: Boolean(item.hasDataScope && item.hasCreate),
  }))
}

export function allAllowedPermissions(): FormPermission[] {
  return emptyPermissions().map((item) => ({
    ...item,
    view: true,
    create: true,
    edit: true,
    delete: true,
    importExport: true,
    flow: true,
    manageView: true,
    dataScope: 'ALL',
    publicAccess: false,
    publicDisplay: false,
    publicSubmit: false,
    publicQuery: false,
    publicExport: false,
  }))
}

export function mergePermissions(source?: Array<Partial<FormPermission> & { module: FormModule }>): FormPermission[] {
  return emptyPermissions().map((item) => {
    const found = source?.find((perm) => perm.module === item.module)
    if (!found) return item
    return {
      ...item,
      ...found,
      module: item.module,
      moduleLabel: found.moduleLabel || item.moduleLabel,
      view: Boolean(found.view),
      create: Boolean(found.create),
      edit: Boolean(found.edit),
      delete: Boolean(found.delete),
      importExport: Boolean(found.importExport),
      flow: Boolean(found.flow),
      manageView: Boolean(found.manageView),
      dataScope: found.dataScope === 'OWN' ? 'OWN' : 'ALL',
      hasDataScope: item.hasDataScope,
      hasCreate: item.hasCreate,
      hasEdit: item.hasEdit !== false,
      hasDelete: item.hasDelete !== false,
      hasImport: item.hasImport !== false,
      hasFlow: found.hasFlow !== undefined ? Boolean(found.hasFlow) : item.hasFlow,
      hasManageView: found.hasManageView !== undefined ? Boolean(found.hasManageView) : item.hasManageView,
      publicAccess: Boolean(found.publicAccess),
      publicDisplay: Boolean(found.publicDisplay),
      publicSubmit: Boolean(found.publicSubmit),
      publicQuery: Boolean(found.publicQuery),
      publicExport: Boolean(found.publicExport),
      hasPublicAccess: item.hasPublicAccess,
    }
  })
}
