const get = require('../../utils/request').get
const post = require('../../utils/request').post
const upload = require('../../utils/request').upload
const guardCustomer = require('../../utils/route').guardCustomer
const openCamera = require('../../utils/scan').openCamera

const GRADES = ['A级', 'B级', 'C级']

function blankForm() {
  return {
    grade: '',
    customerName: '',
    customerAddress: '',
    contactName: '',
    contactPhone: '',
    instrumentName: '',
    instrumentModel: '',
    brand: '',
    fault: '',
    clientOrg: '',
    accountManager: '',
    remark: ''
  }
}

Page({
  data: {
    grades: GRADES,
    gradeIndex: 0,
    code: '',
    device: null,
    lock: {},
    form: blankForm(),
    photos: [],
    pending: false,
    blocked: ''
  },
  onLoad(query) {
    this.pendingId = query.id || ''
    this.pendingCode = query.code ? decodeURIComponent(query.code) : ''
  },
  onShow() {
    if (!guardCustomer()) return
    if (this.data.device) return
    if (this.pendingId) this.loadById(this.pendingId)
    else if (this.pendingCode) this.loadByCode(this.pendingCode)
  },
  onCode(e) {
    this.setData({ code: e.detail.value })
  },
  scan() {
    openCamera((code) => this.loadByCode(code))
  },
  lookupManual() {
    this.loadByCode(this.data.code)
  },
  loadById(id) {
    get('/api/mini/devices/' + id)
      .then((detail) => this.applyDevice(detail && detail.device))
      .catch((err) => wx.showToast({ title: err.message || '加载失败', icon: 'none' }))
  },
  loadByCode(code) {
    const value = String(code || '').trim()
    if (!value) {
      wx.showToast({ title: '请扫描或输入设备编码', icon: 'none' })
      return
    }
    get('/api/mini/devices/lookup', { code: value })
      .then((device) => this.applyDevice(device))
      .catch((err) => wx.showToast({ title: err.message || '找不到这台设备', icon: 'none' }))
  },
  applyDevice(device) {
    if (!device || !device.id) {
      wx.showToast({ title: '找不到这台设备', icon: 'none' })
      return
    }
    const form = blankForm()
    form.instrumentName = device.name || ''
    form.instrumentModel = device.model || ''
    form.brand = device.brand || ''
    form.accountManager = device.managerName || ''
    const blocked = device.status && device.status !== 'IN_USE' ? '当前状态为' + (device.statusLabel || device.status) + '，不能报修' : ''
    this.setData({
      device: device,
      code: device.code || '',
      form: form,
      blocked: blocked,
      lock: {
        instrumentName: !!form.instrumentName,
        instrumentModel: !!form.instrumentModel,
        brand: !!form.brand,
        accountManager: !!form.accountManager
      }
    })
  },
  onGrade(e) {
    const index = Number(e.detail.value)
    const form = Object.assign({}, this.data.form, { grade: GRADES[index] || '' })
    this.setData({ gradeIndex: index, form: form })
  },
  onField(e) {
    const form = Object.assign({}, this.data.form)
    form[e.currentTarget.dataset.key] = e.detail.value
    this.setData({ form: form })
  },
  choosePhoto() {
    const remain = 6 - this.data.photos.length
    if (remain <= 0) {
      wx.showToast({ title: '最多 6 张', icon: 'none' })
      return
    }
    wx.chooseMedia({
      count: remain,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        const next = (res.tempFiles || []).map((file) => file.tempFilePath)
        this.setData({ photos: this.data.photos.concat(next) })
      },
      fail: () => {
        wx.chooseImage({
          count: remain,
          success: (res) => this.setData({ photos: this.data.photos.concat(res.tempFilePaths || []) })
        })
      }
    })
  },
  removePhoto(e) {
    const photos = this.data.photos.slice()
    photos.splice(e.currentTarget.dataset.index, 1)
    this.setData({ photos: photos })
  },
  submit() {
    const device = this.data.device
    const form = this.data.form
    if (!device) {
      wx.showToast({ title: '请先扫码设备', icon: 'none' })
      return
    }
    if (this.data.blocked) {
      wx.showToast({ title: this.data.blocked, icon: 'none' })
      return
    }
    const required = [
      ['grade', '请选择工单等级'],
      ['customerName', '请填写客户名称'],
      ['customerAddress', '请填写单位地址'],
      ['contactName', '请填写联系人'],
      ['contactPhone', '请填写联系电话'],
      ['instrumentName', '请填写仪器名称'],
      ['instrumentModel', '请填写仪器型号'],
      ['brand', '请填写仪器厂家或品牌'],
      ['fault', '请填写故障现象'],
      ['clientOrg', '请填写委托单位'],
      ['accountManager', '请填写客户经理'],
      ['remark', '请填写备注']
    ]
    for (let i = 0; i < required.length; i++) {
      if (!String(form[required[i][0]] || '').trim()) {
        wx.showToast({ title: required[i][1], icon: 'none' })
        return
      }
    }
    this.setData({ pending: true })
    const photos = this.data.photos.slice()
    post('/api/mini/devices/' + device.id + '/repairs', {
      grade: form.grade.trim(),
      customerName: form.customerName.trim(),
      customerAddress: form.customerAddress.trim(),
      contactName: form.contactName.trim(),
      contactPhone: form.contactPhone.trim(),
      instrumentName: form.instrumentName.trim(),
      instrumentModel: form.instrumentModel.trim(),
      brand: form.brand.trim(),
      fault: form.fault.trim(),
      clientOrg: form.clientOrg.trim(),
      accountManager: form.accountManager.trim(),
      remark: form.remark.trim()
    })
      .then((order) => Promise.all(photos.map((file) => upload('/api/mini/devices/' + device.id + '/repairs/' + order.id + '/files', file))))
      .then(() => {
        this.pendingId = ''
        this.pendingCode = ''
        this.setData({ pending: false, photos: [], form: blankForm(), device: null, blocked: '', code: '' })
        wx.showToast({ title: '已提交', icon: 'success' })
      })
      .catch((err) => {
        this.setData({ pending: false })
        wx.showToast({ title: err.message || '提交失败', icon: 'none' })
      })
  }
})
