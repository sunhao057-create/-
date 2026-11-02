import { http, unwrap, ApiError } from '../api/http'
import type { FormModule } from '../types'

export type ImportResult = {
  created: number
  failed: number
  errors: string[]
}

const TEMPLATE_NAMES: Record<string, string> = {
  PROJECT: '项目导入模板.csv',
  PAYMENT: '付款申请导入模板.csv',
  CUSTOMER: '客户导入模板.csv',
  SUPPLIER: '供应商导入模板.csv',
  USER: '账号导入模板.csv',
  ROLE: '角色导入模板.csv',
}

export async function downloadImportTemplate(module: FormModule) {
  const res = await http.get(`/api/imports/${module}/template`, { responseType: 'blob' })
  const blob = res.data as Blob
  if (blob.type.includes('application/json')) {
    const body = JSON.parse(await blob.text()) as { code?: number; message?: string }
    throw new ApiError(body.code || 500, body.message || '模板下载失败')
  }
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = TEMPLATE_NAMES[module] || '导入模板.csv'
  link.click()
  URL.revokeObjectURL(url)
}

export function importCsv(module: FormModule, file: File) {
  const form = new FormData()
  form.append('file', file)
  return unwrap<ImportResult>(http.post(`/api/imports/${module}`, form, { timeout: 60000 }))
}
