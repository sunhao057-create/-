import { useState } from 'react'
import { Button, Form, Input, Typography } from 'antd'
import { Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useBranding } from '../branding/BrandingContext'
import { ApiError } from '../api/http'
import { IosInstallHint } from './IosInstallHint'
import './mini.css'

export function MiniLoginPage() {
  const { user, login } = useAuth()
  const { branding } = useBranding()
  const navigate = useNavigate()
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)

  if (user && !user.mustChangePassword) {
    return <Navigate to="/m" replace />
  }
  if (user?.mustChangePassword) {
    return <Navigate to="/change-password" replace />
  }

  return (
    <div className="mini-shell">
      <div className="mini-phone">
        <div className="mini-login">
          <div className="mini-login-brand">
            {branding.logoUrl ? (
              <img src={branding.logoUrl} alt="" width={48} height={48} style={{ objectFit: 'contain', borderRadius: 12 }} />
            ) : null}
            <h1>{branding.name || '一体化管理'}</h1>
            <p>手机办公入口。待办、申请、个人信息都在这里处理。</p>
          </div>
          <div className="mini-login-card">
            <Typography.Title level={4} style={{ marginTop: 0 }}>
              登录
            </Typography.Title>
            <Form
              layout="vertical"
              onFinish={async (values: { username: string; password: string }) => {
                setError('')
                setPending(true)
                try {
                  const logged = await login(values.username.trim(), values.password)
                  navigate(logged.mustChangePassword ? '/change-password' : '/m', { replace: true })
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
                进入工作台
              </Button>
              <Button type="link" block style={{ marginTop: 8 }} onClick={() => navigate('/login')}>
                使用电脑版
              </Button>
              <IosInstallHint />
            </Form>
          </div>
        </div>
      </div>
    </div>
  )
}
