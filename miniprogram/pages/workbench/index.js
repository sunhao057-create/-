const get = require('../../utils/request').get
const guardOffice = require('../../utils/route').guardOffice
const greeting = require('../../utils/biz').greeting
const openBiz = require('../../utils/module').openBiz

function shape(groups, openTech) {
  const result = []
  const list = groups || []
  for (let i = 0; i < list.length; i++) {
    const group = list[i]
    const src = group.items || []
    const items = []
    for (let j = 0; j < src.length; j++) {
      const item = src[j]
      items.push({
        module: item.module,
        label: item.label,
        path: item.path,
        createPath: item.createPath,
        tone: item.tone,
        canCreate: item.canCreate,
        glyph: (item.label || '?').slice(0, 1),
        openPath: item.path
      })
    }
    const showMore = group.key === 'tech' && items.length > 4
    result.push({
      key: group.key,
      title: group.title,
      items: items,
      showMore: showMore,
      shown: showMore && !openTech ? items.slice(0, 4) : items
    })
  }
  return result
}

Page({
  data: {
    name: '一体化管理系统',
    hello: greeting(),
    displayName: '',
    todo: 0,
    overdue: 0,
    draft: 0,
    openTech: false,
    rawGroups: [],
    groups: []
  },
  onShow: function () {
    if (!guardOffice()) return
    const user = wx.getStorageSync('user') || {}
    this.setData({
      hello: greeting(),
      displayName: user.displayName || user.username || ''
    })
    this.reload()
  },
  reload: function () {
    const self = this
    Promise.all([
      get('/api/branding'),
      get('/api/mobile/workbench'),
      get('/api/inbox', { type: 'TODO' }),
      get('/api/inbox', { type: 'TODO', overdue: true }),
      get('/api/inbox', { type: 'DRAFT' })
    ])
      .then(function (res) {
        const branding = res[0]
        const groups = res[1]
        const todos = res[2]
        const late = res[3]
        const drafts = res[4]
        const raw = groups || []
        self.setData({
          name: (branding && branding.name) || '一体化管理系统',
          rawGroups: raw,
          groups: shape(raw, self.data.openTech),
          todo: (todos || []).length,
          overdue: (late || []).length,
          draft: (drafts || []).length
        })
      })
      .catch(function (err) {
        wx.showToast({ title: err.message || '加载失败', icon: 'none' })
      })
  },
  toggleTech: function () {
    const openTech = !this.data.openTech
    this.setData({ openTech: openTech, groups: shape(this.data.rawGroups, openTech) })
  },
  goInbox: function () {
    wx.reLaunch({ url: '/pages/inbox/index' })
  },
  openApp: function (event) {
    const path = event.currentTarget.dataset.path
    if (!path) return
    openBiz(path)
  }
})
