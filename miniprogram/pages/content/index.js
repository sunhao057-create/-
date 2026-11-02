const get = require('../../utils/request').get
const localImage = require('../../utils/media').localImage
const guardCustomer = require('../../utils/route').guardCustomer

Page({
  data: { banners: [], tiles: [], standards: [] },
  onShow() {
    if (!guardCustomer()) return
    get('/api/mini/portal/MINI_SERVICE').then((rows) => {
      const list = rows || []
      const banners = []
      const tiles = []
      const standards = []
      list.forEach((item) => {
        const category = item.category || ''
        const row = { id: item.id, title: item.title || category, category: category, coverUrl: item.coverUrl }
        if (category === '顶部横幅') banners.push(row)
        else if (category.indexOf('承诺') >= 0) standards.push(row)
        else tiles.push(row)
      })
      const paint = (items) => Promise.all(items.map((item) => localImage(item.coverUrl).then((image) => {
        item.image = image
        return item
      })))
      return Promise.all([paint(banners), paint(tiles), paint(standards)])
    }).then((loaded) => {
      if (!loaded) return
      this.setData({ banners: loaded[0], tiles: loaded[1], standards: loaded[2] })
    }).catch(() => {})
  },
  open(e) {
    const id = e.currentTarget.dataset.id
    if (!id) return
    wx.navigateTo({ url: '/pages/article/index?kind=MINI_SERVICE&id=' + id })
  }
})
