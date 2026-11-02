import { useEffect, useMemo, useState } from 'react'
import { App, AutoComplete, Button, Card, Form, Input, InputNumber, Space, Switch, Upload } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { useNavigate, useParams } from 'react-router-dom'
import { portalApi } from '../../api'
import { ApiError } from '../../api/http'
import { useFormOverlay, useFormOverlayFooter } from '../../ui/CreateDrawer'
import { PORTAL_META, type PortalKind } from './portalMeta'

export function PortalFormPage({ kind }: { kind: PortalKind }) {
  const meta = PORTAL_META[kind]
  const { message } = App.useApp()
  const navigate = useNavigate()
  const { id } = useParams()
  const editing = id != null && id !== 'new' && Number(id) > 0
  const [form] = Form.useForm()
  const [saving, setSaving] = useState(false)
  const [cover, setCover] = useState<string>()
  const overlay = useFormOverlay()

  useEffect(() => {
    if (!editing) {
      form.setFieldsValue({ enabled: true, sortOrder: 0, category: meta.categories[0] })
      return
    }
    portalApi
      .list(kind)
      .then((rows) => {
        const row = rows.find((item) => item.id === Number(id))
        if (!row) {
          message.error('内容不存在')
          return
        }
        form.setFieldsValue(row)
        setCover(row.coverUrl || undefined)
      })
      .catch((e) => message.error(e instanceof ApiError ? e.message : '加载失败'))
  }, [editing, id, kind, form, message, meta.categories])

  const save = async () => {
    const values = await form.validateFields()
    const coverUrl = values.coverUrl || cover || null
    if (!coverUrl) {
      throw new ApiError(400, '请先上传图片')
    }
    const payload = { ...values, kind, coverUrl }
    if (editing) await portalApi.update(kind, Number(id), payload)
    else await portalApi.create(kind, payload)
    message.success('已保存')
    if (overlay?.active) overlay.close()
    else navigate(meta.path)
  }

  const footer = useMemo(() => {
    if (!overlay?.active) return null
    return (
      <Space>
        <Button onClick={overlay.close}>取消</Button>
        <Button type="primary" loading={saving} onClick={() => void save().catch((e) => message.error(e instanceof ApiError ? e.message : '保存失败')).finally(() => setSaving(false))}>
          保存
        </Button>
      </Space>
    )
  }, [overlay, saving, message, cover, form, kind, id, editing])
  useFormOverlayFooter(footer)

  const body = (
    <Form form={form} layout="vertical">
      <Form.Item name="title" label="标题" rules={[{ required: true, message: '请填写标题' }]}>
        <Input maxLength={120} placeholder={kind === 'MINI_BANNER' ? '轮播标题，例如 报修' : '标题'} />
      </Form.Item>
      {meta.categories.length > 0 ? (
        <Form.Item
          name="category"
          label="分类"
          extra={kind === 'MINI_SERVICE' ? '服务中心按标题显示。分类含「承诺」会出现在行业标准。填「顶部横幅」会出现在页面最上方。也可以自己写新分类。' : '可以改成自己的分类名称。'}
          rules={[{ required: true, message: '请填写分类' }]}
        >
          <AutoComplete options={meta.categories.map((item) => ({ value: item }))} placeholder="选择或输入分类" />
        </Form.Item>
      ) : null}
      <Form.Item name="coverUrl" hidden>
        <Input />
      </Form.Item>
      <Form.Item label={meta.coverLabel} required>
        <Upload
          accept="image/png,image/jpeg,image/webp"
          showUploadList={false}
          customRequest={async (options) => {
            try {
              const url = await portalApi.upload(kind, options.file as File)
              form.setFieldValue('coverUrl', url)
              setCover(url)
              options.onSuccess?.(url)
            } catch (e) {
              message.error(e instanceof ApiError ? e.message : '上传失败')
              options.onError?.(e as Error)
            }
          }}
        >
          {cover ? (
            <img src={cover} alt="" style={{ width: 220, height: 120, objectFit: 'cover', borderRadius: 8 }} />
          ) : (
            <Button icon={<PlusOutlined />}>上传图片</Button>
          )}
        </Upload>
      </Form.Item>
      <Form.Item name="summary" label="摘要">
        <Input maxLength={300} />
      </Form.Item>
      {kind === 'MINI_BANNER' ? null : (
        <Form.Item name="body" label="正文">
          <Input.TextArea rows={8} placeholder="填写要在小程序里展示的内容" />
        </Form.Item>
      )}
      <Form.Item name="sortOrder" label="排序">
        <InputNumber min={0} />
      </Form.Item>
      <Form.Item name="enabled" label="在小程序显示" valuePropName="checked">
        <Switch />
      </Form.Item>
    </Form>
  )

  if (overlay?.active) return body
  return (
    <Card title={editing ? meta.title : `新建${meta.title}`} extra={<Button onClick={() => navigate(meta.path)}>返回</Button>}>
      {body}
      <Button type="primary" onClick={() => void save().catch((e) => message.error(e instanceof ApiError ? e.message : '保存失败'))}>
        保存
      </Button>
    </Card>
  )
}
