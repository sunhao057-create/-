import type { WorkflowEdge, WorkflowNode } from '../types'

export const NODE_W = 200
export const NODE_H = 96
export const COND_W = 220
export const COND_H = 108
export const ROW_GAP = 64
export const COL_GAP = 40
export const PAD_X = 96
export const PAD_Y = 48

export function nodeSize(type: string) {
  return type === 'CONDITION' ? { w: COND_W, h: COND_H } : { w: NODE_W, h: NODE_H }
}

export function emptyGraph(): { nodes: WorkflowNode[]; edges: WorkflowEdge[] } {
  const x = PAD_X
  return {
    nodes: [
      { key: 'start', name: '开始', type: 'START', x, y: PAD_Y, fields: {} },
      {
        key: 'fill',
        name: '填写表单',
        type: 'APPROVAL',
        x,
        y: PAD_Y + NODE_H + ROW_GAP,
        fields: {},
        assigneeType: 'STARTER',
        assigneeUserIds: [],
      },
      { key: 'end', name: '结束', type: 'END', x, y: PAD_Y + (NODE_H + ROW_GAP) * 2, fields: {} },
    ],
    edges: [
      { from: 'start', to: 'fill', isDefault: true, conditions: [] },
      { from: 'fill', to: 'end', isDefault: true, conditions: [] },
    ],
  }
}

export function looksHorizontal(nodes: WorkflowNode[]) {
  if (nodes.length < 2) return false
  const xs = nodes.map((item) => item.x)
  const ys = nodes.map((item) => item.y)
  const spanX = Math.max(...xs) - Math.min(...xs)
  const spanY = Math.max(...ys) - Math.min(...ys)
  return spanX > spanY * 1.2 && spanY < NODE_H * 2.5
}

export function layoutVertical(nodes: WorkflowNode[], edges: WorkflowEdge[]): WorkflowNode[] {
  if (nodes.length === 0) return nodes
  const children = new Map<string, string[]>()
  for (const edge of edges) {
    const list = children.get(edge.from) || []
    if (!list.includes(edge.to)) list.push(edge.to)
    children.set(edge.from, list)
  }
  const start = nodes.find((item) => item.type === 'START') || nodes[0]
  const rank = new Map<string, number>()
  const queue = [start.key]
  rank.set(start.key, 0)
  const limit = nodes.length + 2
  while (queue.length) {
    const key = queue.shift()!
    const current = rank.get(key) || 0
    for (const child of children.get(key) || []) {
      const next = current + 1
      if (next >= limit) continue
      if (!rank.has(child) || (rank.get(child) || 0) < next) {
        rank.set(child, next)
        queue.push(child)
      }
    }
  }
  let extra = Math.max(0, ...rank.values())
  for (const node of nodes) {
    if (!rank.has(node.key)) rank.set(node.key, ++extra)
  }

  const byRank = new Map<number, WorkflowNode[]>()
  for (const node of nodes) {
    const row = byRank.get(rank.get(node.key) || 0) || []
    row.push(node)
    byRank.set(rank.get(node.key) || 0, row)
  }

  const result = nodes.map((item) => ({ ...item }))
  const byKey = new Map(result.map((item) => [item.key, item]))
  const ranks = [...byRank.keys()].sort((a, b) => a - b)

  let maxRowW = NODE_W
  for (const r of ranks) {
    const row = byRank.get(r) || []
    const totalW = row.reduce((sum, node) => sum + nodeSize(node.type).w, 0) + COL_GAP * Math.max(0, row.length - 1)
    maxRowW = Math.max(maxRowW, totalW)
  }
  const center = PAD_X + maxRowW / 2

  let y = PAD_Y
  for (const r of ranks) {
    const row = byRank.get(r) || []
    const totalW = row.reduce((sum, node) => sum + nodeSize(node.type).w, 0) + COL_GAP * Math.max(0, row.length - 1)
    const rowH = Math.max(...row.map((node) => nodeSize(node.type).h), NODE_H)
    let x = center - totalW / 2
    for (const node of row) {
      const target = byKey.get(node.key)
      if (target) {
        target.x = Math.max(8, x)
        target.y = y
      }
      x += nodeSize(node.type).w + COL_GAP
    }
    y += rowH + ROW_GAP
  }
  return result
}

export function canvasSize(nodes: WorkflowNode[]) {
  let width = NODE_W + PAD_X * 2
  let height = NODE_H + PAD_Y * 2
  for (const node of nodes) {
    const size = nodeSize(node.type)
    width = Math.max(width, node.x + size.w + 80)
    height = Math.max(height, node.y + size.h + 80)
  }
  return { width, height }
}

export function belowLastNode(nodes: WorkflowNode[], type: string) {
  const size = nodeSize(type)
  const start = nodes.find((item) => item.type === 'START')
  const maxBottom = nodes.reduce((max, node) => Math.max(max, node.y + nodeSize(node.type).h), PAD_Y)
  const x = start ? start.x + nodeSize(start.type).w / 2 - size.w / 2 : PAD_X
  return { x: Math.max(8, x), y: maxBottom + ROW_GAP }
}

export function conditionOffset(
  branch: Pick<WorkflowNode, 'x' | 'y' | 'type'>,
  index: number,
  total = index + 1,
) {
  const size = nodeSize(branch.type)
  const rowW = total * COND_W + Math.max(0, total - 1) * COL_GAP
  const start = branch.x + size.w / 2 - rowW / 2
  return {
    x: Math.max(8, start + index * (COND_W + COL_GAP)),
    y: branch.y + size.h + ROW_GAP,
  }
}

export function verticalEdgeGeometry(from: WorkflowNode, to: WorkflowNode) {
  const fromSize = nodeSize(from.type)
  const toSize = nodeSize(to.type)
  const x1 = from.x + fromSize.w / 2
  const y1 = from.y + fromSize.h
  const x2 = to.x + toSize.w / 2
  const y2 = to.y
  const spanY = Math.max(24, Math.abs(y2 - y1))
  const dir = y2 >= y1 ? 1 : -1
  const dy = Math.max(28, spanY * 0.45) * dir
  const bow = Math.abs(x1 - x2) < 6 ? Math.min(40, Math.max(22, spanY * 0.22)) : 0
  return {
    d: `M ${x1} ${y1} C ${x1 + bow} ${y1 + dy}, ${x2 + bow} ${y2 - dy}, ${x2} ${y2}`,
    labelX: (x1 + x2) / 2 + bow * 0.5,
    labelY: (y1 + y2) / 2,
  }
}
