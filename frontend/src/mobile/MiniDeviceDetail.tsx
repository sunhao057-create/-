import { useEffect, useState } from 'react'
import { App, Button, Input } from 'antd'
import { useNavigate, useParams } from 'react-router-dom'
import { miniDeviceApi } from '../api'
import { ApiError } from '../api/http'
import type { Device, DeviceWorkOrder } from '../types'

const KIND_LABEL: Record<DeviceWorkOrder['kind'], string> = {
  REPAIR: '维修',
  MOVE: '搬迁',
  SCRAP: '报废',
}

export function MiniDeviceDetail() {
  const { id } = useParams()
  const deviceId = Number(id)
  const { message } = App.useApp()
  const navigate = useNavigate()
  const [device, setDevice] = useState<Device | null>(null)
  const [orders, setOrders] = useState<DeviceWorkOrder[]>([])
  const [reason, setReason] = useState('')
  const [saving, setSaving] = useState(false)

  const load = () => {
    if (!deviceId) return
    miniDeviceApi
      .detail(deviceId)
      .then((detail) => {
        setDevice(detail.device)
        setOrders(detail.orders || [])
      })
      .catch((e) => message.error(e instanceof ApiError ? e.message : '加载失败'))
  }

  useEffect(() => {
    load()
  }, [deviceId])

  const submit = async () => {
    if (!reason.trim()) {
      message.warning('请填写维修原因')
      return
    }
    setSaving(true)
    try {
      await miniDeviceApi.repair(deviceId, reason.trim())
      message.success('维修申请已提交')
      setReason('')
      load()
    } catch (e) {
      message.error(e instanceof ApiError ? e.message : '提交失败')
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      <button type="button" className="mini-back" onClick={() => navigate('/m')}>
        返回设备
      </button>
      {device ? (
        <section className="mini-device-card">
          <h2>{device.name}</h2>
          <p>
            {device.code}
            {device.model ? ` · ${device.model}` : ''}
          </p>
          <p>
            {device.departmentName}
            {device.location ? ` · ${device.location}` : ''}
          </p>
          <em>{device.statusLabel}</em>
        </section>
      ) : null}
      {device?.status === 'IN_USE' ? (
        <section className="mini-section">
          <div className="mini-section-head">维修申请</div>
          <Input.TextArea
            rows={3}
            maxLength={500}
            value={reason}
            placeholder="请填写维修原因"
            onChange={(e) => setReason(e.target.value)}
          />
          <Button type="primary" block loading={saving} style={{ marginTop: 12 }} onClick={() => void submit()}>
            提交维修
          </Button>
        </section>
      ) : (
        <section className="mini-section">
          <div className="mini-empty">当前状态不能发起维修</div>
        </section>
      )}
      <section className="mini-section">
        <div className="mini-section-head">处理记录</div>
        {orders.length === 0 ? <div className="mini-empty">还没有处理记录</div> : null}
        {orders.map((order) => (
          <div className="mini-order" key={order.id}>
            <strong>
              {KIND_LABEL[order.kind]} · {order.code}
            </strong>
            <p>{order.reason}</p>
            <span>
              {order.applyDate}
              {order.workflowStatusLabel ? ` · ${order.workflowStatusLabel}` : ''}
              {order.applied ? ' · 已办结' : ' · 处理中'}
            </span>
          </div>
        ))}
      </section>
    </>
  )
}
