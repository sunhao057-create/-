const get = require('../../utils/request').get

Page({
  data: {
    title: '服务记录',
    type: 'move',
    rows: [],
    empty: ''
  },
  onLoad(query) {
    const type = query.type === 'maint' ? 'maint' : 'move'
    const title = query.title ? decodeURIComponent(query.title) : (type === 'maint' ? '维保服务' : '搬迁服务')
    this.setData({ type: type, title: title })
    wx.setNavigationBarTitle({ title: title })
  },
  onShow() {
    if (!wx.getStorageSync('token')) {
      wx.reLaunch({ url: '/pages/login/index' })
      return
    }
    const type = this.data.type
    const path = type === 'maint' ? '/api/mini/services/maint' : '/api/mini/services/moves'
    get(path).then((rows) => {
      const list = (rows || []).map((item) => {
        if (type === 'maint') {
          return {
            id: item.id,
            deviceId: item.deviceId,
            title: item.serviceLabel || '维保',
            device: (item.deviceName || '') + (item.deviceCode ? '（' + item.deviceCode + '）' : ''),
            line: item.customerName || item.title || '',
            extra: item.startTime || ''
          }
        }
        return {
          id: item.id,
          deviceId: item.deviceId,
          title: item.code || '搬迁工单',
          device: (item.deviceName || '') + (item.deviceCode ? '（' + item.deviceCode + '）' : ''),
          line: item.reason || '',
          extra: (item.applyDate || '') + (item.workflowStatusLabel ? ' · ' + item.workflowStatusLabel : '')
        }
      })
      this.setData({
        rows: list,
        empty: list.length ? '' : (type === 'maint' ? '还没有维保服务记录' : '还没有搬迁工单')
      })
    }).catch((err) => {
      this.setData({ empty: err.message || '加载失败' })
    })
  },
  open(e) {
    const deviceId = e.currentTarget.dataset.device
    if (!deviceId) return
    wx.navigateTo({ url: '/pages/device/index?id=' + deviceId })
  }
})
