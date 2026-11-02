import { cloneElement, isValidElement, useEffect, useState, type ReactElement, type ReactNode } from 'react'
import { Form } from 'antd'
import type { FormItemProps } from 'antd'
import { workflowApi } from '../api'
import { useAuth } from '../auth/AuthContext'
import { can } from '../auth/permissions'
import type { FieldMode, FormModule, User, WorkflowRuntime } from '../types'

export const ACTION_CANCEL = '_action_cancel'
export const ACTION_DRAFT = '_action_draft'
export const ACTION_SUBMIT = '_action_submit'
export const ACTION_DELETE = '_action_delete'

export function roleCanWrite(user: User | null | undefined, runtime: WorkflowRuntime | null | undefined) {
  const module = runtime?.formModule as FormModule | undefined
  if (!module) return Boolean(user)
  if (runtime?.bizId == null) return can(user, module, 'create') || can(user, module, 'edit')
  return can(user, module, 'edit')
}

export function roleCanDelete(user: User | null | undefined, runtime: WorkflowRuntime | null | undefined) {
  const module = runtime?.formModule as FormModule | undefined
  return module ? can(user, module, 'delete') : false
}

export function workflowEnded(runtime: WorkflowRuntime | null | undefined) {
  if (!runtime) return false
  return runtime.status === 'DONE' || runtime.status === 'CANCELLED'
}

export function actionShown(runtime: WorkflowRuntime | null | undefined, key: string) {
  if (key === ACTION_DRAFT) return true
  if (workflowEnded(runtime) && runtime?.fields?.[key] !== 'VISIBLE' && runtime?.fields?.[key] !== 'EDITABLE') {
    return false
  }
  const raw = runtime?.fields?.[key]
  if (raw === 'HIDDEN') return false
  if (!runtime?.processId) return raw !== 'HIDDEN'
  return true
}

export function saveActionLabel(runtime: WorkflowRuntime | null | undefined) {
  if (workflowEnded(runtime) || !canActOnForm(runtime)) return '保存'
  return '保存草稿'
}

export function workflowFieldMode(runtime: WorkflowRuntime | null | undefined, key: string): FieldMode {
  if (key.startsWith('_action_')) {
    const raw = runtime?.fields?.[key]
    if (raw === 'HIDDEN') return 'HIDDEN'
    return raw === 'VISIBLE' || raw === 'EDITABLE' ? raw : 'EDITABLE'
  }
  if (!runtime?.processId) return 'EDITABLE'
  if (workflowEnded(runtime)) return 'VISIBLE'
  const raw = runtime?.fields?.[key]
  const mode = raw === 'HIDDEN' || raw === 'VISIBLE' || raw === 'EDITABLE' ? raw : 'EDITABLE'
  const initiating = !runtime.status || runtime.bizId == null || runtime.currentNodeType === 'START'
  if (mode === 'EDITABLE' && runtime.canHandle === false && !initiating) {
    return 'VISIBLE'
  }
  return mode
}

export function canActOnForm(runtime: WorkflowRuntime | null | undefined) {
  if (workflowEnded(runtime)) return false
  if (!runtime?.processId || runtime.bizId == null || !runtime.status || runtime.currentNodeType === 'START') {
    return true
  }
  return runtime.canHandle !== false
}

export function fieldMode(runtime: WorkflowRuntime | null | undefined, key: string, canWrite = false): FieldMode {
  if (key.startsWith('_action_')) return workflowFieldMode(runtime, key)
  if (canWrite) return 'EDITABLE'
  return workflowFieldMode(runtime, key)
}

export function isEditable(runtime: WorkflowRuntime | null | undefined, key: string, canWrite = false) {
  return fieldMode(runtime, key, canWrite) === 'EDITABLE'
}

export function hasEditableField(_runtime: WorkflowRuntime | null | undefined, canWrite = true) {
  if (!canWrite) return false
  return true
}

export function workflowAllowsEdit(runtime: WorkflowRuntime | null | undefined) {
  if (workflowEnded(runtime)) return false
  if (!runtime?.processId || !runtime.status) return true
  return runtime.canHandle !== false
}

export function useWorkflowRuntime(module: string, bizId?: number, autostart = true) {
  const [runtime, setRuntime] = useState<WorkflowRuntime | null>(null)

  const reload = async () => {
    try {
      setRuntime(await workflowApi.runtime(module, bizId, autostart))
    } catch {
      setRuntime(null)
    }
  }

  useEffect(() => {
    void reload()
  }, [module, bizId, autostart])

  return { runtime, reload, setRuntime }
}

export function AccessItem({
  runtime,
  field,
  children,
  ...itemProps
}: FormItemProps & { runtime?: WorkflowRuntime | null; field: string; children: ReactNode }) {
  const { user } = useAuth()
  const canWrite = roleCanWrite(user, runtime)
  const mode = fieldMode(runtime, field, canWrite)
  if (mode === 'HIDDEN') return null
  const disabled = mode !== 'EDITABLE'
  const { rules, ...rest } = itemProps
  const child =
    disabled && isValidElement(children)
      ? cloneElement(children as ReactElement<{ disabled?: boolean }>, { disabled: true })
      : children
  const requireRules = workflowFieldMode(runtime, field) === 'EDITABLE'
  return (
    <Form.Item {...rest} rules={mode === 'EDITABLE' && requireRules ? rules : undefined}>
      {child}
    </Form.Item>
  )
}
