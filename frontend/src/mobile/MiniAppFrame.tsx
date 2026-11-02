import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { PageErrorBoundary } from '../ui/PageErrorBoundary'

export function MiniStayRedirect() {
  const location = useLocation()
  if (sessionStorage.getItem('fromMini') !== '1') return null
  const path = location.pathname
  if (path.startsWith('/m') || path.startsWith('/public') || path === '/login' || path === '/change-password') {
    return null
  }
  if (path === '/') {
    return <Navigate to="/m" replace />
  }
  return <Navigate to={`/m/app${path}${location.search}`} replace />
}

export function MiniAppFrame() {
  return (
    <div className="mini-embed">
      <div className="mini-embed-body">
        <PageErrorBoundary>
          <Outlet />
        </PageErrorBoundary>
      </div>
    </div>
  )
}

export { buildModuleRoutes as miniAppRoutes } from '../routes/moduleRoutes'
