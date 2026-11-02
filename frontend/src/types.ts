export type FormModule =
  | 'PROJECT'
  | 'PROJECT_ANALYTICS'
  | 'COLLECTION'
  | 'DEPOSIT_APPLY'
  | 'DEPOSIT_RETURN'
  | 'BIDDING'
  | 'PAYMENT'
  | 'CUSTOMER'
  | 'CONTRACT'
  | 'SUPPLIER'
  | 'USER'
  | 'ROLE'
  | 'ORG'
  | 'WORKFLOW'
  | 'BRANDING'
  | 'THEME'
  | 'BANK_ACCOUNT'
  | 'EXPENSE'
  | 'BANK_ANALYTICS'
  | 'PURCHASE'
  | 'PURCHASE_PAY'
  | 'PURCHASE_ANALYTICS'
  | 'WAREHOUSE'
  | 'STOCK_OUT_PURCHASE'
  | 'STOCK_OUT_PROJECT'
  | 'STOCK_TRANSFER'
  | 'WAREHOUSE_ANALYTICS'
  | 'DEVICE'
  | 'DEVICE_REPAIR'
  | 'DEVICE_MOVE'
  | 'DEVICE_SCRAP'
  | 'DEVICE_MAINT'
  | 'MINI_BANNER'
  | 'MINI_KNOWLEDGE'
  | 'MINI_COMPANY'
  | 'MINI_SERVICE'
  | 'CUSTOMER_APPLY'
  | 'CUSTOMER_ACCOUNT'
  | 'OA_INTERVIEW'
  | 'OA_ONBOARD'
  | 'OA_TRANSFER'
  | 'OA_PROMOTION'
  | 'OA_RESIGN'
  | 'OA_SALARY'
  | 'OA_SEAL'
  | 'OA_QUALIFY'
  | 'OA_TRIP'
  | 'OA_LEAVE'
  | 'OA_LOAN'
  | 'OA_LOAN_PAY'
  | 'OA_LOAN_REPAY'
  | 'OA_STAFF'
  | 'TECH_CUSTOMER_INSTRUMENT'
  | 'TECH_WECHAT_CONTACTS'
  | 'TECH_REPAIR_TRACK'
  | 'TECH_TICKET_POOL'
  | 'TECH_SERVICE_REPORT'
  | 'TECH_MULTI_REPORT'
  | 'TECH_MOVE_CARD'
  | 'TECH_MAINT_BASIC'
  | 'TECH_MAINT_LCMS'
  | 'TECH_MAINT_ICPMS'
  | 'TECH_MAINT_ICP'
  | 'TECH_MAINT_AAS'
  | 'TECH_MAINT_LC'
  | 'TECH_MAINT_GC'
  | 'TECH_MAINT_GCMS'
  | 'TECH_MAINT_AFS'
  | 'TECH_MAINT_UV'
  | 'TECH_MAINT_LEGACY'
  | 'TECH_SPECIAL_CONFIRM'
  | 'TECH_REPAIR_CARD'
  | 'TECH_INSTRUMENT_LIB'
  | 'TECH_WEIGHT_CALIB'
  | 'TECH_PM_PROJECT'
  | 'TECH_PM_STATUS'
  | 'TECH_PM_CHECKIN'
  | 'TECH_PM_MAINT_DAILY'
  | 'TECH_PM_MOVE_DAILY'
  | 'TECH_PM_MAKEUP'
export type PermAction = 'view' | 'create' | 'edit' | 'delete' | 'importExport' | 'flow' | 'manageView'
export type DataScope = 'ALL' | 'OWN'

export type FormPermission = {
  module: FormModule
  moduleLabel: string
  view: boolean
  create: boolean
  edit: boolean
  delete: boolean
  importExport: boolean
  flow: boolean
  manageView: boolean
  dataScope: DataScope
  hasDataScope: boolean
  hasCreate: boolean
  hasEdit?: boolean
  hasDelete?: boolean
  hasImport?: boolean
  hasFlow?: boolean
  hasManageView?: boolean
  publicAccess: boolean
  publicDisplay: boolean
  publicSubmit: boolean
  publicQuery: boolean
  publicExport: boolean
  hasPublicAccess: boolean
}

export type RoleItem = {
  id: number
  code: string
  name: string
  description?: string | null
  builtin: boolean
  permissions: FormPermission[]
}

export type Status = 'ENABLED' | 'DISABLED'

export type User = {
  id: number
  username: string
  password?: string | null
  displayName: string
  avatarUrl?: string | null
  role: string
  roleName?: string
  status: Status
  mustChangePassword: boolean
  departmentId?: number | null
  departmentName?: string | null
  deptLeader?: boolean
  position?: string | null
  jobLevel?: string | null
  accountType?: 'INTERNAL' | 'EXTERNAL' | null
  createdAt: string
  updatedAt: string
  permissions?: FormPermission[]
}

export type OrgDepartment = {
  id: number
  name: string
  parentId?: number | null
  parentName?: string | null
  sortOrder: number
  memberCount: number
  leaderCount: number
}

export type OrgMember = {
  id: number
  username: string
  displayName: string
  deptLeader: boolean
  status: string
}

export type Branding = {
  name: string
  logoUrl?: string | null
  themeId?: string | null
}

export type PageResult<T> = {
  items: T[]
  total: number
  page: number
  size: number
}

export type StaffPortal = {
  id: number
  displayName: string
  gender?: string | null
  genderLabel?: string | null
  idNumber?: string | null
  birthDate?: string | null
  mobile?: string | null
  position?: string | null
  jobLevel?: string | null
  departmentId?: number | null
  departmentName?: string | null
  hireDate?: string | null
  status: Status
  statusLabel: string
}

export type OaSalaryLine = {
  id?: number
  itemName: string
  amount?: number | null
  sortOrder?: number
}

export type Staff = {
  id: number
  username: string
  displayName: string
  avatarUrl?: string | null
  departmentId?: number | null
  departmentName?: string | null
  position?: string | null
  jobLevel?: string | null
  salary?: number | null
  salaryItems?: OaSalaryLine[]
}

export type CustomerContact = {
  id?: number
  name: string
  title?: string | null
  phone?: string | null
  email?: string | null
  primaryContact: boolean
}

export type CustomerBankAccount = {
  id?: number
  accountName: string
  bankName: string
  accountNo: string
  branch?: string | null
  primaryAccount: boolean
}

export type Customer = {
  id: number
  shortName: string
  companyName: string
  code: string
  level: string
  contacts?: CustomerContact[]
  bankAccounts?: CustomerBankAccount[]
  isPublic?: boolean
}

export type CustomerPayload = {
  shortName: string
  companyName: string
  code: string
  level: string
  contacts: CustomerContact[]
  bankAccounts: CustomerBankAccount[]
  isPublic?: boolean
}

export type SupplierContact = CustomerContact
export type SupplierBankAccount = CustomerBankAccount

export type Supplier = {
  id: number
  shortName: string
  companyName: string
  code: string
  category: string
  contacts?: SupplierContact[]
  bankAccounts?: SupplierBankAccount[]
  isPublic?: boolean
}

export type SupplierPayload = {
  shortName: string
  companyName: string
  code: string
  category: string
  contacts: SupplierContact[]
  bankAccounts: SupplierBankAccount[]
  isPublic?: boolean
}

export type Region = {
  id: number
  name: string
  leader?: Staff | null
}

export type Attachment = {
  id: number
  originalName: string
  url: string
  size: number
  contentType?: string | null
}

export type Project = {
  id: number
  name: string
  code: string
  customer: Customer
  contractId?: number | null
  contractCode?: string | null
  contractName?: string | null
  companyName: string
  companyCode: string
  companyLevel: string
  manager: Staff
  regionId: number
  regionName: string
  regionLeader?: Staff | null
  overview?: string | null
  progressPercent?: number | null
  status: string
  statusLabel: string
  attachments: Attachment[]
  createdAt: string
  isPublic?: boolean
}

export type ProjectProgress = {
  id: number
  projectId: number
  projectName: string
  projectCode: string
  content: string
  percent: number
  submitSource: string
  submitSourceLabel: string
  creatorName: string
  attachments: Attachment[]
  createdAt: string
}

export type Payment = {
  id: number
  code: string
  projectId?: number | null
  projectName?: string | null
  projectCode?: string | null
  amount: number
  reason: string
  supplierId: number
  supplierShortName: string
  supplierCompanyName: string
  accountId: number
  accountName?: string | null
  bankAccountId: number
  payeeName: string
  bankName: string
  accountNo: string
  branch?: string | null
  payMethod: string
  payMethodLabel: string
  expectedPayDate: string
  budgetDeducted: boolean
  availableAmount: number
  paidTotal: number
  workflowStatus?: string | null
  workflowStatusLabel?: string | null
  isPublic?: boolean
  submitSource?: string
  attachments: Attachment[]
  createdAt: string
}

export type LoginLog = {
  id: number
  userId?: number | null
  username: string
  success: boolean
  failReason?: string | null
  ip?: string | null
  userAgent?: string | null
  deviceLabel?: string | null
  createdAt: string
}

export type FieldMode = 'HIDDEN' | 'VISIBLE' | 'EDITABLE'

export type WorkflowNodeType = 'START' | 'APPROVAL' | 'CC' | 'BRANCH' | 'CONDITION' | 'END' | 'TASK' | string

export type WorkflowCondition = {
  field?: string
  op?: string
  value?: string
  /** Same group is AND; different groups are OR. */
  group?: number
}

export type WorkflowNode = {
  key: string
  name: string
  type: WorkflowNodeType
  x: number
  y: number
  fields?: Record<string, FieldMode | string>
  assigneeType?: 'USER' | 'FORM_FIELD' | 'STARTER' | 'ORG_SUPERVISOR' | 'ORG_CHAIN' | string
  assigneeUserIds?: number[]
  assigneeField?: string
  assigneeLevels?: number | null
  timeoutValue?: number | null
  timeoutUnit?: 'HOUR' | 'DAY' | string
  /** 超时提醒人（用户 id）。 */
  remindees?: number[]
  /** 超时后再过多久升级提醒；为空表示不升级。 */
  escalateAfterValue?: number | null
  escalateAfterUnit?: 'HOUR' | 'DAY' | string
  /** 升级提醒人（用户 id）。 */
  escalateRemindees?: number[]
}

export type WorkflowEdge = {
  from: string
  to: string
  name?: string
  isDefault?: boolean
  conditions?: WorkflowCondition[]
}

export type Workflow = {
  id: number
  name: string
  code: string
  formModule: FormModule
  formModuleLabel: string
  published: boolean
  nodes: WorkflowNode[]
  edges: WorkflowEdge[]
  updatedAt?: string
}

export type PreviousNode = {
  key: string
  name: string
}

export type WorkflowHistoryItem = {
  action: string
  actionLabel: string
  fromNodeName?: string | null
  toNodeName?: string | null
  actorName?: string | null
  comment?: string | null
  createdAt?: string | null
}

export type WorkflowRuntime = {
  processId?: number | null
  processName?: string | null
  formModule: string
  bizId?: number | null
  currentNodeKey?: string | null
  currentNodeName?: string | null
  currentNodeType?: string | null
  status?: string | null
  fields: Record<string, FieldMode | string>
  nextNodeKey?: string | null
  nextNodeName?: string | null
  canAdvance: boolean
  canReturn?: boolean
  canHandle?: boolean
  returnReason?: string | null
  returnFromName?: string | null
  previousNodes?: PreviousNode[]
  history?: WorkflowHistoryItem[]
  currentAssignees?: string[]
  dueAt?: string | null
  overdue?: boolean
}

export type FormFieldMeta = {
  key: string
  label: string
  member?: boolean
  parentLabel?: string
}

export type InboxItem = {
  id: number
  inboxType: string
  title: string
  formModule: FormModule | string
  formModuleLabel: string
  bizId: number
  processName?: string | null
  nodeKey?: string | null
  nodeName?: string | null
  comment?: string | null
  status?: string | null
  createdAt?: string | null
  handledAt?: string | null
  dueAt?: string | null
  overdue?: boolean
}

export type Contract = {
  id: number
  code: string
  name: string
  customerId: number
  customerName: string
  customerShortName: string
  amount: number
  signDate?: string | null
  startDate?: string | null
  endDate?: string | null
  status: string
  statusLabel: string
  remark?: string | null
  projectCount: number
  collectedAmount: number
  outstandingAmount: number
  createdAt: string
  isPublic?: boolean
}

export type ContractPayload = {
  name: string
  customerId: number
  amount: number
  signDate?: string | null
  startDate?: string | null
  endDate?: string | null
  status?: string
  remark?: string
  isPublic?: boolean
}

export type Collection = {
  id: number
  code: string
  customerId: number
  customerShortName: string
  contractId: number
  contractCode: string
  contractName: string
  contractAmount: number
  collectedAmount: number
  outstandingAmount: number
  projectId?: number | null
  projectName?: string | null
  projectCode?: string | null
  accountId: number
  accountName?: string | null
  amount: number
  receivedDate: string
  method: string
  remark?: string | null
  createdAt: string
}

export type Deposit = {
  id: number
  code: string
  kind: string
  kindLabel: string
  projectId: number
  projectName: string
  projectCode: string
  customerName?: string | null
  customerShortName?: string | null
  projectStartedAt?: string | null
  belongCompany?: string | null
  projectCategory?: string | null
  managerName?: string | null
  collaboratorName?: string | null
  projectRemark?: string | null
  applyId?: number | null
  applyCode?: string | null
  accountId: number
  accountName: string
  accountCompany?: string | null
  bankName?: string | null
  accountNo?: string | null
  amount: number
  bizDate: string
  remark?: string | null
  ledgerPosted: boolean
  returned: boolean
  remainingAmount: number
  workflowStatus?: string | null
  workflowStatusLabel?: string | null
  attachments: Attachment[]
  createdAt: string
}

export type BidSignup = {
  id?: number
  companyName: string
  bidderId?: number | null
  bidderName?: string | null
  registrantName?: string | null
}

export type BidFile = Attachment & { category?: string }

export type Bid = {
  id: number
  code: string
  customerId: number
  customerName?: string | null
  customerShortName?: string | null
  salesLeadId?: number | null
  salesLeadName?: string | null
  projectId: number
  projectName: string
  projectCode: string
  opportunityName?: string | null
  bidAgent?: string | null
  tenderName: string
  tenderCode: string
  downloadDeadline: string
  bidTime: string
  description?: string | null
  announcementUrl?: string | null
  signedUp: boolean
  result?: string | null
  resultLabel?: string | null
  winAmount?: number | null
  winUnit?: string | null
  winUnitName?: string | null
  round2Name?: string | null
  round2DownloadDeadline?: string | null
  round2BidTime?: string | null
  round2AnnouncementUrl?: string | null
  round2Result?: string | null
  round2ResultLabel?: string | null
  round2WinAmount?: number | null
  round2WinUnit?: string | null
  round2WinUnitName?: string | null
  finalContractName?: string | null
  signups: BidSignup[]
  screenshots: BidFile[]
  awardFiles: BidFile[]
  round2AwardFiles: BidFile[]
  workflowStatus?: string | null
  workflowStatusLabel?: string | null
  createdAt: string
}

export type CollectionPayload = {
  projectId?: number | null
  accountId: number
  amount: number
  receivedDate: string
  method?: string
  remark?: string
}

export type AnalyticsQuery = {
  customerId?: number
  contractCode?: string
  projectName?: string
  from?: string
  to?: string
}

export type AnalyticsView = {
  kpis: {
    customerCount: number
    contractCount: number
    contractAmount: number
    collectedAmount: number
    outstandingAmount: number
    projectCount: number
    paymentAmount: number
    avgProgress?: number | null
    collectionRate?: number | null
    executingContractCount: number
    overdueContractCount: number
  }
  customers: Array<{
    customerId: number
    customerName: string
    contractCount: number
    contractAmount: number
    collectedAmount: number
    outstandingAmount: number
    projectCount: number
    avgProgress?: number | null
  }>
  contracts: Array<{
    contractId: number
    contractCode: string
    contractName: string
    customerId: number
    customerName: string
    statusLabel: string
    amount: number
    collectedAmount: number
    outstandingAmount: number
    projectCount: number
    signDate?: string | null
  }>
  projects: Array<{
    projectId: number
    projectName: string
    projectCode: string
    customerId: number
    customerName: string
    contractId?: number | null
    contractCode?: string | null
    contractName?: string | null
    progressPercent?: number | null
    contractAmount: number
    collectedAmount: number
    paymentAmount: number
  }>
  monthly: Array<{
    month: string
    contractAmount: number
    collectedAmount: number
    paymentAmount: number
  }>
}

export type BankAccount = {
  id: number
  name: string
  bankName: string
  accountName: string
  accountNo: string
  branch?: string | null
  openingBalance: number
  incomeAmount: number
  expenseAmount: number
  balance: number
  remark?: string | null
  enabled: boolean
  createdAt: string
}

export type BankTxn = {
  id: number
  accountId: number
  accountName: string
  txnDate: string
  direction: 'IN' | 'OUT'
  directionLabel: string
  amount: number
  summary: string
  sourceType: string
  sourceId?: number | null
  projectId?: number | null
  projectName?: string | null
  createdAt: string
}

export type Expense = {
  id: number
  code: string
  projectId?: number | null
  projectName?: string | null
  projectCode?: string | null
  accountId: number
  accountName: string
  amount: number
  reason: string
  category: string
  categoryLabel: string
  expenseDate: string
  payeeName?: string | null
  bankName?: string | null
  accountNo?: string | null
  branch?: string | null
  budgetDeducted: boolean
  availableAmount: number
  workflowStatus?: string | null
  workflowStatusLabel?: string | null
  attachments: Attachment[]
  createdAt: string
}

export type FinanceAnalyticsView = {
  kpis: {
    accountCount: number
    totalBalance: number
    monthIncome: number
    monthExpense: number
    expenseAmount: number
    projectLinkedExpense: number
    unlinkedExpense: number
  }
  accounts: Array<{
    accountId: number
    name: string
    bankName: string
    openingBalance: number
    incomeAmount: number
    expenseAmount: number
    balance: number
  }>
  monthly: Array<{ month: string; incomeAmount: number; expenseAmount: number }>
  categories: Array<{ category: string; categoryLabel: string; amount: number; count: number }>
  projects: Array<{
    projectId: number
    projectName: string
    projectCode: string
    availableAmount: number
    paymentAmount: number
    expenseAmount: number
    spendAmount: number
  }>
  recent: BankTxn[]
}

export type FinanceStatementLine = {
  id: number
  txnDate: string
  summary: string
  projectName?: string | null
  income: number
  expense: number
  balance: number
  direction: 'IN' | 'OUT'
  sourceType?: string | null
  sourceId?: number | null
}

export type FinanceStatementView = {
  accounts: FinanceAnalyticsView['accounts']
  accountId?: number | null
  accountName?: string | null
  bankName?: string | null
  accountNo?: string | null
  openingBalance: number
  closingBalance: number
  monthIncome: number
  monthExpense: number
  monthly: FinanceAnalyticsView['monthly']
  lines: FinanceStatementLine[]
}

export type PurchaseLine = {
  id?: number
  itemName: string
  qty: number
  unit: string
  amount: number
  sortOrder?: number
}

export type Purchase = {
  id: number
  code: string
  projectId?: number | null
  projectName?: string | null
  projectCode?: string | null
  supplierId: number
  supplierName: string
  warehouseId: number
  warehouseName: string
  itemName: string
  qty: number
  unit: string
  amount: number
  reason: string
  purchaseDate: string
  budgetDeducted: boolean
  availableAmount: number
  unpaidAmount: number
  workflowStatus?: string | null
  workflowStatusLabel?: string | null
  lines?: PurchaseLine[]
  createdAt: string
}

export type PurchasePay = {
  id: number
  code: string
  purchaseId: number
  purchaseCode: string
  projectId?: number | null
  projectName?: string | null
  accountId: number
  accountName: string
  amount: number
  reason: string
  payDate: string
  ledgerPosted: boolean
  unpaidAmount: number
  workflowStatus?: string | null
  workflowStatusLabel?: string | null
  createdAt: string
}

export type Warehouse = {
  id: number
  name: string
  address?: string | null
  keeper?: string | null
  remark?: string | null
  enabled: boolean
  stockQty: number
  createdAt: string
}

export type Device = {
  id: number
  code: string
  name: string
  model?: string | null
  brand?: string | null
  serialNo?: string | null
  location: string
  remark?: string | null
  status: 'IN_USE' | 'REPAIRING' | 'MOVING' | 'SCRAPPED'
  statusLabel: string
  departmentId: number
  departmentName: string
  managerId?: number | null
  managerName?: string | null
  enabled: boolean
  photoUrl?: string | null
  createdAt: string
}

export type DeviceWorkOrder = {
  id: number
  code: string
  kind: 'REPAIR' | 'MOVE' | 'SCRAP'
  deviceId: number
  deviceCode: string
  deviceName: string
  reason: string
  applyDate: string
  targetLocation?: string | null
  targetDepartmentId?: number | null
  targetDepartmentName?: string | null
  applied: boolean
  workflowStatus?: string | null
  workflowStatusLabel?: string | null
  files?: Attachment[]
  createdAt: string
  grade?: string | null
  customerName?: string | null
  customerAddress?: string | null
  contactName?: string | null
  contactPhone?: string | null
  instrumentName?: string | null
  instrumentModel?: string | null
  brand?: string | null
  fault?: string | null
  clientOrg?: string | null
  accountManager?: string | null
  intakeRemark?: string | null
  clientCustomerId?: number | null
  clientCustomerName?: string | null
  serviceCustomerId?: number | null
  serviceCustomerName?: string | null
}

export type OaKind = 'interview' | 'onboard' | 'transfer' | 'promotion' | 'resign' | 'salary' | 'seal' | 'qualify' | 'trip' | 'leave' | 'loan' | 'loan-pay' | 'loan-repay'

export type OaResumeLine = {
  id?: number
  title?: string | null
  subtitle?: string | null
  extra?: string | null
  startYm?: string | null
  endYm?: string | null
}

export type OaInterview = {
  birthDate?: string | null
  ethnicity?: string | null
  maritalStatus?: string | null
  maritalStatusLabel?: string | null
  email?: string | null
  hukouAddress?: string | null
  currentAddress?: string | null
  school?: string | null
  major?: string | null
  highestEducation?: string | null
  educationNote?: string | null
  educationLines?: OaResumeLine[]
  workLines?: OaResumeLine[]
  familyLines?: OaResumeLine[]
  trainingLines?: OaResumeLine[]
}

export type OaSeal = {
  belongCompany?: string | null
  sealTypes?: string[]
  copyCount?: string | null
}

export type OaQualify = {
  lendDate?: string | null
  returnDate?: string | null
  companies?: string[]
  items?: string[]
  companyAuditorId?: number | null
  companyAuditorName?: string | null
  financeAuditorId?: number | null
  financeAuditorName?: string | null
  contractAuditorId?: number | null
  contractAuditorName?: string | null
  companyAuditorNote?: string | null
  financeAuditorNote?: string | null
  contractAuditorNote?: string | null
  contractDesc?: string | null
  otherCertNote?: string | null
}

export type OaTrip = {
  companionIds?: number[]
  companionNames?: string[]
  fromCity?: string | null
  toCity?: string | null
  travelMode?: string | null
  travelModeLabel?: string | null
  plateNo?: string | null
  mileage?: number | null
  returnPlanDate?: string | null
  reviewTime?: string | null
  actualReturnDate?: string | null
  durationHours?: number | null
  remark?: string | null
}

export type OaLeave = {
  holidayDays?: number | null
  includeCompensatory?: boolean | null
  compensatoryDays?: number | null
  month?: string | null
  leaveType?: string | null
  leaveTypeLabel?: string | null
  kinship?: string | null
  kinshipLabel?: string | null
  endDate?: string | null
  leaveDays?: number | null
  actualEndDate?: string | null
  actualDays?: number | null
}

export type OaLoan = {
  linkProject?: boolean | null
  projectId?: number | null
  projectName?: string | null
  projectCode?: string | null
  customerId?: number | null
  customerName?: string | null
  belongCompany?: string | null
  amount?: number | null
  payAmount?: number | null
  accountId?: number | null
  accountName?: string | null
  accountNo?: string | null
  bankName?: string | null
  loanRequestId?: number | null
  loanCode?: string | null
  repayAmount?: number | null
  paidTotal?: number | null
  pendingPayTotal?: number | null
  remainingPay?: number | null
  repayTotal?: number | null
  pendingRepayTotal?: number | null
  remainingRepay?: number | null
  repayStatus?: string | null
  loanStatus?: string | null
}

export type OaRequest = {
  id: number
  code: string
  kind: string
  kindLabel: string
  applicantName: string
  staffId?: number | null
  staffName?: string | null
  gender?: string | null
  genderLabel?: string | null
  mobile?: string | null
  idNumber?: string | null
  departmentId?: number | null
  departmentName?: string | null
  position?: string | null
  jobLevel?: string | null
  targetDepartmentId?: number | null
  targetDepartmentName?: string | null
  targetPosition?: string | null
  targetLevel?: string | null
  expectedDate?: string | null
  managerId?: number | null
  managerName?: string | null
  handoverUserId?: number | null
  handoverName?: string | null
  probationMonths?: number | null
  resignType?: string | null
  resignTypeLabel?: string | null
  salary?: number | null
  salaryLines?: OaSalaryLine[]
  interview?: OaInterview | null
  seal?: OaSeal | null
  qualify?: OaQualify | null
  trip?: OaTrip | null
  loan?: OaLoan | null
  leave?: OaLeave | null
  reason: string
  isPublic?: boolean
  submitSource?: string | null
  loginUsername?: string | null
  hasLoginPassword?: boolean
  applied?: boolean
  workflowStatus?: string | null
  workflowStatusLabel?: string | null
  files?: Attachment[]
  createdAt: string
}

export type StockTxn = {
  id: number
  warehouseId: number
  warehouseName: string
  txnDate: string
  direction: 'IN' | 'OUT'
  directionLabel: string
  itemName: string
  qty: number
  unit: string
  amount?: number | null
  summary: string
  sourceType: string
  sourceId?: number | null
  projectId?: number | null
  projectName?: string | null
  purchaseId?: number | null
  purchaseCode?: string | null
  createdAt: string
}

export type StockItem = {
  itemName: string
  unit: string
  stockQty: number
}

export type StockOutLine = {
  id?: number
  itemName: string
  qty: number
  unit: string
  amount?: number | null
  sortOrder?: number
}

export type StockOut = {
  id: number
  code: string
  kind: string
  warehouseId: number
  warehouseName: string
  targetWarehouseId?: number | null
  targetWarehouseName?: string | null
  purchaseId?: number | null
  purchaseCode?: string | null
  projectId?: number | null
  projectName?: string | null
  projectCode?: string | null
  itemName: string
  qty: number
  unit: string
  amount?: number | null
  reason: string
  outDate: string
  ledgerPosted: boolean
  budgetDeducted: boolean
  onHand: number
  availableAmount: number
  workflowStatus?: string | null
  workflowStatusLabel?: string | null
  lines?: StockOutLine[]
  createdAt: string
}

export type PurchaseAnalyticsView = {
  kpis: {
    purchaseCount: number
    purchaseAmount: number
    paidAmount: number
    unpaidAmount: number
    linkedAmount: number
    unlinkedAmount: number
  }
  suppliers: Array<{
    supplierId: number
    supplierName: string
    purchaseCount: number
    purchaseAmount: number
    unpaidAmount: number
  }>
  monthly: Array<{ month: string; purchaseAmount: number; paidAmount: number }>
  recent: Array<{
    id: number
    code: string
    itemName: string
    supplierName: string
    purchaseDate: string
    amount: number
    unpaidAmount: number
    linked: boolean
  }>
}

export type WarehouseAnalyticsView = {
  kpis: {
    warehouseCount: number
    stockQty: number
    monthInQty: number
    monthOutQty: number
    purchaseInQty: number
    purchaseOutQty: number
    projectOutQty: number
  }
  warehouses: Array<{ warehouseId: number; name: string; inQty: number; outQty: number; stockQty: number }>
  items: Array<{ itemName: string; unit: string; inQty: number; outQty: number; stockQty: number }>
  monthly: Array<{ month: string; inQty: number; outQty: number }>
  recent: StockTxn[]
}

export type TechRecord = {
  id: number
  code: string
  kind: string
  kindLabel: string
  title: string
  extra: Record<string, unknown>
  isPublic: boolean
  submitSource?: string | null
  creatorName?: string | null
  workflowStatus?: string | null
  workflowStatusLabel?: string | null
  files: Attachment[]
  createdAt: string
}

export type DataViewType = 'TABLE' | 'CARD' | 'BOARD' | 'GANTT' | 'TREE' | 'CALENDAR'
export type DataViewRange = 'ALL' | 'MINE' | 'STARTED' | 'DONE' | 'RELATED'
export type DataViewHeight = 'COMPACT' | 'MEDIUM' | 'TALL'
export type DataViewVisibility = 'ALL' | 'ADMIN' | 'SELF'

export type DataViewDef = {
  id: number
  module: string
  name: string
  type: DataViewType
  remark?: string
  pinTop: boolean
  visibility: DataViewVisibility
  dataRange: DataViewRange
  rowHeight: DataViewHeight
  sortField?: string | null
  sortDir?: 'ASC' | 'DESC'
  searchEnabled: boolean
  queryEnabled: boolean
  fields: string[]
  extra?: Record<string, unknown>
  builtin: boolean
  createdById?: number | null
  createdByName?: string | null
}

export type DataViewField = {
  key: string
  title: string
}



