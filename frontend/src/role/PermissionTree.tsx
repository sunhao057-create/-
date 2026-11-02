import { useState } from 'react'
import { Checkbox, Radio, Space, Typography } from 'antd'
import { CaretRightOutlined } from '@ant-design/icons'
import type { DataScope, FormModule, FormPermission } from '../types'
import { PERMISSION_GROUPS, collectPermissionModules, type PermissionGroupNode } from './modules'
import { isRemovedSidebarModule } from '../qingflow/businessCatalog'

type ActionKey = 'view' | 'create' | 'edit' | 'delete' | 'importExport' | 'flow' | 'manageView'
type PublicKey = 'publicAccess' | 'publicDisplay' | 'publicSubmit' | 'publicQuery' | 'publicExport'

type Props = {
  value: FormPermission[]
  disabled?: boolean
  variant?: 'staff' | 'guest'
  onChange: (next: FormPermission[]) => void
}

const ACTIONS: Array<{ key: ActionKey; label: string }> = [
  { key: 'view', label: '查看' },
  { key: 'create', label: '新增' },
  { key: 'edit', label: '编辑' },
  { key: 'delete', label: '删除' },
  { key: 'importExport', label: '导入导出' },
  { key: 'flow', label: '流程' },
  { key: 'manageView', label: '视图' },
]

const ORG_ACTION_LABELS: Partial<Record<ActionKey, string>> = {
  view: '查看',
  create: '新增部门',
  edit: '编辑与调人',
  delete: '删除部门',
}

function actionLabel(module: FormPermission['module'], key: ActionKey, fallback: string) {
  if (module === 'ORG') return ORG_ACTION_LABELS[key] || fallback
  return fallback
}

const PUBLIC_ACTIONS: Array<{ key: PublicKey; label: string }> = [
  { key: 'publicDisplay', label: '展示' },
  { key: 'publicSubmit', label: '填报' },
  { key: 'publicQuery', label: '查询' },
  { key: 'publicExport', label: '导出' },
]

function actionKeys(item: FormPermission): ActionKey[] {
  return ACTIONS.filter((action) => {
    if (action.key === 'create') return item.hasCreate
    if (action.key === 'edit') return item.hasEdit !== false
    if (action.key === 'delete') return item.hasDelete !== false
    if (action.key === 'importExport') return item.hasImport !== false
    if (action.key === 'flow') return item.hasFlow !== false
    if (action.key === 'manageView') return item.hasManageView !== false
    return true
  }).map((action) => action.key)
}

function checkedCount(item: FormPermission): number {
  return actionKeys(item).filter((key) => item[key]).length
}

function guestCheckedCount(item: FormPermission): number {
  if (!item.publicAccess) return 0
  return 1 + PUBLIC_ACTIONS.filter((action) => item[action.key]).length
}

function guestTotal(_item: FormPermission): number {
  return 1 + PUBLIC_ACTIONS.length
}

function toggleKey(current: Set<string>, key: string) {
  const next = new Set(current)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  return next
}

export function PermissionTree({ value, disabled, variant = 'staff', onChange }: Props) {
  const guest = variant === 'guest'
  const [collapsedGroups, setCollapsedGroups] = useState<Set<string>>(new Set())
  const [collapsedModules, setCollapsedModules] = useState<Set<string>>(new Set())
  const patch = (module: FormPermission['module'], updater: (item: FormPermission) => FormPermission) => {
    onChange(value.map((item) => (item.module === module ? updater({ ...item }) : item)))
  }

  const toggleAction = (module: FormPermission['module'], key: ActionKey, checked: boolean) => {
    patch(module, (item) => {
      const next = { ...item, [key]: checked }
      if ((key === 'create' || key === 'edit' || key === 'delete' || key === 'importExport' || key === 'flow' || key === 'manageView') && checked) {
        next.view = true
      }
      if (key === 'view' && !checked) {
        next.create = false
        next.edit = false
        next.delete = false
        next.importExport = false
        next.flow = false
        next.manageView = false
        next.dataScope = 'ALL'
      }
      return next
    })
  }

  const toggleModule = (item: FormPermission, checked: boolean) => {
    patch(item.module, (current) => {
      if (guest) {
        return {
          ...current,
          publicAccess: checked,
          publicDisplay: checked,
          publicSubmit: checked,
          publicQuery: checked,
          publicExport: checked,
        }
      }
      const next = { ...current }
      for (const key of actionKeys(current)) {
        next[key] = checked
      }
      if (!checked) next.dataScope = 'ALL'
      return next
    })
  }

  const setScope = (module: FormPermission['module'], dataScope: DataScope) => {
    patch(module, (item) => ({ ...item, dataScope, view: true }))
  }

  const togglePublicAccess = (module: FormPermission['module'], checked: boolean) => {
    patch(module, (item) => ({
      ...item,
      publicAccess: checked,
      publicDisplay: checked ? item.publicDisplay : false,
      publicSubmit: checked ? item.publicSubmit : false,
      publicQuery: checked ? item.publicQuery : false,
      publicExport: checked ? item.publicExport : false,
    }))
  }

  const visible = guest ? value.filter((item) => item.hasPublicAccess) : value
  const byModule = new Map(visible.map((item) => [item.module, item]))
  const groupedModules = new Set(collectPermissionModules())
  const leftover = visible.filter(
    (item) => !groupedModules.has(item.module as FormModule) && !isRemovedSidebarModule(item.module),
  )
  const roots: PermissionGroupNode[] = leftover.length
    ? [...PERMISSION_GROUPS, { key: 'grp-other', label: '其它', modules: leftover.map((item) => item.module) }]
    : PERMISSION_GROUPS

  const modulesOf = (node: PermissionGroupNode) =>
    (node.modules ?? []).map((module) => byModule.get(module)).filter((item): item is FormPermission => Boolean(item))

  const nodeVisible = (node: PermissionGroupNode): boolean => {
    if (modulesOf(node).length) return true
    return (node.children ?? []).some(nodeVisible)
  }

  const renderModule = (item: FormPermission) => {
    const keys = actionKeys(item)
    const count = guest ? guestCheckedCount(item) : checkedCount(item)
    const total = guest ? guestTotal(item) : keys.length
    const analyticsOnly = !item.hasCreate && item.hasEdit === false
    const moduleCollapsed = collapsedModules.has(item.module)
    return (
      <div key={item.module} className="permission-module">
        <div className="permission-module-head">
          <button
            type="button"
            className="permission-module-fold"
            aria-expanded={!moduleCollapsed}
            aria-label={moduleCollapsed ? `展开${item.moduleLabel}` : `折叠${item.moduleLabel}`}
            onClick={() => setCollapsedModules((current) => toggleKey(current, item.module))}
          >
            <CaretRightOutlined className="permission-fold-icon" rotate={moduleCollapsed ? 0 : 90} />
          </button>
          <Checkbox
            checked={count === total && count > 0}
            indeterminate={count > 0 && count < total}
            disabled={disabled}
            onChange={(e) => toggleModule(item, e.target.checked)}
          >
            <Typography.Text strong>{item.moduleLabel}</Typography.Text>
            {analyticsOnly ? <Typography.Text type="secondary"> 仅查看</Typography.Text> : null}
          </Checkbox>
        </div>
        {moduleCollapsed ? null : (
          <div className="permission-actions">
            {guest ? (
              <>
                <div>
                  <Checkbox
                    checked={item.publicAccess}
                    disabled={disabled}
                    onChange={(e) => togglePublicAccess(item.module, e.target.checked)}
                  >
                    公开
                  </Checkbox>
                </div>
                {item.publicAccess
                  ? PUBLIC_ACTIONS.map((action) => (
                      <div key={action.key}>
                        <Checkbox
                          checked={item[action.key]}
                          disabled={disabled}
                          onChange={(e) => patch(item.module, (current) => ({ ...current, [action.key]: e.target.checked }))}
                        >
                          {action.label}
                        </Checkbox>
                      </div>
                    ))
                  : null}
              </>
            ) : (
              ACTIONS.filter((action) => {
                if (action.key === 'create') return item.hasCreate
                if (action.key === 'edit') return item.hasEdit !== false
                if (action.key === 'delete') return item.hasDelete !== false
                if (action.key === 'importExport') return item.hasImport !== false
                if (action.key === 'flow') return item.hasFlow !== false
                if (action.key === 'manageView') return item.hasManageView !== false
                return true
              }).map((action) => (
                <div key={action.key}>
                  <Checkbox
                    checked={item[action.key]}
                    disabled={disabled}
                    onChange={(e) => toggleAction(item.module, action.key, e.target.checked)}
                  >
                    {actionLabel(item.module, action.key, action.label)}
                  </Checkbox>
                  {action.key === 'view' && item.hasDataScope ? (
                    <div className="permission-scope">
                      <Radio.Group
                        value={item.view ? item.dataScope : undefined}
                        disabled={disabled || !item.view}
                        onChange={(e) => setScope(item.module, e.target.value)}
                      >
                        <Space wrap size={12}>
                          <Radio value="ALL">全量数据</Radio>
                          <Radio value="OWN">仅个人提交数据</Radio>
                        </Space>
                      </Radio.Group>
                    </div>
                  ) : null}
                </div>
              ))
            )}
          </div>
        )}
      </div>
    )
  }

  const renderGroup = (node: PermissionGroupNode) => {
    if (!nodeVisible(node)) return null
    const groupCollapsed = collapsedGroups.has(node.key)
    return (
      <div key={node.key} className="permission-group">
        <button
          type="button"
          className="permission-group-title"
          aria-expanded={!groupCollapsed}
          onClick={() => setCollapsedGroups((current) => toggleKey(current, node.key))}
        >
          <CaretRightOutlined className="permission-fold-icon" rotate={groupCollapsed ? 0 : 90} />
          {node.label}
        </button>
        {groupCollapsed ? null : (
          <>
            {modulesOf(node).map(renderModule)}
            {(node.children ?? []).map(renderGroup)}
          </>
        )}
      </div>
    )
  }

  return <div className="permission-tree">{roots.map(renderGroup)}</div>
}
