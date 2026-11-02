import { Input } from 'antd'
import type { InputProps } from 'antd'
import type { MouseEvent } from 'react'

const PICKER_TYPES = new Set(['date', 'datetime-local', 'time', 'month', 'week'])

function openPicker(event: MouseEvent<HTMLElement>) {
  const node = event.currentTarget
  const input = (node instanceof HTMLInputElement ? node : node.querySelector('input')) as HTMLInputElement | null
  if (!input || input.disabled || input.readOnly) return
  try {
    input.showPicker?.()
  } catch {
    /* 非安全上下文或不支持时仍可由 CSS 全框点击打开 */
  }
}

/** 日期/时间框：点框内任意位置打开选择器。不要再用裸的 Input type="date"。 */
export function DateInput({ type = 'date', className, onClick, ...props }: InputProps) {
  const htmlType = type && PICKER_TYPES.has(String(type)) ? type : 'date'
  return (
    <Input
      {...props}
      type={htmlType}
      className={['date-input', className].filter(Boolean).join(' ')}
      onClick={(event) => {
        onClick?.(event)
        openPicker(event)
      }}
    />
  )
}
