# 付款申请 Implementation Plan

> **For agentic workers:** Execute inline in this session. User asked to implement now. Do not git commit unless asked.

**Goal:** 侧栏「项目管理」下增加独立「付款申请」，关联项目、走流程，结束时扣预算可用金额。

**Architecture:** 新 `FormModule.PAYMENT` + `payment` 包。创建时启动已发布流程；`WorkflowService` 在实例变为 `DONE` 时调用 `PaymentBudgetService` 扣减，删除已扣单据时冲回。前端列表 `/payments`、单据页 `/payments/:id`。

**Tech Stack:** Java 17、Spring Boot、React、Ant Design、MySQL/H2。

## Global Constraints

- 界面文案只用中文
- 不开放公开办理
- 不拆预算明细科目
- 超预算不拦截提交，扣减后可用金额可为负
- 不提交 git commit

## File map

Create under `backend/src/main/java/com/projectmgmt/payment/`: entity, file entity, enum, repo, view, service, budget hook, controller.

Modify: FormModule, SchemaRepairer, RoleService (USER 默认不开 PAYMENT), WorkflowService/FormFieldCatalog/WorkflowFormData, ProjectService.delete, BudgetView/BudgetService, FileStorageService, CsvTemplates/CsvImportService, frontend layout/routes/pages/types/api.

Test: `PaymentApiTests.java`

## Tasks

1. 权限枚举 + 库枚举补齐 PAYMENT
2. 付款实体/API/建单启动流程
3. 流程结束扣预算、删除冲回、项目删除拦截
4. 导入导出、预算页已付合计
5. 前端列表与单据页、侧栏、待办跳转
6. 回归测试并重启后端
