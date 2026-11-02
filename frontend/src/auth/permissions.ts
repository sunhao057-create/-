import type { FormModule, PermAction, User } from '../types'

export function can(user: User | null | undefined, module: FormModule | string, action: PermAction): boolean {
  if (!user) return false
  if (user.role === 'ADMIN') return true
  const perm = user.permissions?.find((item) => item.module === module)
  if (!perm) return false
  if (action === 'create' && perm.create === undefined) {
    return Boolean(perm.edit)
  }
  return Boolean(perm[action])
}

export function homePath(user: User | null | undefined): string {
  if (!user) return '/login'
  return '/'
}
