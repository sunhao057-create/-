# 流程节点超时提醒 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Do not git commit unless the user asks.

**Goal:** 审批节点可设小时/天超时；待办创建时记下截止时间；到期未办在待办列表和单据流程条标「已超时」。

**Architecture:** 超时配置存在流程图节点 JSON（`timeoutValue` + `timeoutUnit`）。创建审批 `WorkflowTask` 时写入 `dueAt`。列表和 runtime 用「PENDING 且 now > dueAt」判断超时，不引入定时任务。

**Tech Stack:** Java 17、Spring Boot、React、Ant Design、MySQL/H2。测试：`backend\mvnw.cmd`。

## Global Constraints

- 界面文案只用中文
- 不发邮件、短信、站内新消息
- 不自动同意、跳过或转交
- 开始、结束、抄送、分支、条件节点不设超时
- 已发出的待办截止时间不随以后改图而变
- 不提交 git commit

Spec: `docs/superpowers/specs/2026-08-27-workflow-node-timeout-reminder-design.md`

---

## File map

| 职责 | 路径 |
|---|---|
| 节点超时字段 | `backend/src/main/java/com/projectmgmt/workflow/WorkflowGraph.java` |
| 校验 | `WorkflowService.validateGraph` |
| 待办截止时间 | `WorkflowTask.java`、`SchemaRepairer.java`、`WorkflowService.createTasks` |
| 待办 API | `InboxController.java`、`InboxItemView.java`、`WorkflowService.inbox` |
| 流程条数据 | `WorkflowRuntimeView.java`、`WorkflowService.runtimeOf` / `emptyRuntime` |
| 设计器 | `frontend/src/pages/WorkflowsPage.tsx`、`frontend/src/types.ts` |
| 待办/流程条 UI | `InboxPage.tsx`、`WorkflowBar.tsx`、`frontend/src/api/index.ts` |
| 测试 | `backend/src/test/java/com/projectmgmt/WorkflowApiTests.java` |

---

### Task 1: 节点超时配置与校验

**Files:**
- Modify: `backend/src/main/java/com/projectmgmt/workflow/WorkflowGraph.java`
- Modify: `backend/src/main/java/com/projectmgmt/workflow/WorkflowService.java`（`validateGraph`）
- Test: `backend/src/test/java/com/projectmgmt/WorkflowApiTests.java`

**Interfaces:**
- Produces: `WorkflowNode.timeoutValue`（`Integer`，可空）、`timeoutUnit`（`HOUR`/`DAY`，可空）
- 审批节点：`timeoutValue` 空或 ≤0 则清空两项；1–999 且单位 `HOUR` 或 `DAY` 才合法
- 非审批节点保存时清空超时字段

- [ ] **Step 1: Write the failing test**

在 `WorkflowApiTests` 增加：

```java
    @Test
    void approvalTimeoutIsSavedAndInvalidUnitRejected() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        String code = "TO" + System.currentTimeMillis();
        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "超时流程",
                                  "code": "%s",
                                  "formModule": "CUSTOMER",
                                  "graph": {
                                    "nodes": [
                                      {"key":"start","name":"开始","type":"START","x":80,"y":160,"fields":{}},
                                      {"key":"fill","name":"填写","type":"APPROVAL","x":280,"y":160,"fields":{},
                                       "assigneeType":"STARTER","timeoutValue":8,"timeoutUnit":"HOUR"},
                                      {"key":"cc","name":"抄送","type":"CC","x":480,"y":160,"fields":{},
                                       "assigneeType":"STARTER","timeoutValue":3,"timeoutUnit":"DAY"},
                                      {"key":"end","name":"结束","type":"END","x":680,"y":160,"fields":{}}
                                    ],
                                    "edges": [
                                      {"from":"start","to":"fill","isDefault":true},
                                      {"from":"fill","to":"cc","isDefault":true},
                                      {"from":"cc","to":"end","isDefault":true}
                                    ]
                                  }
                                }
                                """.formatted(code))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodes[?(@.key=='fill')].timeoutValue")
                        .value(org.hamcrest.Matchers.hasItem(8)))
                .andExpect(jsonPath("$.data.nodes[?(@.key=='fill')].timeoutUnit")
                        .value(org.hamcrest.Matchers.hasItem("HOUR")))
                .andExpect(jsonPath("$.data.nodes[?(@.key=='cc')].timeoutValue")
                        .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.nullValue())));

        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "非法超时",
                                  "code": "%sX",
                                  "formModule": "CUSTOMER",
                                  "graph": {
                                    "nodes": [
                                      {"key":"start","name":"开始","type":"START","x":80,"y":160,"fields":{}},
                                      {"key":"fill","name":"填写","type":"APPROVAL","x":280,"y":160,"fields":{},
                                       "assigneeType":"STARTER","timeoutValue":8,"timeoutUnit":"WEEK"},
                                      {"key":"end","name":"结束","type":"END","x":480,"y":160,"fields":{}}
                                    ],
                                    "edges": [
                                      {"from":"start","to":"fill","isDefault":true},
                                      {"from":"fill","to":"end","isDefault":true}
                                    ]
                                  }
                                }
                                """.formatted(code))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("超时时限单位只能是小时或天"));
    }
```

抄送节点 JSONPath 若因字段被省略而 `nullValue` 不好匹配，改为 `.doesNotExist()` 或断言 `timeoutValue` 为 `null`。以实际序列化为准：清空后 Lombok getter 仍会输出 `timeoutValue: null`。

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd -q "-Dtest=WorkflowApiTests#approvalTimeoutIsSavedAndInvalidUnitRejected" test`  
工作目录：`D:\项目管理\backend`  
Expected: FAIL（没有 `timeoutValue` 或非法单位仍 200）。

- [ ] **Step 3: Write minimal implementation**

`WorkflowGraph.WorkflowNode` 增加：

```java
        private Integer timeoutValue;
        /** HOUR | DAY */
        private String timeoutUnit;
```

在 `validateGraph` 循环里，`node.setType(type)` 之后：

```java
            if (APPROVAL.equals(type)) {
                normalizeTimeout(node);
            } else {
                node.setTimeoutValue(null);
                node.setTimeoutUnit(null);
            }
```

新增：

```java
    private void normalizeTimeout(WorkflowGraph.WorkflowNode node) {
        Integer value = node.getTimeoutValue();
        if (value == null || value <= 0) {
            node.setTimeoutValue(null);
            node.setTimeoutUnit(null);
            return;
        }
        if (value > 999) {
            throw ApiException.badRequest("超时时限必须是 1 到 999 的整数");
        }
        String unit = node.getTimeoutUnit() == null ? "" : node.getTimeoutUnit().trim().toUpperCase(Locale.ROOT);
        if (!"HOUR".equals(unit) && !"DAY".equals(unit)) {
            throw ApiException.badRequest("超时时限单位只能是小时或天");
        }
        node.setTimeoutUnit(unit);
    }
```

`TASK` 会先 `normalizeType` 成 `APPROVAL`，因此走审批分支。

- [ ] **Step 4: Run test to verify it passes**

同上命令。Expected: PASS。

---

### Task 2: 待办 dueAt、列表筛选、流程条 overdue

**Files:**
- Modify: `WorkflowTask.java`、`SchemaRepairer.java`、`InboxItemView.java`、`InboxController.java`
- Modify: `WorkflowService.java`（`createTasks`、`inbox`、`toInbox`、`toDraftInbox`、`runtimeOf`、`emptyRuntime`）
- Modify: `WorkflowRuntimeView.java`
- Test: `WorkflowApiTests.java`

**Interfaces:**
- `WorkflowTask.dueAt`：`LocalDateTime`，可空；列 `due_at`
- `dueAtOf(node, from, nodeType)`：抄送或未开超时 → null；`HOUR` → `plusHours`；`DAY` → `plusDays`
- `isOverdue(task)`：`PENDING` 且 `dueAt != null` 且 `LocalDateTime.now().isAfter(dueAt)`
- `inbox(type, overdueOnly)`：`overdueOnly` 仅对 `TODO`/`RETURNED` 过滤
- `InboxItemView` 增加 `LocalDateTime dueAt`, `boolean overdue`
- `WorkflowRuntimeView` 末尾增加 `LocalDateTime dueAt`, `boolean overdue`  
  当前节点 PENDING 审批任务：任一超时则 `overdue=true`；`dueAt` 取这些任务里非空截止时间的最早值；没有则都为 null/false
- `emptyRuntime` 补 `null, false`

- [ ] **Step 1: Write the failing test**

```java
    @Autowired
    com.projectmgmt.workflow.WorkflowTaskRepository workflowTaskRepository;

    @Test
    void timeoutMarksInboxAndRuntimeOverdue() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        String code = "TD" + System.currentTimeMillis();
        long bizId = System.currentTimeMillis();
        MvcResult created = mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "超时待办",
                                  "code": "%s",
                                  "formModule": "CUSTOMER",
                                  "graph": {
                                    "nodes": [
                                      {"key":"start","name":"开始","type":"START","x":80,"y":160,"fields":{}},
                                      {"key":"fill","name":"填写","type":"APPROVAL","x":280,"y":160,"fields":{},
                                       "assigneeType":"STARTER","timeoutValue":8,"timeoutUnit":"HOUR"},
                                      {"key":"end","name":"结束","type":"END","x":480,"y":160,"fields":{}}
                                    ],
                                    "edges": [
                                      {"from":"start","to":"fill","isDefault":true},
                                      {"from":"fill","to":"end","isDefault":true}
                                    ]
                                  }
                                }
                                """.formatted(code))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn();
        String id = created.getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1");
        mockMvc.perform(post("/api/workflows/" + id + "/publish").with(user(new AuthUser(admin))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/workflows/runtime")
                        .param("module", "CUSTOMER")
                        .param("bizId", String.valueOf(bizId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentNodeKey").value("fill"))
                .andExpect(jsonPath("$.data.dueAt").exists())
                .andExpect(jsonPath("$.data.overdue").value(false));

        String inbox = mockMvc.perform(get("/api/inbox").param("type", "TODO").with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.bizId==" + bizId + ")].overdue")
                        .value(org.hamcrest.Matchers.hasItem(false)))
                .andExpect(jsonPath("$.data[?(@.bizId==" + bizId + ")].dueAt").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer taskId = com.jayway.jsonpath.JsonPath.read(inbox, "$.data[?(@.bizId==" + bizId + ")].id[0]");

        var task = workflowTaskRepository.findById(taskId.longValue()).orElseThrow();
        task.setDueAt(java.time.LocalDateTime.now().minusHours(1));
        workflowTaskRepository.save(task);

        mockMvc.perform(get("/api/inbox").param("type", "TODO").param("overdue", "true")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.bizId==" + bizId + ")].overdue")
                        .value(org.hamcrest.Matchers.hasItem(true)));
        mockMvc.perform(get("/api/workflows/runtime")
                        .param("module", "CUSTOMER")
                        .param("bizId", String.valueOf(bizId))
                        .with(user(new AuthUser(admin))))
                .andExpect(jsonPath("$.data.overdue").value(true));

        mockMvc.perform(post("/api/workflows/advance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"formModule":"CUSTOMER","bizId":%s,"comment":"办完"}
                                """.formatted(bizId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/inbox").param("type", "TODO").param("overdue", "true")
                        .with(user(new AuthUser(admin))))
                .andExpect(jsonPath("$.data[?(@.bizId==" + bizId + ")]").doesNotExist());
    }
```

访问 runtime 且无实例时，引擎会启动流程（见现有 `designNodeFieldModesAndReadRuntime`），因此第一次 GET runtime 就会建待办。

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd -q "-Dtest=WorkflowApiTests#timeoutMarksInboxAndRuntimeOverdue" test`  
Expected: FAIL（没有 `dueAt` / `overdue`）。

- [ ] **Step 3: Write minimal implementation**

`WorkflowTask`：

```java
    @Column(name = "due_at")
    private LocalDateTime dueAt;
```

`SchemaRepairer.run` 在 `wf_instance` 块后：

```java
            if (tableExists(connection, "wf_task")) {
                ensureColumn(connection, "wf_task", "due_at", "datetime(6) NULL");
            }
```

`createTasks` 在 `save` 前：

```java
            LocalDateTime created = LocalDateTime.now();
            task.setCreatedAt(created);
            task.setDueAt(dueAtOf(node, created, nodeType));
            taskRepository.save(task);
```

```java
    private LocalDateTime dueAtOf(WorkflowGraph.WorkflowNode node, LocalDateTime from, String nodeType) {
        if (!APPROVAL.equals(nodeType)) {
            return null;
        }
        Integer value = node.getTimeoutValue();
        if (value == null || value <= 0 || from == null) {
            return null;
        }
        String unit = node.getTimeoutUnit() == null ? "" : node.getTimeoutUnit().trim().toUpperCase(Locale.ROOT);
        if ("DAY".equals(unit)) {
            return from.plusDays(value);
        }
        if ("HOUR".equals(unit)) {
            return from.plusHours(value);
        }
        return null;
    }

    private boolean isOverdue(WorkflowTask task) {
        return task != null
                && PENDING.equals(task.getStatus())
                && task.getDueAt() != null
                && LocalDateTime.now().isAfter(task.getDueAt());
    }
```

`inbox` 改为 `inbox(String type, boolean overdueOnly)`，TODO/RETURNED 在 `map` 前：

```java
                    .filter(task -> !overdueOnly || isOverdue(task))
```

其它类型忽略 `overdueOnly`。

`InboxController`：

```java
    public ApiResponse<List<InboxItemView>> list(
            @RequestParam(defaultValue = "TODO") String type,
            @RequestParam(defaultValue = "false") boolean overdue
    ) {
        return ApiResponse.ok(workflowService.inbox(type, overdue));
    }
```

`InboxItemView` 在 `handledAt` 后加 `LocalDateTime dueAt, boolean overdue`。`toInbox` 传入 `task.getDueAt(), isOverdue(task)`。`toDraftInbox` 传 `null, false`。

`WorkflowRuntimeView` 在 `currentAssignees` 后加 `LocalDateTime dueAt, boolean overdue`。`runtimeOf` 在组 view 前：

```java
        List<WorkflowTask> pendingApproval = instance == null
                ? List.of()
                : taskRepository.findByInstanceAndNodeKeyAndStatus(instance, current.getKey(), PENDING).stream()
                        .filter(task -> APPROVAL.equals(task.getNodeType()))
                        .toList();
        boolean overdue = pendingApproval.stream().anyMatch(this::isOverdue);
        LocalDateTime dueAt = pendingApproval.stream()
                .map(WorkflowTask::getDueAt)
                .filter(item -> item != null)
                .min(LocalDateTime::compareTo)
                .orElse(null);
```

把 `dueAt, overdue` 接到 `new WorkflowRuntimeView(...)` 末尾。`emptyRuntime` 末尾加 `null, false`。`assignees` 列表可继续从 `pendingApproval` 取名字，避免查两次。

- [ ] **Step 4: Run tests**

`.\mvnw.cmd -q "-Dtest=WorkflowApiTests" test`  
Expected: 全绿（含旧 inbox/runtime 用例）。`InboxItemView` 多了字段，旧断言不读新字段即可。

---

### Task 3: 设计器节点超时 UI

**Files:**
- Modify: `frontend/src/types.ts`
- Modify: `frontend/src/pages/WorkflowsPage.tsx`

**Interfaces:**
- `WorkflowNode` 增加 `timeoutValue?: number | null`、`timeoutUnit?: 'HOUR' | 'DAY' | string`
- 仅 `APPROVAL` | `TASK` 显示「超时」区块（不要给抄送）

- [ ] **Step 1: 类型**

```ts
  timeoutValue?: number | null
  timeoutUnit?: 'HOUR' | 'DAY' | string
```

- [ ] **Step 2: 检查器**

`WorkflowsPage.tsx` 的 antd 导入增加 `InputNumber`。

在办理人 `</div>` 之后、`BRANCH` 区块之前插入（`selected.type === 'APPROVAL' || selected.type === 'TASK'`）：

```tsx
              {selected.type === 'APPROVAL' || selected.type === 'TASK' ? (
                <div className="wf-inspector-section">
                  <h5>超时</h5>
                  <p className="wf-inspector-help">超过此时限仍未办理，待办和单据上将标记已超时。不填或填 0 表示不超时。</p>
                  <Space wrap style={{ marginTop: 8 }}>
                    <InputNumber
                      min={0}
                      max={999}
                      precision={0}
                      placeholder="时长"
                      disabled={!canEdit}
                      value={selected.timeoutValue ?? 0}
                      onChange={(value) =>
                        setNodes((prev) =>
                          prev.map((item) =>
                            item.key === selected.key
                              ? { ...item, timeoutValue: value == null ? 0 : Number(value) }
                              : item,
                          ),
                        )
                      }
                    />
                    <Select
                      style={{ width: 100 }}
                      disabled={!canEdit}
                      value={selected.timeoutUnit || 'HOUR'}
                      options={[
                        { value: 'HOUR', label: '小时' },
                        { value: 'DAY', label: '天' },
                      ]}
                      onChange={(value) =>
                        setNodes((prev) =>
                          prev.map((item) => (item.key === selected.key ? { ...item, timeoutUnit: value } : item)),
                        )
                      }
                    />
                  </Space>
                </div>
              ) : null}
```

保存仍走现有 `graph.nodes`，无需改 API 客户端。

- [ ] **Step 3: 浏览器点选**

登录 `admin` / `Admin123!` → 流程引擎 → 打开任一审批节点，应看到「超时」。填 8 小时保存再打开，值还在。抄送节点没有该区块。

---

### Task 4: 待办列表与流程条

**Files:**
- Modify: `frontend/src/types.ts`（`InboxItem`、`WorkflowRuntime`）
- Modify: `frontend/src/api/index.ts`
- Modify: `frontend/src/pages/InboxPage.tsx`
- Modify: `frontend/src/workflow/WorkflowBar.tsx`

**Interfaces:**
- `InboxItem.dueAt?: string | null`、`overdue?: boolean`
- `WorkflowRuntime.dueAt?: string | null`、`overdue?: boolean`
- `inboxApi.list(type, overdue?: boolean)` → `params: { type, overdue: overdue || undefined }`

- [ ] **Step 1: 类型与 API**

```ts
export const inboxApi = {
  list: (type: string, overdue?: boolean) =>
    unwrap<InboxItem[]>(http.get('/api/inbox', { params: { type, overdue: overdue ? true : undefined } })),
}
```

- [ ] **Step 2: InboxPage**

从 antd 增加 `Switch`。状态：`overdueOnly`。`TODO`/`RETURNED` 时在 Segmented 旁显示「仅看超时」。切 tab 时关掉开关。`load` 调用 `inboxApi.list(next, overdueOnly && (next === 'TODO' || next === 'RETURNED'))`。`useEffect` 依赖 `type, overdueOnly`。

状态列：

```tsx
      render: (_, row) => (
        <Space size={4}>
          <Tag>{statusLabel(row.status)}</Tag>
          {row.overdue ? <Tag color="red">已超时</Tag> : null}
        </Space>
      ),
```

已办/抄送/草稿不传 `overdue`，即使脏数据有 `overdue` 也不展示标签（`type === 'TODO' || type === 'RETURNED'` 才显示）。

- [ ] **Step 3: WorkflowBar**

在 `runtime.currentNodeName` 的 Tag 后：

```tsx
        {runtime.overdue ? <Tag color="red">已超时</Tag> : null}
```

- [ ] **Step 4: 浏览器核对**

1. 给客户（或付款）审批节点设 1 小时，发布，走一单到该节点：待办无红标，单据流程条无「已超时」但后端 `dueAt` 有值  
2. 用 Task 2 的方式或库里把 `due_at` 改到过去后刷新：待办红标、「仅看超时」只留下该行；打开单据流程条有「已超时」  
3. 办完后待办超时筛选不再出现该单  
4. 项目/客户公开页、付款申请原流程不受影响

改后端后重启：释放 8080，`D:\项目管理\backend` 执行 `.\mvnw.cmd -q spring-boot:run`。前端 `npm run dev` 若已停则再开。

---

### Task 5: 回归

- [ ] `.\mvnw.cmd -q "-Dtest=WorkflowApiTests,PaymentApiTests" test` 全绿  
- [ ] 未设超时的旧流程待办 `dueAt` 为空，进不了「仅看超时」  
- [ ] 非法单位保存失败文案为中文

---

## Spec coverage

| Spec | Task |
|---|---|
| 节点 timeoutValue/Unit、校验、抄送清空 | 1 |
| due_at、创建时计算、SchemaRepairer | 2 |
| 已超时定义、办完不再超时 | 2 |
| 待办 dueAt/overdue、overdue=true | 2、4 |
| 流程条 overdue / 最早 dueAt | 2、4 |
| 设计器小时/天 | 3 |
| 不发消息、不定时任务 | 全任务均不引入 |

---

## Execution notes

- `mvn` 不在 PATH：用 `backend\mvnw.cmd`  
- 本仓库不是 git repo：不要 worktree，不要 commit  
- 不要用 Coze
