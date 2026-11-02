const guardCustomer = require('../../utils/route').guardCustomer

Page({
  onShow() {
    if (!guardCustomer()) return
  },
  repair() {
    wx.reLaunch({ url: '/pages/devices/index' })
  },
  open(e) {
    const category = e.currentTarget.dataset.category
    wx.navigateTo({ url: '/pages/articles/index?kind=MINI_SERVICE&category=' + category + '&title=' + category })
  }
})
