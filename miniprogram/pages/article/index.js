const get = require('../../utils/request').get
const localImage = require('../../utils/media').localImage

Page({
  data: { title: '', summary: '', body: '', image: '' },
  onLoad(query) {
    this.kind = query.kind || 'MINI_KNOWLEDGE'
    this.id = query.id
  },
  onShow() {
    if (!this.id) return
    get('/api/mini/portal/' + this.kind).then((rows) => {
      const item = (rows || []).find((row) => String(row.id) === String(this.id))
      if (!item) return
      wx.setNavigationBarTitle({ title: item.title || '详情' })
      localImage(item.coverUrl).then((image) => {
        this.setData({
          title: item.title,
          summary: item.summary || '',
          body: item.body || '',
          image: image
        })
      })
    }).catch((err) => wx.showToast({ title: err.message || '加载失败', icon: 'none' }))
  }
})
