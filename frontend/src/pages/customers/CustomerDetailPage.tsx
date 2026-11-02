import { useEffect, useState } from 'react'
import { App, Button, Card, Descriptions } from 'antd'
import { useNavigate, useParams } from 'react-router-dom'
import { customerApi } from '../../api'
import { ApiError } from '../../api/http'
import type { Customer } from '../../types'

export function CustomerDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { message } = App.useApp()
  const customerId = Number(id)
  const [customer, setCustomer] = useState<Customer | null>(null)

  useEffect(() => {
    if (!Number.isFinite(customerId) || customerId <= 0) return
    customerApi
      .get(customerId)
      .then(setCustomer)
      .catch((e) => message.error(e instanceof ApiError ? e.message : '加载失败'))
  }, [customerId, message])

  if (!customer) {
    return <Card>加载中…</Card>
  }

  return (
    <Card
      title={customer.shortName}
      extra={
        <Button type="link" onClick={() => navigate('/customers')}>
          返回客户列表
        </Button>
      }
    >
      <Descriptions bordered size="middle" column={{ xs: 1, md: 2 }}>
        <Descriptions.Item label="公司名称">{customer.companyName}</Descriptions.Item>
        <Descriptions.Item label="客户编码">{customer.code}</Descriptions.Item>
        <Descriptions.Item label="等级">{customer.level}</Descriptions.Item>
        <Descriptions.Item label="公开">{customer.isPublic ? '已公开' : '未公开'}</Descriptions.Item>
      </Descriptions>
    </Card>
  )
}
