const enter = require('../../utils/route').enter

Page({
  onLoad() {
    if (!wx.getStorageSync('token')) {
      wx.reLaunch({ url: '/pages/login/index' })
      return
    }
    enter()
  }
})
