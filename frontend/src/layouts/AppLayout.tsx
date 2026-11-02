import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { App, Avatar, Badge, Button, Drawer, Dropdown, Form, Grid, Input, Layout, Modal, Space, Upload } from 'antd'
import {
  AppstoreOutlined,
  HomeOutlined,
  LogoutOutlined,
  MenuOutlined,
  NodeIndexOutlined,
  SettingOutlined,
  SkinOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { AppMark } from '../apps/AppMark'
import { defaultPackageKey, packageOfPath, visiblePackages, writeLastPackage } from '../apps/catalog'
import { PackageSider } from '../apps/PackageSider'
import { brandingApi, inboxApi } from '../api'
import { ApiError } from '../api/http'
import { useAuth } from '../auth/AuthContext'
import { can } from '../auth/permissions'
import { useBranding } from '../branding/BrandingContext'
import { PageErrorBoundary } from '../ui/PageErrorBoundary'
import { pageGuide } from '../ui/pageGuide'

const { Header, Content } = Layout

function isSettingsPath(pathname: string) {
  return (
    pathname.startsWith('/org') ||
    pathname.startsWith('/roles') ||
    pathname.startsWith('/users') ||
    pathname.startsWith('/contacts') ||
    pathname.startsWith('/workflows') ||
    pathname.startsWith('/theme') ||
    pathname.startsWith('/cursor') ||
    pathname.startsWith('/profile') ||
    pathname.startsWith('/inbox')
  )
}

function showPackageSider(pathname: string) {
  return pathname.startsWith('/apps') || Boolean(packageOfPath(pathname))
}

function hideChromeHeader(pathname: string) {
  return pathname === '/' || pathname.startsWith('/apps') || Boolean(packageOfPath(pathname))
}

export function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const screens = Grid.useBreakpoint()
  const isMobile = screens.md === false
  const [open, setOpen] = useState(false)
  const [packMenu, setPackMenu] = useState(false)
  const [todoCount, setTodoCount] = useState(0)
  const listPath = location.pathname.replace(/\/new$/, '')
  const guide = pageGuide(listPath)
  const fromMini = sessionStorage.getItem('fromMini') === '1'
  const packs = visiblePackages(user)
  const currentPack = packageOfPath(location.pathname)
  const packKey = location.pathname.startsWith('/apps/')
    ? location.pathname.split('/')[2]
    : currentPack?.key || defaultPackageKey(user)
  const railHome = location.pathname === '/' || location.pathname.startsWith('/inbox')
  const railApps = showPackageSider(location.pathname)
  const canContacts = can(user, 'ORG', 'view') || can(user, 'USER', 'view') || can(user, 'ROLE', 'view')

  useEffect(() => {
    let alive = true
    const loadTodos = () => {
      void inboxApi
        .list('TODO')
        .then((rows) => {
          if (alive) setTodoCount(rows.length)
        })
        .catch(() => {
          if (alive) setTodoCount(0)
        })
    }
    loadTodos()
    const timer = window.setInterval(loadTodos, 60000)
    return () => {
      alive = false
      window.clearInterval(timer)
    }
  }, [location.pathname])

  const goApps = () => {
    const key = defaultPackageKey(user)
    if (key) writeLastPackage(key)
    navigate(key ? `/apps/${key}` : '/apps')
    setOpen(false)
  }

  const settingsItems = useMemo(
    () =>
      [
        can(user, 'WORKFLOW', 'view')
          ? { key: '/workflows', icon: <NodeIndexOutlined />, label: '流程引擎', onClick: () => navigate('/workflows') }
          : null,
        can(user, 'THEME', 'view')
          ? { key: '/theme', icon: <SkinOutlined />, label: '主题风格', onClick: () => navigate('/theme') }
          : null,
        user?.role === 'ADMIN'
          ? { key: '/cursor', icon: <NodeIndexOutlined />, label: '孙浩改系统', onClick: () => navigate('/cursor') }
          : null,
        { key: '/profile', icon: <UserOutlined />, label: '个人中心', onClick: () => navigate('/profile') },
        {
          key: 'logout',
          icon: <LogoutOutlined />,
          label: '退出',
          onClick: async () => {
            await logout()
            sessionStorage.removeItem('fromMini')
            navigate(fromMini ? '/m/login' : '/login', { replace: true })
          },
        },
      ].filter(Boolean) as { key: string; icon: ReactNode; label: string; onClick: () => void }[],
    [user, logout, navigate, fromMini],
  )

  const rail = (
    <aside className="qf-rail">
      <BrandBar />
      <nav className="qf-rail-nav">
        <button type="button" className={railHome ? 'is-active' : ''} onClick={() => { navigate('/'); setOpen(false) }}>
          <HomeOutlined />
          <span>首页</span>
        </button>
        {packs.length ? (
          <Dropdown
            trigger={['click']}
            placement="rightTop"
            open={packMenu}
            onOpenChange={(next) => {
              setPackMenu(next)
              if (next && !railApps) goApps()
            }}
            classNames={{ root: 'qf-pack-flyout-root' }}
            popupRender={() => (
              <div className="qf-pack-flyout">
                <div className="qf-pack-flyout-title">选择应用包</div>
                {packs.map((item) => (
                  <button
                    key={item.key}
                    type="button"
                    className={item.key === packKey ? 'is-active' : ''}
                    onClick={() => {
                      writeLastPackage(item.key)
                      navigate(`/apps/${item.key}`)
                      setPackMenu(false)
                      setOpen(false)
                    }}
                  >
                    <AppMark label={item.label} seed={item.key} size="sm" />
                    <span>{item.label}</span>
                  </button>
                ))}
              </div>
            )}
          >
            <button type="button" className={railApps ? 'is-active' : ''}>
              <AppstoreOutlined />
              <span>应用包</span>
            </button>
          </Dropdown>
        ) : null}
      </nav>
      <div className="qf-rail-foot">
        {canContacts ? (
          <button
            type="button"
            className={location.pathname.startsWith('/org') || location.pathname.startsWith('/users') || location.pathname.startsWith('/roles') ? 'is-active' : ''}
            onClick={() => { navigate('/org'); setOpen(false) }}
          >
            <TeamOutlined />
            <span>通讯录</span>
          </button>
        ) : null}
        <Dropdown menu={{ items: settingsItems }} trigger={['click']} placement="topLeft">
          <button type="button" className={isSettingsPath(location.pathname) && !location.pathname.startsWith('/org') ? 'is-active' : ''}>
            <SettingOutlined />
            <span>系统设置</span>
          </button>
        </Dropdown>
        <button type="button" className="qf-rail-user" onClick={() => { navigate('/profile'); setOpen(false) }}>
          <Avatar size={28} src={user?.avatarUrl || undefined} icon={<UserOutlined />} />
          <span>{user?.displayName}</span>
        </button>
      </div>
    </aside>
  )

  return (
    <Layout className="app-shell qf-shell">
      {isMobile ? (
        <Drawer
          title={null}
          placement="left"
          open={open}
          onClose={() => setOpen(false)}
          width={320}
          rootClassName="app-mobile-sider is-dark"
          styles={{ body: { padding: 0, background: '#17181d', display: 'flex', height: '100%' }, header: { display: 'none' } }}
        >
          {rail}
          {showPackageSider(location.pathname) ? <PackageSider packageKey={packKey} /> : null}
        </Drawer>
      ) : (
        rail
      )}
      {!isMobile && showPackageSider(location.pathname) ? <PackageSider packageKey={packKey} /> : null}
      <Layout className="app-main" style={{ height: '100vh', overflow: 'hidden', background: 'transparent' }}>
        {fromMini || isMobile || !hideChromeHeader(location.pathname) ? (
          <Header className="app-header">
            {fromMini ? (
              <Button
                type="link"
                onClick={() => {
                  sessionStorage.removeItem('fromMini')
                  navigate('/m')
                }}
              >
                返回手机端
              </Button>
            ) : isMobile ? (
              <Button type="text" icon={<MenuOutlined />} onClick={() => setOpen(true)} />
            ) : null}
            {hideChromeHeader(location.pathname) ? null : (
              <div className="app-header-copy">
                <div className="app-header-title">{guide.title}</div>
                <div className="app-header-hint">{guide.hint}</div>
              </div>
            )}
            <div className="app-header-user">
              <Badge count={todoCount} size="small" overflowCount={99}>
                <Button type="text" onClick={() => navigate(fromMini ? '/m/inbox' : '/inbox')}>
                  待办
                </Button>
              </Badge>
            </div>
          </Header>
        ) : null}
        <Content className={isMobile ? 'app-content app-content-mobile' : 'app-content'}>
          <PageErrorBoundary key={listPath}>
            <Outlet />
          </PageErrorBoundary>
        </Content>
      </Layout>
    </Layout>
  )
}

function BrandBar() {
  const { message } = App.useApp()
  const { user } = useAuth()
  const { branding, reload } = useBranding()
  const [open, setOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const canEdit = can(user, 'BRANDING', 'edit')

  return (
    <>
      <button
        type="button"
        className="qf-rail-brand"
        onClick={() => {
          if (canEdit) setOpen(true)
        }}
        disabled={!canEdit}
      >
        <Avatar size={32} src={branding.logoUrl || undefined} style={{ background: '#3370ff', color: '#fff' }}>
          {(branding.name || '项').slice(0, 1)}
        </Avatar>
      </button>
      <Modal title="系统名称与图标" open={open} onCancel={() => setOpen(false)} footer={null} destroyOnHidden>
        <Form
          layout="vertical"
          initialValues={{ name: branding.name }}
          onFinish={async (values: { name: string }) => {
            setSaving(true)
            try {
              await brandingApi.update(values.name.trim())
              await reload()
              message.success('已保存系统名称')
              setOpen(false)
            } catch (e) {
              message.error(e instanceof ApiError ? e.message : '保存失败')
            } finally {
              setSaving(false)
            }
          }}
        >
          <Form.Item name="name" label="系统名称" rules={[{ required: true, message: '请填写系统名称' }]}>
            <Input maxLength={50} placeholder="显示在登录页和左侧栏" />
          </Form.Item>
          <Form.Item label="图标">
            <Upload
              accept="image/png,image/jpeg,image/webp"
              showUploadList={false}
              beforeUpload={async (file) => {
                try {
                  await brandingApi.uploadLogo(file)
                  await reload()
                  message.success('图标已更新')
                } catch (e) {
                  message.error(e instanceof ApiError ? e.message : '上传失败')
                }
                return false
              }}
            >
              <Space>
                <Avatar size={48} src={branding.logoUrl || undefined} style={{ background: '#3370ff', color: '#fff' }}>
                  {(branding.name || '项').slice(0, 1)}
                </Avatar>
                <Button>上传图标</Button>
              </Space>
            </Upload>
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={saving}>
            保存名称
          </Button>
        </Form>
      </Modal>
    </>
  )
}
