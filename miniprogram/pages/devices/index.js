const get = require('../../utils/request').get
const guardCustomer = require('../../utils/route').guardCustomer

Page({
  data: {
    companyName: '',
    total: 0,
    devices: [],
    empty: '',
    loading: true
  },
  onShow() {
    if (!guardCustomer()) return
    this.reload()
  },
  reload() {
    this.setData({ loading: true })
    get('/api/mini/devices')
      .then((home) => {
        const devices = (home && home.devices) || []
        const companyName = (home && home.companyName) || ''
        this.setData({
          companyName: companyName,
          total: home ? home.total : 0,
          devices: devices,
          empty: devices.length ? '' : (companyName ? '本公司还没有设备' : '未分配到公司，看不到设备'),
          loading: false
        })
      })
      .catch((err) => {
        this.setData({ loading: false, empty: err.message || '加载失败' })
      })
  },
  open(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({ url: '/pages/device/index?id=' + id })
  }
})
