# 项目管理系统 — 付款申请设计

日期：2026-08-26  
状态：待实现（设计已口头确认）

## 目标

在侧栏「项目管理」正下方增加独立模块「付款申请」。每笔单据必须关联一个已有项目，走流程引擎审批；流程到达结束节点后，从该项目预算的**可用金额**扣减本单金额。列表能看到本单金额，并按项目汇总已付合计。

## 非目标

- 不开放公开办理
- 不把付款拆到预算明细科目（采购费、人工费等）
- 不在项目详情页做付款页签
- 不在提交时拦截超预算（可用金额不足仍可提交，扣减后允许为负）
- 不把付款写成预算变更历史
- 本期不接网银/实际出纳付款回单

## 入口与权限

- 侧栏：项目管理正下方，「付款申请」，图标用账本类即可
- 路由：`/payments` 列表，`/payments/new` 新建，`/payments/:id` 单据页（详情与编辑同一页，可写性由流程节点控制）
- 新权限模块：`PAYMENT`，中文名「付款申请」
- 权限：查看、新增、编辑、删除、导入导出
- 数据范围：全量 / 仅个人提交（`created_by`）
- `hasCreate = true`，`hasDataScope = true`，`canBePublic = false`
- 系统管理员默认全开；公开访客无此模块；普通用户种子权限默认不开，可在角色管理勾选
- 流程引擎绑定表单下拉增加「付款申请」

界面文案只用中文。

## 数据

表 `pm_payment`：

| 字段 | 说明 |
|---|---|
| id | 主键 |
| code | 申请编码，系统生成，唯一。规则：`PAY` + `yyyyMMdd` + 三位序号，如 `PAY20260826001`，冲突递增 |
| project_id | 必填，关联 `pm_project` |
| amount | 必填，`decimal(18,2)`，必须大于 0 |
| reason | 付款事由，必填 |
| supplier_id | 必填，关联供应商 |
| bank_account_id | 必填，必须属于所选供应商 |
| payee_name / bank_name / account_no / branch | 保存时从所选账户快照，之后不随供应商资料改动 |
| pay_method | `WIRE` 电汇 / `CHECK` 支票 / `CASH` 现金 / `OTHER` 其他 |
| expected_pay_date | 期望付款日，必填 |
| budget_deducted | 是否已扣预算，默认否 |
| created_by | 提交人 |
| created_at / updated_at | 时间 |

附件表 `pm_payment_file`，结构对齐项目附件（原名、url、size、contentType）。文件存到现有文件存储的付款子目录。

一个项目可有多笔付款申请。已付合计 = 该项目 `budget_deducted = true` 的 `amount` 之和，不另存字段。预算页只读展示已付合计；付款表单只读展示该项目当前可用金额与已付合计。

## 列表与表单

列表可搜索：申请编码、项目名称/编码、供应商简称/公司。列：编码、关联项目、供应商、金额、期望付款日、流程状态、是否已扣预算。有权限则新建、导入、导出。导出 CSV 与列表字段一致，不含附件。导入同字段；附件不进 CSV。

新建页：选项目后带出项目名称、编码；选供应商后带出该供应商银行账户下拉（默认账户优先）。带出开户名、开户行、账号，可改选该供应商其它账户。金额、事由、付款方式、期望付款日、发票/附件必填规则见上。保存即建单并 `workflowService.start(PAYMENT, id)`。

未发布任何付款申请流程时，新建返回中文错误：「请先在流程引擎发布付款申请流程」。

已扣预算后，金额与关联项目不可再改（流程字段权限也锁死这两项）。删除已扣预算单据时，先把金额加回该项目可用金额，再删单及流程实例。

## 流程

- `FormFieldCatalog` 支持 `PAYMENT`，字段：`projectId` 关联项目、`amount` 金额、`reason` 付款事由、`supplierId` 供应商、`bankAccountId` 收款账户、`payMethod` 付款方式、`expectedPayDate` 期望付款日、`attachments` 附件
- 启动时若无编码 `PAYMENT_INIT` 的已发布流程，则种子一条：开始 → 填写（发起人）→ 审批（发起人）→ 结束
- 首页待办打开 `/payments/:id`
- `WorkflowFormData` 提供上述字段供分支条件使用
- 流程到达结束节点且实例变为 `DONE` 时扣预算：若 `budget_deducted` 已为真则跳过；否则 `available_amount -= amount`，置 `budget_deducted = true`。同一单不双扣
- 结束前退回不扣。结束后不允许把流程从结束点退回改金额

## 错误处理

- 项目不存在或当前用户按项目数据范围不可见：不能关联
- 供应商无银行账户：必须先在供应商资料补账户
- 所选账户不属于该供应商：拒绝保存
- 删除项目若仍有付款申请：拒绝删除项目（中文提示有未清理的付款申请）
- 扣减时若项目尚无预算行：先 `ensureExists` 再扣

## 主要文件归属

- 后端：`backend/src/main/java/com/projectmgmt/payment/`（实体、仓库、服务、控制器、DTO）
- 前端：`frontend/src/pages/PaymentsPage.tsx`、`PaymentFormPage.tsx`；API 放 `frontend/src/api/index.ts`
- 权限/流程/导入导出接到现有 `FormModule`、`SchemaRepairer`、`WorkflowsPage`、`CsvImportService`、`AppLayout`、`App.tsx`
- 测试：`backend/src/test/java/com/projectmgmt/PaymentApiTests.java`

## 测试要点

- 有权限可建单，待办出现付款申请
- 流程办到结束：该项目可用金额减少本单金额，单据「已扣预算」
- 再办结或重复进入结束：不双扣
- 删除已通过单：可用金额加回
- 其它角色未授权看不到菜单；公开配置不含付款申请
