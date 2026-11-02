const apiBase = require('../config').apiBase

function media(url) {
  if (!url) return ''
  if (url.indexOf('http') === 0 || url.indexOf('wxfile://') === 0) return url
  if (wx.env && url.indexOf(wx.env.USER_DATA_PATH) === 0) return url
  return apiBase + url
}

function localImage(url) {
  const full = media(url)
  if (!full || full.indexOf('https://') === 0 || full.indexOf('wxfile://') === 0) {
    return Promise.resolve(full)
  }
  if (wx.env && full.indexOf(wx.env.USER_DATA_PATH) === 0) {
    return Promise.resolve(full)
  }
  return new Promise(function (resolve) {
    wx.request({
      url: full,
      responseType: 'arraybuffer',
      success: function (res) {
        if (res.statusCode !== 200 || !res.data) {
          resolve('')
          return
        }
        const name = full.replace(/[^\w.]+/g, '_').slice(-60)
        const path = wx.env.USER_DATA_PATH + '/' + name
        wx.getFileSystemManager().writeFile({
          filePath: path,
          data: res.data,
          success: function () { resolve(path) },
          fail: function () { resolve('') }
        })
      },
      fail: function () { resolve('') }
    })
  })
}

module.exports = { media: media, localImage: localImage }
