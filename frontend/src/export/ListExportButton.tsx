import { useState, type Key } from 'react'
import { App, Button } from 'antd'
import { ExportOutlined } from '@ant-design/icons'
import { listExportApi } from '../api'
import { publicApi } from '../api/public'
import { ApiError } from '../api/http'
import type { FormModule } from '../types'
import { saveExportedBlob } from '../utils/download'
import { useAuth } from '../auth/AuthContext'
import { can } from '../auth/permissions'

export const LIST_EXPORT_HINT = '勾选后导出选中数据；未勾选则导出当前筛选结果。导出为 Excel。'

export function useListSelection() {
  const [selectedRowKeys, setSelectedRowKeys] = useState<Key[]>([])
  return {
    selectedRowKeys,
    rowSelection: {
      selectedRowKeys,
      onChange: setSelectedRowKeys,
      preserveSelectedRowKeys: true,
    },
  }
}

export function ListExportButton({
  module,
  selectedRowKeys,
  ids,
  query,
  keyword,
  fallbackName = '导出.xlsx',
  idleLabel = '导出台账',
  visible = true,
}: {
  module: FormModule
  selectedRowKeys?: Key[]
  ids?: Key[]
  query?: {
    keyword?: string
    publicFilter?: string
    status?: string
    customerId?: number
  }
  keyword?: string
  fallbackName?: string
  idleLabel?: string
  visible?: boolean
}) {
  const { message } = App.useApp()
  const { user } = useAuth()
  const [exporting, setExporting] = useState(false)
  if (!visible || !can(user, module, 'importExport')) return null
  const keys = selectedRowKeys ?? ids ?? []
  const exportIds = keys.map((key) => Number(key)).filter((id) => Number.isFinite(id))
  return (
    <Button
      icon={<ExportOutlined />}
      loading={exporting}
      onClick={async () => {
        setExporting(true)
        try {
          const { blob, disposition } = await listExportApi.workbook({
            module,
            ...(exportIds.length ? { ids: exportIds } : {}),
            ...query,
            ...(keyword ? { keyword } : {}),
          })
          await saveExportedBlob(blob, disposition, fallbackName)
          message.success(exportIds.length ? `已导出 ${exportIds.length} 条` : '已导出')
        } catch (e) {
          message.error(e instanceof ApiError ? e.message : '导出失败')
        } finally {
          setExporting(false)
        }
      }}
    >
      {exportIds.length ? `导出已选 (${exportIds.length})` : idleLabel}
    </Button>
  )
}

export function PublicListExportButton({
  kind,
  selectedRowKeys,
  keyword,
  fallbackName,
  visible = true,
}: {
  kind: string
  selectedRowKeys: Key[]
  keyword?: string
  fallbackName: string
  visible?: boolean
}) {
  const { message } = App.useApp()
  const [exporting, setExporting] = useState(false)
  if (!visible) return null
  const ids = (selectedRowKeys ?? []).map((key) => Number(key)).filter((id) => Number.isFinite(id))
  return (
    <Button
      icon={<ExportOutlined />}
      loading={exporting}
      onClick={async () => {
        setExporting(true)
        try {
          const { blob, disposition } = await publicApi.exportWorkbook({
            kind,
            ...(ids.length ? { ids } : {}),
            keyword,
          })
          await saveExportedBlob(blob, disposition, fallbackName)
          message.success(ids.length ? `已导出 ${ids.length} 条` : '已导出')
        } catch (e) {
          message.error(e instanceof ApiError ? e.message : '导出失败')
        } finally {
          setExporting(false)
        }
      }}
    >
      {ids.length ? `导出已选 (${ids.length})` : '导出台账'}
    </Button>
  )
}
