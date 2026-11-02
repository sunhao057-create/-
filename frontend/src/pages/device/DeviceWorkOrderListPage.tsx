import { useEffect, useState } from 'react'
import { App, Button, Card, Input, Space, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { PlusOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { deviceApi } from '../../api'
import { ApiError } from '../../api/http'
import type { DeviceWorkOrder, FormModule } from '../../types'
import { useAuth } from '../../auth/AuthContext'
import { can } from '../../auth/permissions'
import { LIST_EXPORT_HINT, ListExportButton, useListSelection } from '../../export/ListExportButton'
import { FormViewWorkbench, viewColumns } from '../../ui/FormViewWorkbench'
import { PreviewHint, PreviewTable } from '../../ui/PreviewTable'
import { RowActions } from '../../ui/RowActions'
import { FormFlowButton } from '../../workflow/FormFlowButton'

export type DeviceOrderKind = 'repairs' | 'moves' | 'scraps'

const META: Record<
  DeviceOrderKind,
  { module: FormModule; title: string; path: string; createLabel: string }
> = {
  repairs: { module: 'DEVICE_REPAIR', title: '设备维修工单', path: '/devices/repairs', createLabel: '提交数据' },
  moves: { module: 'DEVICE_MOVE', title: '设备搬迁工单', path: '/devices/moves', createLabel: '提交数据' },
  scraps: { module: 'DEVICE_SCRAP', title: '设备报废工单', path: '/devices/scraps', createLabel: '提交数据' },
}

export function DeviceWorkOrderListPage({ kind }: { kind: DeviceOrderKind }) {
  const meta = META[kind]
  const { message } = App.useApp()
  const { user: me } = useAuth()
  const navigate = useNavigate()
  const canCreate = can(me, meta.module, 'create')
  const canView = can(me, meta.module, 'view')
  const canEdit = can(me, meta.module, 'edit')
  const canDelete = can(me, meta.module, 'delete')
  const [keyword, setKeyword] = useState('')
  const [loading, setLoading] = useState(false)
  const [rows, setRows] = useState<DeviceWorkOrder[]>([])
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)
  const { selectedRowKeys, rowSelection } = useListSelection()

  const load = async (nextPage = page) => {
    setLoading(true)
    try {
      const data = await deviceApi.orders(kind, { keyword, page: nextPage, size: 10 })
      setRows(data.items)
      setTotal(data.total)
      setPage(data.page)
    } catch (e) {
      message.error(e instanceof ApiError ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load(1)
  }, [])

  const viewFields = [
    { key: 'code', title: '工单编码' },
    { key: 'deviceName', title: '设备' },
    { key: 'reason', title: '事由' },
    { key: 'applyDate', title: '申请日期' },
    { key: 'workflowStatusLabel', title: '流程状态' },
  ]

  const columns: ColumnsType<DeviceWorkOrder> = [
    { title: '工单编码', dataIndex: 'code', key: 'code', width: 160 },
    { title: '设备', key: 'deviceName', width: 200, ellipsis: true, render: (_, record) => `${record.deviceName}（${record.deviceCode}）` },
    { title: '事由', dataIndex: 'reason', key: 'reason', ellipsis: true },
    { title: '申请日期', dataIndex: 'applyDate', key: 'applyDate', width: 120 },
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
      width: 140,
      render: (_, record) => (
        <RowActions
          canView={canView}
          canEdit={canEdit}
          canDelete={canDelete}
          deleteTitle={`确认删除该${meta.title}？`}
          onEdit={() => navigate(`${meta.path}/${record.id}`)}
          onDelete={async () => {
            try {
              await deviceApi.removeOrder(kind, record.id)
              message.success('已删除')
              await load()
            } catch (e) {
              message.error(e instanceof ApiError ? e.message : '删除失败')
            }
          }}
        />
      ),
    },
  ]

  return (
    <Card className="qf-list-card">
      <FormViewWorkbench<DeviceWorkOrder>
        module={meta.module}
        fields={viewFields}
        rows={rows}
        loading={loading}
        onOpen={(record) => navigate(`${meta.path}/${record.id}`)}
        filters={
          <Input.Search
            allowClear
            placeholder="搜索编码 / 设备 / 事由"
            onSearch={() => void load(1)}
            onChange={(e) => setKeyword(e.target.value)}
            style={{ width: 320 }}
          />
        }
        extra={
          <Space>
            <ListExportButton
              module={meta.module}
              selectedRowKeys={selectedRowKeys}
              query={{ keyword: keyword || undefined }}
              fallbackName={`${meta.title}.xlsx`}
            />
            <FormFlowButton module={meta.module} />
            {canCreate ? (
              <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate(`${meta.path}/new`)}>
                {meta.createLabel}
              </Button>
            ) : null}
          </Space>
        }
        renderTable={({ rows: viewRows, fieldKeys }) => (
          <>
            <PreviewHint>{LIST_EXPORT_HINT}</PreviewHint>
            <PreviewTable
              rowKey="id"
              columns={viewColumns(columns, fieldKeys)}
              dataSource={viewRows}
              rowSelection={rowSelection}
              pagination={{ current: page, total, pageSize: 10, onChange: (next) => void load(next) }}
              rowPath={(record) => `${meta.path}/${record.id}`}
            />
          </>
        )}
      />
    </Card>
  )
}
