import { useState } from 'react'
import { App, Avatar, Button, Form, Input, Modal } from 'antd'
import { RightOutlined, UserOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { authApi, profileApi } from '../api'
import { ApiError } from '../api/http'
import { useAuth } from '../auth/AuthContext'
import { IosInstallHint } from './IosInstallHint'

export function MiniMePage() {
  const { message } = App.useApp()
  const { user, setUser, logout, refresh } = useAuth()
  const navigate = useNavigate()
  const [profileOpen, setProfileOpen] = useState(false)
  const [passOpen, setPassOpen] = useState(false)

  if (!user) return null

  return (
    <>
      <div className="mini-me-head">
        <Avatar size={64} src={user.avatarUrl || undefined} icon={<UserOutlined />} />
        <div>
          <p className="mini-me-name">{user.displayName}</p>
          <div style={{ color: '#64748b', fontSize: 13 }}>
            {user.departmentName || '未分配部门'} · {user.username}
          </div>
        </div>
      </div>

      <div className="mini-card">
        <button type="button" className="mini-row" onClick={() => setProfileOpen(true)}>
          修改姓名 / 头像
          <RightOutlined style={{ color: '#94a3b8' }} />
        </button>
        <button type="button" className="mini-row" onClick={() => setPassOpen(true)}>
          修改密码
          <RightOutlined style={{ color: '#94a3b8' }} />
        </button>
        <button type="button" className="mini-row" onClick={() => {
          sessionStorage.removeItem('fromMini')
          navigate('/')
        }}>
          打开电脑版
          <RightOutlined style={{ color: '#94a3b8' }} />
        </button>
      </div>

      <IosInstallHint />

      <Button
        danger
        block
        size="large"
        style={{ marginTop: 24, borderRadius: 14, height: 48 }}
        onClick={async () => {
          await logout()
          navigate('/m/login', { replace: true })
        }}
      >
        退出登录
      </Button>

      <Modal title="个人资料" open={profileOpen} footer={null} onCancel={() => setProfileOpen(false)} destroyOnHidden>
        <Form
          layout="vertical"
          initialValues={{ displayName: user.displayName }}
          onFinish={async (values: { displayName: string }) => {
            try {
              setUser(await profileApi.update(values.displayName))
              message.success('已保存')
              setProfileOpen(false)
            } catch (e) {
              message.error(e instanceof ApiError ? e.message : '保存失败')
            }
          }}
        >
          <Form.Item label="头像">
            <input
              type="file"
              accept="image/png,image/jpeg,image/webp"
              onChange={async (event) => {
                const file = event.target.files?.[0]
                if (!file) return
                try {
                  setUser(await profileApi.avatar(file))
                  message.success('头像已更新')
                } catch (e) {
                  message.error(e instanceof ApiError ? e.message : '上传失败')
                }
              }}
            />
          </Form.Item>
          <Form.Item name="displayName" label="姓名" rules={[{ required: true, message: '请输入姓名' }]}>
            <Input />
          </Form.Item>
          <Button type="primary" htmlType="submit" block>
            保存
          </Button>
        </Form>
      </Modal>

      <Modal title="修改密码" open={passOpen} footer={null} onCancel={() => setPassOpen(false)} destroyOnHidden>
        <Form
          layout="vertical"
          onFinish={async (values: { oldPassword: string; newPassword: string }) => {
            try {
              await authApi.changePassword(values.oldPassword, values.newPassword)
              await refresh()
              message.success('密码已更新')
              setPassOpen(false)
            } catch (e) {
              message.error(e instanceof ApiError ? e.message : '修改失败')
            }
          }}
        >
          <Form.Item name="oldPassword" label="原密码" rules={[{ required: true }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item name="newPassword" label="新密码" rules={[{ required: true, min: 8, message: '至少 8 位' }]}>
            <Input.Password />
          </Form.Item>
          <Button type="primary" htmlType="submit" block>
            更新密码
          </Button>
        </Form>
      </Modal>
    </>
  )
}
