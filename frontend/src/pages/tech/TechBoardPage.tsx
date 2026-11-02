import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { techApi, type TechSchema } from '../../api'
import type { TechRecord } from '../../types'

const BOARDS = {
  orders: {
    title: '工单管理',
    sections: [
      { slug: 'service-report', title: '工单报表' },
      { slug: 'multi-report', title: '多次维修工单' },
    ],
  },
  maint: {
    title: '设备维保记录',
    sections: [{ slug: 'maint-basic', title: '维保记录' }],
  },
} as const

function cellText(value: unknown): string {
  if (value == null || value === '') return '—'
  if (Array.isArray(value)) return value.map(cellText).filter((item) => item !== '—').join('、') || '—'
  if (typeof value === 'object') {
    const obj = value as Record<string, unknown>
    return cellText(obj.name ?? obj.label ?? obj.title ?? obj.value ?? '')
  }
  return String(value)
}

function DashTable({ slug, title }: { slug: string; title: string }) {
  const navigate = useNavigate()
  const [schema, setSchema] = useState<TechSchema | null>(null)
  const [rows, setRows] = useState<TechRecord[]>([])
  const columns = (schema?.listColumns || [])
    .filter((col) => col.source === 'extra')
    .slice(0, 6)

  useEffect(() => {
    let alive = true
    techApi.schema(slug).then((next) => {
      if (alive) setSchema(next)
    }).catch(() => {
      if (alive) setSchema(null)
    })
    techApi.list(slug, { page: 1, size: 20 }).then((page) => {
      if (alive) setRows(page.items)
    }).catch(() => {
      if (alive) setRows([])
    })
    return () => {
      alive = false
    }
  }, [slug])

  return (
    <section className="qf-dash">
      <h2>{title}</h2>
      <div className="qf-dash-scroll">
        <table className="qf-dash-table">
          <thead>
            <tr>
              <th>编号</th>
              <th>标题</th>
              {columns.map((col) => (
                <th key={col.key}>{col.name}</th>
              ))}
              <th>流程状态</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.id} onClick={() => navigate(`/tech/${slug}/${row.id}`)}>
                <td>{row.code || '—'}</td>
                <td>{row.title || '—'}</td>
                {columns.map((col) => (
                  <td key={col.key}>{cellText(row.extra?.[col.key])}</td>
                ))}
                <td>{row.workflowStatusLabel || '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {rows.length === 0 ? <div className="qf-dash-empty">暂无数据</div> : null}
    </section>
  )
}

export function TechBoardPage() {
  const { board = 'orders' } = useParams()
  const page = board === 'maint' ? BOARDS.maint : BOARDS.orders
  return (
    <div className="qf-dash-page">
      <h1>{page.title}</h1>
      {page.sections.map((section) => (
        <DashTable key={section.slug} slug={section.slug} title={section.title} />
      ))}
    </div>
  )
}
