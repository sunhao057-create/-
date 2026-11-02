import { useState } from 'react'
import { Button, Form, Input, Typography } from 'antd'
import { Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useBranding } from '../branding/BrandingContext'
import { homePath } from '../auth/permissions'
import { ApiError } from '../api/http'

export function LoginPage() {
  const { user, login } = useAuth()
  const { branding } = useBranding()
  const navigate = useNavigate()
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)

  if (user && !user.mustChangePassword) {
    return <Navigate to={homePath(user)} replace />
  }
  if (user?.mustChangePassword) {
    return <Navigate to="/change-password" replace />
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div style={{ marginBottom: 28 }}>
          {branding.logoUrl ? (
            <img src={branding.logoUrl} alt="" style={{ width: 40, height: 40, objectFit: 'contain', marginBottom: 12 }} />
          ) : null}
          <Typography.Title level={3} style={{ margin: 0, fontWeight: 700, letterSpacing: '-0.03em' }}>
            {branding.name || '项目管理'}
          </Typography.Title>
          <Typography.Paragraph type="secondary" style={{ marginTop: 8, marginBottom: 0 }}>
            登录后先看「我的待办」。有单据就点进去办理；要新发起申请，从左侧菜单进模块再点「新建」。
          </Typography.Paragraph>
        </div>
        <Form
          layout="vertical"
          onFinish={async (values: { username: string; password: string }) => {
            setError('')
            setPending(true)
            try {
              const logged = await login(values.username.trim(), values.password)
              navigate(logged.mustChangePassword ? '/change-password' : homePath(logged), { replace: true })
            } catch (e) {
              setError(e instanceof ApiError ? e.message : '登录失败')
            } finally {
              setPending(false)
            }
          }}
        >
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input size="large" autoComplete="username" />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password size="large" autoComplete="current-password" />
          </Form.Item>
          {error ? (
            <Typography.Text type="danger" style={{ display: 'block', marginBottom: 12 }}>
              {error}
            </Typography.Text>
          ) : null}
          <Button type="primary" htmlType="submit" size="large" block loading={pending}>
            登录
          </Button>
          <Button type="link" size="large" block style={{ marginTop: 8 }} onClick={() => navigate('/public')}>
            公开办理
          </Button>
          <Button type="link" size="large" block onClick={() => navigate('/m/login')}>
            手机办公
          </Button>
        </Form>
      </div>
    </div>
  )
}
