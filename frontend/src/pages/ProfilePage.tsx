import { useEffect, useState } from 'react'
import { App, Avatar, Button, Card, Form, Input, Space, Tag, Typography, Upload } from 'antd'
import { UserOutlined } from '@ant-design/icons'
import { authApi, profileApi } from '../api'
import { ApiError } from '../api/http'
import { useAuth } from '../auth/AuthContext'
import type { LoginLog } from '../types'
import { PreviewHint, PreviewTable } from '../ui/PreviewTable'
import { formatDateTime } from '../utils/format'

export function ProfilePage() {
  const { message } = App.useApp()
  const { user, setUser, refresh } = useAuth()
  const [logs, setLogs] = useState<LoginLog[]>([])
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)

  const loadLogs = async (next = 1) => {
    const data = await profileApi.loginLogs(next, 8)
    setLogs(data.items)
    setTotal(data.total)
    setPage(data.page)
  }

  useEffect(() => {
    void loadLogs(1)
  }, [])

  if (!user) return null

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card title="个人资料">
        <Space align="start" size={24} wrap>
          <Upload
            showUploadList={false}
            accept="image/png,image/jpeg,image/webp"
            beforeUpload={async (file) => {
              try {
                const updated = await profileApi.avatar(file)
                setUser(updated)
                message.success('头像已更新')
              } catch (e) {
                message.error(e instanceof ApiError ? e.message : '上传失败')
              }
              return false
            }}
          >
            <Avatar size={88} src={user.avatarUrl || undefined} icon={<UserOutlined />} />
            <div style={{ textAlign: 'center', marginTop: 8 }}>
              <Button size="small">更换头像</Button>
            </div>
          </Upload>
          <Form
            style={{ minWidth: 280, flex: 1 }}
            layout="vertical"
            initialValues={{ displayName: user.displayName, username: user.username }}
            onFinish={async (values: { displayName: string }) => {
              try {
                const updated = await profileApi.update(values.displayName)
                setUser(updated)
                message.success('已保存')
              } catch (e) {
                message.error(e instanceof ApiError ? e.message : '保存失败')
              }
            }}
          >
            <Form.Item label="用户名" name="username">
              <Input disabled />
            </Form.Item>
            <Form.Item label="姓名" name="displayName" rules={[{ required: true, message: '请输入姓名' }]}>
              <Input />
            </Form.Item>
            <Button type="primary" htmlType="submit">
              保存资料
            </Button>
          </Form>
        </Space>
      </Card>

      <Card title="修改密码">
        <Form
          layout="vertical"
          style={{ maxWidth: 360 }}
          onFinish={async (values: { oldPassword: string; newPassword: string }) => {
            try {
              await authApi.changePassword(values.oldPassword, values.newPassword)
              await refresh()
              message.success('密码已更新')
            } catch (e) {
              message.error(e instanceof ApiError ? e.message : '修改失败')
            }
          }}
        >
          <Form.Item name="oldPassword" label="原密码" rules={[{ required: true }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item name="newPassword" label="新密码" rules={[{ required: true, min: 8 }]}>
            <Input.Password />
          </Form.Item>
          <Button type="primary" htmlType="submit">
            更新密码
          </Button>
        </Form>
      </Card>

      <Card title="我的登录日志">
        <PreviewHint />
        <PreviewTable
          rowKey="id"
          dataSource={logs}
          previewTitle="登录日志"
          pagination={{ current: page, total, pageSize: 8, onChange: (p) => void loadLogs(p) }}
          columns={[
            {
              title: '结果',
              dataIndex: 'success',
              render: (ok: boolean) => (ok ? <Tag color="success">成功</Tag> : <Tag color="error">失败</Tag>),
            },
            { title: '网络地址', dataIndex: 'ip' },
            { title: '时间', dataIndex: 'createdAt', render: (value: string) => formatDateTime(value) },
            {
              title: '设备',
              dataIndex: 'deviceLabel',
              ellipsis: true,
              render: (_: string, record: LoginLog) => (
                <Typography.Text type="secondary">{record.deviceLabel || record.userAgent || '—'}</Typography.Text>
              ),
            },
          ]}
        />
      </Card>
    </Space>
  )
}
