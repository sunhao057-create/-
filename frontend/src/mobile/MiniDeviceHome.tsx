import { useEffect, useState } from 'react'
import { App } from 'antd'
import { useNavigate } from 'react-router-dom'
import { miniDeviceApi } from '../api'
import { ApiError } from '../api/http'
import type { Device } from '../types'
import { greeting } from './workbench'
import { useAuth } from '../auth/AuthContext'

export function MiniDeviceHome() {
  const { message } = App.useApp()
  const { user } = useAuth()
  const navigate = useNavigate()
  const [companyName, setCompanyName] = useState('')
  const [devices, setDevices] = useState<Device[]>([])

  useEffect(() => {
    miniDeviceApi
      .home()
      .then((home) => {
        setCompanyName(home.companyName || '')
        setDevices(home.devices || [])
      })
      .catch((e) => message.error(e instanceof ApiError ? e.message : '加载设备失败'))
  }, [message])

  return (
    <>
      <section className="mini-hero">
        <div style={{ fontSize: 13, opacity: 0.9 }}>
          {greeting()}，{user?.displayName}
        </div>
        <h1>{companyName || '本公司设备'}</h1>
        <button type="button" className="mini-device-count" onClick={() => undefined}>
          <b>{devices.length}</b>
          <span>台设备</span>
        </button>
      </section>
      <section className="mini-section">
        <div className="mini-section-head">设备</div>
        {devices.length === 0 ? (
          <div className="mini-empty">{companyName ? '本公司还没有设备' : '未分配到公司，看不到设备'}</div>
        ) : null}
        {devices.map((device) => (
          <button type="button" className="mini-device" key={device.id} onClick={() => navigate(`/m/devices/${device.id}`)}>
            <strong>{device.name}</strong>
            <span>
              {device.code}
              {device.location ? ` · ${device.location}` : ''}
            </span>
            <em>{device.statusLabel}</em>
          </button>
        ))}
      </section>
    </>
  )
}
