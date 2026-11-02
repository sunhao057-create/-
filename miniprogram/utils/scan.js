function codeFromScan(raw) {
  const text = String(raw || '').trim()
  const lines = text.split(/\r?\n/)
  for (let i = 0; i < lines.length; i++) {
    if (lines[i].indexOf('设备编码') >= 0) {
      const value = lines[i].split(/[:：]/).slice(1).join(':').trim()
      if (value) return value
    }
  }
  const matched = text.match(/EQ\d+/)
  return matched ? matched[0] : text
}

function isDevtools() {
  try {
    return wx.getSystemInfoSync().platform === 'devtools'
  } catch (e) {
    return false
  }
}

function finish(raw, done) {
  const code = codeFromScan(raw)
  if (!code) {
    wx.showToast({ title: '二维码里没有设备编码', icon: 'none' })
    return
  }
  if (done) done(code)
}

function askCode(done) {
  wx.showModal({
    title: '模拟扫码',
    editable: true,
    placeholderText: '输入设备编码，如 EQ20260908001',
    success: (res) => {
      if (res.confirm) finish(res.content, done)
    }
  })
}

function openCamera(done) {
  const devtools = isDevtools()
  wx.scanCode({
    onlyFromCamera: !devtools,
    scanType: ['qrCode'],
    success: (res) => finish(res.result, done),
    fail: (err) => {
      const msg = (err && err.errMsg) || ''
      if (msg.indexOf('cancel') >= 0) return
      if (devtools) {
        askCode(done)
        return
      }
      wx.showToast({ title: '无法打开摄像头', icon: 'none' })
    }
  })
}

module.exports = { codeFromScan: codeFromScan, openCamera: openCamera }
