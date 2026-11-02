const get = require('../../utils/request').get
const isExternal = require('../../utils/route').isExternal
const post = require('../../utils/request').post
const put = require('../../utils/request').put
const apiBase = require('../../utils/request').apiBase

Page({
  data: {
    displayName: '',
    username: '',
    departmentName: '未分配部门',
    avatarUrl: '',
    glyph: '用',
    tabMode: 'customer'
  },
  onShow() {
    if (!wx.getStorageSync('token')) {
      wx.reLaunch({ url: '/pages/login/index' })
      return
    }
    this.setData({ tabMode: isExternal(wx.getStorageSync('user')) ? 'customer' : 'office' })
    this.refresh()
  },
  refresh() {
    get('/api/auth/me')
      .then((user) => {
        wx.setStorageSync('user', user)
        this.setData({
          tabMode: isExternal(user) ? 'customer' : 'office',
          displayName: user.displayName,
          username: user.username,
          departmentName: user.departmentName || '未分配部门',
          avatarUrl: user.avatarUrl ? (user.avatarUrl.indexOf('http') === 0 ? user.avatarUrl : apiBase + user.avatarUrl) : '',
          glyph: (user.displayName || '用').slice(0, 1)
        })
      })
      .catch((err) => wx.showToast({ title: err.message || '加载失败', icon: 'none' }))
  },
  editName() {
    wx.showModal({
      title: '修改姓名',
      editable: true,
      placeholderText: this.data.displayName,
      success: (res) => {
        if (!res.confirm) return
        const displayName = (res.content || '').trim()
        if (!displayName) return
        put('/api/profile', { displayName })
          .then((user) => {
            wx.setStorageSync('user', user)
            this.setData({ displayName: user.displayName, glyph: (user.displayName || '用').slice(0, 1) })
            wx.showToast({ title: '已保存', icon: 'success' })
          })
          .catch((err) => wx.showToast({ title: err.message || '保存失败', icon: 'none' }))
      }
    })
  },
  changePass() {
    wx.showModal({
      title: '原密码',
      editable: true,
      placeholderText: '请输入原密码',
      success: (first) => {
        if (!first.confirm) return
        const oldPassword = first.content || ''
        wx.showModal({
          title: '新密码',
          editable: true,
          placeholderText: '至少 8 位，含字母和数字',
          success: (second) => {
            if (!second.confirm) return
            put('/api/auth/password', { oldPassword, newPassword: second.content || '' })
              .then(() => wx.showToast({ title: '密码已更新', icon: 'success' }))
              .catch((err) => wx.showToast({ title: err.message || '修改失败', icon: 'none' }))
          }
        })
      }
    })
  },
  logout() {
    post('/api/auth/logout')
      .catch(() => {})
      .finally(() => {
        wx.removeStorageSync('token')
        wx.removeStorageSync('user')
        wx.reLaunch({ url: '/pages/login/index' })
      })
  }
})
