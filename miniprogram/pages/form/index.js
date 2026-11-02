const get = require('../../utils/request').get
const post = require('../../utils/request').post
const put = require('../../utils/request').put
const postForm = require('../../utils/request').postForm
const putForm = require('../../utils/request').putForm
const upload = require('../../utils/request').upload
const del = require('../../utils/request').del
const parsePath = require('../../utils/module').parsePath
const apiBase = require('../../utils/request').apiBase

const LOOKUP_URL = {
  staff: '/api/staff',
  dept: '/api/oa/lookups',
  customer: '/api/customers',
  project: '/api/projects',
  supplier: '/api/suppliers',
  account: '/api/finance/accounts',
  warehouse: '/api/warehouse',
  device: '/api/devices',
  contract: '/api/contracts',
  region: '/api/regions',
  purchase: '/api/purchase',
  loan: '/api/oa/loan',
  depositApply: '/api/deposit-applies'
}

function today() {
  const d = new Date()
  const m = d.getMonth() + 1
  const day = d.getDate()
  return d.getFullYear() + '-' + (m < 10 ? '0' : '') + m + '-' + (day < 10 ? '0' : '') + day
}

function asList(data) {
  if (!data) return []
  if (Array.isArray(data)) return data
  if (Array.isArray(data.items)) return data.items
  if (Array.isArray(data.staff)) return data.staff
  if (Array.isArray(data.departments)) return data.departments
  return []
}

function optionLabel(item) {
  return item.displayName || item.name || item.shortName || item.title || item.code || item.label || ('#' + item.id)
}

function optionValue(item) {
  if (item.id != null) return String(item.id)
  if (item.value != null) return String(item.value)
  return String(item)
}

function clone(obj) {
  return JSON.parse(JSON.stringify(obj))
}

function flattenDetail(detail) {
  const values = {}
  if (!detail) return values
  const keys = Object.keys(detail)
  for (let i = 0; i < keys.length; i++) {
    const key = keys[i]
    const val = detail[key]
    if (val == null) continue
    if (key === 'leave' || key === 'trip' || key === 'interview' || key === 'seal' || key === 'loan' || key === 'extra') {
      const nested = val
      const inner = Object.keys(nested)
      for (let j = 0; j < inner.length; j++) {
        values[inner[j]] = nested[inner[j]]
      }
    } else if (key === 'qualify') {
      const q = val
      values.lendDate = q.lendDate
      values.returnDate = q.returnDate
      values.qualifyCompanies = q.companies
      values.qualifyItems = q.items
      values.companyAuditorId = q.companyAuditorId
      values.financeAuditorId = q.financeAuditorId
      values.contractAuditorId = q.contractAuditorId
      values.companyAuditorNote = q.companyAuditorNote
      values.financeAuditorNote = q.financeAuditorNote
      values.contractAuditorNote = q.contractAuditorNote
      values.contractDesc = q.contractDesc
      values.otherCertNote = q.otherCertNote
    } else if (key === 'files') {
      values.files = val
      values.attachments = val
    } else if (typeof val !== 'object' || Array.isArray(val)) {
      values[key] = val
    }
  }
  return values
}

function decorateRow(row, no) {
  row.no = no
  const cells = row.cells || []
  for (let i = 0; i < cells.length; i++) {
    const cell = cells[i]
    cell.ck = cell.key
    markCellUi(cell)
  }
  return row
}

function emptyRow(columns) {
  const row = { rid: String(Date.now()) + String(Math.random()), cells: [] }
  for (let i = 0; i < columns.length; i++) {
    const col = columns[i]
    row.cells.push({
      key: col.key,
      ck: col.key,
      type: col.type || 'text',
      label: col.label,
      text: '',
      display: '请选择',
      index: 0,
      optionLabels: col.optionLabels || [],
      show: (col.type || 'text') !== 'hint' && (col.type || 'text') !== 'attachment'
    })
  }
  return row
}

function numberRows(rows) {
  for (let i = 0; i < rows.length; i++) {
    decorateRow(rows[i], i + 1)
  }
  return rows
}

function markCellUi(cell) {
  const t = cell.type || 'text'
  cell.show = t !== 'hint' && t !== 'attachment'
  cell.isDate = t === 'date'
  cell.isPick = t === 'select' || t === 'lookup' || t === 'member'
  cell.isArea = t === 'textarea'
  cell.isText = cell.show && !cell.isDate && !cell.isPick && !cell.isArea
  if (!cell.display) {
    cell.display = cell.isDate ? '请选择日期' : '请选择'
  }
}

function markFieldUi(field) {
  const t = field.type || 'text'
  field.fkey = field.key
  field.visible = !field.hidden
  field.hidden = false
  field.isBool = t === 'boolean'
  field.isHint = t === 'hint'
  field.isDate = t === 'date'
  field.isPick = t === 'select' || t === 'lookup' || t === 'member'
  field.isMulti = t === 'multiselect' || t === 'members'
  field.isArea = t === 'textarea'
  field.isPwd = t === 'password'
  field.isNum = t === 'number'
  field.isFile = t === 'attachment'
  field.isSub = t === 'subtable'
  field.isNormal = !field.isBool
  field.isText = field.isNormal && !field.isHint && !field.isDate && !field.isPick && !field.isMulti && !field.isArea && !field.isPwd && !field.isNum && !field.isFile && !field.isSub
  field.canUpload = !field.readonly
  field.noFiles = field.readonly && (!field.files || !field.files.length)
  if (!field.hint) {
    field.hint = '保存后自动生成'
  }
  if (!field.display) {
    field.display = '请选择'
  }
  field.dateShow = field.text ? field.text : '请选择日期'
  const rows = field.rows || []
  for (let i = 0; i < rows.length; i++) {
    const cells = rows[i].cells || []
    for (let c = 0; c < cells.length; c++) {
      markCellUi(cells[c])
    }
  }
}

function toText(value) {
  if (value == null || value === '') return ''
  if (Array.isArray(value)) return value.join(',')
  return String(value)
}

function fileItems(raw) {
  const list = Array.isArray(raw) ? raw : []
  const out = []
  for (let i = 0; i < list.length; i++) {
    const item = list[i] || {}
    out.push({
      id: item.id || '',
      name: item.originalName || item.name || '附件',
      url: item.url || '',
      pending: item.pending || ''
    })
  }
  return out
}

function fillColumnOptions(columns, lookups) {
  const out = []
  for (let i = 0; i < columns.length; i++) {
    const col = JSON.parse(JSON.stringify(columns[i]))
    let options = col.options ? col.options.slice() : []
    if (col.lookup && lookups[col.lookup]) {
      const rows = lookups[col.lookup]
      options = []
      for (let r = 0; r < rows.length; r++) {
        options.push({ value: optionValue(rows[r]), label: optionLabel(rows[r]) })
      }
    }
    col.options = options
    col.optionLabels = []
    for (let o = 0; o < options.length; o++) {
      col.optionLabels.push(options[o].label)
    }
    out.push(col)
  }
  return out
}

function buildField(src, values, lookups, runtime) {
  const field = {
    key: src.key,
    label: src.label,
    type: src.type,
    required: !!src.required,
    extra: src.extra || 'root',
    saveKey: src.saveKey || src.key,
    lookup: src.lookup || '',
    hint: src.hint || '',
    fileKey: src.fileKey || src.key,
    hidden: false,
    readonly: false,
    offClass: '',
    text: '',
    checked: false,
    index: 0,
    display: '',
    options: [],
    optionLabels: [],
    rows: [],
    files: [],
    columns: src.columns || []
  }
  const mode = runtime && runtime.fields ? runtime.fields[src.key] : ''
  if (mode === 'HIDDEN') {
    field.hidden = true
  }
  if (mode === 'VISIBLE') {
    field.readonly = true
    field.offClass = 'off'
  }
  let options = src.options ? src.options.slice() : []
  if (src.lookup && lookups[src.lookup]) {
    const rows = lookups[src.lookup]
    options = []
    for (let i = 0; i < rows.length; i++) {
      options.push({ value: optionValue(rows[i]), label: optionLabel(rows[i]) })
    }
    if (src.type === 'lookup' || src.type === 'member') {
      field.type = 'lookup'
    }
    if (src.type === 'members') {
      field.type = 'members'
    }
  }
  const raw = values[src.key]
  if (field.type === 'attachment') {
    field.files = fileItems(raw || values.files || values.attachments)
  } else if (field.type === 'boolean') {
    field.checked = !!raw
  } else if (field.type === 'subtable') {
    field.columns = fillColumnOptions(field.columns, lookups)
    const list = Array.isArray(raw) ? raw : []
    const rows = []
    for (let i = 0; i < list.length; i++) {
      const row = emptyRow(field.columns)
      const item = list[i] || {}
      for (let c = 0; c < field.columns.length; c++) {
        const col = field.columns[c]
        const cell = row.cells[c]
        const val = item[col.key]
        cell.text = val == null ? '' : String(val)
        if (col.type === 'boolean') {
          cell.text = val ? '1' : ''
        }
        if ((col.type === 'lookup' || col.type === 'select' || col.type === 'member') && col.options) {
          cell.display = '请选择'
          for (let o = 0; o < col.options.length; o++) {
            if (String(col.options[o].value) === String(val)) {
              cell.index = o
              cell.display = col.options[o].label
              break
            }
          }
        } else if (col.type === 'date') {
          cell.display = cell.text || '请选择日期'
        }
      }
      rows.push(row)
    }
    field.rows = numberRows(rows)
  } else if (field.type === 'multiselect' || field.type === 'members') {
    const selected = {}
    if (Array.isArray(raw)) {
      for (let i = 0; i < raw.length; i++) {
        selected[String(raw[i])] = true
      }
    } else if (typeof raw === 'string' && raw) {
      const parts = raw.split(',')
      for (let i = 0; i < parts.length; i++) {
        selected[parts[i]] = true
      }
    }
    for (let i = 0; i < options.length; i++) {
      options[i] = {
        value: String(options[i].value),
        label: options[i].label,
        checked: !!selected[String(options[i].value)]
      }
    }
    field.options = options
  } else if (field.type === 'select' || field.type === 'lookup' || field.type === 'member') {
    let current = raw == null || raw === '' ? '' : String(raw)
    if (!options.length && current) {
      options.push({ value: current, label: current })
    }
    if (!current && options.length) {
      current = String(options[0].value)
    }
    let index = 0
    for (let i = 0; i < options.length; i++) {
      if (String(options[i].value) === current) {
        index = i
        break
      }
    }
    field.options = options
    field.optionLabels = []
    for (let i = 0; i < options.length; i++) {
      field.optionLabels.push(options[i].label)
    }
    field.index = index
    field.display = options[index] ? options[index].label : '请选择'
    field.text = options[index] ? String(options[index].value) : current
  } else if (field.type === 'hint') {
    field.hint = src.hint || '请在电脑端维护'
  } else {
    field.text = toText(raw)
    if (src.type === 'date' && !field.text && (src.key === 'expectedDate' || src.key === 'expenseDate' || src.key === 'payDate' || src.key === 'applyDate' || src.key === 'bizDate')) {
      field.text = today()
    }
  }
  markFieldUi(field)
  return field
}

function collectLookups(schemaFields) {
  const needed = {}
  for (let i = 0; i < schemaFields.length; i++) {
    const lookup = schemaFields[i].lookup
    if (lookup) needed[lookup] = true
    const cols = schemaFields[i].columns || []
    for (let c = 0; c < cols.length; c++) {
      if (cols[c].lookup) needed[cols[c].lookup] = true
    }
  }
  const jobs = []
  const names = Object.keys(needed)
  for (let i = 0; i < names.length; i++) {
    const name = names[i]
    const url = LOOKUP_URL[name]
    jobs.push(
      url
        ? get(url, name === 'project' || name === 'purchase' || name === 'loan' || name === 'depositApply' || name === 'device' || name === 'contract'
            ? { page: 1, size: 200 }
            : undefined).catch(function () {
            return []
          })
        : Promise.resolve([])
    )
  }
  return Promise.all(jobs).then(function (res) {
    const map = {}
    for (let i = 0; i < names.length; i++) {
      const name = names[i]
      const data = res[i]
      if (name === 'staff') {
        map.staff = asList(data && data.staff ? data.staff : data)
      } else if (name === 'dept') {
        map.dept = asList(data && data.departments ? data.departments : data)
      } else {
        map[name] = asList(data)
      }
    }
    return map
  })
}

function fieldByKey(fields, key) {
  for (let i = 0; i < fields.length; i++) {
    if (fields[i].key === key) return fields[i]
  }
  return null
}

function daysBetweenText(start, end) {
  if (!start || !end) return ''
  const a = Date.parse(start)
  const b = Date.parse(end)
  if (!isFinite(a) || !isFinite(b) || b < a) return ''
  return String(Math.round((b - a) / 86400000) + 1)
}

function fillDerivedFields(fields) {
  const expected = fieldByKey(fields, 'expectedDate')
  const start = expected ? expected.text : ''
  const endField = fieldByKey(fields, 'endDate')
  if (start && endField && (endField.text === '' || endField.text == null)) {
    endField.text = start
  }
  const month = fieldByKey(fields, 'month')
  if (month && start) {
    month.text = String(start).slice(0, 7)
  }
  const leaveDays = fieldByKey(fields, 'leaveDays')
  const end = endField ? endField.text : ''
  if (leaveDays) {
    const days = daysBetweenText(start, end)
    if (days) leaveDays.text = days
  }
}

function missingRequired(fields) {
  for (let i = 0; i < fields.length; i++) {
    const field = fields[i]
    if (!field.required || field.visible === false || field.readonly) continue
    if (field.type === 'hint' || field.type === 'boolean') continue
    if (field.type === 'attachment') {
      if (!field.files || !field.files.length) return field.label
      continue
    }
    if (field.type === 'subtable') continue
    if (field.type === 'multiselect' || field.type === 'members') {
      let n = 0
      const opts = field.options || []
      for (let o = 0; o < opts.length; o++) {
        if (opts[o].checked) n++
      }
      if (!n) return field.label
      continue
    }
    if (field.text === '' || field.text == null) return field.label
  }
  return ''
}

function fieldValue(field) {
  if (field.type === 'attachment' || field.type === 'hint') return undefined
  if (field.key === 'copyCount') {
    if (field.text === '' || field.text == null) return null
    return String(field.text)
  }
  if (field.type === 'boolean') return !!field.checked
  if (field.type === 'subtable') {
    const rows = []
    for (let i = 0; i < field.rows.length; i++) {
      const src = field.rows[i]
      const row = {}
      let filled = false
      const cells = src.cells || []
      for (let c = 0; c < field.columns.length; c++) {
        const col = field.columns[c]
        if (col.type === 'attachment' || col.type === 'hint') continue
        const cell = cells[c] || {}
        const ck = col.key
        let text = cell.text
        if (col.type === 'boolean') {
          row[ck] = text === '1' || text === 'true'
          if (row[ck]) filled = true
          continue
        }
        if (text !== '' && text != null) filled = true
        if ((col.type === 'number' || col.lookup) && text !== '' && text != null) {
          const num = Number(text)
          row[ck] = isFinite(num) ? num : text
        } else {
          row[ck] = text
        }
      }
      if (filled) rows.push(row)
    }
    return rows
  }
  if (field.type === 'multiselect' || field.type === 'members') {
    const out = []
    for (let i = 0; i < field.options.length; i++) {
      if (field.options[i].checked) {
        const val = field.options[i].value
        out.push(field.type === 'members' ? Number(val) : val)
      }
    }
    return out
  }
  if (field.type === 'hint') return undefined
  if (field.type === 'number') {
    if (field.text === '' || field.text == null) return null
    const num = Number(field.text)
    return isFinite(num) ? num : field.text
  }
  if (field.type === 'lookup' || field.type === 'member' || field.type === 'select') {
    if (field.text === '' || field.text == null) return null
    if (field.lookup === 'staff' || field.lookup === 'dept' || field.lookup === 'customer' || field.lookup === 'project'
        || field.lookup === 'supplier' || field.lookup === 'account' || field.lookup === 'warehouse'
        || field.lookup === 'device' || field.lookup === 'contract' || field.lookup === 'region'
        || field.lookup === 'purchase' || field.lookup === 'loan' || field.lookup === 'depositApply') {
      const num = Number(field.text)
      return isFinite(num) ? num : field.text
    }
    return field.text
  }
  return field.text
}

function buildPayload(schema, fields) {
  const root = {}
  const extras = {}
  for (let i = 0; i < fields.length; i++) {
    const field = fields[i]
    if (field.type === 'hint' || field.type === 'attachment' || field.visible === false) continue
    const value = fieldValue(field)
    const extra = field.extra || 'root'
    const key = field.saveKey || field.key
    if (extra === 'root') {
      root[key] = value
    } else if (extra === 'extra') {
      if (!extras.extra) extras.extra = {}
      extras.extra[key] = value
    } else {
      if (!extras[extra]) extras[extra] = {}
      extras[extra][key] = value
    }
  }
  const keys = Object.keys(extras)
  for (let i = 0; i < keys.length; i++) {
    root[keys[i]] = extras[keys[i]]
  }
  if (schema.family === 'tech') {
    return {
      isPublic: !!root.isPublic,
      extra: extras.extra || {}
    }
  }
  return root
}

function formBody(payload) {
  const out = {}
  const keys = Object.keys(payload)
  for (let i = 0; i < keys.length; i++) {
    const key = keys[i]
    const val = payload[key]
    if (val == null || val === '') continue
    if (typeof val === 'boolean') {
      out[key] = val ? 'true' : 'false'
    } else if (typeof val === 'number') {
      out[key] = String(val)
    } else if (typeof val === 'object') {
      continue
    } else {
      out[key] = val
    }
  }
  return out
}

Page({
  data: {
    loading: true,
    pending: false,
    fields: [],
    statusText: '',
    canAdvance: false,
    canSave: false,
    showSubmit: false,
    viewOnly: false,
    showForm: false,
    showEmpty: false,
    recordId: null
  },
  onLoad: function (query) {
    this.spec = parsePath(decodeURIComponent(query.path || ''))
    if (!this.spec) {
      wx.showToast({ title: '页面不存在', icon: 'none' })
      return
    }
    wx.setNavigationBarTitle({ title: this.spec.title || '办理' })
    this.setData({ recordId: this.spec.id })
    this.boot()
  },
  boot: function () {
    const spec = this.spec
    const self = this
    const schemaQuery = spec.module ? { module: spec.module } : { path: spec.listPath || spec.slug }
    get('/api/mobile/form-schema', schemaQuery)
      .then(function (schema) {
        self.schema = schema
        if (schema.module) {
          spec.module = schema.module
        }
        wx.setNavigationBarTitle({ title: schema.title || spec.title || '办理' })
        return collectLookups(schema.fields || []).then(function (lookups) {
          self.lookups = lookups
          const jobs = []
          if (spec.getUrl) {
            jobs.push(get(spec.getUrl))
          } else {
            jobs.push(Promise.resolve(null))
          }
          if (spec.module && spec.id) {
            jobs.push(
              get('/api/workflows/runtime?module=' + encodeURIComponent(spec.module) + '&bizId=' + spec.id).catch(function () {
                return null
              })
            )
          } else {
            jobs.push(Promise.resolve(null))
          }
          return Promise.all(jobs)
        })
      })
      .then(function (res) {
        const detail = res[0]
        const runtime = res[1]
        self.detail = detail
        const user = wx.getStorageSync('user') || {}
        let values = flattenDetail(detail)
        if (!spec.id) {
          if (values.staffId == null && user.id) values.staffId = user.id
          if (!values.applicantName && user.displayName) values.applicantName = user.displayName
          if (values.departmentId == null && user.departmentId) values.departmentId = user.departmentId
          if (!values.position && user.position) values.position = user.position
          if (!values.expectedDate) values.expectedDate = today()
        }
        const schemaFields = self.schema.fields || []
        const canCreate = !!self.schema.canCreate
        const canEdit = !!self.schema.canEdit
        const canSave = spec.id ? canEdit : canCreate
        const fields = []
        for (let i = 0; i < schemaFields.length; i++) {
          const field = buildField(schemaFields[i], values, self.lookups || {}, runtime)
          if (!canSave) {
            field.readonly = true
            field.offClass = 'off'
          }
          if (field.type === 'subtable' && !field.readonly && !field.rows.length) {
            field.rows = numberRows([emptyRow(field.columns)])
          } else if (field.type === 'subtable') {
            field.rows = numberRows(field.rows || [])
          }
          fields.push(field)
        }
        fillDerivedFields(fields)
        for (let j = 0; j < fields.length; j++) {
          markFieldUi(fields[j])
        }
        const canAdvance = !!(runtime && runtime.canAdvance)
        self.setData({
          loading: false,
          fields: fields,
          statusText: (detail && (detail.workflowStatusLabel || detail.statusLabel)) || (runtime && runtime.currentNodeName) || '',
          canAdvance: canAdvance,
          canSave: canSave,
          showSubmit: canSave || canAdvance,
          viewOnly: !canSave,
          showForm: fields.length > 0,
          showEmpty: fields.length === 0,
          recordId: spec.id
        })
      })
      .catch(function (err) {
        self.setData({ loading: false, showEmpty: true, showForm: false })
        wx.showToast({ title: err.message || '加载失败', icon: 'none' })
      })
  },
  patchField: function (index, patch) {
    const fields = clone(this.data.fields)
    const item = fields[index]
    const keys = Object.keys(patch)
    for (let i = 0; i < keys.length; i++) {
      item[keys[i]] = patch[keys[i]]
    }
    fields[index] = item
    this.setData({ fields: fields })
  },
  onText: function (e) {
    this.patchField(Number(e.currentTarget.dataset.index), { text: e.detail.value })
  },
  onDate: function (e) {
    const index = Number(e.currentTarget.dataset.index)
    const fields = clone(this.data.fields)
    fields[index].text = e.detail.value
    if (fields[index].key === 'expectedDate') {
      for (let i = 0; i < fields.length; i++) {
        if (fields[i].key === 'month') {
          fields[i].text = String(e.detail.value).slice(0, 7)
        }
        if (fields[i].key === 'leaveDays') {
          const end = this.findText(fields, 'endDate')
          const days = this.daysBetween(e.detail.value, end)
          if (days) fields[i].text = days
        }
      }
    }
    if (fields[index].key === 'endDate') {
      for (let i = 0; i < fields.length; i++) {
        if (fields[i].key === 'leaveDays') {
          const start = this.findText(fields, 'expectedDate')
          const days = this.daysBetween(start, e.detail.value)
          if (days) fields[i].text = days
        }
      }
    }
    fillDerivedFields(fields)
    for (let i = 0; i < fields.length; i++) {
      markFieldUi(fields[i])
    }
    this.setData({ fields: fields })
  },
  findText: function (fields, key) {
    for (let i = 0; i < fields.length; i++) {
      if (fields[i].key === key) return fields[i].text
    }
    return ''
  },
  daysBetween: function (start, end) {
    if (!start || !end) return ''
    const a = Date.parse(start)
    const b = Date.parse(end)
    if (!isFinite(a) || !isFinite(b) || b < a) return ''
    return String(Math.round((b - a) / 86400000) + 1)
  },
  onBool: function (e) {
    this.patchField(Number(e.currentTarget.dataset.index), { checked: !!e.detail.value })
  },
  onPick: function (e) {
    const index = Number(e.currentTarget.dataset.index)
    const fields = clone(this.data.fields)
    const item = fields[index]
    const next = Number(e.detail.value)
    item.index = next
    if (item.options[next]) {
      item.text = String(item.options[next].value)
      item.display = item.options[next].label
    }
    fields[index] = item
    this.setData({ fields: fields })
  },
  onMulti: function (e) {
    const index = Number(e.currentTarget.dataset.index)
    const picked = {}
    const values = e.detail.value || []
    for (let i = 0; i < values.length; i++) {
      picked[String(values[i])] = true
    }
    const fields = clone(this.data.fields)
    const options = fields[index].options
    for (let i = 0; i < options.length; i++) {
      options[i].checked = !!picked[String(options[i].value)]
    }
    this.setData({ fields: fields })
  },
  onCell: function (e) {
    const index = Number(e.currentTarget.dataset.index)
    const ri = Number(e.currentTarget.dataset.ri)
    const ci = Number(e.currentTarget.dataset.ci)
    const fields = clone(this.data.fields)
    fields[index].rows[ri].cells[ci].text = e.detail.value
    this.setData({ fields: fields })
  },
  addRow: function (e) {
    const index = Number(e.currentTarget.dataset.index)
    const fields = clone(this.data.fields)
    fields[index].rows.push(emptyRow(fields[index].columns))
    numberRows(fields[index].rows)
    this.setData({ fields: fields })
  },
  removeRow: function (e) {
    const index = Number(e.currentTarget.dataset.index)
    const ri = Number(e.currentTarget.dataset.ri)
    const fields = clone(this.data.fields)
    fields[index].rows.splice(ri, 1)
    numberRows(fields[index].rows)
    this.setData({ fields: fields })
  },
  onCellPick: function (e) {
    const index = Number(e.currentTarget.dataset.index)
    const ri = Number(e.currentTarget.dataset.ri)
    const ci = Number(e.currentTarget.dataset.ci)
    const fields = clone(this.data.fields)
    const col = fields[index].columns[ci]
    const next = Number(e.detail.value)
    const cell = fields[index].rows[ri].cells[ci]
    cell.index = next
    if (col && col.options && col.options[next]) {
      cell.text = String(col.options[next].value)
      cell.display = col.options[next].label
    }
    this.setData({ fields: fields })
  },
  onCellDate: function (e) {
    const index = Number(e.currentTarget.dataset.index)
    const ri = Number(e.currentTarget.dataset.ri)
    const ci = Number(e.currentTarget.dataset.ci)
    const fields = clone(this.data.fields)
    fields[index].rows[ri].cells[ci].text = e.detail.value
    fields[index].rows[ri].cells[ci].display = e.detail.value
    this.setData({ fields: fields })
  },
  chooseFile: function (e) {
    const index = Number(e.currentTarget.dataset.index)
    const self = this
    const pick = function (paths) {
      if (self.spec.id) {
        self.setData({ pending: true })
        let chain = Promise.resolve()
        for (let i = 0; i < paths.length; i++) {
          const path = paths[i]
          chain = chain.then(function () {
            return upload('/api/mobile/attachments', path, {
              module: self.schema.module,
              bizId: String(self.spec.id),
              fieldKey: self.data.fields[index].fileKey || self.data.fields[index].key
            })
          })
        }
        chain
          .then(function () {
            wx.showToast({ title: '已上传', icon: 'success' })
            self.boot()
          })
          .catch(function (err) {
            wx.showToast({ title: err.message || '上传失败', icon: 'none' })
          })
          .then(function () {
            self.setData({ pending: false })
          })
        return
      }
      const fields = clone(self.data.fields)
      const files = fields[index].files || []
      for (let i = 0; i < paths.length; i++) {
        const path = paths[i]
        const name = path.split('/').pop()
        files.push({ id: '', name: name, url: '', pending: path })
      }
      fields[index].files = files
      self.setData({ fields: fields })
    }
    wx.chooseMessageFile({
      count: 5,
      type: 'all',
      success: function (res) {
        const paths = []
        const temp = res.tempFiles || []
        for (let i = 0; i < temp.length; i++) {
          paths.push(temp[i].path)
        }
        pick(paths)
      },
      fail: function () {
        wx.chooseImage({
          count: 5,
          success: function (res) {
            pick(res.tempFilePaths || [])
          }
        })
      }
    })
  },
  removeAttach: function (e) {
    const index = Number(e.currentTarget.dataset.index)
    const fi = Number(e.currentTarget.dataset.fi)
    const fields = clone(this.data.fields)
    const file = fields[index].files[fi]
    const self = this
    if (file && file.id && this.spec.id) {
      this.setData({ pending: true })
      del('/api/mobile/attachments?module=' + encodeURIComponent(this.schema.module) + '&bizId=' + this.spec.id + '&fileId=' + file.id)
        .then(function () {
          self.boot()
        })
        .catch(function (err) {
          wx.showToast({ title: err.message || '删除失败', icon: 'none' })
        })
        .then(function () {
          self.setData({ pending: false })
        })
      return
    }
    fields[index].files.splice(fi, 1)
    this.setData({ fields: fields })
  },
  previewAttach: function (e) {
    const index = Number(e.currentTarget.dataset.index)
    const fi = Number(e.currentTarget.dataset.fi)
    const file = this.data.fields[index].files[fi]
    if (!file) return
    let url = file.pending || file.url
    if (!url) return
    if (url.indexOf('http') !== 0 && url.indexOf('/uploads/') === 0) {
      url = apiBase + url
    }
    const lower = (file.name || url).toLowerCase()
    if (lower.indexOf('.png') >= 0 || lower.indexOf('.jpg') >= 0 || lower.indexOf('.jpeg') >= 0 || lower.indexOf('.gif') >= 0 || lower.indexOf('.webp') >= 0) {
      wx.previewImage({ urls: [url] })
      return
    }
    wx.downloadFile({
      url: url,
      header: { Authorization: 'Bearer ' + (wx.getStorageSync('token') || '') },
      success: function (res) {
        wx.openDocument({ filePath: res.tempFilePath, showMenu: true })
      }
    })
  },
  uploadPending: function (bizId) {
    const fields = this.data.fields
    const module = this.schema.module
    let chain = Promise.resolve()
    for (let i = 0; i < fields.length; i++) {
      if (fields[i].type !== 'attachment') continue
      const files = fields[i].files || []
      for (let f = 0; f < files.length; f++) {
        if (!files[f].pending) continue
        const path = files[f].pending
        const fieldKey = fields[i].fileKey || fields[i].key
        chain = chain.then(function () {
          return upload('/api/mobile/attachments', path, {
            module: module,
            bizId: String(bizId),
            fieldKey: fieldKey
          })
        })
      }
    }
    return chain
  },
  saveRequest: function (body) {
    const spec = this.spec
    const schema = this.schema
    const encoding = schema.saveEncoding
    let url = schema.saveUrl
    if (!url) {
      return Promise.reject(new Error('该应用暂不支持在小程序保存'))
    }
    if (spec.id) {
      url = url + '/' + spec.id
      if (encoding === 'form') {
        return putForm(url, formBody(body))
      }
      return put(url, body)
    }
    if (encoding === 'form') {
      return postForm(url, formBody(body))
    }
    return post(url, body)
  },
  save: function () {
    if (!this.data.canSave) {
      wx.showToast({ title: '没有保存权限', icon: 'none' })
      return
    }
    const miss = missingRequired(this.data.fields)
    if (miss) {
      wx.showToast({ title: '请填写' + miss, icon: 'none' })
      return
    }
    const self = this
    this.setData({ pending: true })
    const body = buildPayload(this.schema, this.data.fields)
    this.saveRequest(body)
      .then(function (row) {
        if (row && row.id) {
          self.spec.id = row.id
          self.spec.mode = 'detail'
          self.spec.getUrl = (self.schema.saveUrl || '') + '/' + row.id
          self.setData({ recordId: row.id })
        }
        return self.uploadPending(self.spec.id)
      })
      .then(function () {
        wx.showToast({ title: '已保存', icon: 'success' })
        self.boot()
      })
      .catch(function (err) {
        wx.showToast({ title: err.message || '保存失败', icon: 'none' })
      })
      .then(function () {
        self.setData({ pending: false })
      })
  },
  submit: function () {
    const self = this
    if (this.schema && this.schema.module) {
      this.spec.module = this.schema.module
    }
    if (this.data.canSave) {
      const miss = missingRequired(this.data.fields)
      if (miss) {
        wx.showToast({ title: '请填写' + miss, icon: 'none' })
        return
      }
    }
    if (!this.data.canSave && (!this.spec.id || !this.spec.module)) {
      wx.showToast({ title: '当前不能提交', icon: 'none' })
      return
    }
    this.setData({ pending: true })
    const afterSave = this.data.canSave
      ? this.saveRequest(buildPayload(this.schema, this.data.fields)).then(function (row) {
          if (row && row.id) {
            self.spec.id = row.id
            self.spec.mode = 'detail'
            self.spec.getUrl = (self.schema.saveUrl || '') + '/' + row.id
            self.setData({ recordId: row.id })
          }
          return self.uploadPending(self.spec.id)
        })
      : Promise.resolve()
    afterSave
      .then(function () {
        if (!self.spec.id || !self.spec.module) {
          throw new Error('请先保存后再提交')
        }
        return post('/api/workflows/advance', {
          formModule: self.spec.module,
          bizId: self.spec.id,
          comment: ''
        })
      })
      .then(function () {
        wx.showToast({ title: '已提交', icon: 'success' })
        self.boot()
      })
      .catch(function (err) {
        const msg = err && err.message ? err.message : ''
        if (msg.indexOf('尚未发起') >= 0 || msg.indexOf('流程已结束') >= 0) {
          wx.showToast({ title: '已保存', icon: 'success' })
          self.boot()
          return
        }
        if (msg.indexOf('不由你处理') >= 0) {
          wx.showToast({ title: '已保存，当前节点无需你处理', icon: 'none' })
          self.boot()
          return
        }
        wx.showToast({ title: msg || '提交失败', icon: 'none' })
      })
      .then(function () {
        self.setData({ pending: false })
      })
  }
})
