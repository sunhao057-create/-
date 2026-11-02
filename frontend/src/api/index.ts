import { ApiError, http, unwrap } from './http'
import type {
  Customer,
  CustomerPayload,
  FormPermission,
  LoginLog,
  PageResult,
  Region,
  RoleItem,
  Staff,
  User,
  OrgDepartment,
  OrgMember,
  Workflow,
  WorkflowRuntime,
  FormFieldMeta,
  InboxItem,
  Branding,
  Device,
  DeviceWorkOrder,
  TechRecord,
  Attachment,
  FormModule,
  DataViewDef,
} from '../types'

export const listExportApi = {
  workbook: async (body: {
    module: FormModule
    ids?: number[]
    keyword?: string
    publicFilter?: string
    status?: string
    customerId?: number
  }) => {
    const res = await http.post('/api/lists/export', body, { responseType: 'blob', timeout: 600000 })
    return { blob: res.data as Blob, disposition: String(res.headers['content-disposition'] || '') }
  },
}

export const authApi = {
  login: (username: string, password: string) =>
    unwrap<User>(http.post('/api/auth/login', { username, password })),
  logout: () => unwrap<null>(http.post('/api/auth/logout')),
  me: () => unwrap<User>(http.get('/api/auth/me')),
  changePassword: (oldPassword: string, newPassword: string) =>
    unwrap<null>(http.put('/api/auth/password', { oldPassword, newPassword })),
}

export const profileApi = {
  update: (displayName: string) => unwrap<User>(http.put('/api/profile', { displayName })),
  avatar: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return unwrap<User>(http.post('/api/profile/avatar', form))
  },
  loginLogs: (page = 1, size = 10) =>
    unwrap<PageResult<LoginLog>>(http.get('/api/profile/login-logs', { params: { page, size } })),
}

export const brandingApi = {
  get: () => unwrap<Branding>(http.get('/api/branding')),
  update: (name: string) => unwrap<Branding>(http.put('/api/branding', { name })),
  updateTheme: (themeId: string) => unwrap<Branding>(http.put('/api/branding/theme', { themeId })),
  uploadLogo: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return unwrap<Branding>(http.post('/api/branding/logo', form))
  },
}

export const userApi = {
  list: (params: { keyword?: string; status?: string; page?: number; size?: number }) =>
    unwrap<PageResult<User>>(http.get('/api/users', { params })),
  create: (payload: {
    username: string
    password: string
    displayName: string
    role: string
    departmentId?: number | null
    deptLeader?: boolean
    accountType?: 'INTERNAL' | 'EXTERNAL'
  }) => unwrap<User>(http.post('/api/users', payload)),
  update: (
    id: number,
    payload: {
      displayName: string
      role?: string
      departmentId?: number | null
      deptLeader?: boolean
      accountType?: 'INTERNAL' | 'EXTERNAL'
    },
  ) => unwrap<User>(http.put(`/api/users/${id}`, payload)),
  updateStatus: (id: number, status: string) =>
    unwrap<null>(http.put(`/api/users/${id}/status`, { status })),
  resetPassword: (id: number, password: string) =>
    unwrap<null>(http.put(`/api/users/${id}/password`, { password })),
  remove: (id: number) => unwrap<null>(http.delete(`/api/users/${id}`)),
  loginLogs: (params: { keyword?: string; page?: number; size?: number }) =>
    unwrap<PageResult<LoginLog>>(http.get('/api/users/login-logs', { params })),
}

export const orgApi = {
  departments: () => unwrap<OrgDepartment[]>(http.get('/api/org/departments')),
  createDepartment: (payload: { name: string; parentId?: number | null; sortOrder?: number }) =>
    unwrap<OrgDepartment>(http.post('/api/org/departments', payload)),
  updateDepartment: (id: number, payload: { name: string; parentId?: number | null; sortOrder?: number }) =>
    unwrap<OrgDepartment>(http.put(`/api/org/departments/${id}`, payload)),
  removeDepartment: (id: number) => unwrap<null>(http.delete(`/api/org/departments/${id}`)),
  members: (id: number) => unwrap<OrgMember[]>(http.get(`/api/org/departments/${id}/members`)),
  assignMember: (id: number, payload: { userId?: number; userIds?: number[]; deptLeader?: boolean }) =>
    unwrap<OrgMember[]>(http.post(`/api/org/departments/${id}/members`, payload)),
  removeMember: (id: number, userId: number) =>
    unwrap<OrgMember[]>(http.delete(`/api/org/departments/${id}/members/${userId}`)),
}

export const lookupApi = {
  customers: () => unwrap<Customer[]>(http.get('/api/customers')),
  regions: () => unwrap<Region[]>(http.get('/api/regions')),
  staff: () => unwrap<Staff[]>(http.get('/api/staff')),
}

export const customerApi = {
  list: (keyword?: string, publicFilter?: string) =>
    unwrap<Customer[]>(http.get('/api/customers', { params: { keyword, publicFilter } })),
  get: (id: number) => unwrap<Customer>(http.get(`/api/customers/${id}`)),
  create: (payload: CustomerPayload) => unwrap<Customer>(http.post('/api/customers', payload)),
  update: (id: number, payload: CustomerPayload) => unwrap<Customer>(http.put(`/api/customers/${id}`, payload)),
  remove: (id: number) => unwrap<null>(http.delete(`/api/customers/${id}`)),
}

export const roleApi = {
  list: () => unwrap<RoleItem[]>(http.get('/api/roles')),
  modules: () => unwrap<{ code: string; label: string }[]>(http.get('/api/roles/modules')),
  create: (payload: {
    name: string
    description?: string
    permissions: FormPermission[]
  }) => unwrap<RoleItem>(http.post('/api/roles', payload)),
  update: (
    id: number,
    payload: { name: string; description?: string; permissions: FormPermission[] },
  ) => unwrap<RoleItem>(http.put(`/api/roles/${id}`, payload)),
  remove: (id: number) => unwrap<null>(http.delete(`/api/roles/${id}`)),
}


export const miniDeviceApi = {
  home: () => unwrap<{ total: number; companyName: string; devices: Device[] }>(http.get('/api/mini/devices')),
  detail: (id: number) => unwrap<{ device: Device; orders: DeviceWorkOrder[] }>(http.get(`/api/mini/devices/${id}`)),
  repair: (id: number, reason: string) => unwrap<DeviceWorkOrder>(http.post(`/api/mini/devices/${id}/repairs`, { reason })),
}

export const deviceApi = {
  list: (query?: {
    keyword?: string
    departmentId?: number
    enabled?: boolean
    code?: string
    name?: string
    model?: string
    location?: string
  }) => unwrap<Device[]>(http.get('/api/devices', { params: query })),
  departments: () => unwrap<OrgDepartment[]>(http.get('/api/devices/dept-options')),
  get: (id: number) => unwrap<Device>(http.get(`/api/devices/${id}`)),
  create: (payload: Record<string, unknown>) => unwrap<Device>(http.post('/api/devices', payload)),
  update: (id: number, payload: Record<string, unknown>) => unwrap<Device>(http.put(`/api/devices/${id}`, payload)),
  remove: (id: number) => unwrap<null>(http.delete(`/api/devices/${id}`)),
  uploadPhoto: (id: number, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return unwrap<Device>(http.post(`/api/devices/${id}/photo`, form, { timeout: 60000 }))
  },
  removePhoto: (id: number) => unwrap<Device>(http.delete(`/api/devices/${id}/photo`)),
  qrcode: async (id: number) => {
    const res = await http.get(`/api/devices/${id}/qrcode`, { responseType: 'blob' })
    return res.data as Blob
  },
  exportWorkbook: async (opts?: {
    keyword?: string
    departmentId?: number
    enabled?: boolean
    code?: string
    name?: string
    model?: string
    location?: string
    ids?: number[]
  }) => {
    const params: {
      keyword?: string
      departmentId?: number
      enabled?: boolean
      code?: string
      name?: string
      model?: string
      location?: string
      ids?: number[]
    } = {}
    if (opts?.keyword) params.keyword = opts.keyword
    if (opts?.departmentId != null) params.departmentId = opts.departmentId
    if (opts?.enabled != null) params.enabled = opts.enabled
    if (opts?.code) params.code = opts.code
    if (opts?.name) params.name = opts.name
    if (opts?.model) params.model = opts.model
    if (opts?.location) params.location = opts.location
    if (opts?.ids?.length) params.ids = opts.ids
    const res = await http.post('/api/devices/export', params, { responseType: 'blob', timeout: 600000 })
    const blob = res.data as Blob
    if (blob.type.includes('application/json')) {
      const body = JSON.parse(await blob.text()) as { code?: number; message?: string }
      throw new ApiError(body.code || 500, body.message || '导出失败')
    }
    return { blob, disposition: String(res.headers['content-disposition'] || '') }
  },
  orders: (kind: 'repairs' | 'moves' | 'scraps', params: { keyword?: string; page?: number; size?: number }) =>
    unwrap<PageResult<DeviceWorkOrder>>(http.get(`/api/devices/${kind}`, { params })),
  order: (kind: 'repairs' | 'moves' | 'scraps', id: number) =>
    unwrap<DeviceWorkOrder>(http.get(`/api/devices/${kind}/${id}`)),
  repairCustomers: () => unwrap<CustomerPick[]>(http.get('/api/devices/repairs/customers')),
  createOrder: (kind: 'repairs' | 'moves' | 'scraps', payload: Record<string, unknown>) =>
    unwrap<DeviceWorkOrder>(http.post(`/api/devices/${kind}`, payload)),
  updateOrder: (kind: 'repairs' | 'moves' | 'scraps', id: number, payload: Record<string, unknown>) =>
    unwrap<DeviceWorkOrder>(http.put(`/api/devices/${kind}/${id}`, payload)),
  removeOrder: (kind: 'repairs' | 'moves' | 'scraps', id: number) =>
    unwrap<null>(http.delete(`/api/devices/${kind}/${id}`)),
  uploadOrderFiles: (kind: 'repairs' | 'moves' | 'scraps', id: number, files: File[]) => {
    const form = new FormData()
    files.forEach((file) => form.append('files', file))
    return unwrap<DeviceWorkOrder>(http.post(`/api/devices/${kind}/${id}/files`, form, { timeout: 60000 }))
  },
  removeOrderFile: (kind: 'repairs' | 'moves' | 'scraps', id: number, fileId: number) =>
    unwrap<DeviceWorkOrder>(http.delete(`/api/devices/${kind}/${id}/files/${fileId}`)),
}


export type TechSchemaField = {
  key: string
  name: string
  type: string
  required: boolean
  applicant: boolean
  section: string
  span: number
  subfields: TechSchemaField[]
  options: string[]
}

export type TechSchema = {
  slug: string
  label: string
  module: string
  group: string
  groupLabel: string
  fields: TechSchemaField[]
  listColumns: { key: string; name: string; source: string }[]
}

export type MaintServiceOption = { slug: string; label: string }

export type MaintRecord = {
  id: number
  code: string
  service: string
  serviceLabel: string
  title: string
  customerName: string
  deviceCode: string
  instrumentName: string
  instrumentModel: string
  vendor: string
  maintainer: string
  startTime: string
  location: string
  extra: Record<string, unknown>
  workflowStatus?: string | null
  workflowStatusLabel?: string | null
  creatorName?: string | null
  files: Attachment[]
  createdAt?: string | null
}

export type CustomerPick = { id: number; companyName: string; shortName?: string | null }

export type CustomerOpen = {
  id: number
  code: string
  companyName: string
  shortName: string
  level: string
  contactName?: string | null
  contactPhone?: string | null
  address?: string | null
  departmentId?: number | null
  customerId?: number | null
  settled: boolean
  workflowStatus?: string | null
  workflowStatusLabel?: string | null
}

export type CustomerAccountOpen = {
  id: number
  code: string
  customerId: number
  companyName: string
  departmentId?: number | null
  departmentName?: string | null
  displayName: string
  username: string
  phone?: string | null
  accountType?: string | null
  roleCode?: string | null
  roleName?: string | null
  initialPassword?: string | null
  userId?: number | null
  settled: boolean
  workflowStatus?: string | null
  workflowStatusLabel?: string | null
}

export const customerOpenApi = {
  list: () => unwrap<CustomerOpen[]>(http.get('/api/customer-opens')),
  get: (id: number) => unwrap<CustomerOpen>(http.get(`/api/customer-opens/${id}`)),
  create: (payload: Partial<CustomerOpen>) => unwrap<CustomerOpen>(http.post('/api/customer-opens', payload)),
  update: (id: number, payload: Partial<CustomerOpen>) => unwrap<CustomerOpen>(http.put(`/api/customer-opens/${id}`, payload)),
  remove: (id: number) => unwrap<null>(http.delete(`/api/customer-opens/${id}`)),
}

export const customerAccountApi = {
  list: () => unwrap<CustomerAccountOpen[]>(http.get('/api/customer-accounts')),
  get: (id: number) => unwrap<CustomerAccountOpen>(http.get(`/api/customer-accounts/${id}`)),
  customers: () => unwrap<CustomerPick[]>(http.get('/api/customer-accounts/customers')),
  roles: () => unwrap<{ code: string; name: string }[]>(http.get('/api/customer-accounts/roles')),
  departments: (customerId: number) =>
    unwrap<{ id: number; name: string; parentId?: number | null }[]>(http.get('/api/customer-accounts/departments', { params: { customerId } })),
  create: (payload: Partial<CustomerAccountOpen>) => unwrap<CustomerAccountOpen>(http.post('/api/customer-accounts', payload)),
  update: (id: number, payload: Partial<CustomerAccountOpen>) =>
    unwrap<CustomerAccountOpen>(http.put(`/api/customer-accounts/${id}`, payload)),
  remove: (id: number) => unwrap<null>(http.delete(`/api/customer-accounts/${id}`)),
}

export type CursorTask = {
  id: number
  prompt: string
  status: string
  statusLabel: string
  agentId?: string | null
  agentUrl?: string | null
  prUrl?: string | null
  branch?: string | null
  resultText?: string | null
  errorMessage?: string | null
  createdByName?: string | null
  createdAt: string
  updatedAt: string
}

export const cursorApi = {
  status: () => unwrap<{ configured: boolean; repoUrl: string; hint: string }>(http.get('/api/cursor/status')),
  tasks: () => unwrap<CursorTask[]>(http.get('/api/cursor/tasks')),
  create: (prompt: string) => unwrap<CursorTask>(http.post('/api/cursor/tasks', { prompt })),
  follow: (id: number, prompt: string) => unwrap<CursorTask>(http.post(`/api/cursor/tasks/${id}/follow`, { prompt })),
}

export const maintApi = {
  services: () => unwrap<MaintServiceOption[]>(http.get('/api/devices/maint-records/services')),
  schema: (service: string) => unwrap<TechSchema>(http.get('/api/devices/maint-records/schema', { params: { service } })),
  list: (params: { keyword?: string; page?: number; size?: number }) =>
    unwrap<PageResult<MaintRecord>>(http.get('/api/devices/maint-records', { params })),
  get: (id: number) => unwrap<MaintRecord>(http.get(`/api/devices/maint-records/${id}`)),
  create: (payload: Record<string, unknown>) => unwrap<MaintRecord>(http.post('/api/devices/maint-records', payload)),
  update: (id: number, payload: Record<string, unknown>) =>
    unwrap<MaintRecord>(http.put(`/api/devices/maint-records/${id}`, payload)),
  remove: (id: number) => unwrap<null>(http.delete(`/api/devices/maint-records/${id}`)),
  uploadFiles: (id: number, files: File[], fieldKey?: string) => {
    const form = new FormData()
    files.forEach((file) => form.append('files', file))
    if (fieldKey) form.append('fieldKey', fieldKey)
    return unwrap<null>(http.post(`/api/devices/maint-records/${id}/files`, form, { timeout: 60000 }))
  },
  removeFile: (id: number, fileId: number) =>
    unwrap<null>(http.delete(`/api/devices/maint-records/${id}/files/${fileId}`)),
}

export type PortalPost = {
  id: number
  kind: string
  category?: string | null
  title: string
  summary?: string | null
  body?: string | null
  coverUrl?: string | null
  sortOrder: number
  enabled: boolean
  createdAt?: string | null
}

export const portalApi = {
  list: (kind: string) => unwrap<PortalPost[]>(http.get(`/api/portal/${kind}`)),
  create: (kind: string, payload: Partial<PortalPost>) => unwrap<PortalPost>(http.post(`/api/portal/${kind}`, payload)),
  update: (kind: string, id: number, payload: Partial<PortalPost>) =>
    unwrap<PortalPost>(http.put(`/api/portal/${kind}/${id}`, payload)),
  remove: (kind: string, id: number) => unwrap<null>(http.delete(`/api/portal/${kind}/${id}`)),
  upload: (kind: string, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return unwrap<string>(http.post(`/api/portal/${kind}/images`, form))
  },
}

export const techApi = {
  lookups: () => unwrap<{ staff: Staff[] }>(http.get('/api/tech/lookups')),
  schema: (kind: string) => unwrap<TechSchema>(http.get(`/api/tech/${kind}/schema`)),
  list: (kind: string, params: { keyword?: string; page?: number; size?: number }) =>
    unwrap<PageResult<TechRecord>>(http.get(`/api/tech/${kind}`, { params })),
  get: (kind: string, id: number) => unwrap<TechRecord>(http.get(`/api/tech/${kind}/${id}`)),
  create: (kind: string, payload: Record<string, unknown>) =>
    unwrap<TechRecord>(http.post(`/api/tech/${kind}`, payload)),
  update: (kind: string, id: number, payload: Record<string, unknown>) =>
    unwrap<TechRecord>(http.put(`/api/tech/${kind}/${id}`, payload)),
  remove: (kind: string, id: number) => unwrap<null>(http.delete(`/api/tech/${kind}/${id}`)),
  uploadFiles: (kind: string, id: number, files: File[], fieldKey?: string) => {
    const form = new FormData()
    files.forEach((file) => form.append('files', file))
    if (fieldKey) form.append('fieldKey', fieldKey)
    return unwrap<TechRecord>(http.post(`/api/tech/${kind}/${id}/files`, form, { timeout: 60000 }))
  },
  removeFile: (kind: string, id: number, fileId: number) =>
    unwrap<TechRecord>(http.delete(`/api/tech/${kind}/${id}/files/${fileId}`)),
}


export const workflowApi = {
  list: () => unwrap<Workflow[]>(http.get('/api/workflows')),
  get: (id: number) => unwrap<Workflow>(http.get(`/api/workflows/${id}`)),
  formFields: (module: string) =>
    unwrap<FormFieldMeta[]>(http.get('/api/workflows/form-fields', { params: { module } })),
  runtime: (module: string, bizId?: number, autostart?: boolean) =>
    unwrap<WorkflowRuntime>(http.get('/api/workflows/runtime', { params: { module, bizId, autostart } })),
  forForm: (module: string) =>
    unwrap<Workflow>(http.get('/api/workflows/for-form', { params: { module } })),
  create: (payload: {
    name: string
    code: string
    formModule: string
    graph: { nodes: Workflow['nodes']; edges: Workflow['edges'] }
  }) => unwrap<Workflow>(http.post('/api/workflows', payload)),
  update: (
    id: number,
    payload: {
      name: string
      code: string
      formModule: string
      graph: { nodes: Workflow['nodes']; edges: Workflow['edges'] }
    },
  ) => unwrap<Workflow>(http.put(`/api/workflows/${id}`, payload)),
  remove: (id: number) => unwrap<null>(http.delete(`/api/workflows/${id}`)),
  publish: (id: number) => unwrap<Workflow>(http.post(`/api/workflows/${id}/publish`)),
  unpublish: (id: number) => unwrap<Workflow>(http.post(`/api/workflows/${id}/unpublish`)),
  advance: (formModule: string, bizId: number, comment?: string) =>
    unwrap<WorkflowRuntime>(http.post('/api/workflows/advance', { formModule, bizId, comment })),
  returnTo: (formModule: string, bizId: number, targetNodeKey: string, reason: string) =>
    unwrap<WorkflowRuntime>(http.post('/api/workflows/return', { formModule, bizId, targetNodeKey, reason })),
}

export const inboxApi = {
  list: (type: string, overdue?: boolean) =>
    unwrap<InboxItem[]>(http.get('/api/inbox', { params: { type, overdue: overdue ? true : undefined } })),
}

export const formViewApi = {
  list: (module: string) => unwrap<DataViewDef[]>(http.get('/api/form-views', { params: { module } })),
  create: (module: string, payload: Omit<DataViewDef, 'id' | 'module' | 'builtin' | 'createdById' | 'createdByName'>) =>
    unwrap<DataViewDef>(http.post('/api/form-views', payload, { params: { module } })),
  update: (
    module: string,
    id: number,
    payload: Omit<DataViewDef, 'id' | 'module' | 'builtin' | 'createdById' | 'createdByName'>,
  ) => unwrap<DataViewDef>(http.put(`/api/form-views/${id}`, payload, { params: { module } })),
  remove: (module: string, id: number) => unwrap<null>(http.delete(`/api/form-views/${id}`, { params: { module } })),
}
