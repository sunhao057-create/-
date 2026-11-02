import { useState, type MouseEvent, type ReactNode } from 'react'
import { Button, Descriptions, Drawer, Space, Table, Typography } from 'antd'
import type { TableProps } from 'antd'
import { useNavigate } from 'react-router-dom'
import { displayText, formatDateTime } from '../utils/format'

const LABELS: Record<string, string> = {
  id: '编号',
  name: '名称',
  code: '编码',
  title: '标题',
  shortName: '简称',
  companyName: '公司名称',
  companyCode: '公司代码',
  companyLevel: '公司等级',
  level: '等级',
  category: '类型',
  departmentName: '部门',
  deptLeader: '主管',
  progressPercent: '完成进度',
  status: '状态',
  statusLabel: '状态',
  hireDate: '入职日期',
  gender: '性别',
  genderLabel: '性别',
  idNumber: '身份证号码',
  birthDate: '出生日期',
  mobile: '联系电话',
  position: '岗位',
  password: '密码',
  role: '角色',
  roleName: '角色名称',
  description: '说明',
  builtin: '内置',
  published: '已发布',
  formModule: '绑定表单',
  formModuleLabel: '绑定表单',
  processName: '流程',
  nodeKey: '节点标识',
  nodeName: '当前节点',
  comment: '说明',
  inboxType: '类型',
  bizId: '单据编号',
  regionName: '所属区域',
  regionId: '区域编号',
  regionLeader: '区域负责人',
  manager: '项目经理',
  managerId: '项目经理',
  customer: '客户',
  customerId: '客户',
  customerShortName: '客户简称',
  customerName: '客户',
  contractId: '合同',
  contractCode: '合同编号',
  contractName: '合同名称',
  collectedAmount: '已回款',
  outstandingAmount: '待回款',
  projectCount: '立项数',
  signDate: '签订日期',
  startDate: '开始日期',
  endDate: '结束日期',
  receivedDate: '回款日期',
  method: '回款方式',
  remark: '说明',
  managerName: '项目经理',
  regionLeaderName: '区域负责人',
  leaderName: '负责人',
  projectName: '项目名称',
  projectCode: '项目编码',
  amount: '金额',
  payMethod: '付款方式',
  payMethodLabel: '付款方式',
  expectedPayDate: '期望付款日',
  budgetDeducted: '已入账',
  availableAmount: '可用金额',
  paidTotal: '已付合计',
  payeeName: '开户名',
  supplierId: '供应商',
  supplierShortName: '供应商',
  supplierCompanyName: '供应商公司',
  bankAccountId: '收款账户',
  accountId: '付款账户',
  workflowStatus: '流程状态',
  workflowStatusLabel: '流程状态',
  submitSource: '提交来源',
  contacts: '联系人',
  bankAccounts: '银行账户',
  attachments: '附件',
  phone: '电话',
  email: '邮箱',
  primaryContact: '主联系人',
  accountName: '开户名',
  bankName: '开户银行',
  accountNo: '账号',
  branch: '开户支行',
  primaryAccount: '默认账户',
  originalName: '文件名',
  url: '地址',
  size: '大小',
  contentType: '文件类型',
  avatarUrl: '头像',
  fieldKey: '字段标识',
  fieldLabel: '字段',
  oldValue: '原值',
  newValue: '新值',
  reason: '原因',
  operatorName: '操作人',
  operatorId: '操作人编号',
  filled: '已填写',
  files: '附件',
  createdAt: '创建时间',
  updatedAt: '更新时间',
  handledAt: '处理时间',
  success: '是否成功',
  ip: '网络地址',
  userAgent: '浏览器标识',
  deviceLabel: '登录设备',
  failReason: '失败原因',
  mustChangePassword: '需改密',
  view: '查看',
  create: '新增',
  edit: '编辑',
  delete: '删除',
  importExport: '导入导出',
  flow: '流程',
  manageView: '视图',
  publicAccess: '公开',
  publicDisplay: '展示',
  publicSubmit: '填报',
  publicQuery: '查询',
  publicExport: '导出',
  isPublic: '允许公开',
  module: '模块',
  moduleLabel: '模块',
  key: '标识',
  type: '类型',
  from: '从',
  to: '到',
  assigneeType: '办理人类型',
  assigneeUserIds: '指定人员',
  assigneeField: '成员字段',
  isDefault: '默认路径',
  conditions: '条件',
  field: '字段',
  op: '判断',
  value: '值',
  nodes: '节点',
  edges: '连线',
  fields: '字段权限',
  x: '横向位置',
  y: '纵向位置',
  leader: '负责人',
  warehouseName: '调出仓',
  targetWarehouseName: '调入仓',
  itemName: '物料',
  qty: '数量',
  unit: '单位',
  outDate: '日期',
  lines: '明细',
}

const HIDDEN = new Set([
  'passwordHash',
  'graph',
  'xml',
  'nodes',
  'edges',
  'fields',
  'permissions',
  'nodeKey',
  'currentNodeKey',
  'nextNodeKey',
  'x',
  'y',
  'from',
  'to',
  'key',
  'avatarUrl',
  'leaderAvatarUrl',
  'logoUrl',
  'hasCreate',
  'hasEdit',
  'hasDelete',
  'hasDataScope',
  'hasPublicAccess',
])
const NAME_KEYS = ['displayName', 'name', 'shortName', 'companyName', 'title', 'accountName', 'originalName', 'label', 'username']
const LONG_KEYS = new Set(['overview', 'description', 'comment', 'failReason', 'reason', 'userAgent'])
const BULKY_KEYS = new Set(['graph', 'nodes', 'edges', 'fields', 'permissions', 'xml'])

function labelOf(key: string) {
  return LABELS[key] || ''
}

function skipField(key: string, data: Record<string, unknown>) {
  if (HIDDEN.has(key)) return true
  if (!labelOf(key)) return true
  if (`${key}Label` in data && data[`${key}Label`] != null && data[`${key}Label`] !== '') return true
  if (key === 'role' && typeof data.roleName === 'string' && data.roleName) return true
  if (key === 'code' && 'published' in data && 'formModule' in data) return true
  if (key.endsWith('Id')) {
    const base = key.slice(0, -2)
    if (data[`${base}Name`] != null || data[`${base}ShortName`] != null || isPlainObject(data[base])) {
      return true
    }
  }
  return false
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

function objectSummary(data: Record<string, unknown>): string {
  for (const key of NAME_KEYS) {
    const value = data[key]
    if (typeof value === 'string' && value.trim()) {
      const code = typeof data.code === 'string' && data.code.trim() && key !== 'code' ? `（${data.code}）` : ''
      return `${value.trim()}${code}`
    }
  }
  const bits = Object.entries(data)
    .filter(([, value]) => value != null && value !== '' && (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean'))
    .slice(0, 3)
    .map(([key, value]) => `${labelOf(key)} ${typeof value === 'boolean' ? (value ? '是' : '否') : displayText(key, String(value))}`)
  return bits.join(' / ') || '-'
}

function bulkySummary(key: string, value: unknown): string {
  if (Array.isArray(value)) return `${value.length} 项`
  if (isPlainObject(value)) return `${Object.keys(value).length} 项`
  return labelOf(key)
}

function ValueView({ value, compact, field }: { value: unknown; compact?: boolean; field?: string }) {
  if (value === null || value === undefined || value === '') {
    return <Typography.Text type="secondary">-</Typography.Text>
  }
  if (typeof value === 'boolean') return <>{value ? '是' : '否'}</>
  if (typeof value === 'number') return <>{String(value)}</>
  if (typeof value === 'string') {
    if (value.startsWith('/uploads/') || value.startsWith('http')) {
      return (
        <a href={value} target="_blank" rel="noreferrer">
          查看
        </a>
      )
    }
    const dated = formatDateTime(value)
    const shown = field ? displayText(field, dated) : dated
    return (
      <span style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
        {compact && shown.length > 80 ? `${shown.slice(0, 80)}…` : shown}
      </span>
    )
  }
  if (Array.isArray(value)) {
    if (value.length === 0) return <Typography.Text type="secondary">无</Typography.Text>
    if (value.every((item) => isPlainObject(item))) {
      const shown = value.slice(0, 6)
      return (
        <div>
          {shown.map((item, index) => (
            <div key={index}>{objectSummary(item)}</div>
          ))}
          {value.length > shown.length ? (
            <Typography.Text type="secondary">等 {value.length} 项</Typography.Text>
          ) : null}
        </div>
      )
    }
    return <>{value.map((item) => (isPlainObject(item) ? objectSummary(item) : String(item))).join('、')}</>
  }
  if (isPlainObject(value)) return <>{objectSummary(value)}</>
  return <>{String(value)}</>
}

function ObjectView({ data }: { data: Record<string, unknown> }) {
  const entries = Object.entries(data).filter(([key, value]) => !skipField(key, data) && value !== undefined)
  if (entries.length === 0) return <Typography.Text type="secondary">无内容</Typography.Text>
  return (
    <Descriptions bordered size="small" column={2} styles={{ label: { width: 108, whiteSpace: 'nowrap' } }}>
      {entries.map(([key, value]) => {
        const long = LONG_KEYS.has(key) || (typeof value === 'string' && value.length > 36)
        const content = BULKY_KEYS.has(key) ? bulkySummary(key, value) : <ValueView value={value} compact field={key} />
        return (
          <Descriptions.Item key={key} label={labelOf(key)} span={long || Array.isArray(value) ? 2 : 1}>
            {content}
          </Descriptions.Item>
        )
      })}
    </Descriptions>
  )
}

function isControlClick(event: MouseEvent) {
  const target = event.target as HTMLElement | null
  return Boolean(
    target?.closest(
      'button, a, input, textarea, .ant-btn, .ant-switch, .ant-dropdown, .ant-popover, .ant-popconfirm, .ant-checkbox, .ant-checkbox-wrapper, .ant-table-selection-column',
    ),
  )
}

export function PreviewTable<T extends object>(
  props: TableProps<T> & {
    previewTitle?: string | ((record: T) => string)
    previewLoad?: (record: T) => Promise<unknown> | unknown
    previewLink?: (record: T) => { label: string; path: string } | undefined
    rowPath?: (record: T) => string
  },
) {
  const { previewTitle, previewLoad, previewLink, rowPath, onRow, scroll, ...tableProps } = props
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const [source, setSource] = useState<T | null>(null)
  const [detail, setDetail] = useState<unknown>(null)

  const title = !source
    ? '数据详情'
    : typeof previewTitle === 'function'
      ? previewTitle(source)
      : previewTitle || '数据详情'

  const link = source && previewLink ? previewLink(source) : undefined

  return (
    <>
      <Table<T>
        {...tableProps}
        locale={{
          emptyText: '还没有记录。要新增时，请点本页右上角的「新建」。',
          ...tableProps.locale,
        }}
        scroll={{ x: 'max-content', ...scroll }}
        tableLayout="auto"
        onRow={(record, index) => {
          const extra = onRow?.(record, index) || {}
          return {
            ...extra,
            style: { cursor: 'pointer', ...extra.style },
            onClick: async (event) => {
              if (isControlClick(event)) {
                extra.onClick?.(event)
                return
              }
              event.stopPropagation()
              const dest = rowPath?.(record) || previewLink?.(record)?.path
              if (dest && dest !== '/') {
                navigate(dest)
                extra.onClick?.(event)
                return
              }
              if (extra.onClick) {
                extra.onClick(event)
                return
              }
              setSource(record)
              setOpen(true)
              if (previewLoad) {
                setLoading(true)
                try {
                  setDetail(await previewLoad(record))
                } catch {
                  setDetail(record)
                } finally {
                  setLoading(false)
                }
              } else {
                setDetail(record)
              }
            },
          }
        }}
      />
      <Drawer
        className="qf-preview-drawer"
        title={
          <Typography.Text ellipsis={{ tooltip: title }} style={{ maxWidth: 520, fontWeight: 600 }}>
            {title}
          </Typography.Text>
        }
        open={open}
        onClose={() => setOpen(false)}
        placement="right"
        width="min(880px, 92vw)"
        extra={
          <Space>
            {link ? (
              <Button
                type="primary"
                onClick={() => {
                  setOpen(false)
                  navigate(link.path)
                }}
              >
                {link.label}
              </Button>
            ) : null}
            <Button onClick={() => setOpen(false)}>关闭</Button>
          </Space>
        }
      >
        {loading ? (
          <Typography.Text type="secondary">加载中…</Typography.Text>
        ) : isPlainObject(detail) ? (
          <ObjectView data={detail} />
        ) : (
          <ValueView value={detail} />
        )}
      </Drawer>
    </>
  )
}

export function PreviewHint({ children, extra }: { children?: ReactNode; extra?: ReactNode }) {
  return (
    <Typography.Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 8 }}>
      {children || extra || '点一行打开记录，和轻流一样从右侧查看或进入办理。'}
    </Typography.Text>
  )
}
