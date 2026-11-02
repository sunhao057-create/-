import { useCallback, useEffect, useState } from 'react'
import { App, Button, Card, Input, Space, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { PlusOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { maintApi, type MaintRecord } from '../../api'
import { ApiError } from '../../api/http'
import { useAuth } from '../../auth/AuthContext'
import { can } from '../../auth/permissions'
import { FormViewWorkbench, viewColumns } from '../../ui/FormViewWorkbench'
import { PreviewTable } from '../../ui/PreviewTable'
import { RowActions } from '../../ui/RowActions'
import { FormFlowButton } from '../../workflow/FormFlowButton'
import type { DataViewRange } from '../../types'

const PATH = '/devices/maint-records'

export function MaintRecordListPage() {
  const { message } = App.useApp()
  const { user: me } = useAuth()
  const navigate = useNavigate()
  const canCreate = can(me, 'DEVICE_MAINT', 'create')
  const canView = can(me, 'DEVICE_MAINT', 'view')
  const canEdit = can(me, 'DEVICE_MAINT', 'edit')
  const canDelete = can(me, 'DEVICE_MAINT', 'delete')
  const [keyword, setKeyword] = useState('')
  const [loading, setLoading] = useState(false)
  const [rows, setRows] = useState<MaintRecord[]>([])
  const [page, setPage] = useState(1)
  const [size, setSize] = useState(20)
  const [total, setTotal] = useState(0)
  const [related, setRelated] = useState(false)
  const onRange = useCallback((next: DataViewRange) => setRelated(next === 'RELATED'), [])

  const load = useCallback(
    async (q: string, p: number, s: number, relatedMode = related) => {
      setLoading(true)
      try {
        const data = await maintApi.list({
          keyword: q || undefined,
          page: relatedMode ? 1 : p,
          size: relatedMode ? 200 : s,
        })
        setRows(data.items)
        setTotal(data.total)
        setPage(data.page)
      } catch (e) {
        message.error(e instanceof ApiError ? e.message : '加载失败')
      } finally {
        setLoading(false)
      }
    },
    [related, message],
  )

  useEffect(() => {
    void load(keyword, 1, size)
  }, [load])

  const viewFields = [
    { key: 'code', title: '编号' },
    { key: 'serviceLabel', title: '维保服务' },
    { key: 'customerName', title: '客户名称' },
    { key: 'deviceCode', title: '设备编号' },
    { key: 'instrumentName', title: '仪器名称' },
    { key: 'instrumentModel', title: '仪器型号' },
    { key: 'vendor', title: '生产厂家' },
    { key: 'maintainer', title: '维保人员' },
    { key: 'startTime', title: '维保开始时间' },
    { key: 'location', title: '维保定位' },
    { key: 'workflowStatusLabel', title: '流程状态' },
  ]

  const columns: ColumnsType<MaintRecord> = [
    { title: '编号', dataIndex: 'code', key: 'code', width: 140 },
    { title: '维保服务', dataIndex: 'serviceLabel', key: 'serviceLabel', width: 180, ellipsis: true },
    { title: '客户名称', dataIndex: 'customerName', key: 'customerName', width: 160, ellipsis: true },
    { title: '设备编号', dataIndex: 'deviceCode', key: 'deviceCode', width: 120, ellipsis: true },
    { title: '仪器名称', dataIndex: 'instrumentName', key: 'instrumentName', width: 140, ellipsis: true },
    { title: '仪器型号', dataIndex: 'instrumentModel', key: 'instrumentModel', width: 120, ellipsis: true },
    { title: '生产厂家', dataIndex: 'vendor', key: 'vendor', width: 140, ellipsis: true },
    { title: '维保人员', dataIndex: 'maintainer', key: 'maintainer', width: 100 },
    { title: '维保开始时间', dataIndex: 'startTime', key: 'startTime', width: 160 },
    { title: '维保定位', dataIndex: 'location', key: 'location', width: 160, ellipsis: true },
    {
      title: '流程状态',
      dataIndex: 'workflowStatusLabel',
      key: 'workflowStatusLabel',
      width: 110,
      render: (label: string) => <Tag color="orange">{label || '未启动'}</Tag>,
    },
    {
      title: '操作',
      key: 'actions',
      width: 120,
      fixed: 'right',
      render: (_, record) => (
        <RowActions
          canView={canView}
          canEdit={canEdit}
          canDelete={canDelete}
          deleteTitle="确认删除该维保服务记录？"
          onEdit={() => navigate(`${PATH}/${record.id}`)}
          onDelete={async () => {
            try {
              await maintApi.remove(record.id)
              message.success('已删除')
              await load(keyword, page, size)
            } catch (e) {
              message.error(e instanceof ApiError ? e.message : '删除失败')
            }
          }}
        />
      ),
    },
  ]

  return (
    <Card className="qf-list-card" title="维保服务记录">
      <FormViewWorkbench<MaintRecord>
        module="DEVICE_MAINT"
        fields={viewFields}
        rows={rows}
        loading={loading}
        onRange={onRange}
        onOpen={(record) => navigate(`${PATH}/${record.id}`)}
        filters={
          <Input.Search
            allowClear
            placeholder="搜索编号 / 客户 / 仪器"
            style={{ width: 280 }}
            onChange={(e) => setKeyword(e.target.value)}
            onSearch={(value) => {
              setKeyword(value)
              setPage(1)
              void load(value, 1, size)
            }}
          />
        }
        extra={
          <Space>
            <FormFlowButton module="DEVICE_MAINT" />
            {canCreate ? (
              <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate(`${PATH}/new`)}>
                提交数据
              </Button>
            ) : null}
          </Space>
        }
        renderTable={({ rows: viewRows, fieldKeys, rowHeight }) => (
          <PreviewTable
            rowKey="id"
            columns={viewColumns(columns, fieldKeys)}
            dataSource={viewRows}
            size={rowHeight <= 36 ? 'small' : rowHeight >= 64 ? 'large' : 'middle'}
            pagination={
              related
                ? { pageSize: viewRows.length || 1, total: viewRows.length, showTotal: (count) => `共 ${count} 条` }
                : {
                    current: page,
                    pageSize: size,
                    total,
                    showSizeChanger: true,
                    showTotal: (count) => `共 ${count} 条`,
                    onChange: (next, nextSize) => {
                      setPage(next)
                      setSize(nextSize)
                      void load(keyword, next, nextSize, false)
                    },
                  }
            }
            rowPath={(record) => `${PATH}/${record.id}`}
          />
        )}
      />
    </Card>
  )
}
