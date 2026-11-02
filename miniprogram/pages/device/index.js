const get = require('../../utils/request').get
const guardCustomer = require('../../utils/route').guardCustomer

const KIND = { REPAIR: '维修', MOVE: '搬迁', SCRAP: '报废' }

Page({
  data: {
    id: '',
    device: null,
    orders: [],
    maintRecords: [],
  },
  onLoad(query) {
    this.setData({ id: query.id || '' })
  },
  onShow() {
    if (!guardCustomer()) return
    this.reload()
  },
  reload() {
    if (!this.data.id) return
    get('/api/mini/devices/' + this.data.id)
      .then((detail) => {
        const orders = ((detail && detail.orders) || []).map((item) => ({
          id: item.id,
          kindLabel: KIND[item.kind] || item.kind || '处理',
          reason: item.reason || '',
          applyDate: item.applyDate || '',
          status: item.workflowStatusLabel || (item.applied ? '已办结' : '处理中')
        }))
        const maint = ((detail && detail.maintRecords) || []).map((item) => ({
          id: item.id,
          serviceLabel: item.serviceLabel || '维保',
          title: item.title || '',
          customerName: item.customerName || '',
          startTime: item.startTime || ''
        }))
        this.setData({ device: detail.device, orders: orders, maintRecords: maint })
      })
      .catch((err) => wx.showToast({ title: err.message || '加载失败', icon: 'none' }))
  },
  goRepair() {
    wx.navigateTo({ url: '/pages/scan/index?id=' + this.data.id })
  }
})
