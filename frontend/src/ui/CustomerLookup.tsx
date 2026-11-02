import { useEffect, useMemo, useState } from 'react'
import { Form, Input, Select } from 'antd'
import { lookupApi } from '../api'

export type CustomerLinkOption = {
  id: number
  companyName: string
  shortName: string
  code?: string
}

export type NamedField = { key: string; name: string }

function normalizeFieldName(name?: string | null) {
  return String(name || '')
    .replace(/[（(][^）)]*[）)]/g, '')
    .replace(/\s+/g, '')
}

export function isCustomerLinkField(name?: string | null) {
  const n = normalizeFieldName(name)
  if (!n || n.includes('缩写') || n.includes('编号') || n.includes('编码') || n.includes('取消')) return false
  return n === '客户名称' || n === '客户单位' || n === '客户单位名称' || n.endsWith('客户名称')
}

export function isCustomerAbbrField(name?: string | null) {
  const n = normalizeFieldName(name)
  return n.includes('客户') && n.includes('缩写')
}

export function customerFillValues(fields: NamedField[], customer: CustomerLinkOption) {
  const patch: Record<string, unknown> = { _customerId: customer.id }
  for (const field of fields) {
    if (isCustomerLinkField(field.name)) patch[field.key] = customer.companyName
    if (isCustomerAbbrField(field.name)) patch[field.key] = customer.shortName
  }
  return patch
}

export function CustomerLookup({
  value,
  onChange,
  fields,
  loadCustomers,
  placeholder,
  disabled,
}: {
  value?: string | null
  onChange?: (value: string | undefined) => void
  fields?: NamedField[]
  loadCustomers?: () => Promise<CustomerLinkOption[]>
  placeholder?: string
  disabled?: boolean
}) {
  const form = Form.useFormInstance()
  const [items, setItems] = useState<CustomerLinkOption[]>([])
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    const load = loadCustomers || (() => lookupApi.customers())
    void load()
      .then((rows) => {
        setItems(rows || [])
        setFailed(false)
      })
      .catch(() => {
        setItems([])
        setFailed(true)
      })
  }, [loadCustomers])

  if (failed && items.length === 0) {
    return <Input disabled={disabled} placeholder={placeholder || '请输入客户名称'} value={value ?? undefined} onChange={(e) => onChange?.(e.target.value)} />
  }

  const options = useMemo(() => {
    const seen = new Set<string>()
    const list = items.map((item) => {
      seen.add(item.companyName)
      const extra = [item.shortName, item.code].filter(Boolean).join(' / ')
      return {
        value: item.companyName,
        label: extra ? `${item.companyName}（${extra}）` : item.companyName,
        customer: item,
      }
    })
    const current = String(value || '').trim()
    if (current && !seen.has(current)) {
      list.unshift({ value: current, label: current, customer: { id: 0, companyName: current, shortName: '' } })
    }
    return list
  }, [items, value])

  return (
    <Select
      allowClear
      showSearch
      disabled={disabled}
      placeholder={placeholder || '请选择客户'}
      optionFilterProp="label"
      value={value ? String(value) : undefined}
      options={options}
      onChange={(next, option) => {
        onChange?.(next)
        const picked = !Array.isArray(option) && option && 'customer' in option ? option.customer : undefined
        if (fields && picked && picked.id) {
          form.setFieldsValue(customerFillValues(fields, picked))
        }
        if (!next) {
          form.setFieldsValue({ _customerId: undefined })
        }
      }}
    />
  )
}
