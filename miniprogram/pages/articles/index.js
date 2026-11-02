const get = require('../../utils/request').get
const localImage = require('../../utils/media').localImage

Page({
  data: { title: '内容', rows: [] },
  onLoad(query) {
    this.kind = query.kind || 'MINI_KNOWLEDGE'
    this.category = query.category ? decodeURIComponent(query.category) : ''
    const title = query.title ? decodeURIComponent(query.title) : '内容'
    this.setData({ title: title })
    wx.setNavigationBarTitle({ title: title })
  },
  onShow() {
    if (!wx.getStorageSync('token')) {
      wx.reLaunch({ url: '/pages/login/index' })
      return
    }
    get('/api/mini/portal/' + this.kind).then((rows) => {
      const list = (rows || []).filter((item) => !this.category || item.category === this.category)
      Promise.all(list.map((item) => localImage(item.coverUrl).then((image) => ({
        id: item.id,
        title: item.title,
        summary: item.summary || '',
        image: image
      })))).then((rows) => this.setData({ rows: rows }))
    }).catch((err) => wx.showToast({ title: err.message || '加载失败', icon: 'none' }))
  },
  open(e) {
    wx.navigateTo({ url: '/pages/article/index?kind=' + this.kind + '&id=' + e.currentTarget.dataset.id })
  }
})
