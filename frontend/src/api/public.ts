import { http, unwrap } from './http'

export type PublicModuleFlags = {
  publicAccess: boolean
  display: boolean
  submit: boolean
  query: boolean
  export: boolean
}

export type PublicModuleConfig = {
  module: string
  moduleLabel: string
  path: string
  flags: PublicModuleFlags
}

export type PublicConfig = {
  project: PublicModuleFlags
  customer: PublicModuleFlags
  supplier: PublicModuleFlags
  payment: PublicModuleFlags
  modules: PublicModuleConfig[]
}

export type PublicContact = {
  name: string
  title?: string | null
  phone?: string | null
  email?: string | null
  primaryContact: boolean
}

export type PublicBankAccount = {
  id?: number | null
  accountName: string
  bankName: string
  accountNo: string
  branch?: string | null
  primaryAccount: boolean
}

export type PublicCustomer = {
  id: number
  shortName: string
  companyName: string
  code: string
  level: string
  contacts?: PublicContact[]
  bankAccounts?: PublicBankAccount[]
}

export type PublicSupplier = {
  id: number
  shortName: string
  companyName: string
  code: string
  category: string
  contacts?: PublicContact[]
  bankAccounts?: PublicBankAccount[]
}

export type PublicAttachment = {
  id: number
  originalName: string
  url: string
}

export type PublicProject = {
  id: number
  name: string
  code: string
  customerId?: number | null
  customerShortName?: string | null
  companyName: string
  companyCode?: string | null
  companyLevel?: string | null
  managerId?: number | null
  managerName?: string | null
  regionId?: number | null
  regionName?: string | null
  regionLeaderName?: string | null
  status: string
  overview?: string | null
  progressPercent?: number | null
  attachments?: PublicAttachment[]
}

export type PublicStaffOption = {
  id: number
  displayName: string
  avatarUrl?: string | null
  departmentId?: number | null
  departmentName?: string | null
  position?: string | null
  jobLevel?: string | null
}

export type PublicRegionOption = {
  id: number
  name: string
  leaderName?: string | null
  leaderAvatarUrl?: string | null
}

export type PublicLookups = {
  customers: PublicCustomer[]
  staff: PublicStaffOption[]
  regions: PublicRegionOption[]
}

export type PublicProjectOption = {
  id: number
  name: string
  code: string
}

export type PublicPaymentLookups = {
  projects: PublicProjectOption[]
  suppliers: PublicSupplier[]
  accounts: Array<{ id: number; name: string; bankName: string; accountNo: string }>
}

export type PublicPayment = {
  id: number
  code: string
  projectId?: number | null
  projectName?: string | null
  projectCode?: string | null
  amount: number | string
  reason: string
  supplierId: number
  supplierShortName: string
  supplierCompanyName: string
  payeeName: string
  bankName: string
  accountNo: string
  branch?: string | null
  payMethod: string
  payMethodLabel: string
  expectedPayDate: string
  accountId?: number | null
  accountName?: string | null
  attachments?: PublicAttachment[]
}

export type PublicOa = {
  id: number
  code: string
  kind: string
  kindLabel: string
  applicantName: string
  gender?: string | null
  genderLabel?: string | null
  mobile?: string | null
  departmentName?: string | null
  position?: string | null
  jobLevel?: string | null
  targetDepartmentName?: string | null
  targetPosition?: string | null
  targetLevel?: string | null
  expectedDate?: string | null
  managerName?: string | null
  handoverName?: string | null
  probationMonths?: number | null
  resignType?: string | null
  resignTypeLabel?: string | null
  reason: string
  loginUsername?: string | null
  applied?: boolean
  interview?: import('../types').OaInterview | null
  seal?: import('../types').OaSeal | null
  qualify?: import('../types').OaQualify | null
  trip?: import('../types').OaTrip | null
  loan?: import('../types').OaLoan | null
  leave?: import('../types').OaLeave | null
  files?: PublicAttachment[]
}

export type PublicTech = {
  id: number
  code: string
  kind: string
  kindLabel: string
  title: string
  extra: Record<string, unknown>
  files?: PublicAttachment[]
  createdAt?: string
}

export type PublicTechSchemaField = {
  key: string
  name: string
  type: string
  required: boolean
  applicant: boolean
  section: string
  span: number
  subfields: PublicTechSchemaField[]
  options: string[]
}

export type PublicTechSchema = {
  slug: string
  label: string
  module: string
  fields: PublicTechSchemaField[]
  listColumns: { key: string; name: string; source: string }[]
}

export type PublicOaLookups = {
  departments: Array<{
    id: number
    name: string
    parentId?: number | null
    parentName?: string | null
    sortOrder: number
    memberCount: number
    leaderCount: number
  }>
  staff: PublicStaffOption[]
}

export type PublicPartySubmit = {
  shortName: string
  companyName: string
  code?: string
  level?: string
  category?: string
  contactName?: string
  contactPhone?: string
  contacts?: PublicContact[]
  bankAccounts?: PublicBankAccount[]
}

export const publicApi = {
  config: () => unwrap<PublicConfig>(http.get('/api/public/config')),
  lookups: () => unwrap<PublicLookups>(http.get('/api/public/lookups')),
  customers: (keyword?: string) =>
    unwrap<PublicCustomer[]>(http.get('/api/public/customers', { params: { keyword } })),
  customer: (id: number) => unwrap<PublicCustomer>(http.get(`/api/public/customers/${id}`)),
  submitCustomer: (payload: PublicPartySubmit) =>
    unwrap<PublicCustomer>(http.post('/api/public/customers', payload)),
  techLookups: (kind: string) => unwrap<{ staff: PublicStaffOption[] }>(http.get(`/api/public/tech/${kind}/lookups`)),
  techSchema: (kind: string) => unwrap<PublicTechSchema>(http.get(`/api/public/tech/${kind}/schema`)),
  techList: (kind: string, keyword?: string) =>
    unwrap<PublicTech[]>(http.get(`/api/public/tech/${kind}`, { params: { keyword } })),
  tech: (kind: string, id: number) => unwrap<PublicTech>(http.get(`/api/public/tech/${kind}/${id}`)),
  submitTech: (kind: string, payload: FormData) =>
    unwrap<PublicTech>(http.post(`/api/public/tech/${kind}`, payload, { timeout: 60000 })),
  exportWorkbook: async (body: { kind: string; ids?: number[]; keyword?: string }) => {
    const res = await http.post('/api/public/export', body, { responseType: 'blob', timeout: 600000 })
    return { blob: res.data as Blob, disposition: String(res.headers['content-disposition'] || '') }
  },
}
