const OA = {
  interview: { title: '面试申请', module: 'OA_INTERVIEW', reason: '面试说明', date: '应聘时间' },
  onboard: { title: '入职申请', module: 'OA_ONBOARD', reason: '入职说明', date: '入职日期' },
  transfer: { title: '转岗申请', module: 'OA_TRANSFER', reason: '转岗原因', date: '期望转岗日' },
  promotion: { title: '晋升申请', module: 'OA_PROMOTION', reason: '晋升理由', date: '期望生效日' },
  resign: { title: '离职申请', module: 'OA_RESIGN', reason: '离职原因', date: '最后工作日' },
  salary: { title: '薪资调整', module: 'OA_SALARY', reason: '调薪原因', date: '期望生效日' },
  seal: { title: '盖章申请', module: 'OA_SEAL', reason: '用途说明', date: '盖章日期' },
  qualify: { title: '资质借出', module: 'OA_QUALIFY', reason: '外借用途说明', date: '申请时间' },
  trip: { title: '出差申请', module: 'OA_TRIP', reason: '出差事由说明', date: '出发日期' },
  leave: { title: '请假申请', module: 'OA_LEAVE', reason: '事由说明', date: '开始日期' },
  loan: { title: '项目借款', module: 'OA_LOAN', reason: '用途', date: '借款日期' },
  'loan-pay': { title: '借款付款单', module: 'OA_LOAN_PAY', reason: '用途', date: '付款日期' },
  'loan-repay': { title: '借款还款单', module: 'OA_LOAN_REPAY', reason: '还款说明', date: '还款日期' }
}

const BIZ = [
  { prefix: '/projects', title: '立项', module: 'PROJECT', list: '/api/projects', get: '/api/projects/' },
  { prefix: '/payments', title: '付款', module: 'PAYMENT', list: '/api/payments', get: '/api/payments/' },
  { prefix: '/finance/expenses', title: '报销', module: 'EXPENSE', list: '/api/finance/expenses', get: '/api/finance/expenses/' },
  { prefix: '/finance/accounts', title: '银行账户', module: 'BANK_ACCOUNT', list: '/api/finance/accounts', get: '/api/finance/accounts/' },
  { prefix: '/purchase/payments', title: '采购付款', module: 'PURCHASE_PAY', list: '/api/purchase/payments', get: '/api/purchase/payments/' },
  { prefix: '/purchase', title: '采购', module: 'PURCHASE', list: '/api/purchase', get: '/api/purchase/' },
  { prefix: '/contracts', title: '合同', module: 'CONTRACT', list: '/api/contracts', get: '/api/contracts/' },
  { prefix: '/customers', title: '客户', module: 'CUSTOMER', list: '/api/customers', get: '/api/customers/' },
  { prefix: '/suppliers', title: '供应商', module: 'SUPPLIER', list: '/api/suppliers', get: '/api/suppliers/' },
  { prefix: '/customer-accounts', title: '新建客户账号', module: 'CUSTOMER_ACCOUNT', list: '/api/customer-accounts', get: '/api/customer-accounts/' },
  { prefix: '/customer-opens', title: '新建客户', module: 'CUSTOMER_APPLY', list: '/api/customer-opens', get: '/api/customer-opens/' },
  { prefix: '/devices/maint-records', title: '维保服务记录', module: 'DEVICE_MAINT', list: '/api/devices/maint-records', get: '/api/devices/maint-records/' },
  { prefix: '/devices/repairs', title: '设备维修', module: 'DEVICE_REPAIR', list: '/api/devices/repairs', get: '/api/devices/repairs/' },
  { prefix: '/portal/banners', title: '轮播图', module: 'MINI_BANNER', list: '/api/portal/MINI_BANNER', get: '/api/portal/MINI_BANNER/' },
  { prefix: '/portal/knowledge', title: '知识库', module: 'MINI_KNOWLEDGE', list: '/api/portal/MINI_KNOWLEDGE', get: '/api/portal/MINI_KNOWLEDGE/' },
  { prefix: '/portal/company', title: '公司介绍', module: 'MINI_COMPANY', list: '/api/portal/MINI_COMPANY', get: '/api/portal/MINI_COMPANY/' },
  { prefix: '/portal/service', title: '服务内容', module: 'MINI_SERVICE', list: '/api/portal/MINI_SERVICE', get: '/api/portal/MINI_SERVICE/' },
  { prefix: '/devices/moves', title: '设备搬迁', module: 'DEVICE_MOVE', list: '/api/devices/moves', get: '/api/devices/moves/' },
  { prefix: '/devices/scraps', title: '设备报废', module: 'DEVICE_SCRAP', list: '/api/devices/scraps', get: '/api/devices/scraps/' },
  { prefix: '/devices', title: '设备', module: 'DEVICE', list: '/api/devices', get: '/api/devices/' },
  { prefix: '/collections/bids', title: '招标', module: 'BIDDING', list: '/api/bids', get: '/api/bids/' },
  { prefix: '/collections/deposits', title: '保证金申请', module: 'DEPOSIT_APPLY', list: '/api/deposit-applies', get: '/api/deposit-applies/' },
  { prefix: '/collections/deposit-returns', title: '保证金退回', module: 'DEPOSIT_RETURN', list: '/api/deposit-returns', get: '/api/deposit-returns/' },
  { prefix: '/warehouse/purchase-out', title: '采购出库', module: 'STOCK_OUT_PURCHASE', list: '/api/warehouse/purchase-out', get: '/api/warehouse/purchase-out/' },
  { prefix: '/warehouse/project-out', title: '项目出库', module: 'STOCK_OUT_PROJECT', list: '/api/warehouse/project-out', get: '/api/warehouse/project-out/' },
  { prefix: '/warehouse/transfer', title: '仓库调拨', module: 'STOCK_TRANSFER', list: '/api/warehouse/transfer', get: '/api/warehouse/transfer/' },
  { prefix: '/warehouse/info', title: '仓库信息', module: 'WAREHOUSE', list: '/api/warehouse', get: '/api/warehouse/' },
  { prefix: '/oa/staff', title: '人员', module: 'OA_STAFF', list: '/api/personnel', get: '/api/personnel/' }
]

function strip(path) {
  return String(path || '').split('?')[0]
}

function parsePath(raw) {
  const path = strip(raw)
  const parts = path.replace(/^\//, '').split('/').filter(Boolean)
  if (parts[0] === 'oa' && parts[1] && parts[1] !== 'staff') {
    const slug = parts[1]
    const meta = OA[slug]
    if (!meta) return null
    const third = parts[2]
    const mode = third === 'new' ? 'create' : third ? 'detail' : 'list'
    return {
      family: 'oa',
      slug,
      title: meta.title,
      module: meta.module,
      reasonLabel: meta.reason,
      dateLabel: meta.date,
      mode,
      id: mode === 'detail' ? Number(third) : null,
      listPath: '/oa/' + slug,
      listUrl: '/api/oa/' + slug,
      getUrl: mode === 'detail' ? '/api/oa/' + slug + '/' + third : ''
    }
  }
  if (parts[0] === 'tech' && parts[1]) {
    const slug = parts[1]
    const third = parts[2]
    const mode = third === 'new' ? 'create' : third ? 'detail' : 'list'
      return {
        family: 'tech',
        slug,
        title: '技术服务',
        module: '',
        mode,
        id: mode === 'detail' ? Number(third) : null,
        listPath: '/tech/' + slug,
        listUrl: '/api/tech/' + slug,
        getUrl: mode === 'detail' ? '/api/tech/' + slug + '/' + third : '',
        canCreateNative: true
      }
  }
  const sorted = BIZ.slice().sort((a, b) => b.prefix.length - a.prefix.length)
  for (let i = 0; i < sorted.length; i++) {
    const item = sorted[i]
    if (path === item.prefix || path.indexOf(item.prefix + '/') === 0) {
      const rest = path.slice(item.prefix.length).replace(/^\//, '')
      const mode = rest === 'new' ? 'create' : rest ? 'detail' : 'list'
      const id = mode === 'detail' ? Number(rest.split('/')[0]) : null
      return {
        family: 'biz',
        slug: item.prefix,
        title: item.title,
        module: item.module,
        mode: mode,
        id,
        listPath: item.prefix,
        listUrl: item.list,
        getUrl: id ? item.get + id : '',
        canCreateNative: true
      }
    }
  }
  return null
}

function listRows(data) {
  if (!data) return []
  if (Array.isArray(data)) return data
  if (Array.isArray(data.items)) return data.items
  return []
}

function rowTitle(item) {
  return item.title || item.name || item.code || item.applicantName || item.displayName || ('单据 #' + item.id)
}

function rowSub(item) {
  const bits = [
    item.applicantName,
    item.departmentName,
    item.workflowStatusLabel || item.statusLabel || item.status,
    item.createdAt
  ].filter(Boolean)
  return bits.join(' · ')
}

function openBiz(path) {
  const spec = parsePath(path)
  if (!spec) {
    wx.showToast({ title: '暂不支持该应用', icon: 'none' })
    return
  }
  if (spec.mode === 'create' || spec.mode === 'detail') {
    wx.navigateTo({ url: '/pages/form/index?path=' + encodeURIComponent(path) })
    return
  }
  wx.navigateTo({ url: '/pages/list/index?path=' + encodeURIComponent(spec.listPath) })
}

module.exports = { parsePath, listRows, rowTitle, rowSub, openBiz, OA }
