export type TechKind = {
  module: string
  slug: string
  label: string
  group: string
}

export type TechGroup = {
  key: string
  label: string
}

export const TECH_KINDS: TechKind[] = [
  { module: 'TECH_CUSTOMER_INSTRUMENT', slug: 'customer-instruments', label: '客户仪器信息表', group: 'customer' },
  { module: 'TECH_WECHAT_CONTACTS', slug: 'wechat-contacts', label: '客户微信资料库', group: 'customer' },
  { module: 'TECH_REPAIR_TRACK', slug: 'repair-track', label: '客户仪器送修信息追踪卡', group: 'repair' },
  { module: 'TECH_TICKET_POOL', slug: 'ticket-pool', label: '客户报修工单池', group: 'repair' },
  { module: 'TECH_SERVICE_REPORT', slug: 'service-report', label: '技术服务报告单', group: 'repair' },
  { module: 'TECH_MULTI_REPORT', slug: 'multi-report', label: '多次技术服务报告单', group: 'repair' },
  { module: 'TECH_MOVE_CARD', slug: 'move-card', label: '设备搬迁记录卡', group: 'move' },
  { module: 'TECH_MAINT_BASIC', slug: 'maint-basic', label: '基础设备维保记录卡', group: 'maint' },
  { module: 'TECH_MAINT_LCMS', slug: 'maint-lcms', label: '液质联用仪-设备维保记录卡', group: 'maint' },
  { module: 'TECH_MAINT_ICPMS', slug: 'maint-icpms', label: 'ICP-MS-设备维保记录卡', group: 'maint' },
  { module: 'TECH_MAINT_ICP', slug: 'maint-icp', label: 'ICP-设备维保记录卡', group: 'maint' },
  { module: 'TECH_MAINT_AAS', slug: 'maint-aas', label: '原子吸收光谱仪-设备维保记录卡', group: 'maint' },
  { module: 'TECH_MAINT_LC', slug: 'maint-lc', label: '液相色谱仪-设备维保记录卡', group: 'maint' },
  { module: 'TECH_MAINT_GC', slug: 'maint-gc', label: '气相色谱仪-设备维保记录卡', group: 'maint' },
  { module: 'TECH_MAINT_GCMS', slug: 'maint-gcms', label: '气相色谱质谱仪-设备维保记录卡', group: 'maint' },
  { module: 'TECH_MAINT_AFS', slug: 'maint-afs', label: '原子荧光光度计-设备维保记录卡', group: 'maint' },
  { module: 'TECH_MAINT_UV', slug: 'maint-uv', label: '紫外可见分光光度计-设备维保记录卡', group: 'maint' },
  { module: 'TECH_MAINT_LEGACY', slug: 'maint-legacy', label: '设备维保记录卡(隐藏旧版）', group: 'other' },
  { module: 'TECH_SPECIAL_CONFIRM', slug: 'special-confirm', label: '特殊仪器确认表', group: 'other' },
  { module: 'TECH_REPAIR_CARD', slug: 'repair-card', label: '设备维修记录卡', group: 'other' },
  { module: 'TECH_INSTRUMENT_LIB', slug: 'instrument-lib', label: '仪器信息资料库', group: 'other' },
  { module: 'TECH_WEIGHT_CALIB', slug: 'weight-calib', label: '承重类维护后校准记录表', group: 'other' },
]

export const TECH_GROUPS: TechGroup[] = [
  { key: 'customer', label: '客户信息' },
  { key: 'repair', label: '维修/安装/培训记录' },
  { key: 'move', label: '搬迁服务记录' },
  { key: 'maint', label: '维保服务记录' },
  { key: 'other', label: '开发数据' },
]

export function techKindBySlug(slug: string | undefined | null): TechKind | null {
  if (!slug) return null
  return TECH_KINDS.find((item) => item.slug === slug) || null
}

export function techKindByModule(module: string | undefined | null): TechKind | null {
  if (!module) return null
  return TECH_KINDS.find((item) => item.module === module) || null
}

export function techKindsInGroup(group: string): TechKind[] {
  return TECH_KINDS.filter((item) => item.group === group)
}
