import { App as AntApp, ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { BrandingProvider } from './branding/BrandingContext'
import { Guard } from './auth/Guard'
import { AppLayout } from './layouts/AppLayout'
import { PublicLayout } from './layouts/PublicLayout'
import { PublicHomePage } from './pages/public/PublicHomePage'
import { MiniLayout } from './mobile/MiniLayout'
import { MiniLoginPage } from './mobile/MiniLoginPage'
import { MiniInboxPage } from './mobile/MiniInboxPage'
import { MiniMePage } from './mobile/MiniMePage'
import { MiniAppFrame, MiniStayRedirect } from './mobile/MiniAppFrame'
import { MiniDeviceHome } from './mobile/MiniDeviceHome'
import { MiniDeviceDetail } from './mobile/MiniDeviceDetail'
import { LoginPage } from './pages/LoginPage'
import { ChangePasswordPage } from './pages/ChangePasswordPage'
import { ProfilePage } from './pages/ProfilePage'
import { InboxPage } from './pages/InboxPage'
import { HomePage } from './pages/HomePage'
import { AppPackageHome } from './pages/apps/AppPackageHome'
import { ThemeProvider, useAppTheme } from './theme/ThemeContext'
import { buildModuleRoutes } from './routes/moduleRoutes'
import './theme/theme.css'
import './ui/app-shell.css'

function ThemedApp() {
  const { antdTheme } = useAppTheme()
  return (
    <ConfigProvider
      locale={zhCN}
      theme={antdTheme}
      form={{ scrollToFirstError: true }}
    >
      <AntApp>
        <BrowserRouter>
          <AuthProvider>
            <BrandingProvider>
            <MiniStayRedirect />
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/m/login" element={<MiniLoginPage />} />
              <Route element={<PublicLayout />}>
                <Route path="/public" element={<PublicHomePage />} />
                <Route path="/public/customers/*" element={<Navigate to="/public" replace />} />
                <Route path="/public/tech/*" element={<Navigate to="/public" replace />} />
              </Route>
              <Route element={<Guard />}>
                <Route path="/m" element={<MiniLayout />}>
                  <Route index element={<MiniDeviceHome />} />
                  <Route path="devices/:id" element={<MiniDeviceDetail />} />
                  <Route path="inbox" element={<MiniInboxPage />} />
                  <Route path="me" element={<MiniMePage />} />
                  <Route path="app" element={<MiniAppFrame />}>
                    {buildModuleRoutes()}
                  </Route>
                </Route>
                <Route path="/change-password" element={<ChangePasswordPage />} />
                <Route element={<AppLayout />}>
                  <Route index element={<HomePage />} />
                  <Route path="inbox" element={<InboxPage />} />
                  <Route path="apps" element={<AppPackageHome />} />
                  <Route path="apps/:key" element={<AppPackageHome />} />
                  {buildModuleRoutes()}
                  <Route path="profile" element={<ProfilePage />} />
                </Route>
              </Route>
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
            </BrandingProvider>
          </AuthProvider>
        </BrowserRouter>
      </AntApp>
    </ConfigProvider>
  )
}

export default function App() {
  return (
    <ThemeProvider>
      <ThemedApp />
    </ThemeProvider>
  )
}
