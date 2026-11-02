import { useEffect, useState } from 'react'
import { App, Button, Card, Empty, Space, Switch, Tabs, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { inboxApi } from '../api'
import { ApiError } from '../api/http'
import type { InboxItem } from '../types'
import { formatDateTime, statusLabel } from '../utils/format'
import { PreviewTable } from '../ui/PreviewTable'
import { bizPath } from '../workflow/WorkflowBar'
import { useNavigate, useSearchParams } from 'react-router-dom'

const TABS = [
  { key: 'TODO', label: '我的待办', empty: '现在没有等你处理的单据。被退回的也会出现在这里。' },
  { key: 'STARTED', label: '进行中', empty: '你发起的单据还没有正在流转的。' },
  { key: 'FINISHED', label: '已完成', empty: '还没有你发起并已结束的单据。' },
  { key: 'DONE', label: '我的已办', empty: '还没有你处理过的记录。' },
  { key: 'CC', label: '抄送我的', empty: '还没有抄送给你的单据。' },
  { key: 'DRAFT', label: '草稿箱', empty: '没有未提交的草稿。填写到一半可点「保存草稿」，之后在这里继续。' },
  { key: 'RETURNED', label: '退回给我', empty: '没有被退回的单据。' },
]

const TAB_KEYS = new Set(TABS.map((item) => item.key))

export function InboxPage() {
  const { message } = App.useApp()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const type = TAB_KEYS.has(params.get('type') || '') ? String(params.get('type')) : 'TODO'
  const overdueOnly = params.get('overdue') === '1'
  const setType = (next: string) => navigate(`/inbox?type=${next}`, { replace: true })
  const setOverdueOnly = (next: boolean) => navigate(`/inbox?type=${type}${next ? '&overdue=1' : ''}`, { replace: true })
  const [loading, setLoading] = useState(false)
  const [rows, setRows] = useState<InboxItem[]>([])
  const load = async (next = type, overdue = overdueOnly) => {
    setLoading(true)
    try {
      const filterOverdue = overdue && (next === 'TODO' || next === 'RETURNED')
      setRows(await inboxApi.list(next, filterOverdue))
    } catch (e) {
      message.error(e instanceof ApiError ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load(type, overdueOnly)
  }, [type, overdueOnly])

  const tab = TABS.find((item) => item.key === type) || TABS[0]

  const columns: ColumnsType<InboxItem> = [
    { title: '应用', dataIndex: 'formModuleLabel', width: 120 },
    { title: '标题', dataIndex: 'title', ellipsis: true },
    { title: '当前节点', dataIndex: 'nodeName', width: 140 },
    {
      title: '说明',
      dataIndex: 'comment',
      ellipsis: true,
      render: (value: string) => value || '—',
    },
    {
      title: '状态',
      width: 140,
      render: (_, row) => (
        <Space size={4}>
          <Tag>{statusLabel(row.status)}</Tag>
          {(type === 'TODO' || type === 'RETURNED') && row.overdue ? <Tag color="red">已超时</Tag> : null}
        </Space>
      ),
    },
    { title: '时间', dataIndex: 'createdAt', width: 160, render: (value: string) => formatDateTime(value) },
    {
      title: '操作',
      width: 100,
      render: (_, row) => (
        <Button
          type="link"
          size="small"
          onClick={(event) => {
            event.stopPropagation()
            navigate(bizPath(String(row.formModule), row.bizId))
          }}
        >
            打开
        </Button>
      ),
    },
  ]

  return (
    <Card>
      <Tabs
        className="qf-inbox-tabs"
        activeKey={type}
        onChange={(value) => {
          setOverdueOnly(false)
          setType(String(value))
        }}
        tabBarExtraContent={
          type === 'TODO' || type === 'RETURNED' ? (
            <Space size={8}>
              <span>只看已超时</span>
              <Switch checked={overdueOnly} onChange={setOverdueOnly} />
            </Space>
          ) : null
        }
        items={TABS.map((item) => ({ key: item.key, label: item.label }))}
      />
      <PreviewTable
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={false}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={tab.empty} /> }}
        previewTitle={(row) => row.title || '待办详情'}
        previewLink={(row) => ({ label: '打开办理', path: bizPath(String(row.formModule), row.bizId) })}
      />
    </Card>
  )
}
