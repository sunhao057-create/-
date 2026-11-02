const get = require('../../utils/request').get
const localImage = require('../../utils/media').localImage
const guardCustomer = require('../../utils/route').guardCustomer

Page({
  data: {
    banners: [],
    companies: [],
    notice: '欢迎使用蜜蜂e工设备服务'
  },
  onShow() {
    if (!guardCustomer()) return
    this.load()
  },
  load() {
    Promise.all([
      get('/api/mini/portal/MINI_BANNER'),
      get('/api/mini/portal/MINI_COMPANY'),
      get('/api/mini/portal/MINI_KNOWLEDGE')
    ]).then((res) => {
      const bannerRows = (res[0] || []).map((item) => ({ id: item.id, coverUrl: item.coverUrl, title: item.title }))
      const companyRows = (res[1] || []).slice(0, 2).map((item) => ({
        id: item.id,
        title: item.category || item.title,
        coverUrl: item.coverUrl
      }))
      const first = (res[2] || [])[0]
      return Promise.all([
        Promise.all(bannerRows.map((item) => localImage(item.coverUrl).then((image) => ({ id: item.id, title: item.title, image: image })))),
        Promise.all(companyRows.map((item) => localImage(item.coverUrl).then((image) => ({ id: item.id, title: item.title, image: image }))))
      ]).then((loaded) => {
        this.setData({
          banners: loaded[0],
          companies: loaded[1],
          notice: first && first.title ? first.title : '欢迎使用蜜蜂e工设备服务'
        })
      })
    }).catch(() => {})
  },
  openRepair() {
    wx.reLaunch({ url: '/pages/devices/index' })
  },
  openKnowledge() {
    wx.navigateTo({ url: '/pages/articles/index?kind=MINI_KNOWLEDGE&title=知识中心' })
  },
  openMove() {
    wx.navigateTo({ url: '/pages/records/index?type=move&title=' + encodeURIComponent('搬迁服务') })
  },
  openMaint() {
    wx.navigateTo({ url: '/pages/records/index?type=maint&title=' + encodeURIComponent('维保服务') })
  },
  openArticle(e) {
    wx.navigateTo({ url: '/pages/article/index?kind=MINI_COMPANY&id=' + e.currentTarget.dataset.id })
  }
})
