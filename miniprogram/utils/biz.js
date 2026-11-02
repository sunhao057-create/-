const TECH = {
  TECH_CUSTOMER_INSTRUMENT: 'customer-instruments',
  TECH_WECHAT_CONTACTS: 'wechat-contacts',
  TECH_REPAIR_TRACK: 'repair-track',
  TECH_TICKET_POOL: 'ticket-pool',
  TECH_SERVICE_REPORT: 'service-report',
  TECH_MULTI_REPORT: 'multi-report',
  TECH_MOVE_CARD: 'move-card',
  TECH_MAINT_BASIC: 'maint-basic',
  TECH_MAINT_LCMS: 'maint-lcms',
  TECH_MAINT_ICPMS: 'maint-icpms',
  TECH_MAINT_ICP: 'maint-icp',
  TECH_MAINT_AAS: 'maint-aas',
  TECH_MAINT_LC: 'maint-lc',
  TECH_MAINT_GC: 'maint-gc',
  TECH_MAINT_GCMS: 'maint-gcms',
  TECH_MAINT_AFS: 'maint-afs',
  TECH_MAINT_UV: 'maint-uv',
  TECH_MAINT_LEGACY: 'maint-legacy',
  TECH_SPECIAL_CONFIRM: 'special-confirm',
  TECH_REPAIR_CARD: 'repair-card',
  TECH_INSTRUMENT_LIB: 'instrument-lib',
  TECH_WEIGHT_CALIB: 'weight-calib',
  TECH_PM_PROJECT: 'pm-projects',
  TECH_PM_STATUS: 'pm-status',
  TECH_PM_CHECKIN: 'pm-checkin',
  TECH_PM_MAINT_DAILY: 'pm-maint-daily',
  TECH_PM_MOVE_DAILY: 'pm-move-daily',
  TECH_PM_MAKEUP: 'pm-makeup'
}

function bizPath(formModule, bizId) {
  if (!formModule || !bizId) return '/'
  if (TECH[formModule]) return '/tech/' + TECH[formModule] + '/' + bizId
  const map = {
    PROJECT: '/projects/' + bizId,
    DEPOSIT_APPLY: '/collections/deposits/' + bizId,
    DEPOSIT_RETURN: '/collections/deposit-returns/' + bizId,
    BIDDING: '/collections/bids/' + bizId,
    PAYMENT: '/payments/' + bizId,
    CONTRACT: '/contracts/' + bizId,
    EXPENSE: '/finance/expenses/' + bizId,
    PURCHASE: '/purchase/' + bizId,
    PURCHASE_PAY: '/purchase/payments/' + bizId,
    STOCK_OUT_PURCHASE: '/warehouse/purchase-out/' + bizId,
    STOCK_OUT_PROJECT: '/warehouse/project-out/' + bizId,
    STOCK_TRANSFER: '/warehouse/transfer/' + bizId,
    DEVICE_REPAIR: '/devices/repairs/' + bizId,
    DEVICE_MOVE: '/devices/moves/' + bizId,
    DEVICE_SCRAP: '/devices/scraps/' + bizId,
    OA_ONBOARD: '/oa/onboard/' + bizId,
    OA_TRANSFER: '/oa/transfer/' + bizId,
    OA_PROMOTION: '/oa/promotion/' + bizId,
    OA_RESIGN: '/oa/resign/' + bizId,
    OA_SALARY: '/oa/salary/' + bizId,
    OA_INTERVIEW: '/oa/interview/' + bizId,
    OA_SEAL: '/oa/seal/' + bizId,
    OA_QUALIFY: '/oa/qualify/' + bizId,
    OA_TRIP: '/oa/trip/' + bizId,
    OA_LEAVE: '/oa/leave/' + bizId,
    OA_LOAN: '/oa/loan/' + bizId,
    OA_LOAN_PAY: '/oa/loan-pay/' + bizId,
    OA_LOAN_REPAY: '/oa/loan-repay/' + bizId
  }
  return map[formModule] || '/'
}

function toMiniApp(path) {
  if (!path || path === '/') return '/m/inbox?webview=1'
  let dest = path
  if (path.indexOf('/m') !== 0) {
    dest = '/m/app' + (path.charAt(0) === '/' ? path : '/' + path)
  }
  dest += dest.indexOf('?') >= 0 ? '&webview=1' : '?webview=1'
  return dest
}

function greeting() {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
}

function statusLabel(status) {
  const map = {
    DRAFT: '草稿',
    PENDING: '待办',
    IN_PROGRESS: '办理中',
    DONE: '已完成',
    RETURNED: '已退回',
    REJECTED: '已拒绝',
    CANCELLED: '已取消'
  }
  return map[status] || status || ''
}

module.exports = { bizPath, toMiniApp, greeting, statusLabel }
