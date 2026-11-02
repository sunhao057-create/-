import { useEffect, useState } from 'react'
import { App, Button, Card, Checkbox, Col, Divider, Form, Input, Row, Select, Space } from 'antd'
import { ApiError } from '../../api/http'
import {
  publicApi,
  type PublicBankAccount,
  type PublicContact,
  type PublicCustomer,
  type PublicPartySubmit,
} from '../../api/public'
import { BankAccountLineList, ContactLineList } from '../../ui/PartyLineLists'

const LEVELS = ['一般重要', '重要', '非常重要']

export function PublicPartyForm({
  mode,
  detail,
  onCancel,
}: {
  mode: 'create' | 'view'
  detail?: PublicCustomer | null
  onCancel: () => void
}) {
  const { message } = App.useApp()
  const view = mode === 'view'
  const [form] = Form.useForm()
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!detail) {
      form.resetFields()
      form.setFieldsValue({ level: '一般重要', contacts: [], bankAccounts: [] })
      return
    }
    form.setFieldsValue({
      shortName: detail.shortName,
      companyName: detail.companyName,
      code: detail.code,
      level: detail.level,
      contacts: detail.contacts ?? [],
      bankAccounts: detail.bankAccounts ?? [],
      isPublic: true,
    })
  }, [detail, form])

  return (
    <Card title={view ? '客户详情' : '新建客户'} styles={{ body: { maxWidth: 920 } }}>
      <Form
        form={form}
        layout="vertical"
        disabled={view}
        onFinish={async (values) => {
          setSaving(true)
          try {
            const payload: PublicPartySubmit = {
              shortName: values.shortName,
              companyName: values.companyName,
              code: values.code,
              level: values.level,
              contacts: ((values.contacts ?? []) as PublicContact[]).map((item) => ({
                ...item,
                primaryContact: !!item.primaryContact,
              })),
              bankAccounts: ((values.bankAccounts ?? []) as PublicBankAccount[]).map((item) => ({
                ...item,
                primaryAccount: !!item.primaryAccount,
              })),
            }
            await publicApi.submitCustomer(payload)
            message.success('已提交，待后台审核后公开')
            onCancel()
          } catch (e) {
            message.error(e instanceof ApiError ? e.message : '提交失败')
          } finally {
            setSaving(false)
          }
        }}
      >
        <Row gutter={12}>
          <Col xs={24} md={12}>
            <Form.Item name="shortName" label="客户简称" rules={[{ required: true, message: '请填写简称' }]}>
              <Input placeholder="例如 苏州冠礼" />
            </Form.Item>
          </Col>
          <Col xs={24} md={12}>
            <Form.Item name="code" label="客户编码" rules={[{ required: true, message: '请填写编码' }]}>
              <Input placeholder="例如 133" />
            </Form.Item>
          </Col>
          <Col xs={24} md={16}>
            <Form.Item name="companyName" label="公司名称" rules={[{ required: true, message: '请填写公司名称' }]}>
              <Input placeholder="营业执照上的公司全称" />
            </Form.Item>
          </Col>
          <Col xs={24} md={8}>
            <Form.Item name="level" label="客户等级" rules={[{ required: true, message: '请选择等级' }]}>
              <Select options={LEVELS.map((value) => ({ value, label: value }))} />
            </Form.Item>
          </Col>
          {view ? (
            <Col xs={24}>
              <Form.Item name="isPublic" valuePropName="checked">
                <Checkbox>允许公开</Checkbox>
              </Form.Item>
            </Col>
          ) : null}
        </Row>

        <Divider>联系人</Divider>
        <ContactLineList editable={!view} />

        <Divider>银行账户</Divider>
        <BankAccountLineList editable={!view} />

        <Space style={{ marginTop: 16 }}>
          <Button htmlType="button" disabled={false} onClick={onCancel}>
            {view ? '返回列表' : '取消'}
          </Button>
          {view ? null : (
            <Button type="primary" htmlType="submit" loading={saving}>
              提交
            </Button>
          )}
        </Space>
      </Form>
    </Card>
  )
}
