import { Button, Form, Input, InputNumber, Select, Upload } from 'antd'
import { InboxOutlined, PlusOutlined } from '@ant-design/icons'
import type { UploadFile } from 'antd/es/upload/interface'
import type { TechSchemaField } from '../api'
import { DateInput } from '../ui/DateInput'
import { CustomerLookup, isCustomerLinkField } from '../ui/CustomerLookup'
import { SubformTable } from '../ui/SubformTable'

export function isWideField(field: TechSchemaField) {
  return (
    field.type === 'subtable' ||
    field.type === 'attachment' ||
    field.type === 'image' ||
    field.type === 'textarea'
  )
}

function toUploadList(value: unknown): UploadFile[] {
  if (!Array.isArray(value)) return []
  return value.map((item, index) => {
    const row = item as Record<string, unknown>
    const uid = String(row.uid ?? row.id ?? index)
    return {
      uid,
      name: String(row.name ?? row.originalName ?? '附件'),
      status: 'done' as const,
      url: row.url ? String(row.url) : undefined,
      originFileObj: row.originFileObj as UploadFile['originFileObj'],
      response: row,
    }
  })
}

export function QfFieldControl({
  field,
  users,
  fields,
  disabled,
  freeText,
  value,
  onChange,
}: {
  field: TechSchemaField
  users: { value: string; label: string }[]
  fields: { key: string; name: string }[]
  disabled?: boolean
  /** 客户主数据不在本表时，客户名称按文本填写。 */
  freeText?: boolean
  value?: unknown
  onChange?: (value: unknown) => void
}) {
  if (!freeText && isCustomerLinkField(field.name) && field.type !== 'member') {
    return (
      <CustomerLookup
        fields={fields}
        placeholder={`请选择${field.name}`}
        disabled={disabled}
        value={value as string | undefined}
        onChange={onChange as (next: string | undefined) => void}
      />
    )
  }
  if (field.type === 'member') {
    return (
      <Select
        allowClear
        showSearch
        disabled={disabled}
        optionFilterProp="label"
        options={users}
        placeholder={`请选择${field.name}`}
        value={value as string | undefined}
        onChange={onChange}
      />
    )
  }
  if (field.type === 'multiselect') {
    return (
      <Select
        mode="multiple"
        allowClear
        disabled={disabled}
        options={(field.options || []).map((opt) => ({ value: opt, label: opt }))}
        placeholder={`请选择${field.name}`}
        value={(value as string[]) || undefined}
        onChange={onChange}
      />
    )
  }
  if (field.options && field.options.length > 0 && field.type !== 'subtable') {
    return (
      <Select
        allowClear
        disabled={disabled}
        options={field.options.map((opt) => ({ value: opt, label: opt }))}
        placeholder={`请选择${field.name}`}
        value={value as string | undefined}
        onChange={onChange}
      />
    )
  }
  if (field.type === 'attachment' || field.type === 'image') {
    const picture = field.type === 'image' || field.name.includes('照片') || field.name.includes('图片')
    return (
      <Upload
        multiple
        disabled={disabled}
        listType={picture ? 'picture-card' : 'text'}
        fileList={toUploadList(value)}
        beforeUpload={() => false}
        onChange={({ fileList }) => {
          onChange?.(
            fileList.map((file) => ({
              uid: file.uid,
              id: (file.response as { id?: number } | undefined)?.id,
              name: file.name,
              originalName: file.name,
              url: file.url,
              originFileObj: file.originFileObj,
            })),
          )
        }}
      >
        {picture ? (
          <div>
            <PlusOutlined />
            <div style={{ marginTop: 4 }}>上传</div>
          </div>
        ) : (
          <Button icon={<InboxOutlined />} disabled={disabled}>
            上传附件
          </Button>
        )}
      </Upload>
    )
  }
  switch (field.type) {
    case 'textarea':
      return <Input.TextArea rows={3} disabled={disabled} placeholder={`请输入${field.name}`} value={value as string} onChange={(e) => onChange?.(e.target.value)} />
    case 'number':
      return (
        <InputNumber
          style={{ width: '100%' }}
          disabled={disabled}
          placeholder={`请输入${field.name}`}
          value={value as number}
          onChange={onChange}
        />
      )
    case 'date':
      return <DateInput disabled={disabled} value={value as string} onChange={(e) => onChange?.(e.target.value)} />
    case 'datetime':
      return (
        <DateInput
          type="datetime-local"
          disabled={disabled}
          value={value as string}
          onChange={(e) => onChange?.(e.target.value)}
        />
      )
    default:
      return <Input disabled={disabled} placeholder={`请输入${field.name}`} value={value as string} onChange={(e) => onChange?.(e.target.value)} />
  }
}

export function QfSubtable({
  field,
  users,
  fields,
  disabled,
}: {
  field: TechSchemaField
  users: { value: string; label: string }[]
  fields: { key: string; name: string }[]
  disabled?: boolean
}) {
  return (
    <Form.List name={field.key}>
      {(rows, { add, remove }) => (
        <>
          <SubformTable
            fields={rows}
            onRemove={disabled ? undefined : (name) => remove(name)}
            columns={field.subfields.map((sub) => ({
              title: sub.name + (sub.required ? ' *' : ''),
              width: 160,
              render: (row) => (
                <Form.Item name={[row.name, sub.key]} rules={sub.required ? [{ required: true, message: `请填写${sub.name}` }] : undefined} style={{ marginBottom: 0 }}>
                  <QfFieldControl field={sub} users={users} fields={fields} disabled={disabled} />
                </Form.Item>
              ),
            }))}
          />
          {disabled ? null : (
            <Button type="dashed" block icon={<PlusOutlined />} onClick={() => add({})} style={{ marginTop: 8 }}>
              添加一行
            </Button>
          )}
        </>
      )}
    </Form.List>
  )
}
