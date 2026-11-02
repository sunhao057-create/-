import { type ReactNode } from 'react'
import { Button, Popconfirm, Space } from 'antd'

type Props = {
  canView?: boolean
  canEdit?: boolean
  canDelete?: boolean
  viewLabel?: string
  editLabel?: string
  deleteTitle: string
  onView?: () => void
  onEdit?: () => void
  onDelete?: () => void | Promise<void>
  extra?: ReactNode
}

export function RowActions({
  canView = false,
  canEdit = false,
  canDelete = false,
  viewLabel = '查看',
  editLabel = '编辑',
  deleteTitle,
  onView,
  onEdit,
  onDelete,
  extra,
}: Props) {
  const viewFn = onView ?? (!canEdit ? onEdit : undefined)
  const showView = canView && !!viewFn
  const showEdit = canEdit && !!onEdit
  if (!showView && !showEdit && !canDelete && !extra) return null
  return (
    <Space wrap size={0} onClick={(event) => event.stopPropagation()}>
      {showView ? (
        <Button size="small" type="link" onClick={viewFn}>
          {viewLabel}
        </Button>
      ) : null}
      {showEdit ? (
        <Button size="small" type="link" onClick={onEdit}>
          {editLabel}
        </Button>
      ) : null}
      {extra}
      {canDelete && onDelete ? (
        <Popconfirm title={deleteTitle} onConfirm={() => void onDelete()}>
          <Button size="small" type="link" danger>
            删除
          </Button>
        </Popconfirm>
      ) : null}
    </Space>
  )
}
