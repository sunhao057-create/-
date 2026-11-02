# 公开办理页 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 未登录访客可打开 `/public`，其展示/填报/查询完全由内置角色「公开访客」控制，样式与后台一致。

**Architecture:** 后端新增 `com.projectmgmt.portal` 读 `PUBLIC_GUEST` 权限并提供公开 CRUD 子集；记录用 `is_public` + `submit_source` 隔离。前端在 Guard 外挂 `PublicLayout`，角色树在编辑公开访客时切换为公开三项。

**Tech Stack:** Java 17 + Spring Boot + Spring Security Session；React + Vite + Ant Design 6；MockMvc 集成测试。

## Global Constraints

- 用户可见文案只用中文；标识符可用英文（`PUBLIC_GUEST`、`admin`）
- 不要用 Coze
- 不要 git commit，除非用户明确要求提交
- 公开接口不返回预算、负责人账号、银行账号
- 公开填报不启动流程引擎
- 仅项目 / 客户 / 供应商可公开；预算、账号、角色、流程、品牌不可
- 测试命令：`backend\mvnw.cmd -q "-Dtest=PublicPortalApiTests,RoleApiTests" test`（在 `D:\项目管理\backend`）
- 前端开发：`http://localhost:5173/`，后端 `8080`，登录 `admin` / `Admin123!`
- 改完代码覆盖更新 `D:\智襄网的工作动态\daily\2026-08-26.md` 项目管理节

## File map

**Create**

- `backend/src/main/java/com/projectmgmt/portal/PublicAccess.java`
- `backend/src/main/java/com/projectmgmt/portal/PublicModuleFlags.java`
- `backend/src/main/java/com/projectmgmt/portal/PublicConfigView.java`
- `backend/src/main/java/com/projectmgmt/portal/PublicCustomerView.java`
- `backend/src/main/java/com/projectmgmt/portal/PublicSupplierView.java`
- `backend/src/main/java/com/projectmgmt/portal/PublicProjectView.java`
- `backend/src/main/java/com/projectmgmt/portal/PublicCustomerSubmitRequest.java`
- `backend/src/main/java/com/projectmgmt/portal/PublicSupplierSubmitRequest.java`
- `backend/src/main/java/com/projectmgmt/portal/PublicProjectSubmitRequest.java`
- `backend/src/main/java/com/projectmgmt/portal/SubmitRateLimiter.java`
- `backend/src/main/java/com/projectmgmt/portal/PublicPortalService.java`
- `backend/src/main/java/com/projectmgmt/portal/PublicPortalController.java`
- `backend/src/test/java/com/projectmgmt/PublicPortalApiTests.java`
- `frontend/src/layouts/PublicLayout.tsx`
- `frontend/src/pages/public/PublicHomePage.tsx`
- `frontend/src/pages/public/PublicProjectsPage.tsx`
- `frontend/src/pages/public/PublicCustomersPage.tsx`
- `frontend/src/pages/public/PublicSuppliersPage.tsx`
- `frontend/src/api/public.ts`

**Modify**

- `PermissionService.java` — `PUBLIC_GUEST_CODE`
- `RoleService.java` — 种子、保存公开标志、禁止删除已有逻辑覆盖访客
- `SysRolePermission.java` / `FormPermissionView.java` / `FormPermissionRequest.java`
- `FormModule.java` — `canBePublic()`
- `SchemaRepairer.java` — 权限列 + `is_public` / `submit_source`
- `SecurityConfig.java` — `/api/public/**` permitAll
- `ApiException.java` — `tooManyRequests`
- `Project.java` `Customer.java` `Supplier.java` 及对应 Service/Controller/DTO
- `UserService.java` — 拒绝分配 `PUBLIC_GUEST`
- `frontend/src/types.ts` `PermissionTree.tsx` `RolesPage.tsx` `UsersPage.tsx`
- 后台项目/客户/供应商表单加「允许公开」
- `App.tsx` `LoginPage.tsx`

---

### Task 1: 公开访客角色种子与不可分配

**Files:**
- Modify: `backend/src/main/java/com/projectmgmt/role/PermissionService.java`
- Modify: `backend/src/main/java/com/projectmgmt/role/RoleService.java`
- Modify: `backend/src/main/java/com/projectmgmt/user/UserService.java`
- Modify: `backend/src/test/java/com/projectmgmt/RoleApiTests.java`
- Test: `backend/src/test/java/com/projectmgmt/PublicPortalApiTests.java`

**Interfaces:**
- Consumes: 现有 `RoleService.ensureBuiltin()`、`UserService` 设角色
- Produces: `PermissionService.PUBLIC_GUEST_CODE = "PUBLIC_GUEST"`；启动后库中存在该角色且 builtin

- [ ] **Step 1: 写失败测试**

在 `PublicPortalApiTests.java`：

```java
package com.projectmgmt;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectmgmt.role.PermissionService;
import com.projectmgmt.role.RoleRepository;
import com.projectmgmt.security.AuthUser;
import com.projectmgmt.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PublicPortalApiTests {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;

    @Test
    void publicGuestRoleIsBuiltinAndCannotBeAssignedOrDeleted() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        var guest = roleRepository.findByCode(PermissionService.PUBLIC_GUEST_CODE).orElseThrow();

        mockMvc.perform(get("/api/roles").with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].name").exists())
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].builtin").value(org.hamcrest.Matchers.hasItem(true)));

        mockMvc.perform(delete("/api/roles/" + guest.getId()).with(user(new AuthUser(admin))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("内置角色不能删除"));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"guesttry","displayName":"试","role":"PUBLIC_GUEST","password":"Guest1234!"}
                                """)
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("不能把账号设为公开访客"));
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `.\mvnw.cmd -q "-Dtest=PublicPortalApiTests" test`  
Expected: 编译失败或 `PUBLIC_GUEST` 找不到 / 能分配成功

- [ ] **Step 3: 最小实现**

`PermissionService` 增加 `public static final String PUBLIC_GUEST_CODE = "PUBLIC_GUEST";`

`RoleService.RESERVED_CODES` 加入该编码。`ensureBuiltin()` 增加：

```java
ensureRole(PermissionService.PUBLIC_GUEST_CODE, "公开访客", "未登录访客的公开办理权限", module -> false);
```

`UserService` 在 `create`/`update` 设角色前：

```java
if (PermissionService.PUBLIC_GUEST_CODE.equalsIgnoreCase(request.role())) {
    throw ApiException.badRequest("不能把账号设为公开访客");
}
```

`RoleService.requireByCode` 仍可用于读公开访客权限。分配账号必须走上面的校验。

- [ ] **Step 4: 再跑测试**

Expected: PASS

- [ ] **Step 5: Commit**

跳过（用户未要求提交）

---

### Task 2: 权限列 公开/展示/填报/查询

**Files:**
- Modify: `SysRolePermission.java`、`FormPermissionView.java`、`FormPermissionRequest.java`、`FormModule.java`、`RoleService.replacePermissions`、`SchemaRepairer.java`、`frontend/src/types.ts`、`PermissionTree.tsx`、`RolesPage.tsx`
- Test: 扩展 `PublicPortalApiTests`

**Interfaces:**
- Consumes: `PUBLIC_GUEST` 角色
- Produces: `FormPermissionView` 增加 `boolean publicAccess, publicDisplay, publicSubmit, publicQuery`；`FormModule.canBePublic()` 仅 PROJECT/CUSTOMER/SUPPLIER 为 true

- [ ] **Step 1: 写失败测试**

```java
@Test
void guestPublicFlagsPersistAndOtherRolesIgnoreThem() throws Exception {
    var admin = userRepository.findByUsername("admin").orElseThrow();
    var guest = roleRepository.findByCode(PermissionService.PUBLIC_GUEST_CODE).orElseThrow();

    mockMvc.perform(put("/api/roles/" + guest.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name":"公开访客","description":"未登录访客的公开办理权限","permissions":[
                              {"module":"CUSTOMER","publicAccess":true,"publicDisplay":true,"publicSubmit":true,"publicQuery":false,
                               "view":false,"create":false,"edit":false,"delete":false,"importExport":false}
                            ]}
                            """)
                    .with(user(new AuthUser(admin))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.permissions[?(@.module=='CUSTOMER')].publicAccess").value(org.hamcrest.Matchers.hasItem(true)))
            .andExpect(jsonPath("$.data.permissions[?(@.module=='CUSTOMER')].publicDisplay").value(org.hamcrest.Matchers.hasItem(true)))
            .andExpect(jsonPath("$.data.permissions[?(@.module=='CUSTOMER')].publicSubmit").value(org.hamcrest.Matchers.hasItem(true)))
            .andExpect(jsonPath("$.data.permissions[?(@.module=='CUSTOMER')].publicQuery").value(org.hamcrest.Matchers.hasItem(false)));

    var userRole = roleRepository.findByCode(PermissionService.USER_CODE).orElseThrow();
    mockMvc.perform(put("/api/roles/" + userRole.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name":"普通用户","permissions":[
                              {"module":"CUSTOMER","view":true,"create":true,"edit":true,"publicAccess":true,"publicDisplay":true,
                               "delete":false,"importExport":false}
                            ]}
                            """)
                    .with(user(new AuthUser(admin))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.permissions[?(@.module=='CUSTOMER')].publicAccess").value(org.hamcrest.Matchers.hasItem(false)));
}
```

取消公开时三项必须存否：在 `replacePermissions` 中若 `!publicAccess` 则 display/submit/query 全 false；若未勾公开却勾了子项，只认公开为否。

- [ ] **Step 2: 跑测试确认失败**

- [ ] **Step 3: 实现**

列名：`can_public`、`can_public_display`、`can_public_submit`、`can_public_query`，`tinyint(1) NOT NULL DEFAULT 0`。`SchemaRepairer` 里 `addColumnIfMissing`。

`FormModule.canBePublic()`：`this == PROJECT || this == CUSTOMER || this == SUPPLIER`。

`replacePermissions`：仅当 `PermissionService.PUBLIC_GUEST_CODE.equals(role.getCode()) && module.canBePublic()` 时写入四项；其它角色四项强制 false。公开访客的 view/create/edit/delete/importExport 保持 false。

`FormPermissionView.from` 读四项；`none`/`allAllowed` 的公开四项均为 false（管理员员工权限不等于公开页）。

前端：`FormPermission` 加同样四字段。`PermissionTree` 增加 `variant: 'staff' | 'guest'`。`guest` 时只渲染 `canBePublic` 模块（用 `hasPublicAccess` 从后端带上，或前端写死三模块），勾选公开后显示展示/填报/查询。`RolesPage` 在 `role.code === 'PUBLIC_GUEST'` 时 `variant="guest"`。账号角色下拉过滤 `code !== 'PUBLIC_GUEST'`。

- [ ] **Step 4: 测试通过**

- [ ] **Step 5: 跳过 commit**

---

### Task 3: 公开 config 接口与 Security 放行

**Files:**
- Create: `PublicModuleFlags.java` `PublicConfigView.java` `PublicAccess.java` `PublicPortalController.java`（先只 config）
- Modify: `SecurityConfig.java`

**Interfaces:**
- Produces: `GET /api/public/config` 匿名可访问，返回

```java
public record PublicModuleFlags(boolean publicAccess, boolean display, boolean submit, boolean query) {}
public record PublicConfigView(PublicModuleFlags project, PublicModuleFlags customer, PublicModuleFlags supplier) {}
```

`PublicAccess.flags(FormModule module)`：读 `PUBLIC_GUEST` 对应权限；角色不存在则全 false。`display/submit/query` 仅当 `publicAccess` 为 true 时才为 true。

Controller：

```java
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicPortalController {
    private final PublicPortalService portal;
    @GetMapping("/config")
    public ApiResponse<PublicConfigView> config() {
        return ApiResponse.ok(portal.config());
    }
}
```

Security：`.requestMatchers("/api/public/**").permitAll()` 放在 `/api/**` authenticated 之前。

测试：无 session `GET /api/public/config` 200；Task 2 打开客户展示后 config.customer.display 为 true。

- [ ] 写测试 → 跑红 → 实现 → 跑绿 → 跳过 commit

---

### Task 4: 客户公开列表/查询/填报

**Files:**
- Modify: `Customer.java` 加 `boolean isPublic`（列 `is_public`）、`String submitSource`（列 `submit_source` varchar(16) 默认 `ADMIN`）
- Modify: `SchemaRepairer` 给 `crm_customer` 补列
- Modify: `CustomerService` 后台保存读写 `isPublic`
- Create: `PublicCustomerView` `PublicCustomerSubmitRequest` 方法在 `PublicPortalService`
- Modify: `PublicPortalController`

**Interfaces:**
- `PublicCustomerView(Long id, String shortName, String companyName, String code, String level)` 无银行账号
- `PublicCustomerSubmitRequest`：`shortName`、`companyName`、`contactName`、`contactPhone` 均 `@NotBlank`
- `GET /api/public/customers?keyword=`
  - 未开展示且未开查询：400「该内容未开放公开办理」
  - 仅查询未开展示：keyword 空则 `[]`；有 keyword 则按 code 或 companyName/shortName 包含匹配，且 `is_public=true`
  - 开展示：返回全部 `is_public=true`，keyword 可选过滤
- `GET /api/public/customers/{id}`：非公开或不存在 404「内容不存在」；需展示或查询已开
- `POST /api/public/customers`：未开填报 400「该表单未开放填报」；成功 `isPublic=false`、`submitSource=PUBLIC`；编码 `PUB` + 年月日 + 4 位；联系人一条；无银行账号；不跑工作流

测试要点：

1. 后台建公开客户能出现在匿名列表；未公开的列表和按编码查询都没有
2. 匿名 POST 成功后库中 `is_public=0`，列表查不到
3. 未开填报 POST 400

- [ ] 写测试 → 跑红 → 实现 → 跑绿 → 跳过 commit

---

### Task 5: 供应商公开列表/查询/填报

与 Task 4 对等。

- `PublicSupplierView(Long id, String shortName, String companyName, String code, String category)`
- 填报：`shortName`、`companyName`、`contactName`、`contactPhone`；`category` 固定 `"原材料"`
- 路径 `/api/public/suppliers`
- 表 `crm_supplier` 同样两列
- 不跑工作流、不返回银行账号

- [ ] 写测试 → 跑红 → 实现 → 跑绿 → 跳过 commit

---

### Task 6: 项目公开列表/查询/填报

**Files:** `Project.java`、`PublicProjectView`、`PublicProjectSubmitRequest`、`PublicPortalService`、`RegionRepository`、`UserRepository`

**Interfaces:**
- View：`id, name, code, companyName, status, overview`（status 用现有中文序列化若后台已是中文枚举名则保持一致）
- Submit：`name, companyName, overview, contactName, contactPhone`
- 编码 `PUB` + `yyyyMMdd` + 4 位，查 `pm_project.code` 唯一
- 客户：`companyName` 精确匹配已有客户；否则新建未公开客户（简称截断 50，等级「一般重要」，一条联系人），`submitSource=PUBLIC`
- `manager` = `userRepository.findByUsername("admin")`；`region` = `regionRepository.findAll()` 按 id 升序第一条。缺一则 400「暂无法提交，请联系管理员」
- `companyCode`/`companyLevel`：若新建客户则用客户 code/level；若匹配已有客户则抄客户字段
- 不创建附件、不写预算、不调 `WorkflowService`

测试：公开项目出现在列表；未公开 404；POST 后未公开；无员工下拉相关字段不在 JSON 里。

- [ ] 写测试 → 跑红 → 实现 → 跑绿 → 跳过 commit

---

### Task 7: 填报限流

**Files:** `SubmitRateLimiter.java`、`ApiException.java`、`PublicPortalService`

```java
public static ApiException tooManyRequests(String message) {
    return new ApiException(429, message);
}
```

`SubmitRateLimiter.check(String ip, String module)`：滑动窗口 10 分钟最多 10 次；超限 `throw ApiException.tooManyRequests("提交过于频繁，请稍后再试")`。内存 `ConcurrentHashMap`，单机足够。Controller 从 `X-Forwarded-For` 第一段或 `request.getRemoteAddr()` 取 IP。

测试：可用反射或把 limiter 的窗口做成可测；至少单测 limiter 第 11 次抛错。若集成测试太慢，`SubmitRateLimiterTests` 纯单测即可。

- [ ] 写测试 → 跑红 → 实现 → 跑绿 → 跳过 commit

---

### Task 8: 后台「允许公开」与列表筛选

**Files:** 项目/客户/供应商 的 View DTO、create/update payload、前端 `ProjectCreatePage.tsx` `CustomersPage.tsx` `SuppliersPage.tsx` `ProjectsPage.tsx`

每条保存增加 `isPublic` 勾选，默认 false。列表增加筛选：全部 / 已公开 / 未公开（query `publicFilter=ALL|YES|NO`）。公开页提交的记录在后台可见，便于勾选公开。

测试：管理员 PUT 客户 `isPublic:true` 后匿名列表能看到。

- [ ] 写测试 → 实现前后台表单 → 跑绿 → 跳过 commit

---

### Task 9: 前端公开页

**Files:** 见 File map 的 frontend create 项；`App.tsx`；`LoginPage.tsx`

**路由（Guard 外）：**

```tsx
<Route element={<PublicLayout />}>
  <Route path="/public" element={<PublicHomePage />} />
  <Route path="/public/projects" element={<PublicProjectsPage />} />
  <Route path="/public/projects/new" element={<PublicProjectsPage mode="create" />} />
  <Route path="/public/projects/query" element={<PublicProjectsPage mode="query" />} />
  <Route path="/public/projects/:id" element={<PublicProjectsPage mode="detail" />} />
  {/* customers / suppliers 同样 */}
</Route>
```

`PublicLayout`：复制后台外壳 class（`app-shell` `app-sider` `app-header` `app-content`），菜单只渲染 config 里 `publicAccess` 的模块；顶栏右侧 `<Button type="link" onClick={() => navigate('/login')}>登录后台</Button>`。无用户、无退出。沿用 `ThemeProvider`（已在 App 根上）。

未开放路径：Card 内「该内容未开放公开办理」。

列表页：`PreviewTable` 或 `Table`，列按 spec；空状态「暂无数据」。填报成功 `message.success('已提交，待后台审核后公开')`。

登录页 `auth-card` 底部增加「公开办理」按钮，`navigate('/public')`。

客户/供应商公开表单：简称、公司名、联系人、电话；无银行账号。项目：名称、公司名称、概况、联系人、电话。

`frontend/src/api/public.ts` 用现有 `http` 封装；公开接口不带登录也可（cookie 可有可无）。

浏览器验证：

1. 未开任何公开：`/public` 显示暂未开放
2. 后台把公开访客的客户：公开+展示+填报 打开，一条客户勾允许公开 → 刷新公开页能看到列表、能提交、提交后列表仍无该新行
3. 登录页有入口；主题切换后公开页颜色跟着变

- [ ] 实现页面 → 浏览器走完上述 3 步 → 跳过 commit

---

### Task 10: 回归与清单

- [ ] `.\mvnw.cmd -q "-Dtest=PublicPortalApiTests,RoleApiTests,CustomerApiTests,SupplierApiTests,ProjectApiTests" test` 全过
- [ ] 更新 `D:\智襄网的工作动态\daily\2026-08-26.md` 项目管理节：公开办理页进行中或完成，列出关键文件

---

## Spec coverage

| Spec 条目 | Task |
|---|---|
| PUBLIC_GUEST 内置、不能删、不能分配 | 1 |
| 权限树公开/展示/填报/查询，其它角色忽略 | 2 |
| `/public` 外壳与登录入口 | 9 |
| 记录 is_public、submit_source | 4–6、8 |
| 对外字段与项目占位负责人/区域 | 6 |
| GET/POST `/api/public/**`、Security | 3–6 |
| 未开功能中文错误、404 不泄露 | 4–6 |
| 限流 10 次 / 10 分钟 | 7 |
| 不跑工作流、无银行账号 | 4–6 |
| 后台勾选允许公开 | 8 |
| 测试清单 | 1–7、10 |
