import { Alert, App, Button, Modal, Typography, Upload } from 'antd'
import { InboxOutlined } from '@ant-design/icons'
import { useEffect, useState } from 'react'
import type { FormModule } from '../types'
import { ApiError } from '../api/http'
import { downloadImportTemplate, importCsv } from './client'

type Props = {
  open: boolean
  module: FormModule
  title: string
  onClose: () => void
  onImported: () => void
}

export function ImportModal({ open, module, title, onClose, onImported }: Props) {
  const { message } = App.useApp()
  const [file, setFile] = useState<File | null>(null)
  const [uploading, setUploading] = useState(false)
  const [errors, setErrors] = useState<string[]>([])

  useEffect(() => {
    if (open) {
      setFile(null)
      setErrors([])
    }
  }, [open])

  return (
    <Modal
      title={title}
      open={open}
      okText="开始导入"
      cancelText="取消"
      confirmLoading={uploading}
      okButtonProps={{ disabled: !file }}
      onCancel={onClose}
      onOk={async () => {
        if (!file) return
        setUploading(true)
        try {
          const result = await importCsv(module, file)
          if (result.failed === 0) {
            message.success(`已导入 ${result.created} 条`)
            onClose()
            onImported()
            return
          }
          setErrors(result.errors)
          if (result.created > 0) {
            message.warning(`成功 ${result.created} 条，失败 ${result.failed} 条`)
            onImported()
          } else {
            message.error(`导入失败 ${result.failed} 条，请按模板修正后重试`)
          }
        } catch (e) {
          message.error(e instanceof ApiError ? e.message : '导入失败')
        } finally {
          setUploading(false)
        }
      }}
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 12 }}
        message="请先下载模板。第1行是填写提示，第2行是表头请勿改，从第3行开始填数据。如用表格软件编辑，请另存为逗号分隔文本后再上传。"
      />
      <Button
        style={{ marginBottom: 12 }}
        onClick={async () => {
          try {
            await downloadImportTemplate(module)
          } catch (e) {
            message.error(e instanceof ApiError ? e.message : '模板下载失败')
          }
        }}
      >
        下载模板
      </Button>
      <Upload.Dragger
        accept=".csv,.txt"
        maxCount={1}
        fileList={file ? [{ uid: '1', name: file.name, status: 'done' }] : []}
        beforeUpload={(next) => {
          setFile(next)
          setErrors([])
          return false
        }}
        onRemove={() => {
          setFile(null)
          return true
        }}
      >
        <p className="ant-upload-drag-icon">
          <InboxOutlined />
        </p>
        <p className="ant-upload-text">点击或拖拽模板文件到这里</p>
        <p className="ant-upload-hint">仅支持下载的模板格式</p>
      </Upload.Dragger>
      {errors.length > 0 ? (
        <div className="import-errors">
          <Typography.Text type="danger">失败明细</Typography.Text>
          <ul>
            {errors.slice(0, 20).map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
          {errors.length > 20 ? <Typography.Text type="secondary">仅显示前 20 条</Typography.Text> : null}
        </div>
      ) : null}
    </Modal>
  )
}
