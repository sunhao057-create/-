import { Button, Card, Form, Input, Typography, App } from 'antd'
import { useNavigate } from 'react-router-dom'
import { authApi } from '../api'
import { useAuth } from '../auth/AuthContext'
import { ApiError } from '../api/http'

export function ChangePasswordPage() {
  const { message } = App.useApp()
  const { user, refresh, logout } = useAuth()
  const navigate = useNavigate()

  return (
    <div className="auth-page">
      <Card style={{ width: 'min(460px, 100%)' }}>
        <Typography.Title level={4}>修改密码</Typography.Title>
        <Typography.Paragraph type="secondary">
          {user?.mustChangePassword ? '首次登录或密码被重置后，需要先改密码才能继续。' : '请输入原密码和新密码。'}
        </Typography.Paragraph>
        <Form
          layout="vertical"
          onFinish={async (values: { oldPassword: string; newPassword: string }) => {
            try {
              await authApi.changePassword(values.oldPassword, values.newPassword)
              await refresh()
              message.success('密码已更新')
              navigate('/', { replace: true })
            } catch (e) {
              message.error(e instanceof ApiError ? e.message : '修改失败')
            }
          }}
        >
          <Form.Item name="oldPassword" label="原密码" rules={[{ required: true, message: '请输入原密码' }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item
            name="newPassword"
            label="新密码"
            rules={[
              { required: true, message: '请输入新密码' },
              { min: 8, message: '至少 8 位' },
            ]}
          >
            <Input.Password />
          </Form.Item>
          <Form.Item
            name="confirm"
            label="确认新密码"
            dependencies={['newPassword']}
            rules={[
              { required: true, message: '请再次输入新密码' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('newPassword') === value) {
                    return Promise.resolve()
                  }
                  return Promise.reject(new Error('两次输入不一致'))
                },
              }),
            ]}
          >
            <Input.Password />
          </Form.Item>
          <Button type="primary" htmlType="submit" block>
            保存
          </Button>
          <Button
            type="link"
            block
            onClick={async () => {
              await logout()
              navigate('/login', { replace: true })
            }}
          >
            退出登录
          </Button>
        </Form>
      </Card>
    </div>
  )
}
