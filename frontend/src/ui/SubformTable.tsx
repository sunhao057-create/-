import { MinusCircleOutlined } from '@ant-design/icons'
import { Button, Empty, Grid, Table } from 'antd'
import type { ReactNode } from 'react'
import './SubformTable.css'

export type SubformField = { name: number; key: number }

export type SubformColumn<T extends SubformField = SubformField> = {
  title: string
  width?: number
  /** 较宽屏下占半列；窄屏始终整行 */
  narrow?: boolean
  render: (field: T) => ReactNode
}

export function SubformTable<T extends SubformField>({
  fields,
  columns,
  onRemove,
  showRemove,
}: {
  fields: T[]
  columns: SubformColumn<T>[]
  onRemove?: (name: number) => void
  showRemove?: (field: T) => boolean
}) {
  const screens = Grid.useBreakpoint()
  const isMobile = screens.md !== true

  if (isMobile) {
    if (!fields.length) {
      return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无数据" style={{ marginBottom: 12 }} />
    }
    return (
      <div className="subform-stack">
        {fields.map((field, index) => (
          <div className="subform-card" key={field.key}>
            <div className="subform-card-bar">
              <span>第 {index + 1} 行</span>
              {onRemove && showRemove?.(field) !== false ? (
                <Button type="link" danger size="small" onClick={() => onRemove(field.name)}>
                  删除
                </Button>
              ) : null}
            </div>
            <div className="subform-card-grid">
              {columns.map((col) => (
                <div key={col.title} className={col.narrow ? 'subform-card-cell' : 'subform-card-cell subform-card-cell--wide'}>
                  <div className="subform-card-label">{col.title}</div>
                  {col.render(field)}
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
    )
  }

  return (
    <div className="subform-table-wrap">
      <Table<T>
        className="subform-table"
        size="small"
        pagination={false}
        dataSource={fields}
        rowKey="key"
        scroll={{ x: 'max-content' }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无数据" /> }}
        columns={[
          ...columns.map((col) => ({
            title: col.title,
            width: col.width,
            render: (_: unknown, field: T) => col.render(field),
          })),
          ...(onRemove
            ? [
                {
                  title: '',
                  width: 48,
                  render: (_: unknown, field: T) =>
                    showRemove?.(field) === false ? null : <MinusCircleOutlined onClick={() => onRemove(field.name)} />,
                },
              ]
            : []),
        ]}
      />
    </div>
  )
}
