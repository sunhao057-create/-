const get = require('../../utils/request').get
const parsePath = require('../../utils/module').parsePath
const listRows = require('../../utils/module').listRows
const rowTitle = require('../../utils/module').rowTitle
const rowSub = require('../../utils/module').rowSub

Page({
  data: {
    keyword: '',
    loading: false,
    rows: [],
    canCreate: false
  },
  onLoad: function (query) {
    this.spec = parsePath(decodeURIComponent(query.path || ''))
    if (!this.spec) {
      wx.showToast({ title: '页面不存在', icon: 'none' })
      return
    }
    wx.setNavigationBarTitle({ title: this.spec.title })
    this.setData({ canCreate: false })
    const self = this
    const schemaQuery = this.spec.module ? { module: this.spec.module } : { path: this.spec.listPath }
    get('/api/mobile/form-schema', schemaQuery)
      .then(function (schema) {
        if (schema && schema.module) {
          self.spec.module = schema.module
        }
        self.setData({ canCreate: !!(schema && schema.canCreate) })
      })
      .catch(function () {
        self.setData({ canCreate: false })
      })
    this.reload()
  },
  onPullDownRefresh: function () {
    this.reload().then(function () {
      wx.stopPullDownRefresh()
    })
  },
  onKeyword: function (event) {
    this.setData({ keyword: event.detail.value })
  },
  reload: function () {
    const self = this
    if (!this.spec) return Promise.resolve()
    this.setData({ loading: true })
    return get(this.spec.listUrl, { keyword: this.data.keyword, page: 1, size: 50 })
      .then(function (data) {
        const src = listRows(data)
        const rows = []
        for (let i = 0; i < src.length; i++) {
          const item = src[i]
          rows.push({
            id: item.id,
            titleText: rowTitle(item),
            subText: rowSub(item)
          })
        }
        self.setData({ rows: rows, loading: false })
      })
      .catch(function (err) {
        self.setData({ loading: false })
        wx.showToast({ title: err.message || '加载失败', icon: 'none' })
      })
  },
  create: function () {
    if (!this.data.canCreate) {
      wx.showToast({ title: '没有新增权限', icon: 'none' })
      return
    }
    wx.navigateTo({ url: '/pages/form/index?path=' + encodeURIComponent(this.spec.listPath + '/new') })
  },
  openItem: function (event) {
    const id = event.currentTarget.dataset.id
    wx.navigateTo({ url: '/pages/form/index?path=' + encodeURIComponent(this.spec.listPath + '/' + id) })
  }
})
