import { useEffect, useState } from 'react'
import { App, Button, Card, Input, Space, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../../api/http'
import { publicApi, type PublicCustomer } from '../../api/public'
import { LIST_EXPORT_HINT, PublicListExportButton, useListSelection } from '../../export/ListExportButton'
import { PublicClosed, usePublicConfig } from '../../layouts/PublicLayout'
import { PreviewHint, PreviewTable } from '../../ui/PreviewTable'
import { PublicPartyForm } from './PublicPartyForm'

export type PublicPageMode = 'list' | 'create' | 'query' | 'detail'

export function PublicRecordsPage({ kind, mode = 'list' }: { kind: 'customer'; mode?: PublicPageMode }) {
  const { message } = App.useApp()
  const navigate = useNavigate()
  const params = useParams()
  const config = usePublicConfig()
  const flags = config.customer
  const base = '/public/customers'
  const [loading, setLoading] = useState(false)
  const [rows, setRows] = useState<PublicCustomer[]>([])
  const [detail, setDetail] = useState<PublicCustomer | null>(null)
  const [missing, setMissing] = useState('')
  const [lastKeyword, setLastKeyword] = useState<string | undefined>(undefined)
  const { selectedRowKeys, rowSelection } = useListSelection()

  const canList = flags.publicAccess && (flags.display || flags.query)
  const canCreate = flags.publicAccess && flags.submit
  const canExport = flags.publicAccess && flags.export

  const loadList = async (q?: string) => {
    if (!canList) return
    if (!flags.display && !String(q || '').trim()) {
      setRows([])
      return
    }
    setLastKeyword(String(q || '').trim() || undefined)
    setLoading(true)
    try {
      setRows(await publicApi.customers(q))
    } catch (e) {
      message.error(e instanceof ApiError ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (mode === 'list') void loadList(flags.display ? undefined : '')
    if (mode === 'query') setRows([])
  }, [kind, mode, flags.display, flags.query, flags.publicAccess])

  useEffect(() => {
    if (mode !== 'detail') {
      setDetail(null)
      setMissing('')
      return
    }
    const id = Number(params.id)
    if (!id) {
      setMissing('内容不存在')
      return
    }
    publicApi
      .customer(id)
      .then(setDetail)
      .catch((e) => setMissing(e instanceof ApiError ? e.message : '内容不存在'))
  }, [kind, mode, params.id])

  if (!flags.publicAccess) return <PublicClosed />
  if (mode === 'create' && !canCreate) return <PublicClosed text="该表单未开放填报" />
  if ((mode === 'list' || mode === 'query' || mode === 'detail') && !canList) return <PublicClosed />

  if (mode === 'detail') {
    if (missing) return <PublicClosed text={missing} />
    if (!detail) return null
    return <PublicPartyForm mode="view" detail={detail} onCancel={() => navigate(base)} />
  }

  if (mode === 'create') {
    return <PublicPartyForm mode="create" onCancel={() => navigate(base)} />
  }

  const columns: ColumnsType<PublicCustomer> = [
    { title: '客户简称', dataIndex: 'shortName', width: 140 },
    { title: '公司名称', dataIndex: 'companyName', ellipsis: true },
    { title: '客户编码', dataIndex: 'code', width: 120 },
    { title: '等级', dataIndex: 'level', width: 110 },
  ]

  return (
    <Card
      title={mode === 'query' ? '查询客户' : '客户列表'}
      extra={
        canExport || canCreate ? (
          <Space>
            {canExport ? (
              <PublicListExportButton
                kind="customer"
                selectedRowKeys={selectedRowKeys}
                keyword={lastKeyword}
                fallbackName="客户列表.xlsx"
              />
            ) : null}
            {canCreate ? (
              <Button type="primary" onClick={() => navigate(`${base}/new`)}>
                填报
              </Button>
            ) : null}
          </Space>
        ) : null
      }
    >
      {flags.query || flags.display ? (
        <Space wrap style={{ marginBottom: 16 }}>
          <Input.Search
            allowClear
            placeholder="搜索简称 / 公司 / 编码"
            onSearch={(value) => void loadList(value)}
            style={{ width: 320 }}
          />
          {flags.query && !flags.display && mode !== 'query' ? (
            <Typography.Text type="secondary">请输入关键字后查询</Typography.Text>
          ) : null}
        </Space>
      ) : null}
      {canExport ? <PreviewHint>{LIST_EXPORT_HINT}</PreviewHint> : null}
      <PreviewTable
        rowKey="id"
        loading={loading}
        rowSelection={canExport ? rowSelection : undefined}
        columns={columns}
        dataSource={rows}
        pagination={false}
        locale={{ emptyText: '暂无数据' }}
        previewTitle={(record) => record.shortName}
        previewLink={(record) => ({ label: '查看详情', path: `${base}/${record.id}` })}
      />
    </Card>
  )
}
