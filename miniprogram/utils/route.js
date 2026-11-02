function isExternal(user) {
  return !!(user && user.accountType === 'EXTERNAL')
}

function enter() {
  const user = wx.getStorageSync('user') || {}
  wx.reLaunch({ url: isExternal(user) ? '/pages/home/index' : '/pages/workbench/index' })
}

function guardCustomer() {
  if (!wx.getStorageSync('token')) {
    wx.reLaunch({ url: '/pages/login/index' })
    return false
  }
  if (!isExternal(wx.getStorageSync('user') || {})) {
    wx.reLaunch({ url: '/pages/workbench/index' })
    return false
  }
  return true
}

function guardOffice() {
  if (!wx.getStorageSync('token')) {
    wx.reLaunch({ url: '/pages/login/index' })
    return false
  }
  if (isExternal(wx.getStorageSync('user') || {})) {
    wx.reLaunch({ url: '/pages/home/index' })
    return false
  }
  return true
}

module.exports = { isExternal: isExternal, enter: enter, guardCustomer: guardCustomer, guardOffice: guardOffice }
