import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type CSSProperties,
  type ReactNode,
} from 'react'
import { Button, Drawer, Modal, Space } from 'antd'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { pageGuide } from './pageGuide'
import { PageErrorBoundary } from './PageErrorBoundary'

export const entityDrawerLayout = {
  rootClassName: 'qf-create-drawer',
  placement: 'right' as const,
  size: 'large' as const,
  width: 'min(960px, 100vw)',
  destroyOnHidden: true,
  styles: { body: { padding: '12px 20px 28px' } },
}

/** 轻流业务表字段多、子表宽，用接近全屏的抽屉，避免列被挤没。 */
export const wideFormDrawerLayout = {
  rootClassName: 'qf-create-drawer qf-form-drawer-wide',
  placement: 'right' as const,
  width: 'min(1480px, 96vw)',
  destroyOnHidden: true,
  styles: { body: { padding: '12px 24px 28px', overflowX: 'auto' as const } },
}

type FormOverlayValue = {
  active: boolean
  close: () => void
  setFooter: (node: ReactNode) => void
}

const FormOverlayContext = createContext<FormOverlayValue | null>(null)

export function useFormOverlay() {
  return useContext(FormOverlayContext)
}

export function useFormOverlayFooter(node: ReactNode) {
  const overlay = useFormOverlay()
  useEffect(() => {
    if (!overlay?.active) return
    overlay.setFooter(node)
    return () => overlay.setFooter(null)
  }, [overlay, node])
}

export function ListCreateShell({ children }: { children: ReactNode }) {
  return (
    <>
      {children}
      <Outlet />
    </>
  )
}

function QfSubmitModal({
  title,
  open,
  onClose,
  footer,
  children,
}: {
  title: ReactNode
  open: boolean
  onClose: () => void
  footer?: ReactNode
  children: ReactNode
}) {
  return (
    <Modal
      wrapClassName="qf-submit-overlay"
      title={title}
      open={open}
      onCancel={onClose}
      footer={footer ?? <Button onClick={onClose}>取消</Button>}
      width="90%"
      centered
      maskClosable={false}
      destroyOnHidden
    >
      <div className="qf-submit-form">
        <PageErrorBoundary key={open ? 'open' : 'closed'}>{children}</PageErrorBoundary>
      </div>
    </Modal>
  )
}

export function CreateDrawer({ children }: { children: ReactNode }) {
  const navigate = useNavigate()
  const location = useLocation()
  const parent = location.pathname.replace(/\/new$/, '').replace(/\/\d+$/, '') || '/'
  const title = pageGuide(parent).title || pageGuide(location.pathname).title || '提交数据'
  const [footer, setFooter] = useState<ReactNode>(null)
  const close = useCallback(() => navigate(parent), [navigate, parent])
  const overlay = useMemo<FormOverlayValue>(
    () => ({ active: true, close, setFooter }),
    [close],
  )
  return (
    <QfSubmitModal title={title} open onClose={close} footer={footer}>
      <FormOverlayContext.Provider value={overlay}>{children}</FormOverlayContext.Provider>
    </QfSubmitModal>
  )
}

export function EntityDrawer({
  title,
  open,
  onClose,
  children,
  footer,
  extra,
  confirmLoading,
  onOk,
  okText = '确定',
  cancelText = '取消',
  okButtonProps,
  variant = 'drawer',
}: {
  title: ReactNode
  open: boolean
  onClose: () => void
  children: ReactNode
  footer?: ReactNode
  extra?: ReactNode
  confirmLoading?: boolean
  onOk?: () => void
  okText?: string
  cancelText?: string
  okButtonProps?: { style?: CSSProperties; disabled?: boolean }
  variant?: 'drawer' | 'qingflow'
}) {
  const hideOk = okButtonProps?.style?.display === 'none'
  const resolvedFooter =
    footer !== undefined
      ? footer
      : onOk
        ? (
            <Space>
              <Button onClick={onClose}>{cancelText}</Button>
              {hideOk ? null : (
                <Button type="primary" loading={confirmLoading} disabled={okButtonProps?.disabled} onClick={onOk}>
                  {okText}
                </Button>
              )}
            </Space>
          )
        : null
  if (variant === 'qingflow') {
    return (
      <QfSubmitModal title={title} open={open} onClose={onClose} footer={resolvedFooter}>
        {children}
      </QfSubmitModal>
    )
  }
  return (
    <Drawer {...entityDrawerLayout} title={title} open={open} onClose={onClose} extra={extra} footer={resolvedFooter}>
      <PageErrorBoundary key={open ? 'open' : 'closed'}>{children}</PageErrorBoundary>
    </Drawer>
  )
}
