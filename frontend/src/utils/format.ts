import { FORM_MODULES } from '../role/modules'
import { techKindByModule } from '../tech/catalog'

const ISO = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/
const ALREADY = /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}/

export function formatDateTime(value?: string | null) {
  if (!value) return ''
  if (ALREADY.test(value)) return value.slice(0, 16)
  if (!ISO.test(value)) return value
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value.replace('T', ' ').slice(0, 16)
  }
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

const TEXT_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  RUNNING: '进行中',
  DONE: '已结束',
  PENDING: '待处理',
  CANCELLED: '已取消',
  ENABLED: '启用',
  DISABLED: '停用',
  TODO: '待办',
  RETURNED: '已退回',
  START: '发起',
  APPROVE: '同意',
  CC: '抄送',
  RETURN: '退回',
  END: '结束',
  APPROVAL: '审批',
  BRANCH: '分支',
  CONDITION: '分支条件',
  TASK: '办理',
  USER_TASK: '办理',
  HIDDEN: '隐藏',
  VISIBLE: '可见',
  EDITABLE: '可编辑',
  READONLY: '只读',
  PROJECT: '项目立项',
  COLLECTION: '项目回款',
  DEPOSIT_APPLY: '项目保证金申请',
  DEPOSIT_RETURN: '项目保证金退回',
  BIDDING: '招标信息',
  PAYMENT: '付款申请',
  CUSTOMER: '客户信息',
  CONTRACT: '合同申请',
  SUPPLIER: '供应商管理',
  USER: '账号管理',
  ROLE: '角色管理',
  ORG: '组织架构',
  WORKFLOW: '流程引擎',
  BRANDING: '系统名称',
  THEME: '主题风格',
  SIGNED: '已签订',
  EXECUTING: '执行中',
  CLOSED: '已关闭',
  WIRE: '电汇',
  CHECK: '支票',
  CASH: '现金',
  OTHER: '其他',
  STARTER: '发起人',
  FORM_FIELD: '表单成员字段',
  ADMIN: '系统管理员',
  EQ: '等于',
  NE: '不等于',
  CONTAINS: '包含',
  GT: '大于',
  GTE: '大于等于',
  LT: '小于',
  LTE: '小于等于',
  EMPTY: '为空',
  NOT_EMPTY: '不为空',
}

export function formatMoney(value?: number | string | null) {
  const num = Number(value)
  if (!Number.isFinite(num)) return '0.00'
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export function statusLabel(value?: string | null) {
  if (!value) return ''
  const tech = techKindByModule(value)
  if (tech) return tech.label
  const form = FORM_MODULES.find((item) => item.module === value)
  if (form) return form.moduleLabel
  return TEXT_LABEL[value] || value
}

export function displayText(key: string, value?: string | null) {
  if (!value) return ''
  if (key === 'assigneeType' && value === 'USER') return '指定人员'
  if (key === 'role' && value === 'USER') return '普通用户'
  if (key === 'submitSource' && value === 'ADMIN') return '后台'
  if (key === 'submitSource' && value === 'PUBLIC') return '公开填报'
  return statusLabel(value)
}

export function axiosErrorText(raw?: string) {
  const text = (raw || '').trim()
  if (!text) return '网络异常'
  const lower = text.toLowerCase()
  if (lower.includes('network error')) return '网络异常'
  if (lower.includes('timeout')) return '请求超时'
  if (lower.includes('request failed') || /status code \d+/.test(lower)) return '请求失败'
  return /[A-Za-z]{4,}/.test(text) && !/[\u4e00-\u9fff]/.test(text) ? '请求失败' : text
}
