const get = require('../../utils/request').get
const post = require('../../utils/request').post
const enter = require('../../utils/route').enter

Page({
  data: {
    name: '设备服务',
    username: '',
    password: '',
    error: '',
    pending: false
  },
  onShow() {
    if (wx.getStorageSync('token')) {
      enter()
      return
    }
    get('/api/branding')
      .then((branding) => {
        this.setData({ name: (branding && branding.name) || '设备服务' })
      })
      .catch(() => {})
  },
  onUser(e) {
    this.setData({ username: e.detail.value, error: '' })
  },
  onPass(e) {
    this.setData({ password: e.detail.value, error: '' })
  },
  showError(message) {
    this.setData({ error: message, pending: false })
    wx.showModal({ title: '登录失败', content: message, showCancel: false })
  },
  submit() {
    const username = (this.data.username || '').trim()
    const password = this.data.password || ''
    if (!username || !password) {
      this.showError('请输入用户名和密码')
      return
    }
    this.setData({ pending: true, error: '' })
    post('/api/auth/mini-login', { username, password })
      .then((data) => {
        wx.setStorageSync('token', data.token)
        wx.setStorageSync('user', data.user)
        getApp().globalData.token = data.token
        getApp().globalData.user = data.user
        enter()
      })
      .catch((err) => {
        this.showError(err.message || '用户名或密码错误')
      })
  }
})
