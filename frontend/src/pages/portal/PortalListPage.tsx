import { useCallback, useEffect, useState } from 'react'
import { App, Button, Card, Space, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { PlusOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { portalApi, type PortalPost } from '../../api'
import { ApiError } from '../../api/http'
import { useAuth } from '../../auth/AuthContext'
import { can } from '../../auth/permissions'
import { RowActions } from '../../ui/RowActions'
import { PORTAL_META, type PortalKind } from './portalMeta'

export function PortalListPage({ kind }: { kind: PortalKind }) {
  const meta = PORTAL_META[kind]
  const { message } = App.useApp()
  const { user } = useAuth()
  const navigate = useNavigate()
  const [rows, setRows] = useState<PortalPost[]>([])
  const [loading, setLoading] = useState(false)
  const canCreate = can(user, kind, 'create')
  const canEdit = can(user, kind, 'edit')
  const canDelete = can(user, kind, 'delete')

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setRows(await portalApi.list(kind))
    } catch (e) {
      message.error(e instanceof ApiError ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }, [kind, message])

  useEffect(() => {
    void load()
  }, [load])

  const columns: ColumnsType<PortalPost> = [
    {
      title: '图片',
      dataIndex: 'coverUrl',
      width: 88,
      render: (url: string) => (url ? <img src={url} alt="" style={{ width: 64, height: 40, objectFit: 'cover', borderRadius: 6 }} /> : '—'),
    },
    { title: '标题', dataIndex: 'title', ellipsis: true },
    { title: '分类', dataIndex: 'category', width: 140, render: (value: string) => value || '—' },
    { title: '排序', dataIndex: 'sortOrder', width: 72 },
    {
      title: '显示',
      dataIndex: 'enabled',
      width: 80,
      render: (enabled: boolean) => (enabled ? <Tag color="green">显示</Tag> : <Tag>隐藏</Tag>),
    },
    {
      title: '操作',
      key: 'actions',
      width: 120,
      render: (_, record) => (
        <RowActions
          canView
          canEdit={canEdit}
          canDelete={canDelete}
          deleteTitle="确认删除这条内容？"
          onEdit={() => navigate(`${meta.path}/${record.id}`)}
          onDelete={async () => {
            try {
              await portalApi.remove(kind, record.id)
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
    <Card className="qf-list-card" title={meta.title}>
      <Space style={{ marginBottom: 12 }}>
        {canCreate ? (
          <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate(`${meta.path}/new`)}>
            提交数据
          </Button>
        ) : null}
      </Space>
      <Table rowKey="id" loading={loading} columns={columns} dataSource={rows} pagination={false} />
    </Card>
  )
}
