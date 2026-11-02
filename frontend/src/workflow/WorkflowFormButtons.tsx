import { Button, Popconfirm, Space } from 'antd'
import type { ReactNode } from 'react'
import { useAuth } from '../auth/AuthContext'
import type { WorkflowRuntime } from '../types'
import {
  ACTION_CANCEL,
  ACTION_DELETE,
  ACTION_SUBMIT,
  actionShown,
  canActOnForm,
  roleCanDelete,
  roleCanWrite,
  saveActionLabel,
  workflowEnded,
} from './formAccess'

export function submitLabel(runtime: WorkflowRuntime | null | undefined) {
  if (!runtime?.processId || !runtime.status || runtime.status === 'DRAFT' || runtime.currentNodeType === 'START') {
    return '提交办理'
  }
  return '同意并送下一步'
}

export function WorkflowFormButtons({
  runtime,
  loading,
  disabled,
  onCancel,
  cancelText = '关闭',
  onDraft,
  onSubmit,
  extra,
  deleteTitle,
  onDelete,
  appearance,
}: {
  runtime?: WorkflowRuntime | null
  loading?: boolean
  disabled?: boolean
  onCancel?: () => void
  cancelText?: string
  onDraft: () => void
  onSubmit: () => void
  extra?: ReactNode
  deleteTitle?: string
  onDelete?: () => void | Promise<void>
  appearance?: 'qingflow'
}) {
  const { user } = useAuth()
  const canWrite = roleCanWrite(user, runtime)
  const canRemove = roleCanDelete(user, runtime)
  const canAct = canActOnForm(runtime) && canWrite
  const showCancel = !!onCancel && actionShown(runtime, ACTION_CANCEL)
  const showDraft = canWrite
  const showSubmit = actionShown(runtime, ACTION_SUBMIT) && canAct
  const showDelete = !!onDelete && actionShown(runtime, ACTION_DELETE) && canRemove
  if (!showCancel && !showDraft && !showSubmit && !showDelete && !extra) return null
  const qingflow = appearance === 'qingflow'
  const draftLabel = qingflow ? (workflowEnded(runtime) || !canAct ? '保存' : '暂存') : saveActionLabel(runtime)
  const submitText = qingflow ? '提交' : submitLabel(runtime)
  return (
    <Space wrap>
      {showCancel ? <Button onClick={onCancel}>{qingflow ? '取消' : cancelText}</Button> : null}
      {showDraft ? (
        <Button
          type={showSubmit ? 'default' : 'primary'}
          loading={loading}
          disabled={disabled}
          onClick={onDraft}
          title={workflowEnded(runtime) || !canAct ? '写入当前内容，不推动流程' : '只保存，不送给下一办理人'}
        >
          {draftLabel}
        </Button>
      ) : null}
      {showSubmit ? (
        <Button type="primary" loading={loading} disabled={disabled || !canAct} onClick={onSubmit} title="保存并交给下一办理人">
          {submitText}
        </Button>
      ) : null}
      {showDelete ? (
        <Popconfirm title={deleteTitle || '确认删除？'} onConfirm={() => void onDelete?.()}>
          <Button danger>删除</Button>
        </Popconfirm>
      ) : null}
      {extra}
    </Space>
  )
}
