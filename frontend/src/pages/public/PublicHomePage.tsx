import { Button, Card, Col, Row, Typography } from 'antd'
import { ShopOutlined, ToolOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import type { ReactNode } from 'react'
import { usePublicConfig } from '../../layouts/PublicLayout'
import type { PublicModuleConfig, PublicModuleFlags } from '../../api/public'

function moduleIcon(module: string) {
  if (module === 'CUSTOMER') return <ShopOutlined />
  if (module.startsWith('TECH_') || module.startsWith('DEVICE')) return <ToolOutlined />
  return <ShopOutlined />
}

function ModuleCard({
  title,
  icon,
  path,
  flags,
}: {
  title: string
  icon: ReactNode
  path: string
  flags: PublicModuleFlags
}) {
  const navigate = useNavigate()
  if (!flags.publicAccess) return null
  return (
    <Col xs={24} md={8}>
      <Card title={<span>{icon} {title}</span>}>
        <Typography.Paragraph type="secondary" style={{ minHeight: 44 }}>
          {flags.display ? '可查看已公开内容。' : flags.query ? '可按名称或编码查询。' : '可填报，提交后需后台审核。'}
        </Typography.Paragraph>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {flags.display || flags.query ? (
            <Button type="primary" onClick={() => navigate(flags.display ? path : `${path}/query`)}>
              {flags.display ? '查看' : '查询'}
            </Button>
          ) : null}
          {flags.submit ? <Button onClick={() => navigate(`${path}/new`)}>填报</Button> : null}
        </div>
      </Card>
    </Col>
  )
}

export function PublicHomePage() {
  const config = usePublicConfig()
  const cards: PublicModuleConfig[] = config.modules?.length
    ? config.modules
    : []
  const open = cards.some((item) => item.flags.publicAccess)
  if (!open) {
    return (
      <Card>
        <Typography.Title level={4} style={{ marginTop: 0 }}>
          公开办理
        </Typography.Title>
        <Typography.Paragraph type="secondary">暂未开放公开办理</Typography.Paragraph>
      </Card>
    )
  }
  return (
    <Row gutter={[16, 16]}>
      {cards
        .filter((item) => item.flags.publicAccess)
        .map((item) => (
          <ModuleCard
            key={item.module}
            title={item.moduleLabel}
            icon={moduleIcon(item.module)}
            path={item.path}
            flags={item.flags}
          />
        ))}
    </Row>
  )
}
