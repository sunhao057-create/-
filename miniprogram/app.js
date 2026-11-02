App({
  onLaunch() {
    const token = wx.getStorageSync('token')
    const user = wx.getStorageSync('user')
    this.globalData.token = token || ''
    this.globalData.user = user || null
  },
  globalData: {
    token: '',
    user: null,
    branding: null
  }
})
