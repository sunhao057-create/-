const openCamera = require('../../utils/scan').openCamera

Component({
  options: {
    virtualHost: true
  },
  properties: {
    active: { type: String, value: 'workbench' },
    todo: { type: Number, value: 0 },
    mode: { type: String, value: 'customer' }
  },
  methods: {
    go(event) {
      const url = event.currentTarget.dataset.url
      if (!url) return
      if (url.indexOf('/pages/scan/index') === 0) {
        openCamera((code) => {
          wx.reLaunch({ url: '/pages/scan/index?code=' + encodeURIComponent(code) })
        })
        return
      }
      wx.reLaunch({ url })
    }
  }
})
