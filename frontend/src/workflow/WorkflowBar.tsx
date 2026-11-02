import { App, Button, Input, Modal, Select, Space, Tag, Timeline, Alert } from 'antd'
import { useState } from 'react'
import { workflowApi } from '../api'
import { ApiError } from '../api/http'
import type { WorkflowRuntime } from '../types'
import { formatDateTime, statusLabel } from '../utils/format'
import { techKindByModule } from '../tech/catalog'
import { ACTION_SUBMIT, actionShown, canActOnForm, roleCanWrite, saveActionLabel, workflowEnded } from './formAccess'
import { useAuth } from '../auth/AuthContext'

export function bizPath(formModule?: string | null, bizId?: number | null) {
  if (!formModule || !bizId) return '/'
  const tech = techKindByModule(formModule)
  if (tech) return `/tech/${tech.slug}/${bizId}`
  if (formModule === 'CUSTOMER') return `/customers?id=${bizId}`
  if (formModule === 'DEVICE_REPAIR') return `/devices/repairs/${bizId}`
  if (formModule === 'DEVICE_MAINT') return `/devices/maint-records/${bizId}`
  if (formModule === 'DEVICE_MOVE') return `/devices/moves/${bizId}`
  if (formModule === 'DEVICE_SCRAP') return `/devices/scraps/${bizId}`
  return '/'
}

/** 表单对应列表页路径，用于从流程设计页返回表单列表。 */
export function formListPath(formModule?: string | null) {
  if (!formModule) return '/'
  const tech = techKindByModule(formModule)
  if (tech) return `/tech/${tech.slug}`
  if (formModule === 'CUSTOMER') return '/customers'
  if (formModule === 'CUSTOMER_APPLY') return '/customer-opens'
  if (formModule === 'CUSTOMER_ACCOUNT') return '/customer-accounts'
  if (formModule === 'DEVICE_REPAIR') return '/devices/repairs'
  if (formModule === 'DEVICE_MAINT') return '/devices/maint-records'
  if (formModule === 'DEVICE_MOVE') return '/devices/moves'
  if (formModule === 'DEVICE_SCRAP') return '/devices/scraps'
  return '/'
}

export function WorkflowBar({
  module,
  bizId,
  runtime,
  onReloaded,
  onSaveDraft,
  hideAdvance,
  comment,
  onCommentChange,
}: {
  module: string
  bizId?: number
  runtime: WorkflowRuntime | null
  onReloaded?: () => Promise<void> | void
  onSaveDraft?: () => Promise<void> | void
  hideAdvance?: boolean
  comment?: string
  onCommentChange?: (value: string) => void
}) {
  const { message } = App.useApp()
  const { user } = useAuth()
  const [innerComment, setInnerComment] = useState('')
  const [reason, setReason] = useState('')
  const [target, setTarget] = useState<string>()
  const [openReturn, setOpenReturn] = useState(false)
  const [busy, setBusy] = useState(false)
  const note = comment ?? innerComment
  const setNote = onCommentChange ?? setInnerComment

  if (!runtime?.processId || !bizId) return null

  const ended = workflowEnded(runtime)
  const mine = canActOnForm(runtime)
  const canWrite = roleCanWrite(user, runtime)
  const node = runtime.currentNodeName || '当前步骤'
  const handlers = runtime.currentAssignees?.length ? runtime.currentAssignees.join('、') : ''
  const nowTitle = ended
    ? canWrite
      ? '流程已结束，改完请点保存'
      : '流程已结束，这张单据只能查看'
    : mine
      ? `现在请你办理：${node}`
      : `当前由${handlers || '其他人'}办理${canWrite ? '，你仍可改内容并保存' : '，你只能查看'}`
  const nowDesc = ended
    ? canWrite
      ? '点「保存」写入当前内容，不会再往下走流程。'
      : '如需改内容，请联系有编辑权限的人员。'
    : mine
      ? runtime.status === 'DRAFT' || runtime.currentNodeType === 'START'
        ? '填完点「提交办理」送给下一办理人。还不想送审就点「保存草稿」。'
        : '看完内容后点「同意并送下一步」。要退回上一节点请写原因再点退回。'
      : canWrite
        ? '点「保存」只改数据，不会代替当前办理人往下走。'
        : handlers
          ? `请等 ${handlers} 处理完成。`
          : '当前步骤不由你处理。'

  return (
    <div className="workflow-now">
    <div className="workflow-now-title">流程日志</div>
    <Alert
      showIcon
      type={ended ? 'success' : mine ? (runtime.overdue ? 'error' : 'info') : 'warning'}
      message={nowTitle}
      description={
        <Space direction="vertical" size={8} style={{ width: '100%' }}>
          <span>
            {nowDesc}
            {runtime.overdue && mine ? ' 已超时，请尽快处理。' : ''}
          </span>
          <Space wrap size={4}>
            {runtime.status ? <Tag>{statusLabel(runtime.status)}</Tag> : null}
            {handlers ? <Tag>办理人：{handlers}</Tag> : null}
          </Space>
        </Space>
      }
    />
    <Space direction="vertical" size={12} style={{ width: '100%', marginTop: 12 }}>
      <Space wrap>
        {!hideAdvance && onSaveDraft && canWrite ? (
          <Button
            loading={busy}
            onClick={async () => {
              setBusy(true)
              try {
                await onSaveDraft()
                message.success('已保存')
                await onReloaded?.()
              } catch (e) {
                message.error(e instanceof ApiError ? e.message : '保存失败')
              } finally {
                setBusy(false)
              }
            }}
          >
            {saveActionLabel(runtime)}
          </Button>
        ) : null}
        {!hideAdvance && actionShown(runtime, ACTION_SUBMIT) && canActOnForm(runtime) ? (
          <Button
            type="primary"
            loading={busy}
            onClick={async () => {
              setBusy(true)
              try {
                if (onSaveDraft) {
                  await onSaveDraft()
                }
                await workflowApi.advance(module, bizId, note)
                message.success(runtime.currentNodeType === 'START' || runtime.status === 'DRAFT' ? '已提交' : '已提交到下一节点')
                setNote('')
                await onReloaded?.()
              } catch (e) {
                message.error(e instanceof ApiError ? e.message : '提交失败')
              } finally {
                setBusy(false)
              }
            }}
          >
            {runtime.currentNodeType === 'START' || runtime.status === 'DRAFT' ? '提交办理' : '同意并送下一步'}
          </Button>
        ) : null}
        {!workflowEnded(runtime) && runtime.canReturn ? (
          <Button
            danger
            onClick={() => {
              setTarget(runtime.previousNodes?.[0]?.key)
              setOpenReturn(true)
            }}
          >
            退回
          </Button>
        ) : null}
      </Space>
        {runtime.canAdvance && !workflowEnded(runtime) ? (
        <Input.TextArea
          rows={2}
          value={note}
          onChange={(e) => setNote(e.target.value)}
          placeholder="审批意见（选填）"
        />
      ) : null}
      {runtime.returnReason ? (
        <Alert
          type="warning"
          showIcon
          message={`由「${runtime.returnFromName || '上一节点'}」退回`}
          description={runtime.returnReason}
        />
      ) : null}
      {runtime.history && runtime.history.length > 0 ? (
        <Timeline
          items={runtime.history.map((item, index) => ({
            key: String(index),
            children: (
              <div>
                <div>
                  <Tag>{statusLabel(item.actionLabel || item.action)}</Tag>
                  {item.fromNodeName ? `${item.fromNodeName} → ` : ''}
                  {item.toNodeName || ''}
                </div>
                <div style={{ color: '#888', fontSize: 12 }}>
                  {item.actorName || '未记录提交人'} · {formatDateTime(item.createdAt)}
                </div>
                {item.comment ? <div>意见：{item.comment}</div> : null}
              </div>
            ),
          }))}
        />
      ) : null}
      <Modal
        title="退回到之前的节点"
        open={openReturn}
        onCancel={() => setOpenReturn(false)}
        okText="确定"
        cancelText="取消"
        confirmLoading={busy}
        onOk={async () => {
          if (!target) {
            message.warning('请选择退回节点')
            return
          }
          if (!reason.trim()) {
            message.warning('请填写退回原因')
            return
          }
          setBusy(true)
          try {
            await workflowApi.returnTo(module, bizId, target, reason.trim())
            message.success('已退回')
            setOpenReturn(false)
            setReason('')
            await onReloaded?.()
          } catch (e) {
            message.error(e instanceof ApiError ? e.message : '退回失败')
          } finally {
            setBusy(false)
          }
        }}
      >
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
          <div>退回后，目标节点办理人可以再次处理，并看到退回原因。</div>
          <Select
            style={{ width: '100%' }}
            value={target}
            onChange={setTarget}
            options={(runtime.previousNodes || []).map((item) => ({ value: item.key, label: item.name }))}
            placeholder="选择退回节点"
          />
          <Input.TextArea rows={4} value={reason} onChange={(e) => setReason(e.target.value)} placeholder="退回原因（必填）" />
        </Space>
      </Modal>
    </Space>
    </div>
  )
}
