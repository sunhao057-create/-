import { ApiError } from '../api/http'

export function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

export function filenameFromDisposition(header: string, fallback: string) {
  const utf = header.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf?.[1]) return decodeURIComponent(utf[1])
  const plain = header.match(/filename="?([^";]+)"?/i)
  return plain?.[1] || fallback
}

export async function saveExportedBlob(blob: Blob, disposition: string, fallback: string) {
  if (blob.type.includes('application/json')) {
    const body = JSON.parse(await blob.text()) as { code?: number; message?: string }
    throw new ApiError(body.code || 500, body.message || '导出失败')
  }
  saveBlob(blob, filenameFromDisposition(disposition, fallback))
}
