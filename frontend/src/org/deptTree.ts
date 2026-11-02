import type { OrgDepartment } from '../types'

export type DeptTreeNode = {
  key: number
  value: number
  title: string
  children?: DeptTreeNode[]
}

function parentKey(item: OrgDepartment): number | null {
  if (item.parentId == null || item.parentId === undefined) return null
  const id = Number(item.parentId)
  return Number.isFinite(id) ? id : null
}

export function buildDeptTree(depts: OrgDepartment[], excludeId?: number | null): DeptTreeNode[] {
  const excluded = new Set<number>()
  if (excludeId != null) {
    const collect = (id: number) => {
      excluded.add(id)
      depts.filter((item) => parentKey(item) === id).forEach((item) => collect(item.id))
    }
    collect(excludeId)
  }
  const visible = depts
    .filter((item) => !excluded.has(item.id))
    .slice()
    .sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id)
  const map = new Map<number, DeptTreeNode>()
  visible.forEach((item) => {
    map.set(item.id, { key: item.id, value: item.id, title: item.name })
  })
  const roots: DeptTreeNode[] = []
  visible.forEach((item) => {
    const node = map.get(item.id)
    if (!node) return
    const parentId = parentKey(item)
    const parent = parentId != null ? map.get(parentId) : undefined
    if (parent) {
      parent.children = parent.children ?? []
      parent.children.push(node)
    } else {
      roots.push(node)
    }
  })
  return roots
}

export function deptPath(depts: OrgDepartment[], id: number | null | undefined): string {
  if (id == null) return ''
  const byId = new Map(depts.map((item) => [item.id, item]))
  const names: string[] = []
  const seen = new Set<number>()
  let current = byId.get(id)
  while (current && !seen.has(current.id)) {
    seen.add(current.id)
    names.unshift(current.name)
    current = parentKey(current) != null ? byId.get(parentKey(current)!) : undefined
  }
  return names.join(' / ')
}

export function allDeptKeys(depts: OrgDepartment[]): number[] {
  return depts.map((item) => item.id)
}

export function descendantIds(depts: OrgDepartment[], id: number): number[] {
  const ids = [id]
  for (const item of depts) {
    if (item.parentId === id) ids.push(...descendantIds(depts, item.id))
  }
  return ids
}
