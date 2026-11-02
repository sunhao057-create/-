import { useCallback, useEffect, useMemo, useState } from 'react'
import { App, Button, Card, Input, Space, Table } from 'antd'
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate, useParams } from 'react-router-dom'
import { techApi, type TechSchema } from '../../api'
import { ApiError } from '../../api/http'
import { useAuth } from '../../auth/AuthContext'
import { can } from '../../auth/permissions'
import { techKindBySlug } from '../../tech/catalog'
import { FormViewWorkbench, viewColumns } from '../../ui/FormViewWorkbench'
import type { DataViewRange } from '../../types'
import { FormFlowButton } from '../../workflow/FormFlowButton'
import type { DataViewField, TechRecord } from '../../types'

function cellText(value: unknown): string {
  if (value == null) return ''
  if (Array.isArray(value)) return value.map(cellText).filter(Boolean).join('、')
  if (typeof value === 'object') {
    const obj = value as Record<string, unknown>
    return cellText(obj.name ?? obj.label ?? obj.title ?? obj.value ?? '')
  }
  return String(value)
}

export function TechListPage({ slug: slugProp, title }: { slug?: string; title?: string } = {}) {
  const { message } = App.useApp()
  const params = useParams()
  const slug = slugProp || params.slug || ''
  const navigate = useNavigate()
  const { user: me } = useAuth()
  const kind = techKindBySlug(slug)
  const module = kind?.module || ''
  const canCreate = can(me, module, 'create')
  const canDelete = can(me, module, 'delete')

  const [schema, setSchema] = useState<TechSchema | null>(null)
  const [rows, setRows] = useState<TechRecord[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [size, setSize] = useState(20)
  const [keyword, setKeyword] = useState('')
  const [loading, setLoading] = useState(false)
  const [related, setRelated] = useState(false)
  const onRange = useCallback((next: DataViewRange) => setRelated(next === 'RELATED'), [])

  const load = useCallback(
    async (q = keyword, p = page, s = size) => {
      if (!slug) return
      setLoading(true)
      try {
        const result = await techApi.list(slug, {
          keyword: q || undefined,
          page: related ? 1 : p,
          size: related ? 500 : s,
        })
        setRows(result.items)
        setTotal(result.total)
      } catch (e) {
        message.error(e instanceof ApiError ? e.message : '加载失败')
      } finally {
        setLoading(false)
      }
    },
    [slug, keyword, page, size, related, message],
  )

  useEffect(() => {
    if (!slug) return
    techApi
      .schema(slug)
      .then(setSchema)
      .catch(() => setSchema(null))
  }, [slug])

  useEffect(() => {
    void load()
  }, [load])

  const viewFields = useMemo<DataViewField[]>(() => {
    const extra = (schema?.listColumns || [])
      .filter((col) => col.source !== 'code' && col.source !== 'title')
      .map((col) => ({ key: col.key, title: col.name }))
    return [
      { key: 'code', title: '编号' },
      { key: 'title', title: '标题' },
      ...extra,
      { key: 'workflowStatusLabel', title: '流程状态' },
      { key: 'creatorName', title: '创建人' },
    ]
  }, [schema])

  const columns = useMemo<ColumnsType<TechRecord>>(() => {
    const base: ColumnsType<TechRecord> = [
      { title: '编号', dataIndex: 'code', key: 'code', width: 150 },
      { title: '标题', dataIndex: 'title', key: 'title', ellipsis: true },
    ]
    const extraCols: ColumnsType<TechRecord> = (schema?.listColumns || [])
      .filter((col) => col.source !== 'code' && col.source !== 'title')
      .map((col) => ({
        title: col.name,
        key: col.key,
        ellipsis: true,
        render: (_: unknown, record) => cellText(record.extra?.[col.key]),
      }))
    const tail: ColumnsType<TechRecord> = [
      { title: '流程状态', dataIndex: 'workflowStatusLabel', key: 'workflowStatusLabel', width: 110 },
      { title: '创建人', dataIndex: 'creatorName', key: 'creatorName', width: 100 },
      {
        title: '操作',
        key: 'actions',
        width: 140,
        render: (_: unknown, record) => (
          <Space size={4}>
            <Button type="link" size="small" onClick={() => navigate(`/tech/${slug}/${record.id}`)}>
              查看
            </Button>
            {canDelete && (
              <Button
                type="link"
                size="small"
                danger
                onClick={async () => {
                  try {
                    await techApi.remove(slug, record.id)
                    message.success('已删除')
                    void load()
                  } catch (e) {
                    message.error(e instanceof ApiError ? e.message : '删除失败')
                  }
                }}
              >
                删除
              </Button>
            )}
          </Space>
        ),
      },
    ]
    return [...base, ...extraCols, ...tail]
  }, [schema, slug, navigate, canDelete, load, message])

  return (
    <Card className="qf-list-card" title={title || kind?.label || '技术资料'}>
      <FormViewWorkbench<TechRecord>
        module={module}
        fields={viewFields}
        rows={rows}
        loading={loading}
        onRange={onRange}
        onOpen={(record) => navigate(`/tech/${slug}/${record.id}`)}
        filters={
          <Input
            allowClear
            prefix={<SearchOutlined />}
            placeholder="搜索"
            style={{ width: 220 }}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onPressEnter={() => {
              setPage(1)
              void load(keyword, 1, size)
            }}
          />
        }
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => void load()} />
            <FormFlowButton module={module} />
            {canCreate && (
              <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate(`/tech/${slug}/new`)}>
                提交数据
              </Button>
            )}
          </Space>
        }
        renderTable={({ rows: viewRows, fieldKeys, rowHeight }) => (
          <Table<TechRecord>
            rowKey="id"
            columns={viewColumns(columns, fieldKeys)}
            dataSource={viewRows}
            onRow={(record) => ({ onClick: () => navigate(`/tech/${slug}/${record.id}`), style: { cursor: 'pointer' } })}
            pagination={
              related
                ? { pageSize: viewRows.length || 1, total: viewRows.length, showTotal: (count) => `共 ${count} 条` }
                : {
                    current: page,
                    pageSize: size,
                    total,
                    showSizeChanger: true,
                    showTotal: (count) => `共 ${count} 条`,
                    onChange: (p, s) => {
                      setPage(p)
                      setSize(s)
                      void load(keyword, p, s)
                    },
                  }
            }
            size={rowHeight <= 36 ? 'small' : rowHeight >= 64 ? 'large' : 'middle'}
          />
        )}
      />
    </Card>
  )
}
