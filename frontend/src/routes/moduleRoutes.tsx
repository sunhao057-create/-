import { cloneElement, isValidElement, type ReactNode } from 'react'
import { Navigate, Route, useLocation } from 'react-router-dom'
import { can, homePath } from '../auth/permissions'
import { useAuth } from '../auth/AuthContext'
import type { FormModule, PermAction } from '../types'
import { ListCreateShell, CreateDrawer } from '../ui/CreateDrawer'
import { UsersPage } from '../pages/UsersPage'
import { ThemePage } from '../pages/ThemePage'
import { CursorPage } from '../pages/cursor/CursorPage'
import { RolesPage } from '../pages/RolesPage'
import { WorkflowsPage } from '../pages/WorkflowsPage'
import { OrgStructurePage } from '../pages/org/OrgStructurePage'
import { DevicesPage } from '../pages/device/DevicesPage'
import { DeviceWorkOrderListPage } from '../pages/device/DeviceWorkOrderListPage'
import { DeviceWorkOrderFormPage } from '../pages/device/DeviceWorkOrderFormPage'
import { MaintRecordFormPage } from '../pages/device/MaintRecordFormPage'
import { MaintRecordListPage } from '../pages/device/MaintRecordListPage'
import { PortalFormPage } from '../pages/portal/PortalFormPage'
import { PortalListPage } from '../pages/portal/PortalListPage'
import { CustomerAccountFormPage, CustomerAccountListPage } from '../pages/customer/CustomerAccountPages'
import { CustomerOpenFormPage, CustomerOpenListPage } from '../pages/customer/CustomerOpenPages'

function useDenyTo() {
  const mini = useLocation().pathname.startsWith('/m')
  const { user } = useAuth()
  return mini ? '/m' : homePath(user)
}

function ContactsPerm({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const fallback = useDenyTo()
  if (can(user, 'USER', 'view') || can(user, 'ORG', 'view') || can(user, 'ROLE', 'view')) {
    return children
  }
  return <Navigate to={fallback} replace />
}

export function PermRoute({
  module,
  action = 'view',
  children,
}: {
  module: FormModule | string
  action?: PermAction
  children: ReactNode
}) {
  const { user } = useAuth()
  const fallback = useDenyTo()
  if (!can(user, module, action)) {
    return <Navigate to={fallback} replace />
  }
  return children
}

function AppNav({ to }: { to: string }) {
  const mini = useLocation().pathname.startsWith('/m')
  const href = mini && to.startsWith('/') && !to.startsWith('/m') ? `/m/app${to}` : to
  return <Navigate to={href} replace />
}

function copy(node: ReactNode) {
  return isValidElement(node) ? cloneElement(node) : node
}

function crud(path: string, module: FormModule, list: ReactNode, form: ReactNode) {
  return (
    <Route
      key={path}
      path={path}
      element={
        <PermRoute module={module}>
          <ListCreateShell>{list}</ListCreateShell>
        </PermRoute>
      }
    >
      <Route
        path="new"
        element={
          <PermRoute module={module} action="create">
            <CreateDrawer>{copy(form)}</CreateDrawer>
          </PermRoute>
        }
      />
      <Route
        path=":id"
        element={
          <PermRoute module={module}>
            <CreateDrawer>{copy(form)}</CreateDrawer>
          </PermRoute>
        }
      />
    </Route>
  )
}

/** 电脑端与 /m/app 共用；每次调用生成新节点，避免同一棵树挂两处。 */
export function buildModuleRoutes() {
  return (
    <>
      {crud('portal/banners', 'MINI_BANNER', <PortalListPage kind="MINI_BANNER" />, <PortalFormPage kind="MINI_BANNER" />)}
      {crud('portal/knowledge', 'MINI_KNOWLEDGE', <PortalListPage kind="MINI_KNOWLEDGE" />, <PortalFormPage kind="MINI_KNOWLEDGE" />)}
      {crud('portal/company', 'MINI_COMPANY', <PortalListPage kind="MINI_COMPANY" />, <PortalFormPage kind="MINI_COMPANY" />)}
      {crud('portal/service', 'MINI_SERVICE', <PortalListPage kind="MINI_SERVICE" />, <PortalFormPage kind="MINI_SERVICE" />)}
      {crud('customer-opens', 'CUSTOMER_APPLY', <CustomerOpenListPage />, <CustomerOpenFormPage />)}
      {crud('customer-accounts', 'CUSTOMER_ACCOUNT', <CustomerAccountListPage />, <CustomerAccountFormPage />)}
      {crud(
        'devices/maint-records',
        'DEVICE_MAINT',
        <MaintRecordListPage />,
        <MaintRecordFormPage />,
      )}
      {crud(
        'devices/repairs',
        'DEVICE_REPAIR',
        <DeviceWorkOrderListPage kind="repairs" />,
        <DeviceWorkOrderFormPage kind="repairs" />,
      )}
      {crud(
        'devices/moves',
        'DEVICE_MOVE',
        <DeviceWorkOrderListPage kind="moves" />,
        <DeviceWorkOrderFormPage kind="moves" />,
      )}
      {crud(
        'devices/scraps',
        'DEVICE_SCRAP',
        <DeviceWorkOrderListPage kind="scraps" />,
        <DeviceWorkOrderFormPage kind="scraps" />,
      )}
      <Route
        path="devices"
        element={
          <PermRoute module="DEVICE">
            <DevicesPage />
          </PermRoute>
        }
      />
      <Route path="customers" element={<AppNav to="/devices" />} />
      <Route path="customers/*" element={<AppNav to="/devices" />} />
      <Route path="tech" element={<AppNav to="/devices" />} />
      <Route path="tech/*" element={<AppNav to="/devices" />} />
      <Route path="apps/tech" element={<AppNav to="/devices" />} />
      <Route path="apps/tech/*" element={<AppNav to="/devices" />} />
      <Route
        path="users"
        element={
          <ContactsPerm>
            <UsersPage />
          </ContactsPerm>
        }
      />
      <Route
        path="roles"
        element={
          <ContactsPerm>
            <RolesPage />
          </ContactsPerm>
        }
      />
      <Route
        path="org"
        element={
          <ContactsPerm>
            <OrgStructurePage />
          </ContactsPerm>
        }
      />
      <Route path="workflows/org" element={<AppNav to="/org" />} />
      <Route
        path="workflows"
        element={
          <PermRoute module="WORKFLOW">
            <WorkflowsPage />
          </PermRoute>
        }
      />
      <Route
        path="workflows/:id"
        element={
          <PermRoute module="WORKFLOW">
            <WorkflowsPage />
          </PermRoute>
        }
      />
      <Route path="cursor" element={<CursorPage />} />
      <Route
        path="theme"
        element={
          <PermRoute module="THEME">
            <ThemePage />
          </PermRoute>
        }
      />
    </>
  )
}
