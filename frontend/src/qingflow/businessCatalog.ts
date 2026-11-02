/** 已下线分组：OA、轻流业务、经营统计、技术服务项目管理。 */
const RETIRED_PREFIXES = ['OA_', 'TECH_PM_', 'TECH_']
const RETIRED_MODULES = new Set([
  'CUSTOMER',
  'PROJECT',
  'COLLECTION',
  'DEPOSIT_APPLY',
  'DEPOSIT_RETURN',
  'BIDDING',
  'PAYMENT',
  'CONTRACT',
  'SUPPLIER',
  'PROJECT_ANALYTICS',
  'BANK_ACCOUNT',
  'EXPENSE',
  'BANK_ANALYTICS',
  'PURCHASE',
  'PURCHASE_PAY',
  'PURCHASE_ANALYTICS',
  'WAREHOUSE',
  'STOCK_OUT_PURCHASE',
  'STOCK_OUT_PROJECT',
  'STOCK_TRANSFER',
  'WAREHOUSE_ANALYTICS',
])

export function isRemovedSidebarModule(module?: string | null) {
  if (!module) return false
  if (RETIRED_MODULES.has(module)) return true
  return RETIRED_PREFIXES.some((prefix) => module.startsWith(prefix))
}
