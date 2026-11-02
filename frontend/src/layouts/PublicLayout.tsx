import { createContext, useContext, useEffect, useState } from 'react'
import { Avatar, Button, Card, Drawer, Grid, Layout, Menu, Spin } from 'antd'
import { HomeOutlined, LoginOutlined, MenuOutlined, ShopOutlined, ToolOutlined } from '@ant-design/icons'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useBranding } from '../branding/BrandingContext'
import { useAppTheme } from '../theme/ThemeContext'
import { publicApi, type PublicConfig, type PublicModuleFlags } from '../api/public'
import { techKindBySlug } from '../tech/catalog'

const { Header, Sider, Content } = Layout

const CLOSED: PublicModuleFlags = { publicAccess: false, display: false, submit: false, query: false, export: false }
const EMPTY_CONFIG: PublicConfig = {
  project: CLOSED,
  customer: CLOSED,
  supplier: CLOSED,
  payment: CLOSED,
  modules: [],
}

const PublicPortalContext = createContext<PublicConfig>(EMPTY_CONFIG)

export function usePublicConfig() {
  return useContext(PublicPortalContext)
}

function pageTitle(pathname: string) {
  if (pathname.startsWith('/public/customers')) return '客户信息'
  if (pathname.startsWith('/public/tech/')) {
    const slug = pathname.split('/')[3]
    const kind = techKindBySlug(slug)
    if (kind) return kind.label
  }
  return '公开办理'
}

function moduleIcon(module: string) {
  if (module === 'CUSTOMER') return <ShopOutlined />
  if (module.startsWith('TECH_') || module.startsWith('DEVICE')) return <ToolOutlined />
  return <ShopOutlined />
}

function moduleNavLabel(module: string, fallback: string) {
  if (module === 'CUSTOMER') return '客户信息'
  return fallback
}

export function PublicLayout() {
  const { branding } = useBranding()
  const { preset } = useAppTheme()
  const navigate = useNavigate()
  const location = useLocation()
  const screens = Grid.useBreakpoint()
  const isMobile = screens.md === false
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(true)
  const [config, setConfig] = useState<PublicConfig>(EMPTY_CONFIG)
  const siderDark = preset.siderDark

  useEffect(() => {
    publicApi
      .config()
      .then((data) =>
        setConfig({
          ...EMPTY_CONFIG,
          ...data,
          payment: data.payment ?? CLOSED,
          modules: data.modules ?? [],
        }),
      )
      .catch(() => setConfig(EMPTY_CONFIG))
      .finally(() => setLoading(false))
  }, [])

  const techMatch = location.pathname.match(/^\/public\/tech\/[^/]+/)
  const selected = location.pathname === '/public'
    ? ['/public']
    : location.pathname.startsWith('/public/customers')
      ? ['/public/customers']
      : techMatch
        ? [techMatch[0]]
        : ['/public']

  const moduleItems = (config.modules?.length
    ? config.modules.filter((item) => item.flags.publicAccess)
    : [{ module: 'CUSTOMER', moduleLabel: '客户信息', path: '/public/customers', flags: config.customer }].filter(
        (item) => item.flags.publicAccess,
      )
  )
    .filter((item) => {
      const module = String(item.module || '')
      return (
        module === 'CUSTOMER' ||
        (module.startsWith('TECH_') && !module.startsWith('TECH_PM_')) ||
        module.startsWith('DEVICE')
      )
    })
    .map((item) => ({
    key: item.path,
    icon: moduleIcon(item.module),
    label: moduleNavLabel(item.module, item.moduleLabel),
  }))

  const menu = (
    <Menu
      className="app-sider-menu"
      theme={siderDark ? 'dark' : 'light'}
      mode="inline"
      selectedKeys={selected}
      onClick={({ key }) => {
        navigate(key)
        setOpen(false)
      }}
      items={[{ key: '/public', icon: <HomeOutlined />, label: '首页' }, ...moduleItems]}
    />
  )

  const brand = (
    <div className="app-brand" style={{ cursor: 'default' }}>
      <Avatar
        size={32}
        src={branding.logoUrl || undefined}
        style={{ background: 'var(--app-primary)', color: 'var(--app-on-primary)', flexShrink: 0 }}
      >
        {(branding.name || '项').slice(0, 1)}
      </Avatar>
      <div style={{ minWidth: 0, flex: 1 }}>
        <div className="app-brand-name">{branding.name || '项目管理'}</div>
      </div>
    </div>
  )

  if (loading) {
    return (
      <div style={{ display: 'grid', placeItems: 'center', minHeight: '100vh' }}>
        <Spin size="large" />
      </div>
    )
  }

  return (
    <PublicPortalContext.Provider value={config}>
      <Layout className="app-shell">
        {isMobile ? (
          <Drawer
            title={null}
            placement="left"
            open={open}
            onClose={() => setOpen(false)}
            width={280}
            rootClassName={siderDark ? 'app-mobile-sider is-dark' : 'app-mobile-sider'}
            styles={{
              body: {
                padding: 0,
                background: 'var(--app-sider-bg)',
                display: 'flex',
                flexDirection: 'column',
              },
              header: { display: 'none' },
            }}
          >
            {brand}
            {menu}
          </Drawer>
        ) : (
          <Sider
            className={siderDark ? 'app-sider is-dark' : 'app-sider'}
            theme={siderDark ? 'dark' : 'light'}
            width={240}
            collapsedWidth={0}
            trigger={null}
            style={{ overflow: 'hidden', height: '100vh' }}
          >
            {brand}
            {menu}
          </Sider>
        )}
        <Layout className="app-main" style={{ height: '100vh', overflow: 'hidden', background: 'transparent' }}>
          <Header className="app-header">
            {isMobile ? <Button type="text" icon={<MenuOutlined />} onClick={() => setOpen(true)} /> : null}
            <div className="app-header-title">{pageTitle(location.pathname)}</div>
            <div className="app-header-user">
              <Button type="link" icon={<LoginOutlined />} onClick={() => navigate('/login')}>
                登录后台
              </Button>
            </div>
          </Header>
          <Content className={isMobile ? 'app-content app-content-mobile' : 'app-content'}>
            <Outlet />
          </Content>
        </Layout>
      </Layout>
    </PublicPortalContext.Provider>
  )
}

export function PublicClosed({ text = '该内容未开放公开办理' }: { text?: string }) {
  return <Card>{text}</Card>
}
