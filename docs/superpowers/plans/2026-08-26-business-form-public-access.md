# 业务表单公开访问 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Do not git commit unless the user asks.

**Goal:** 业务表单（有数据范围且能新增）在公开访客角色里可勾公开权限；付款申请补齐与项目/客户/供应商相同的公开办理。

**Architecture:** `FormModule.canBePublic()` 改为 `hasDataScope() && hasCreate()`。付款实体补 `isPublic`/`submitSource`。公开配置增加 `payment` 与 `modules` 数组。公开付款 API 放在现有 `portal` 包，创建逻辑复用 `PaymentService.createPublic`。前端公开页单独做付款列表/填报，首页和侧栏按 `modules` 渲染。

**Tech Stack:** Java 17、Spring Boot、React、Ant Design、MySQL/H2。测试用 `backend\mvnw.cmd`。

## Global Constraints

- 界面文案只用中文
- 不把预算、账号、角色、流程、系统名称/图标、主题开放给公开页
- 公开填报付款只能选已公开项目和已公开供应商
- 公开提交默认不公开、`submitSource=PUBLIC`、启动已发布付款流程、结束才扣预算
- 公开详情不展示预算扣减、可用金额、已付合计、流程内部状态
- 不提交 git commit

Spec: `docs/superpowers/specs/2026-08-26-business-form-public-access-design.md`

---

## File map

| 职责 | 路径 |
|---|---|
| 可公开规则 | `backend/src/main/java/com/projectmgmt/role/FormModule.java` |
| 前端与规则对齐 | `frontend/src/role/modules.ts` |
| 付款公开列 | `Payment.java`、`SchemaRepairer.java`、`PaymentView.java`、`PaymentService.java`、`PaymentController.java` |
| 公开配置 | `PublicConfigView.java`、新建 `PublicModuleConfig.java`、`PublicPortalService.config` |
| 公开付款读写 | 新建 `PublicPaymentView.java`、`PublicPaymentLookupsView.java`；`PublicPortalService`、`PublicPortalController` |
| 公开前端 | `frontend/src/api/public.ts`、`PublicLayout.tsx`、`PublicHomePage.tsx`、`App.tsx`；新建 `PublicPaymentsPage.tsx`、`PublicPaymentForm.tsx` |
| 后台勾选 | `PaymentsPage.tsx`、`PaymentFormPage.tsx`、`types.ts` |
| 测试 | `RoleApiTests.java`、`PublicPortalApiTests.java` |

---

### Task 1: 可公开规则

**Files:**
- Modify: `backend/src/main/java/com/projectmgmt/role/FormModule.java`
- Modify: `frontend/src/role/modules.ts`
- Test: `backend/src/test/java/com/projectmgmt/RoleApiTests.java`

**Interfaces:**
- Produces: `FormModule.canBePublic()` 对 PROJECT/PAYMENT/CUSTOMER/SUPPLIER 为 true，其余为 false；前端 `hasPublicAccess: item.hasDataScope && item.hasCreate`

- [ ] **Step 1: Write the failing test**

在 `RoleApiTests.adminCanManageRolesAndBuiltinUserCannotViewUsers` 的 PAYMENT 断言后追加：

```java
        mockMvc.perform(get("/api/roles").with(user(new AuthUser(admin))))
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].permissions[?(@.module=='PAYMENT')].hasPublicAccess")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].permissions[?(@.module=='PROJECT')].hasPublicAccess")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].permissions[?(@.module=='BUDGET')].hasPublicAccess")
                        .value(org.hamcrest.Matchers.hasItem(false)))
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].permissions[?(@.module=='USER')].hasPublicAccess")
                        .value(org.hamcrest.Matchers.hasItem(false)))
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].permissions[?(@.module=='WORKFLOW')].hasPublicAccess")
                        .value(org.hamcrest.Matchers.hasItem(false)))
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].permissions[?(@.module=='THEME')].hasPublicAccess")
                        .value(org.hamcrest.Matchers.hasItem(false)));
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd -q "-Dtest=RoleApiTests#adminCanManageRolesAndBuiltinUserCannotViewUsers" test`  
工作目录：`D:\项目管理\backend`  
Expected: FAIL，PAYMENT 的 `hasPublicAccess` 仍为 false。

- [ ] **Step 3: Write minimal implementation**

`FormModule.canBePublic()` 改为：

```java
    public boolean canBePublic() {
        return hasDataScope() && hasCreate();
    }
```

`frontend/src/role/modules.ts` 的 `emptyPermissions` 里：

```ts
    hasPublicAccess: Boolean(item.hasDataScope && item.hasCreate),
```

删掉原来写死的 `item.module === 'PROJECT' || ...`。

- [ ] **Step 4: Run test to verify it passes**

Run: 同上  
Expected: PASS。再跑 `.\mvnw.cmd -q "-Dtest=RoleApiTests" test` 全绿。

---

### Task 2: 付款单据允许公开

**Files:**
- Modify: `Payment.java`、`PaymentView.java`、`PaymentService.java`、`PaymentController.java`
- Modify: `SchemaRepairer.java`（`run` 里对 `pm_payment` 调已有 `ensurePublicColumns`）
- Modify: `frontend/src/types.ts`、`PaymentsPage.tsx`、`PaymentFormPage.tsx`

**Interfaces:**
- Consumes: Task 1 的 `canBePublic`
- Produces: `Payment.isPublic` / `submitSource`；`PaymentView` 增加 `boolean isPublic`、`String submitSource`；创建/更新 multipart 参数 `isPublic`（默认 false）；后台列表有「公开」列，单据页有「允许公开」勾选

- [ ] **Step 1: Write the failing test**

在 `PublicPortalApiTests` 增加（此时公开 API 还没有，先测后台）：

```java
    @Test
    void adminCanMarkPaymentPublic() throws Exception {
        var admin = admin();
        var manager = userRepository.findByUsername("liuxiaolin").orElseThrow();
        var customer = customerRepository.findByCode("133").orElseThrow();
        var region = regionRepository.findByName("华东地区").orElseThrow();
        var supplier = supplierRepository.findByCode("S01").orElseThrow();
        String supplierBody = mockMvc.perform(get("/api/suppliers/" + supplier.getId()).with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer bankId = JsonPath.read(supplierBody, "$.data.bankAccounts[0].id");
        String projectCode = "PAY-PUB-" + System.currentTimeMillis();
        String projectBody = mockMvc.perform(multipart("/api/projects")
                        .param("name", "付款公开项目")
                        .param("code", projectCode)
                        .param("customerId", String.valueOf(customer.getId()))
                        .param("managerId", String.valueOf(manager.getId()))
                        .param("regionId", String.valueOf(region.getId()))
                        .param("isPublic", "true")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer projectId = JsonPath.read(projectBody, "$.data.id");

        String created = mockMvc.perform(multipart("/api/payments")
                        .param("projectId", String.valueOf(projectId))
                        .param("amount", "100")
                        .param("reason", "公开标记")
                        .param("supplierId", String.valueOf(supplier.getId()))
                        .param("bankAccountId", String.valueOf(bankId))
                        .param("payMethod", "WIRE")
                        .param("expectedPayDate", "2026-09-01")
                        .param("isPublic", "true")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPublic").value(true))
                .andExpect(jsonPath("$.data.submitSource").value("ADMIN"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer paymentId = JsonPath.read(created, "$.data.id");

        mockMvc.perform(multipart("/api/payments/" + paymentId)
                        .with(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/payments/" + paymentId)
                                .param("projectId", String.valueOf(projectId))
                                .param("amount", "100")
                                .param("reason", "公开标记")
                                .param("supplierId", String.valueOf(supplier.getId()))
                                .param("bankAccountId", String.valueOf(bankId))
                                .param("payMethod", "WIRE")
                                .param("expectedPayDate", "2026-09-01")
                                .param("isPublic", "false"))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPublic").value(false));
    }
```

更稳妥：`update` 用 `MockMvcRequestBuilders.put` + `.param`，不要套在 `multipart("/api/payments/"+id)` 上。参照 `PaymentApiTests` 的 put 写法。若现有付款创建是 `multipart`，create 保持 multipart；update 用：

```java
        mockMvc.perform(multipart("/api/payments/" + paymentId)
                        .param("projectId", String.valueOf(projectId))
                        .param("amount", "100")
                        .param("reason", "公开标记")
                        .param("supplierId", String.valueOf(supplier.getId()))
                        .param("bankAccountId", String.valueOf(bankId))
                        .param("payMethod", "WIRE")
                        .param("expectedPayDate", "2026-09-01")
                        .param("isPublic", "false")
                        .with(org.springframework.http.HttpMethod.PUT)
                        .with(user(new AuthUser(admin))))
```

（Spring `multipart` + `.with(HttpMethod.PUT)` 与现有项目更新一致则沿用现有测试风格。）

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd -q "-Dtest=PublicPortalApiTests#adminCanMarkPaymentPublic" test`  
Expected: FAIL（没有 `isPublic` 字段或参数被忽略）。

- [ ] **Step 3: Write minimal implementation**

`Payment.java` 增加：

```java
    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    @Column(name = "submit_source", nullable = false, length = 16)
    private String submitSource = "ADMIN";
```

`SchemaRepairer.run` 增加：`ensurePublicColumns(connection, "pm_payment");`

`PaymentView` 构造与 `from` 增加 `boolean isPublic`、`String submitSource`。

`PaymentService.create` / `update` 增加 `Boolean isPublic`；create 时 `payment.setSubmitSource("ADMIN")`，`payment.setPublic(Boolean.TRUE.equals(isPublic))`；update 时按入参设置 `isPublic`。

`PaymentController` create/update 增加 `@RequestParam(required = false) Boolean isPublic`。

前端：`Payment` 类型加 `isPublic?`、`submitSource?`。列表加「公开」列。单据页 `Form.Item name="isPublic" valuePropName="checked"`「允许公开」，提交 `form.append('isPublic', values.isPublic ? 'true' : 'false')`。

- [ ] **Step 4: Run test to verify it passes**

Expected: PASS。`PaymentApiTests` 全绿（新字段有默认值，旧测试不传 `isPublic` 即为 false）。

---

### Task 3: 公开配置按模块返回

**Files:**
- Create: `backend/src/main/java/com/projectmgmt/portal/PublicModuleConfig.java`
- Modify: `PublicConfigView.java`、`PublicPortalService.config()`
- Modify: `frontend/src/api/public.ts`

**Interfaces:**
- Produces:

```java
public record PublicModuleConfig(String module, String moduleLabel, String path, PublicModuleFlags flags) {}

public record PublicConfigView(
        PublicModuleFlags project,
        PublicModuleFlags customer,
        PublicModuleFlags supplier,
        PublicModuleFlags payment,
        List<PublicModuleConfig> modules
) {}
```

`path`：PROJECT → `/public/projects`，PAYMENT → `/public/payments`，CUSTOMER → `/public/customers`，SUPPLIER → `/public/suppliers`。

`modules` 含全部 `canBePublic()` 模块，flags 来自 `PublicAccess.flags(module)`。

- [ ] **Step 1: Write the failing test**

在 `anonymousConfigReflectsGuestFlags` 末尾追加（或新测试）：

```java
        mockMvc.perform(get("/api/public/config"))
                .andExpect(jsonPath("$.data.payment.publicAccess").value(false))
                .andExpect(jsonPath("$.data.modules[?(@.module=='PAYMENT')].path")
                        .value(hasItem("/public/payments")))
                .andExpect(jsonPath("$.data.modules[?(@.module=='PAYMENT')].moduleLabel")
                        .value(hasItem("付款申请")));

        saveGuest(perm("PAYMENT", true, true, false, true, true));
        mockMvc.perform(get("/api/public/config"))
                .andExpect(jsonPath("$.data.payment.publicAccess").value(true))
                .andExpect(jsonPath("$.data.payment.display").value(true))
                .andExpect(jsonPath("$.data.payment.submit").value(false))
                .andExpect(jsonPath("$.data.payment.query").value(true))
                .andExpect(jsonPath("$.data.payment.export").value(true))
                .andExpect(jsonPath("$.data.modules[?(@.module=='PAYMENT')].flags.publicAccess")
                        .value(hasItem(true)));
```

- [ ] **Step 2: Run test to verify it fails**

Expected: FAIL，没有 `payment` / `modules`。

- [ ] **Step 3: Write minimal implementation**

`PublicPortalService.config()`：

```java
    public PublicConfigView config() {
        List<PublicModuleConfig> modules = FormModule.all().stream()
                .filter(FormModule::canBePublic)
                .map(module -> new PublicModuleConfig(
                        module.name(),
                        module.label(),
                        publicPath(module),
                        publicAccess.flags(module)
                ))
                .toList();
        return new PublicConfigView(
                publicAccess.flags(FormModule.PROJECT),
                publicAccess.flags(FormModule.CUSTOMER),
                publicAccess.flags(FormModule.SUPPLIER),
                publicAccess.flags(FormModule.PAYMENT),
                modules
        );
    }

    private String publicPath(FormModule module) {
        return switch (module) {
            case PROJECT -> "/public/projects";
            case PAYMENT -> "/public/payments";
            case CUSTOMER -> "/public/customers";
            case SUPPLIER -> "/public/suppliers";
            default -> "/public";
        };
    }
```

前端 `PublicConfig` 增加 `payment` 与 `modules`；缺省时 `payment` 用全关 flags，避免旧响应把首页打挂。

- [ ] **Step 4: Run tests**

`.\mvnw.cmd -q "-Dtest=PublicPortalApiTests#anonymousConfigReflectsGuestFlags" test` 以及整个 `PublicPortalApiTests`。Expected: PASS（旧的 `project/customer/supplier` 字段仍在）。

---

### Task 4: 公开付款 API

**Files:**
- Create: `PublicPaymentView.java`、`PublicPaymentLookupsView.java`（供应商项复用 `PublicSupplierView` 即可）
- Modify: `PublicPortalService.java`、`PublicPortalController.java`
- Modify: `PaymentService.java` 增加 `createPublic`
- Test: `PublicPortalApiTests.java`

**Interfaces:**
- `GET /api/public/payments/lookups` 必须写在 `GET /api/public/payments/{id}` **之前**
- `PaymentService.createPublic(projectId, amount, reason, supplierId, bankAccountId, payMethod, expectedPayDate, files)`：校验项目、供应商 `isPublic`；`isPublic=false`；`submitSource="PUBLIC"`；`createdBy=null`；`workflowService.start(PAYMENT, id)`
- `PublicPaymentView` 字段：id, code, projectId, projectName, projectCode, amount, reason, supplierId, supplierShortName, supplierCompanyName, payeeName, bankName, accountNo, branch, payMethod, payMethodLabel, expectedPayDate, attachments（`List<PublicAttachmentView>`）
- 未开放：list/get/lookups → `该内容未开放公开办理`；submit → `该表单未开放填报`
- 未公开单据 get → 404 `内容不存在`
- 仅查询无 keyword：空列表

- [ ] **Step 1: Write the failing tests**

同一测试类增加：

```java
    @Test
    void publicPaymentClosedAndSubmitFlow() throws Exception {
        saveGuest("[]");
        mockMvc.perform(get("/api/public/payments"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该内容未开放公开办理"));
        mockMvc.perform(get("/api/public/payments/lookups"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该内容未开放公开办理"));
        mockMvc.perform(multipart("/api/public/payments")
                        .param("projectId", "1")
                        .param("amount", "10")
                        .param("reason", "x")
                        .param("supplierId", "1")
                        .param("bankAccountId", "1")
                        .param("payMethod", "WIRE")
                        .param("expectedPayDate", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该表单未开放填报"));

        saveGuest(perm("PAYMENT", true, true, true, true, false));
        var admin = admin();
        var manager = userRepository.findByUsername("liuxiaolin").orElseThrow();
        var customer = customerRepository.findByCode("133").orElseThrow();
        var region = regionRepository.findByName("华东地区").orElseThrow();
        var supplier = supplierRepository.findByCode("S01").orElseThrow();
        supplier.setPublic(true);
        supplierRepository.save(supplier);
        Integer bankId = JsonPath.read(
                mockMvc.perform(get("/api/suppliers/" + supplier.getId()).with(user(new AuthUser(admin))))
                        .andReturn().getResponse().getContentAsString(),
                "$.data.bankAccounts[0].id");

        Integer privateProjectId = JsonPath.read(
                mockMvc.perform(multipart("/api/projects")
                                .param("name", "未公开项目")
                                .param("code", "PRIV-" + System.currentTimeMillis())
                                .param("customerId", String.valueOf(customer.getId()))
                                .param("managerId", String.valueOf(manager.getId()))
                                .param("regionId", String.valueOf(region.getId()))
                                .param("isPublic", "false")
                                .with(user(new AuthUser(admin))))
                        .andReturn().getResponse().getContentAsString(),
                "$.data.id");
        Integer publicProjectId = JsonPath.read(
                mockMvc.perform(multipart("/api/projects")
                                .param("name", "已公开项目")
                                .param("code", "PUBP-" + System.currentTimeMillis())
                                .param("customerId", String.valueOf(customer.getId()))
                                .param("managerId", String.valueOf(manager.getId()))
                                .param("regionId", String.valueOf(region.getId()))
                                .param("isPublic", "true")
                                .with(user(new AuthUser(admin))))
                        .andReturn().getResponse().getContentAsString(),
                "$.data.id");

        mockMvc.perform(get("/api/public/payments/lookups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projects[?(@.id==" + publicProjectId + ")]").exists())
                .andExpect(jsonPath("$.data.projects[?(@.id==" + privateProjectId + ")]").doesNotExist())
                .andExpect(jsonPath("$.data.suppliers[?(@.id==" + supplier.getId() + ")]").exists());

        mockMvc.perform(multipart("/api/public/payments")
                        .param("projectId", String.valueOf(privateProjectId))
                        .param("amount", "50")
                        .param("reason", "未公开项目")
                        .param("supplierId", String.valueOf(supplier.getId()))
                        .param("bankAccountId", String.valueOf(bankId))
                        .param("payMethod", "WIRE")
                        .param("expectedPayDate", "2026-09-01"))
                .andExpect(status().isBadRequest());

        String submitted = mockMvc.perform(multipart("/api/public/payments")
                        .param("projectId", String.valueOf(publicProjectId))
                        .param("amount", "80")
                        .param("reason", "访客付款事由")
                        .param("supplierId", String.valueOf(supplier.getId()))
                        .param("bankAccountId", String.valueOf(bankId))
                        .param("payMethod", "WIRE")
                        .param("expectedPayDate", "2026-09-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").exists())
                .andExpect(jsonPath("$.data.budgetDeducted").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer paymentId = JsonPath.read(submitted, "$.data.id");
        String code = JsonPath.read(submitted, "$.data.code");

        mockMvc.perform(get("/api/public/payments"))
                .andExpect(jsonPath("$.data[*].code").value(not(hasItem(code))));
        mockMvc.perform(get("/api/public/payments/" + paymentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("内容不存在"));

        mockMvc.perform(get("/api/payments/" + paymentId).with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submitSource").value("PUBLIC"))
                .andExpect(jsonPath("$.data.isPublic").value(false))
                .andExpect(jsonPath("$.data.budgetDeducted").value(false))
                .andExpect(jsonPath("$.data.workflowStatus").exists());

        mockMvc.perform(multipart("/api/payments/" + paymentId)
                        .param("projectId", String.valueOf(publicProjectId))
                        .param("amount", "80")
                        .param("reason", "访客付款事由")
                        .param("supplierId", String.valueOf(supplier.getId()))
                        .param("bankAccountId", String.valueOf(bankId))
                        .param("payMethod", "WIRE")
                        .param("expectedPayDate", "2026-09-01")
                        .param("isPublic", "true")
                        .with(org.springframework.http.HttpMethod.PUT)
                        .with(user(new AuthUser(admin))))
                .andExpect(jsonPath("$.data.isPublic").value(true));

        mockMvc.perform(get("/api/public/payments"))
                .andExpect(jsonPath("$.data[?(@.code=='" + code + "')].reason").value(hasItem("访客付款事由")));
        mockMvc.perform(get("/api/public/payments/" + paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountNo").exists())
                .andExpect(jsonPath("$.data.availableAmount").doesNotExist());
    }

    @Test
    void publicPaymentQueryOnlyNeedsKeyword() throws Exception {
        saveGuest(perm("PAYMENT", true, false, false, true, false));
        mockMvc.perform(get("/api/public/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
```

选未公开供应商的失败用例可写在同一测试里：`supplier.setPublic(false)` 后再 submit，期望 400。

- [ ] **Step 2: Run tests to verify they fail**

Expected: 404/不存在这些路径。

- [ ] **Step 3: Write minimal implementation**

`PaymentService.createPublic`：无 `require(PERMISSION)`；`project.isPublic()` 与 `supplier.isPublic()` 否则 `ApiException.badRequest("该内容未开放公开办理")`；银行账户必须属于该供应商。其余 `applyCore` 与 create 相同。`setPublic(false)`、`setSubmitSource("PUBLIC")`、`setCreatedBy(null)`，然后 `start`。

`PublicPortalService`：

- `paymentLookups()`：`requireSubmit(PAYMENT)`；项目 `projectRepository.findAll` 过滤 `isPublic`，映射为精简 `{id,name,code}`；供应商过滤 `isPublic`，用现有 `toPublicSupplier`
- `listPayments(keyword)`：`requireListable`；无 display 且 keyword 空 → 空列表；只返回 `isPublic`；keyword 匹配 code 或 reason
- `getPayment(id)`：`requireReadable`；非公开 → notFound
- `submitPayment(...)`：`requireSubmit`；`rateLimiter.check(ip, "PAYMENT")`；调用 `createPublic`

Controller：`lookups` 映射写在 `{id}` 之前。submit 用 `multipart`，参数与后台 create 相同。

- [ ] **Step 4: Run tests**

`.\mvnw.cmd -q "-Dtest=PublicPortalApiTests" test`  
Expected: PASS。必要时再跑 `PaymentApiTests`。

---

### Task 5: 公开办理前端

**Files:**
- Modify: `frontend/src/api/public.ts`、`PublicLayout.tsx`、`PublicHomePage.tsx`、`App.tsx`
- Create: `frontend/src/pages/public/PublicPaymentForm.tsx`、`frontend/src/pages/public/PublicPaymentsPage.tsx`

**Interfaces:**
- Consumes: Task 3 `config.modules` / `config.payment`；Task 4 APIs
- `publicApi.payments(keyword?)`、`payment(id)`、`submitPayment(FormData)`、`paymentLookups()`
- 路由：`/public/payments`、`/new`、`/query`、`/:id`

- [ ] **Step 1: 类型与 API**

`PublicConfig` 增加 `payment`、`modules?: { module, moduleLabel, path, flags }[]`。  
缺省：`EMPTY_CONFIG.payment = CLOSED`，`modules = []`。

```ts
  payments: (keyword?: string) =>
    unwrap<PublicPayment[]>(http.get('/api/public/payments', { params: { keyword } })),
  payment: (id: number) => unwrap<PublicPayment>(http.get(`/api/public/payments/${id}`)),
  submitPayment: (payload: FormData) =>
    unwrap<PublicPayment>(http.post('/api/public/payments', payload, { timeout: 60000 })),
  paymentLookups: () => unwrap<PublicPaymentLookups>(http.get('/api/public/payments/lookups')),
```

- [ ] **Step 2: 布局与首页**

`PublicLayout` 侧栏：若 `config.modules?.length`，按 `flags.publicAccess` 渲染（付款申请图标用 `AccountBookOutlined`）；否则保持原来三张卡片逻辑作兜底。`pageTitle` 对 `/public/payments` 返回「付款申请」。

`PublicHomePage`：优先 `config.modules.filter(m => m.flags.publicAccess)` 出 `ModuleCard`；`open` 判断改为 modules 任一公开，或旧三字段任一公开。

- [ ] **Step 3: 付款公开页**

`PublicPaymentsPage` 对齐 `PublicRecordsPage` 的 list/create/query/detail 开关，不要塞进 `Kind = project|customer|supplier`。

`PublicPaymentForm`：

- create：lookups 拉已公开项目/供应商；选供应商后选其 `bankAccounts`；字段与后台新建一致（无可用金额/已付合计）；提交成功 `message.success('已提交，待后台审核后公开')`
- view：只读展示 spec 中的公开字段，含收款账户

列表列：编码、项目、供应商、金额、期望付款日。导出 CSV 列与此一致。展示且填报时有「新建」。

`App.tsx` 在 PublicLayout 下增加四条路由。

- [ ] **Step 4: 浏览器核对**

1. 角色管理 → 公开访客 → 打开付款申请的公开、展示、填报、查询、导出  
2. `/public` 出现付款申请卡片和侧栏  
3. 填报（已公开项目+供应商）成功；后台能打开该单，默认未公开  
4. 勾「允许公开」保存后，公开列表能看到  
5. 关掉公开访客付款申请后，`/public/payments` 显示「该内容未开放公开办理」

改后端后重启 `spring-boot:run`（`.\mvnw.cmd -q spring-boot:run`，先释放 8080）。

---

### Task 6: 回归

- [ ] `.\mvnw.cmd -q "-Dtest=PublicPortalApiTests,RoleApiTests,PaymentApiTests" test` 全绿  
- [ ] 内部付款申请新建（不勾公开）仍走流程，公开列表不可见  
- [ ] 项目/客户/供应商公开办理原路径未坏

---

## Spec coverage

| Spec | Task |
|---|---|
| `canBePublic = hasDataScope && hasCreate` | 1 |
| 公开访客权限树出现付款申请 | 1（前端 hasPublicAccess） |
| `is_public` / `submit_source`、后台勾选 | 2 |
| config `payment` + `modules` | 3 |
| 公开 CRUD/lookups/限流/未公开不可见 | 4 |
| 公开页路由、卡片、填报、导出 | 5 |
| 以后新表单清单 | 规则在 Task 1；付款作对照一次做完 |

无 git commit 步骤（用户未要求）。
