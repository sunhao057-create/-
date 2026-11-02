const get = require('../../utils/request').get
const guardOffice = require('../../utils/route').guardOffice
const bizPath = require('../../utils/biz').bizPath
const statusLabel = require('../../utils/biz').statusLabel
const openBiz = require('../../utils/module').openBiz

const TABS = [
  { key: 'TODO', label: '待我处理' },
  { key: 'DONE', label: '我已处理' },
  { key: 'DRAFT', label: '草稿' },
  { key: 'RETURNED', label: '退回' },
  { key: 'CC', label: '抄送' }
]

Page({
  data: {
    tabs: TABS,
    type: 'TODO',
    overdueOnly: false,
    loading: false,
    rows: [],
    todo: 0
  },
  onShow() {
    if (!guardOffice()) return
    this.load()
  },
  onPullDownRefresh() {
    this.load().then(function () {
      wx.stopPullDownRefresh()
    })
  },
  switchTab(event) {
    this.setData({ type: event.currentTarget.dataset.key, overdueOnly: false })
    this.load()
  },
  onOverdue(event) {
    this.setData({ overdueOnly: event.detail.value })
    this.load()
  },
  load() {
    const type = this.data.type
    const overdue = this.data.overdueOnly && (type === 'TODO' || type === 'RETURNED')
    this.setData({ loading: true })
    const self = this
    const jobs = [get('/api/inbox', { type: type, overdue: overdue })]
    if (type !== 'TODO') {
      jobs.push(get('/api/inbox', { type: 'TODO' }))
    }
    return Promise.all(jobs)
      .then(function (results) {
        const first = results[0] || []
        const rows = []
        for (let i = 0; i < first.length; i++) {
          const row = first[i]
          rows.push({
            id: row.id,
            title: row.title,
            formModuleLabel: row.formModuleLabel,
            nodeName: row.nodeName,
            overdue: row.overdue,
            createdAt: row.createdAt,
            path: bizPath(row.formModule, row.bizId),
            statusText: statusLabel(row.status)
          })
        }
        const todo = type === 'TODO' ? rows.length : (results[1] || []).length
        self.setData({ rows: rows, todo: todo, loading: false })
      })
      .catch(function (err) {
        self.setData({ loading: false })
        wx.showToast({ title: err.message || '加载失败', icon: 'none' })
      })
  },
  openRow(event) {
    const path = event.currentTarget.dataset.path
    if (!path || path === '/') return
    openBiz(path)
  }
})
