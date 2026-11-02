package com.projectmgmt.workflow;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.dynamic.DynamicFormService;
import com.projectmgmt.tech.TechCatalog;
import com.projectmgmt.org.OrgResolver;
import com.projectmgmt.finance.ExpenseBudgetService;
import com.projectmgmt.payment.PaymentBudgetService;
import com.projectmgmt.customeropen.CustomerOpenSettleService;
import com.projectmgmt.device.DeviceSettleService;
import com.projectmgmt.oa.OaKind;
import com.projectmgmt.deposit.DepositSettleService;
import com.projectmgmt.oa.OaSettleService;
import com.projectmgmt.purchase.PurchaseSettleService;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.security.CurrentUser;
import com.projectmgmt.user.SysUser;
import com.projectmgmt.user.UserRepository;
import com.projectmgmt.user.UserStatus;
import com.projectmgmt.workflow.dto.FormFieldView;
import com.projectmgmt.workflow.dto.InboxItemView;
import com.projectmgmt.workflow.dto.UpsertWorkflowRequest;
import com.projectmgmt.workflow.dto.WorkflowRuntimeView;
import com.projectmgmt.workflow.dto.WorkflowRuntimeView.PreviousNodeView;
import com.projectmgmt.workflow.dto.WorkflowRuntimeView.WorkflowHistoryView;
import com.projectmgmt.workflow.dto.WorkflowView;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);

    public static final String HIDDEN = "HIDDEN";
    public static final String VISIBLE = "VISIBLE";
    public static final String EDITABLE = "EDITABLE";
    public static final String DRAFT = "DRAFT";
    public static final String RUNNING = "RUNNING";
    public static final String DONE = "DONE";
    public static final String PENDING = "PENDING";
    public static final String CANCELLED = "CANCELLED";
    public static final String START = "START";
    public static final String APPROVAL = "APPROVAL";
    public static final String CC = "CC";
    public static final String CONDITION = "CONDITION";
    public static final String BRANCH = "BRANCH";
    public static final String END = "END";
    public static final String ROBOT = "ROBOT";
    public static final String NORMAL = "NORMAL";
    public static final String RETURN = "RETURN";
    public static final String STARTER = "STARTER";
    public static final String USER = "USER";
    public static final String FORM_FIELD = "FORM_FIELD";
    public static final String ORG_SUPERVISOR = "ORG_SUPERVISOR";
    public static final String ORG_CHAIN = "ORG_CHAIN";

    private static final Set<String> NODE_TYPES = Set.of(START, APPROVAL, CC, BRANCH, CONDITION, END, "TASK", ROBOT);
    private static final Set<String> OPS = Set.of("EQ", "NE", "CONTAINS", "GT", "GTE", "LT", "LTE", "EMPTY", "NOT_EMPTY");

    private final WorkflowRepository workflowRepository;
    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowTaskRepository taskRepository;
    private final WorkflowHistoryRepository historyRepository;
    private final WorkflowFormData formData;
    private final CurrentUser currentUser;
    private final UserRepository userRepository;
    private final PaymentBudgetService paymentBudgetService;
    private final ExpenseBudgetService expenseBudgetService;
    private final PurchaseSettleService purchaseSettleService;
    private final DeviceSettleService deviceSettleService;
    private CustomerOpenSettleService customerOpenSettleService;

    @org.springframework.beans.factory.annotation.Autowired
    public void setCustomerOpenSettleService(@Lazy CustomerOpenSettleService customerOpenSettleService) {
        this.customerOpenSettleService = customerOpenSettleService;
    }
    private final OaSettleService oaSettleService;
    private final DepositSettleService depositSettleService;
    private final OrgResolver orgResolver;
    private final PermissionService permissionService;
    @Lazy private final DynamicFormService dynamicFormService;

    @Transactional(readOnly = true)
    public List<WorkflowView> list() {
        return workflowRepository.findAllByOrderByIdDesc().stream()
                .filter(item -> !TechCatalog.isLegacyAppKey(item.getFormModule()))
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkflowView get(Long id) {
        return toView(require(id));
    }

    @Transactional(readOnly = true)
    public List<FormFieldView> formFields(String moduleCode) {
        return FormFieldCatalog.configurableFields(moduleCode).stream().map(FormFieldView::from).toList();
    }

    @Transactional
    public WorkflowView forForm(String moduleCode) {
        parseBindable(moduleCode);
        return workflowRepository.findFirstByFormModuleAndPublishedTrue(moduleCode)
                .or(() -> workflowRepository.findFirstByFormModuleOrderByIdDesc(moduleCode))
                .map(this::toView)
                .orElseGet(() -> createDefaultForForm(moduleCode));
    }

    private WorkflowView createDefaultForForm(String moduleCode) {
        FormModule module = FormModule.fromNameOrNull(moduleCode);
        String label = labelOf(moduleCode);
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setName(label.endsWith("流程") ? label : label + "流程");
        definition.setCode(uniqueFlowCode(moduleCode));
        definition.setFormModule(module != null ? module.name() : moduleCode);
        definition.setPublished(false);
        definition.setGraph(validated(defaultGraph(module), definition.getFormModule()));
        return toView(workflowRepository.save(definition));
    }

    private String uniqueFlowCode(String moduleCode) {
        String base = moduleCode.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (base.isEmpty() || !Character.isLetter(base.charAt(0))) {
            base = "F" + base;
        }
        if (base.length() > 28) {
            base = base.substring(0, 28);
        }
        String code = base.endsWith("F") ? base : base + "_F";
        if (code.length() > 32) {
            code = code.substring(0, 32);
        }
        if (!workflowRepository.existsByCode(code)) {
            return code;
        }
        for (int i = 2; i < 100; i++) {
            String suffix = "_F" + i;
            String next = (base.length() + suffix.length() > 32 ? base.substring(0, 32 - suffix.length()) : base) + suffix;
            if (!workflowRepository.existsByCode(next)) {
                return next;
            }
        }
        return ("F" + System.currentTimeMillis()).substring(0, 32);
    }

    @Transactional
    public WorkflowView create(UpsertWorkflowRequest request) {
        String formModuleCode = request.formModule() == null ? "" : request.formModule().trim();
        FormModule module = parseBindable(formModuleCode);
        String code = normalizeCode(request.code());
        if (workflowRepository.existsByCode(code)) {
            throw ApiException.badRequest("流程编码已存在");
        }
        WorkflowGraph graph = request.graph() == null ? defaultGraph(module) : request.graph();
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setName(request.name().trim());
        definition.setCode(code);
        definition.setFormModule(module != null ? module.name() : formModuleCode);
        definition.setPublished(false);
        definition.setGraph(validated(graph, definition.getFormModule()));
        return toView(workflowRepository.save(definition));
    }

    @Transactional
    public WorkflowView update(Long id, UpsertWorkflowRequest request) {
        WorkflowDefinition definition = require(id);
        FormModule module = parseBindable(request.formModule());
        if (module == null) {
            // 动态表单流程：编码即表单编码，保持原值不做规范化，仅更新名称与流程图
            WorkflowGraph graph = request.graph() == null ? graphOf(definition) : request.graph();
            definition.setName(request.name().trim());
            definition.setGraph(validated(graph, definition.getFormModule()));
            return toView(definition);
        }
        String code = normalizeCode(request.code());
        if (workflowRepository.existsByCodeAndIdNot(code, id)) {
            throw ApiException.badRequest("流程编码已存在");
        }
        WorkflowGraph graph = request.graph() == null ? graphOf(definition) : request.graph();
        definition.setName(request.name().trim());
        definition.setCode(code);
        definition.setFormModule(module.name());
        definition.setGraph(validated(graph, module));
        return toView(definition);
    }

    @Transactional
    public void delete(Long id) {
        WorkflowDefinition definition = require(id);
        if (definition.isPublished()) {
            throw ApiException.badRequest("已发布的流程请先取消发布再删除");
        }
        workflowRepository.delete(definition);
    }

    @Transactional
    public void deleteForBiz(FormModule module, Long bizId) {
        instanceRepository.findByFormModuleAndBizId(module, bizId).ifPresent(instance -> {
            taskRepository.deleteByInstance(instance);
            historyRepository.deleteByInstance(instance);
            instanceRepository.delete(instance);
        });
    }

    @Transactional
    public WorkflowView publish(Long id) {
        WorkflowDefinition definition = require(id);
        validated(graphOf(definition), definition.getFormModule());
        for (WorkflowDefinition other : workflowRepository.findAllByFormModuleAndPublishedTrue(definition.getFormModule())) {
            if (!other.getId().equals(id)) {
                other.setPublished(false);
            }
        }
        definition.setPublished(true);
        return toView(definition);
    }

    @Transactional
    public WorkflowView unpublish(Long id) {
        WorkflowDefinition definition = require(id);
        definition.setPublished(false);
        return toView(definition);
    }

    @Transactional(readOnly = true)
    public boolean hasPublished(FormModule module) {
        return workflowRepository.findFirstByFormModuleAndPublishedTrue(module).isPresent();
    }

    @Transactional
    public void completeImported(FormModule module, Long bizId) {
        instanceRepository.findByFormModuleAndBizId(module, bizId).ifPresent(this::completeInstance);
    }

    @Transactional
    public void start(FormModule module, Long bizId) {
        start(module.name(), bizId);
    }

    @Transactional
    public void start(String moduleCode, Long bizId) {
        parseBindable(moduleCode);
        if (bizId == null || instanceRepository.findByFormModuleAndBizId(moduleCode, bizId).isPresent()) {
            return;
        }
        WorkflowDefinition definition = workflowRepository.findFirstByFormModuleAndPublishedTrue(moduleCode).orElse(null);
        if (definition == null) {
            return;
        }
        SysUser starter = currentOrNull();
        WorkflowGraph graph = graphOf(definition);
        WorkflowGraph.WorkflowNode start = findStart(graph);
        if (start == null) {
            // 未配置流程节点（如未挂流程的动态表单）：不自动发起，避免空指针打断读取事务
            return;
        }
        WorkflowInstance instance = new WorkflowInstance();
        instance.setDefinition(definition);
        instance.setFormModule(moduleCode);
        instance.setBizId(bizId);
        instance.setTitle(formData.title(moduleCode, bizId));
        instance.setStarterId(starter == null ? null : starter.getId());
        instance.setStarterName(starter == null ? formData.starterLabel(moduleCode, bizId) : starter.getDisplayName());
        instance.setStatus(DRAFT);
        instance.setVisitedKeys(new ArrayList<>());
        instance.setCurrentNodeKey(start.getKey());
        instanceRepository.save(instance);
        addHistory(instance, null, null, start.getKey(), start.getName(), START, starter, null);
        enterNode(instance, graph, start, NORMAL, null, 0);
    }

    @Transactional
    public WorkflowRuntimeView advance(String moduleCode, Long bizId, String comment) {
        parseBindable(moduleCode);
        WorkflowInstance instance = instanceRepository.findByFormModuleAndBizId(moduleCode, bizId)
                .orElseThrow(() -> ApiException.badRequest("尚未发起流程"));
        if (DONE.equals(instance.getStatus())) {
            throw ApiException.badRequest("流程已结束");
        }
        SysUser actor = currentUser.entity();
        WorkflowGraph graph = graphOf(instance.getDefinition());
        WorkflowGraph.WorkflowNode current = node(graph, instance.getCurrentNodeKey());
        if (!canHandle(instance, graph, actor)) {
            throw ApiException.badRequest("当前节点不由你处理");
        }
        if (START.equals(normalizeType(current.getType()))) {
            return submitStart(instance, graph, current, actor, comment, moduleCode, bizId);
        }
        WorkflowTask mine = myPendingApproval(instance, actor.getId());
        if (mine == null) {
            throw ApiException.badRequest("当前节点不由你处理");
        }
        mine.setStatus(DONE);
        mine.setComment(trimToNull(comment));
        mine.setHandledAt(LocalDateTime.now());
        for (WorkflowTask other : taskRepository.findByInstanceAndNodeKeyAndStatus(instance, current.getKey(), PENDING)) {
            if (!other.getId().equals(mine.getId()) && APPROVAL.equals(other.getNodeType())) {
                other.setStatus(CANCELLED);
            }
        }
        instance.setLastReturnReason(null);
        instance.setLastReturnFromKey(null);
        instance.setLastReturnFromName(null);
        instance.setStatus(RUNNING);
        if (ORG_CHAIN.equals(assigneeTypeOf(current))) {
            Set<Long> approved = new HashSet<>(approvedActorIds(instance, current.getKey()));
            approved.add(actor.getId());
            List<SysUser> nextLevel = orgResolver.currentLevel(orgChain(instance, current), approved);
            if (!nextLevel.isEmpty()) {
                addHistory(
                        instance,
                        current.getKey(),
                        current.getName(),
                        current.getKey(),
                        current.getName(),
                        "APPROVE",
                        actor,
                        trimToNull(comment)
                );
                createTasksFor(instance, current, nextLevel, APPROVAL, NORMAL, null);
                return runtimeOf(instance.getDefinition(), graph, instance, moduleCode, bizId);
            }
        }
        Map<String, String> values = formData.values(moduleCode, bizId);
        WorkflowGraph.WorkflowNode next = pickNext(graph, current, values);
        addHistory(
                instance,
                current.getKey(),
                current.getName(),
                next == null ? null : next.getKey(),
                next == null ? null : next.getName(),
                "APPROVE",
                actor,
                trimToNull(comment)
        );
        if (next == null) {
            completeInstance(instance);
        } else {
            enterNode(instance, graph, next, NORMAL, null, 0);
        }
        return runtimeOf(instance.getDefinition(), graph, instance, moduleCode, bizId);
    }

    @Transactional
    public WorkflowRuntimeView advanceByTask(Long taskId, String comment) {
        WorkflowTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> ApiException.badRequest("任务不存在"));
        return advance(task.getInstance().getFormModule(), task.getInstance().getBizId(), comment);
    }

    @Transactional
    public WorkflowRuntimeView returnByTask(Long taskId, String reason) {
        WorkflowTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> ApiException.badRequest("任务不存在"));
        WorkflowInstance instance = task.getInstance();
        List<String> visited = visitedOf(instance);
        int idx = visited.indexOf(task.getNodeKey());
        String targetKey = idx > 0 ? visited.get(idx - 1) : visited.get(0);
        return returnTo(instance.getFormModule(), instance.getBizId(), targetKey, reason);
    }

    @Transactional(readOnly = true)
    public String statusOf(String moduleCode, Long bizId) {
        if (bizId == null) {
            return DRAFT;
        }
        WorkflowInstance instance = instanceRepository.findByFormModuleAndBizId(moduleCode, bizId).orElse(null);
        return instance == null ? DRAFT : instance.getStatus();
    }

    @Transactional
    public void cancel(String moduleCode, Long bizId) {
        WorkflowInstance instance = instanceRepository.findByFormModuleAndBizId(moduleCode, bizId).orElse(null);
        if (instance != null) {
            historyRepository.deleteByInstance(instance);
            taskRepository.deleteByInstance(instance);
            instanceRepository.delete(instance);
        }
    }

    @Transactional(readOnly = true)
    public List<WorkflowTask> pendingTasks(Long assigneeId) {
        if (assigneeId == null) {
            return List.of();
        }
        return taskRepository.findByAssigneeIdAndNodeTypeAndStatusOrderByHandledAtDesc(assigneeId, APPROVAL, PENDING);
    }

    @Transactional(readOnly = true)
    public boolean isPendingAssignee(String moduleCode, Long bizId, SysUser user) {
        if (user == null || bizId == null) {
            return false;
        }
        WorkflowInstance instance = instanceRepository.findByFormModuleAndBizId(moduleCode, bizId).orElse(null);
        if (instance == null) {
            return false;
        }
        return !taskRepository.findByInstanceAndStatusAndAssigneeId(instance, PENDING, user.getId()).isEmpty();
    }

    @Transactional
    public WorkflowRuntimeView returnTo(String moduleCode, Long bizId, String targetNodeKey, String reason) {
        parseBindable(moduleCode);
        WorkflowInstance instance = instanceRepository.findByFormModuleAndBizId(moduleCode, bizId)
                .orElseThrow(() -> ApiException.badRequest("尚未发起流程"));
        if (DONE.equals(instance.getStatus())) {
            throw ApiException.badRequest("流程已结束");
        }
        if (!StringUtils.hasText(reason)) {
            throw ApiException.badRequest("请填写退回原因");
        }
        SysUser actor = currentUser.entity();
        WorkflowGraph graph = graphOf(instance.getDefinition());
        if (!canHandle(instance, graph, actor)) {
            throw ApiException.badRequest("当前节点不由你处理");
        }
        WorkflowGraph.WorkflowNode current = node(graph, instance.getCurrentNodeKey());
        WorkflowGraph.WorkflowNode target = node(graph, targetNodeKey);
        String targetType = normalizeType(target.getType());
        if (!APPROVAL.equals(targetType) && !START.equals(targetType)) {
            throw ApiException.badRequest("只能退回到开始或审批节点");
        }
        List<String> visited = visitedOf(instance);
        if (!visited.contains(target.getKey()) || target.getKey().equals(current.getKey())) {
            throw ApiException.badRequest("只能退回到之前经过的节点");
        }
        cancelPending(instance);
        int index = visited.indexOf(target.getKey());
        instance.setVisitedKeys(new ArrayList<>(visited.subList(0, index + 1)));
        instance.setLastReturnReason(reason.trim());
        instance.setLastReturnFromKey(current.getKey());
        instance.setLastReturnFromName(current.getName());
        instance.setStatus(START.equals(targetType) ? DRAFT : RUNNING);
        addHistory(instance, current.getKey(), current.getName(), target.getKey(), target.getName(), RETURN, actor, reason.trim());
        enterNode(instance, graph, target, RETURN, reason.trim(), 0);
        return runtimeOf(instance.getDefinition(), graph, instance, moduleCode, bizId);
    }

    @Transactional
    public WorkflowRuntimeView runtime(String moduleCode, Long bizId) {
        parseBindable(moduleCode);
        if (bizId != null) {
            start(moduleCode, bizId);
        }
        WorkflowInstance instance = bizId == null
                ? null
                : instanceRepository.findByFormModuleAndBizId(moduleCode, bizId).orElse(null);
        if (instance != null && !StringUtils.hasText(instance.getStarterName())) {
            instance.setStarterName(formData.starterLabel(moduleCode, bizId));
        }
        WorkflowDefinition definition = instance != null
                ? instance.getDefinition()
                : workflowRepository.findFirstByFormModuleAndPublishedTrue(moduleCode).orElse(null);
        if (definition == null) {
            return emptyRuntime(moduleCode, bizId);
        }
        WorkflowGraph graph = graphOf(definition);
        if (findStart(graph) == null) {
            // 未配置流程节点的表单（如未挂流程的动态表单）：返回空流程视图，避免空指针打断调用方事务
            return emptyRuntime(moduleCode, bizId);
        }
        if (instance != null) {
            repairTasks(instance, graph);
        }
        return runtimeOf(definition, graph, instance, moduleCode, bizId);
    }

    @Transactional
    public Map<String, String> writeModes(FormModule module, Long bizId) {
        WorkflowRuntimeView runtime = runtime(module.name(), bizId);
        Map<String, String> modes;
        if (runtime.processId() == null || DONE.equals(runtime.status()) || CANCELLED.equals(runtime.status())) {
            modes = new LinkedHashMap<>(defaultModes(module));
        } else {
            modes = new LinkedHashMap<>(runtime.fields());
            if (bizId != null && !runtime.canHandle() && !canWrite(module, bizId)) {
                modes.replaceAll((key, value) -> FormFieldCatalog.isAction(key)
                        ? value
                        : EDITABLE.equals(value) ? VISIBLE : value);
            }
        }
        modes.keySet().removeIf(FormFieldCatalog::isAction);
        Map<String, String> scoped = applyRoleModes(modes, module, bizId);
        scoped.keySet().removeIf(FormFieldCatalog::isAction);
        return scoped;
    }

    public boolean canWriteField(FormModule module, Long bizId, String field) {
        return EDITABLE.equals(writeModes(module, bizId).getOrDefault(field, EDITABLE));
    }

    @Transactional(readOnly = true)
    public List<InboxItemView> inbox(String type, boolean overdueOnly) {
        return inboxFor(currentUser.entity().getId(), type, overdueOnly);
    }

    public List<InboxItemView> inboxFor(Long userId, String type, boolean overdueOnly) {
        String inboxType = type == null ? "TODO" : type.trim().toUpperCase(Locale.ROOT);
        return switch (inboxType) {
            case "TODO" -> pendingTodo(userId, overdueOnly);
            case "RETURNED" -> {
                List<InboxItemView> items = new ArrayList<>(taskRepository
                        .findByAssigneeIdAndNodeTypeAndStatusAndKindOrderByIdDesc(userId, APPROVAL, PENDING, RETURN)
                        .stream()
                        .filter(task -> !overdueOnly || isOverdue(task))
                        .map(task -> toInbox(task, "RETURNED"))
                        .toList());
                for (WorkflowInstance draft : instanceRepository.findByStarterIdAndStatusOrderByIdDesc(userId, DRAFT)) {
                    if (!StringUtils.hasText(draft.getLastReturnReason()) || overdueOnly) {
                        continue;
                    }
                    items.add(toDraftInbox(draft, "RETURNED"));
                }
                items.sort(Comparator.comparing(InboxItemView::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));
                yield items;
            }
            case "DONE" -> taskRepository
                    .findByAssigneeIdAndNodeTypeAndStatusOrderByHandledAtDesc(userId, APPROVAL, DONE)
                    .stream()
                    .map(task -> toInbox(task, "DONE"))
                    .toList();
            case "CC" -> taskRepository
                    .findByAssigneeIdAndNodeTypeOrderByIdDesc(userId, CC)
                    .stream()
                    .map(task -> toInbox(task, "CC"))
                    .toList();
            case "DRAFT" -> instanceRepository.findByStarterIdAndStatusOrderByIdDesc(userId, DRAFT).stream()
                    .map(this::toDraftInbox)
                    .toList();
            case "STARTED" -> instanceRepository.findByStarterIdAndStatusOrderByIdDesc(userId, RUNNING).stream()
                    .map(instance -> toDraftInbox(instance, "STARTED"))
                    .toList();
            case "FINISHED" -> instanceRepository.findByStarterIdAndStatusOrderByIdDesc(userId, DONE).stream()
                    .map(instance -> toDraftInbox(instance, "FINISHED"))
                    .toList();
            default -> throw ApiException.badRequest("待办类型无效");
        };
    }

    @Transactional
    public void seedDefault() {
        if (workflowRepository.existsByCode("PROJECT_INIT")) {
            workflowRepository.findAll().stream()
                    .filter(item -> "PROJECT_INIT".equals(item.getCode()))
                    .findFirst()
                    .ifPresent(existing -> existing.setGraph(graphOf(existing)));
            seedPaymentInit();
            seedExpenseInit();
            seedPurchaseFlows();
            seedCustomerFlows();
            syncFormBindings();
            return;
        }
        FormModule module = FormModule.PROJECT;
        WorkflowGraph graph = defaultGraph(module);
        Map<String, String> reviewFields = defaultModes(module);
        reviewFields.put("name", VISIBLE);
        reviewFields.put("code", VISIBLE);
        reviewFields.put("overview", EDITABLE);
        reviewFields.put("attachments", HIDDEN);
        node(graph, "fill").setName("填写立项");
        node(graph, "fill").setAssigneeType(STARTER);
        WorkflowGraph.WorkflowNode review = newNode("review", "区域审核", APPROVAL, 500, 200, reviewFields);
        review.setAssigneeType(FORM_FIELD);
        review.setAssigneeField("regionLeader");
        graph.getNodes().add(2, review);
        node(graph, "end").setX(720);
        graph.getEdges().clear();
        addEdge(graph, "start", "fill", null, true);
        addEdge(graph, "fill", "review", null, true);
        addEdge(graph, "review", "end", null, true);

        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setName("项目立项流程");
        definition.setCode("PROJECT_INIT");
        definition.setFormModule(module.name());
        definition.setPublished(true);
        definition.setGraph(validated(graph, module));
        workflowRepository.save(definition);
        seedPaymentInit();
        seedExpenseInit();
        seedPurchaseFlows();
        seedCustomerFlows();
        syncFormBindings();
    }

    private void seedCustomerFlows() {
        seedApplyFlow(
                "CUSTOMER_APPLY_INIT",
                "新建客户流程",
                FormModule.CUSTOMER_APPLY,
                "填写客户",
                "审核客户",
                false);
        seedApplyFlow(
                "CUSTOMER_ACCOUNT_INIT",
                "新建客户账号流程",
                FormModule.CUSTOMER_ACCOUNT,
                "填写账号",
                "审核账号",
                true);
        ensureAccountDeptField();
    }

    private void ensureAccountDeptField() {
        WorkflowDefinition definition = workflowRepository.findByCode("CUSTOMER_ACCOUNT_INIT").orElse(null);
        if (definition == null) {
            return;
        }
        WorkflowGraph graph = graphOf(definition);
        boolean changed = false;
        for (WorkflowGraph.WorkflowNode node : graph.getNodes()) {
            if (node.getFields() == null) {
                node.setFields(new LinkedHashMap<>());
            }
            if ("fill".equals(node.getKey()) && !node.getFields().containsKey("departmentId")) {
                node.getFields().put("departmentId", EDITABLE);
                changed = true;
            }
            if ("review".equals(node.getKey()) && !node.getFields().containsKey("departmentId")) {
                node.getFields().put("departmentId", EDITABLE);
                changed = true;
            }
        }
        if (changed) {
            definition.setGraph(validated(graph, FormModule.CUSTOMER_ACCOUNT));
            workflowRepository.save(definition);
        }
    }

    private void seedApplyFlow(
            String code,
            String name,
            FormModule module,
            String fillName,
            String reviewName,
            boolean reviewAccount
    ) {
        if (workflowRepository.existsByCode(code)) {
            return;
        }
        WorkflowGraph graph = defaultGraph(module);
        Map<String, String> fillFields = defaultModes(module);
        Map<String, String> reviewFields = defaultModes(module);
        for (String key : reviewFields.keySet()) {
            reviewFields.put(key, VISIBLE);
        }
        if (reviewAccount) {
            fillFields.put("accountType", HIDDEN);
            fillFields.put("roleCode", HIDDEN);
            fillFields.put("departmentId", EDITABLE);
            reviewFields.put("accountType", EDITABLE);
            reviewFields.put("roleCode", EDITABLE);
            reviewFields.put("departmentId", EDITABLE);
        }
        node(graph, "fill").setName(fillName);
        node(graph, "fill").setAssigneeType(STARTER);
        node(graph, "fill").setFields(fillFields);
        WorkflowGraph.WorkflowNode review = newNode("review", reviewName, APPROVAL, 500, 200, reviewFields);
        review.setAssigneeType(STARTER);
        graph.getNodes().add(2, review);
        node(graph, "end").setX(720);
        graph.getEdges().clear();
        addEdge(graph, "start", "fill", null, true);
        addEdge(graph, "fill", "review", null, true);
        addEdge(graph, "review", "end", null, true);
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setName(name);
        definition.setCode(code);
        definition.setFormModule(module.name());
        definition.setPublished(true);
        definition.setGraph(validated(graph, module));
        workflowRepository.save(definition);
    }

    private void seedPaymentInit() {
        if (workflowRepository.existsByCode("PAYMENT_INIT")) {
            return;
        }
        FormModule module = FormModule.PAYMENT;
        WorkflowGraph graph = defaultGraph(module);
        Map<String, String> reviewFields = defaultModes(module);
        reviewFields.put("projectId", VISIBLE);
        reviewFields.put("amount", VISIBLE);
        node(graph, "fill").setName("填写申请");
        node(graph, "fill").setAssigneeType(STARTER);
        WorkflowGraph.WorkflowNode review = newNode("review", "付款审批", APPROVAL, 500, 200, reviewFields);
        review.setAssigneeType(STARTER);
        graph.getNodes().add(2, review);
        node(graph, "end").setX(720);
        graph.getEdges().clear();
        addEdge(graph, "start", "fill", null, true);
        addEdge(graph, "fill", "review", null, true);
        addEdge(graph, "review", "end", null, true);

        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setName("付款申请流程");
        definition.setCode("PAYMENT_INIT");
        definition.setFormModule(module.name());
        definition.setPublished(true);
        definition.setGraph(validated(graph, module));
        workflowRepository.save(definition);
    }

    private void seedExpenseInit() {
        if (workflowRepository.existsByCode("EXPENSE_INIT")) {
            return;
        }
        FormModule module = FormModule.EXPENSE;
        WorkflowGraph graph = defaultGraph(module);
        Map<String, String> reviewFields = defaultModes(module);
        reviewFields.put("projectId", VISIBLE);
        reviewFields.put("amount", VISIBLE);
        node(graph, "fill").setName("填写申请");
        node(graph, "fill").setAssigneeType(STARTER);
        WorkflowGraph.WorkflowNode review = newNode("review", "报销审批", APPROVAL, 500, 200, reviewFields);
        review.setAssigneeType(STARTER);
        graph.getNodes().add(2, review);
        node(graph, "end").setX(720);
        graph.getEdges().clear();
        addEdge(graph, "start", "fill", null, true);
        addEdge(graph, "fill", "review", null, true);
        addEdge(graph, "review", "end", null, true);

        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setName("报销申请流程");
        definition.setCode("EXPENSE_INIT");
        definition.setFormModule(module.name());
        definition.setPublished(true);
        definition.setGraph(validated(graph, module));
        workflowRepository.save(definition);
    }

    private void seedPurchaseFlows() {
        seedSimpleFlow("PURCHASE_INIT", "采购申请流程", FormModule.PURCHASE, "填写申请", "采购审批");
        seedSimpleFlow("PURCHASE_PAY_INIT", "采购付款流程", FormModule.PURCHASE_PAY, "填写付款", "付款审批");
        seedSimpleFlow("STOCK_OUT_PO_INIT", "采购出库流程", FormModule.STOCK_OUT_PURCHASE, "填写出库", "出库审批");
        seedSimpleFlow("STOCK_OUT_PJ_INIT", "项目出库流程", FormModule.STOCK_OUT_PROJECT, "填写出库", "出库审批");
        seedSimpleFlow("STOCK_TRANSFER_INIT", "仓库调拨流程", FormModule.STOCK_TRANSFER, "填写调拨", "调拨审批");
        seedSimpleFlow("DEVICE_REPAIR_INIT", "设备维修工单流程", FormModule.DEVICE_REPAIR, "填写维修", "维修审批");
        seedSimpleFlow("DEVICE_MOVE_INIT", "设备搬迁工单流程", FormModule.DEVICE_MOVE, "填写搬迁", "搬迁审批");
        seedSimpleFlow("DEVICE_SCRAP_INIT", "设备报废工单流程", FormModule.DEVICE_SCRAP, "填写报废", "报废审批");
        seedSimpleFlow("DEVICE_MAINT_INIT", "维保服务记录流程", FormModule.DEVICE_MAINT, "填写维保", "维保确认");
        seedSimpleFlow("OA_ONBOARD_INIT", "入职申请流程", FormModule.OA_ONBOARD, "填写申请", "入职审批");
        seedSimpleFlow("OA_TRANSFER_INIT", "转岗申请流程", FormModule.OA_TRANSFER, "填写申请", "转岗审批");
        seedSimpleFlow("OA_PROMOTION_INIT", "晋升申请流程", FormModule.OA_PROMOTION, "填写申请", "晋升审批");
        seedSimpleFlow("OA_RESIGN_INIT", "离职申请流程", FormModule.OA_RESIGN, "填写申请", "离职审批");
        seedSimpleFlow("OA_SALARY_INIT", "薪资调整流程", FormModule.OA_SALARY, "填写申请", "调薪审批");
        seedSimpleFlow("OA_INTERVIEW_INIT", "面试申请流程", FormModule.OA_INTERVIEW, "填写申请", "面试审批");
        seedSimpleFlow("OA_SEAL_INIT", "盖章申请流程", FormModule.OA_SEAL, "填写申请", "盖章审批");
        seedSimpleFlow("OA_QUALIFY_INIT", "资质借出流程", FormModule.OA_QUALIFY, "填写申请", "借出审批");
        seedSimpleFlow("OA_TRIP_INIT", "出差申请流程", FormModule.OA_TRIP, "填写申请", "出差审批");
        seedSimpleFlow("OA_LEAVE_INIT", "请假申请流程", FormModule.OA_LEAVE, "填写申请", "请假审批");
        seedSimpleFlow("OA_LOAN_INIT", "项目借款流程", FormModule.OA_LOAN, "填写申请", "借款审批");
        seedSimpleFlow("OA_LOAN_PAY_INIT", "借款付款单流程", FormModule.OA_LOAN_PAY, "填写付款", "付款审批");
        seedSimpleFlow("OA_LOAN_REPAY_INIT", "借款还款单流程", FormModule.OA_LOAN_REPAY, "填写还款", "还款审批");
        seedSimpleFlow("DEPOSIT_APPLY_INIT", "项目保证金申请流程", FormModule.DEPOSIT_APPLY, "填写申请", "保证金审批");
        seedSimpleFlow("DEPOSIT_RETURN_INIT", "项目保证金退回流程", FormModule.DEPOSIT_RETURN, "填写退回", "退回审批");
        seedSimpleFlow("BIDDING_INIT", "招标信息流程", FormModule.BIDDING, "填写招标", "招标审批");
        seedTechFlows();
        patchOaSalaryFieldModes();
    }

    private void seedTechFlows() {
        for (com.projectmgmt.tech.TechKind kind : com.projectmgmt.tech.TechKind.values()) {
            if (!kind.module().isTech()) {
                continue;
            }
            String code = "TECH_" + kind.name() + "_INIT";
            String name = kind.label() + "流程";
            workflowRepository.findByCode(code).ifPresentOrElse(
                    definition -> {
                        definition.setName(name);
                        definition.setFormModule(kind.module().name());
                        definition.setPublished(true);
                        WorkflowGraph stored = graphOf(definition);
                        boolean legacyRepair = stored.getNodes().stream().anyMatch(node ->
                                "用户报修".equals(node.getName())
                                        || "填写服务单".equals(node.getName())
                                        || "工程师办理".equals(node.getName()));
                        if (stored.getNodes().isEmpty() || legacyRepair) {
                            definition.setGraph(validated(techGraph(kind), kind.module()));
                        } else {
                            try {
                                definition.setGraph(validated(stored, kind.module()));
                            } catch (Exception e) {
                                log.warn("技术服务流程 {} 校验失败，保留现有图: {}", code, e.getMessage());
                            }
                        }
                    },
                    () -> {
                        WorkflowDefinition definition = new WorkflowDefinition();
                        definition.setName(name);
                        definition.setCode(code);
                        definition.setFormModule(kind.module().name());
                        definition.setPublished(true);
                        definition.setGraph(validated(techGraph(kind), kind.module()));
                        workflowRepository.save(definition);
                    }
            );
        }
    }

    private void syncFormBindings() {
        for (WorkflowDefinition definition : workflowRepository.findAll()) {
            if (TechCatalog.isLegacyAppKey(definition.getFormModule())) {
                definition.setPublished(false);
                continue;
            }
            String label = labelOf(definition.getFormModule());
            if (!StringUtils.hasText(label) || label.equals(definition.getFormModule())) {
                continue;
            }
            boolean seed = definition.getCode() != null && definition.getCode().endsWith("_INIT");
            boolean dynamic = FormModule.fromNameOrNull(definition.getFormModule()) == null;
            if (!seed && !dynamic) {
                continue;
            }
            String expected = label.endsWith("流程") ? label : label + "流程";
            if (!expected.equals(definition.getName())) {
                definition.setName(expected);
            }
        }
    }

    private WorkflowGraph techGraph(com.projectmgmt.tech.TechKind kind) {
        com.projectmgmt.tech.TechCatalog.Kind catalog = com.projectmgmt.tech.TechCatalog.of(kind);
        Map<String, String> apply = new LinkedHashMap<>();
        Map<String, String> handle = new LinkedHashMap<>();
        Map<String, String> review = new LinkedHashMap<>();
        boolean later = false;
        for (FormFieldCatalog.Field field : FormFieldCatalog.fieldsOf(kind.module())) {
            apply.put(field.key(), EDITABLE);
            handle.put(field.key(), VISIBLE);
            review.put(field.key(), VISIBLE);
        }
        for (com.projectmgmt.tech.TechCatalog.Field field : catalog.fields()) {
            String applyMode = field.applicant() ? EDITABLE : HIDDEN;
            String handleMode = field.applicant() ? VISIBLE : EDITABLE;
            if (!field.applicant()) {
                later = true;
            }
            apply.put(field.key(), applyMode);
            handle.put(field.key(), handleMode);
            review.put(field.key(), VISIBLE);
            for (com.projectmgmt.tech.TechCatalog.Field sub : field.subfields()) {
                apply.put(sub.key(), applyMode);
                handle.put(sub.key(), handleMode);
                review.put(sub.key(), VISIBLE);
            }
        }
        apply.put("code", VISIBLE);
        apply.put("title", VISIBLE);
        apply.put("files", applicantFiles(catalog) ? EDITABLE : HIDDEN);
        handle.put("files", EDITABLE);
        review.put("files", VISIBLE);
        if (kind == com.projectmgmt.tech.TechKind.TICKET_POOL) {
            return ticketPoolGraph(apply, review);
        }
        if (kind == com.projectmgmt.tech.TechKind.SERVICE_REPORT) {
            return serviceReportGraph(apply, handle, review);
        }
        List<com.projectmgmt.tech.TechFlowCatalog.Step> steps = com.projectmgmt.tech.TechFlowCatalog.of(kind);
        WorkflowGraph graph = new WorkflowGraph();
        graph.getNodes().add(newNode("start", "开始", START, 80, 80, apply));
        List<String> chain = new ArrayList<>();
        chain.add("start");
        int x = 320;
        if (steps.isEmpty()) {
            WorkflowGraph.WorkflowNode fill = newNode("fill", later ? "服务办理" : "填写表单", APPROVAL, x, 80, later ? handle : apply);
            fill.setAssigneeType(STARTER);
            graph.getNodes().add(fill);
            chain.add("fill");
            x += 240;
        } else {
            boolean firstApplicant = true;
            for (com.projectmgmt.tech.TechFlowCatalog.Step step : steps) {
                String type = step.type() == null ? "" : step.type().toLowerCase(Locale.ROOT);
                String nodeType = "cc".equals(type) ? CC : APPROVAL;
                Map<String, String> modes = firstApplicant && "applicant".equals(type) ? apply : (later ? handle : review);
                WorkflowGraph.WorkflowNode node = newNode(step.key(), step.name(), nodeType, x, 80, modes);
                if ("applicant".equals(type) || !StringUtils.hasText(step.assigneeField())) {
                    node.setAssigneeType(STARTER);
                } else {
                    node.setAssigneeType(FORM_FIELD);
                    node.setAssigneeField(step.assigneeField());
                }
                graph.getNodes().add(node);
                chain.add(step.key());
                if ("applicant".equals(type)) {
                    firstApplicant = false;
                }
                x += 240;
            }
        }
        graph.getNodes().add(newNode("end", "结束", END, x, 80, Map.of()));
        chain.add("end");
        for (int i = 0; i < chain.size() - 1; i++) {
            addEdge(graph, chain.get(i), chain.get(i + 1), null, true);
        }
        return graph;
    }

    /** 报修工单池：外部写入或内勤录入 → 负责销售审核。维修/安装分叉后结束，不自动生成另一张单。 */
    private WorkflowGraph ticketPoolGraph(Map<String, String> apply, Map<String, String> review) {
        WorkflowGraph graph = new WorkflowGraph();
        graph.getNodes().add(newNode("start", "开始", START, 80, 120, apply));
        WorkflowGraph.WorkflowNode intake = newNode("intake", "数据自动传输", APPROVAL, 320, 120, apply);
        intake.setAssigneeType(STARTER);
        graph.getNodes().add(intake);
        WorkflowGraph.WorkflowNode audit = newNode("audit", "报修工单审核", APPROVAL, 560, 120, review);
        audit.setAssigneeType(FORM_FIELD);
        audit.setAssigneeField("field_354920929");
        graph.getNodes().add(audit);
        graph.getNodes().add(newNode("end", "结束", END, 860, 120, Map.of()));
        addEdge(graph, "start", "intake", null, true);
        addEdge(graph, "intake", "audit", null, true);
        addCondEdge(graph, "audit", "end", "维修", "field_354937177", "维修");
        addCondEdge(graph, "audit", "end", "安装", "field_354937177", "安装");
        addEdge(graph, "audit", "end", "审核通过", true);
        return graph;
    }

    /** 技术服务报告单：内勤填写后按是否指定工程师分叉，办理完由接单工程师签字并抄送负责销售。 */
    private WorkflowGraph serviceReportGraph(
            Map<String, String> apply,
            Map<String, String> handle,
            Map<String, String> review) {
        WorkflowGraph graph = new WorkflowGraph();
        graph.getNodes().add(newNode("start", "开始", START, 40, 160, apply));
        WorkflowGraph.WorkflowNode office = newNode("office", "内勤填写", APPROVAL, 240, 160, apply);
        office.setAssigneeType(STARTER);
        graph.getNodes().add(office);
        WorkflowGraph.WorkflowNode respond = memberNode("respond", "工程师响应", 480, 40, handle, "field_133455806");
        WorkflowGraph.WorkflowNode accept = memberNode("accept", "工程师接单", 720, 40, handle, "field_133455806");
        WorkflowGraph.WorkflowNode respondNamed = memberNode("respond_named", "指定工程师响应", 480, 280, handle, "field_285995417");
        WorkflowGraph.WorkflowNode acceptNamed = memberNode("accept_named", "指定工程师接单", 720, 280, handle, "field_285995417");
        WorkflowGraph.WorkflowNode work = memberNode("work", "工单填写", 960, 40, handle, "field_133455806");
        WorkflowGraph.WorkflowNode workNamed = memberNode("work_named", "工单填写", 960, 280, handle, "field_285995417");
        WorkflowGraph.WorkflowNode sign = memberNode("sign", "用户老师签字", 1200, 40, handle, "field_133455806");
        WorkflowGraph.WorkflowNode signNamed = memberNode("sign_named", "用户老师签字", 1200, 280, handle, "field_285995417");
        WorkflowGraph.WorkflowNode cc = memberNode("cc", "抄送", 1440, 160, review, "field_221722810");
        cc.setType(CC);
        graph.getNodes().add(respond);
        graph.getNodes().add(accept);
        graph.getNodes().add(respondNamed);
        graph.getNodes().add(acceptNamed);
        graph.getNodes().add(work);
        graph.getNodes().add(workNamed);
        graph.getNodes().add(sign);
        graph.getNodes().add(signNamed);
        graph.getNodes().add(cc);
        graph.getNodes().add(newNode("end", "结束", END, 1680, 160, Map.of()));
        addEdge(graph, "start", "office", null, true);
        addCondEdge(graph, "office", "respond", "未指定", "field_448458436", "否");
        addCondEdge(graph, "office", "respond_named", "已指定", "field_448458436", "是");
        addEdge(graph, "office", "respond", "默认", true);
        addEdge(graph, "respond", "accept", null, true);
        addEdge(graph, "accept", "work", null, true);
        addEdge(graph, "work", "sign", null, true);
        addEdge(graph, "respond_named", "accept_named", null, true);
        addEdge(graph, "accept_named", "work_named", null, true);
        addEdge(graph, "work_named", "sign_named", null, true);
        addEdge(graph, "sign", "cc", null, true);
        addEdge(graph, "sign_named", "cc", null, true);
        addEdge(graph, "cc", "end", null, true);
        return graph;
    }

    private WorkflowGraph.WorkflowNode memberNode(
            String key, String name, int x, int y, Map<String, String> fields, String memberField) {
        WorkflowGraph.WorkflowNode node = newNode(key, name, APPROVAL, x, y, fields);
        node.setAssigneeType(FORM_FIELD);
        node.setAssigneeField(memberField);
        return node;
    }

    private void addCondEdge(WorkflowGraph graph, String from, String to, String name, String field, String value) {
        WorkflowGraph.WorkflowEdge edge = new WorkflowGraph.WorkflowEdge();
        edge.setFrom(from);
        edge.setTo(to);
        edge.setName(name);
        edge.setDefault(false);
        WorkflowGraph.WorkflowCondition condition = new WorkflowGraph.WorkflowCondition();
        condition.setField(field);
        condition.setOp("EQ");
        condition.setValue(value);
        edge.setConditions(new ArrayList<>(List.of(condition)));
        graph.getEdges().add(edge);
    }

    private boolean applicantFiles(com.projectmgmt.tech.TechCatalog.Kind catalog) {
        return catalog.fields().stream().noneMatch(field -> "attachment".equals(field.type()));
    }

    private void patchOaSalaryFieldModes() {
        for (WorkflowDefinition definition : workflowRepository.findAll()) {
            OaKind kind = OaKind.fromModule(definition.getFormModule());
            if (kind == null || !kind.hasSalary()) {
                continue;
            }
            String fillMode = kind == OaKind.ONBOARD ? HIDDEN : VISIBLE;
            WorkflowGraph graph = graphOf(definition);
            WorkflowGraph.WorkflowNode fill = nodeOrNull(graph, "fill");
            WorkflowGraph.WorkflowNode review = nodeOrNull(graph, "review");
            boolean changed = false;
            if (fill != null) {
                changed |= putFieldMode(fill, "salary", fillMode);
                changed |= putFieldMode(fill, "salaryLines", fillMode);
            }
            if (review != null) {
                changed |= putFieldMode(review, "salary", EDITABLE);
                changed |= putFieldMode(review, "salaryLines", EDITABLE);
            }
            if (changed) {
                definition.setGraph(validated(graph, definition.getFormModule()));
            }
        }
    }

    private boolean putFieldMode(WorkflowGraph.WorkflowNode node, String key, String mode) {
        if (node.getFields() == null) {
            node.setFields(new LinkedHashMap<>());
        }
        if (mode.equals(node.getFields().get(key))) {
            return false;
        }
        node.getFields().put(key, mode);
        return true;
    }

    private void seedSimpleFlow(String code, String name, FormModule module, String fillName, String reviewName) {
        String syncedName = module.label() + "流程";
        if (workflowRepository.existsByCode(code)) {
            workflowRepository.findByCode(code).ifPresent(definition -> {
                if (!syncedName.equals(definition.getName())) {
                    definition.setName(syncedName);
                }
            });
            return;
        }
        name = syncedName;
        WorkflowGraph graph = defaultGraph(module);
        Map<String, String> reviewFields = defaultModes(module);
        reviewFields.put("amount", VISIBLE);
        reviewFields.put("qty", VISIBLE);
        node(graph, "fill").setName(fillName);
        node(graph, "fill").setAssigneeType(STARTER);
        WorkflowGraph.WorkflowNode review = newNode("review", reviewName, APPROVAL, 500, 200, reviewFields);
        review.setAssigneeType(STARTER);
        graph.getNodes().add(2, review);
        node(graph, "end").setX(720);
        graph.getEdges().clear();
        addEdge(graph, "start", "fill", null, true);
        addEdge(graph, "fill", "review", null, true);
        addEdge(graph, "review", "end", null, true);
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setName(name);
        definition.setCode(code);
        definition.setFormModule(module.name());
        definition.setPublished(true);
        definition.setGraph(validated(graph, module));
        workflowRepository.save(definition);
    }

    private void completeInstance(WorkflowInstance instance) {
        instance.setStatus(DONE);
        WorkflowGraph graph = graphOf(instance.getDefinition());
        WorkflowGraph.WorkflowNode end = findEnd(graph);
        if (end != null) {
            instance.setCurrentNodeKey(end.getKey());
        }
        cancelPending(instance);
        paymentBudgetService.onWorkflowEnded(instance.getFormModule(), instance.getBizId());
        expenseBudgetService.onWorkflowEnded(instance.getFormModule(), instance.getBizId());
        purchaseSettleService.onWorkflowEnded(instance.getFormModule(), instance.getBizId());
        deviceSettleService.onWorkflowEnded(instance.getFormModule(), instance.getBizId());
        customerOpenSettleService.onWorkflowEnded(instance.getFormModule(), instance.getBizId());
        oaSettleService.onWorkflowEnded(instance.getFormModule(), instance.getBizId());
        depositSettleService.onWorkflowEnded(instance.getFormModule(), instance.getBizId());
    }

    private void enterNode(
            WorkflowInstance instance,
            WorkflowGraph graph,
            WorkflowGraph.WorkflowNode raw,
            String taskKind,
            String returnReason,
            int depth
    ) {
        if (depth > 24) {
            throw ApiException.badRequest("流程分支配置形成了循环，请检查连线");
        }
        WorkflowGraph.WorkflowNode node = raw;
        String type = normalizeType(node.getType());
        instance.setCurrentNodeKey(node.getKey());
        Map<String, String> values = formData.values(instance.getFormModule(), instance.getBizId());
        if (START.equals(type)) {
            markVisited(instance, node.getKey());
            instance.setStatus(DRAFT);
            return;
        }
        if (BRANCH.equals(type) || CONDITION.equals(type)) {
            WorkflowGraph.WorkflowNode next = pickNext(graph, node, values);
            if (next == null) {
                completeInstance(instance);
                return;
            }
            enterNode(instance, graph, next, taskKind, returnReason, depth + 1);
            return;
        }
        if (END.equals(type)) {
            completeInstance(instance);
            addHistory(instance, null, null, node.getKey(), node.getName(), END, currentOrNull(), null);
            return;
        }
        if (ROBOT.equals(type)) {
            try {
                dynamicFormService.createRobotRecord(node.getTargetCode(), instance.getBizId(), currentOrNull());
                addHistory(instance, null, null, node.getKey(), node.getName(), ROBOT, currentOrNull(),
                        node.getRobotName());
            } catch (Exception e) {
                log.warn("ROBOT 节点执行失败: node={}, target={}, error={}",
                        node.getKey(), node.getTargetCode(), e.getMessage());
                addHistory(instance, null, null, node.getKey(), node.getName(), ROBOT, currentOrNull(),
                        "执行失败: " + e.getMessage());
            }
            WorkflowGraph.WorkflowNode next = pickNext(graph, node, values);
            if (next == null) {
                completeInstance(instance);
                return;
            }
            enterNode(instance, graph, next, taskKind, returnReason, depth + 1);
            return;
        }
        if (CC.equals(type)) {
            if (isOrgAssignee(node)) {
                List<SysUser> ccUsers = orgCcAssignees(instance, node);
                if (ccUsers.isEmpty()) {
                    addHistory(instance, null, null, node.getKey(), node.getName(), CC, currentOrNull(), "发起人没有上级主管，自动通过");
                } else {
                    createTasksFor(instance, node, ccUsers, CC, CC, null);
                    addHistory(instance, null, null, node.getKey(), node.getName(), CC, currentOrNull(), null);
                }
            } else {
                createTasks(instance, node, CC, CC, null);
                addHistory(instance, null, null, node.getKey(), node.getName(), CC, currentOrNull(), null);
            }
            WorkflowGraph.WorkflowNode next = pickNext(graph, node, values);
            if (next == null) {
                completeInstance(instance);
                return;
            }
            enterNode(instance, graph, next, taskKind, returnReason, depth + 1);
            return;
        }
        markVisited(instance, node.getKey());
        if (isOrgAssignee(node)) {
            List<SysUser> assignees = currentOrgAssignees(instance, node);
            if (assignees.isEmpty()) {
                autoPassOrgNode(instance, graph, node, taskKind, returnReason, depth);
                return;
            }
            createTasksFor(instance, node, assignees, APPROVAL, taskKind, returnReason);
        } else {
            createTasks(instance, node, APPROVAL, taskKind, returnReason);
        }
        if (instance.getStatus() == null) {
            instance.setStatus(DRAFT);
        }
    }

    private void repairTasks(WorkflowInstance instance, WorkflowGraph graph) {
        if (DONE.equals(instance.getStatus())) {
            return;
        }
        WorkflowGraph.WorkflowNode current = nodeOrNull(graph, instance.getCurrentNodeKey());
        if (current == null || !APPROVAL.equals(normalizeType(current.getType()))) {
            return;
        }
        if (taskRepository.findByInstanceAndNodeKeyAndStatus(instance, current.getKey(), PENDING).isEmpty()) {
            if (isOrgAssignee(current)) {
                List<SysUser> assignees = currentOrgAssignees(instance, current);
                if (!assignees.isEmpty()) {
                    createTasksFor(instance, current, assignees, APPROVAL,
                            StringUtils.hasText(instance.getLastReturnReason()) ? RETURN : NORMAL,
                            instance.getLastReturnReason());
                }
            } else {
                createTasks(instance, current, APPROVAL,
                        StringUtils.hasText(instance.getLastReturnReason()) ? RETURN : NORMAL,
                        instance.getLastReturnReason());
            }
        }
    }

    private void createTasks(
            WorkflowInstance instance,
            WorkflowGraph.WorkflowNode node,
            String nodeType,
            String kind,
            String returnReason
    ) {
        createTasksFor(instance, node, resolveAssignees(instance, node), nodeType, kind, returnReason);
    }

    private void createTasksFor(
            WorkflowInstance instance,
            WorkflowGraph.WorkflowNode node,
            List<SysUser> assignees,
            String nodeType,
            String kind,
            String returnReason
    ) {
        List<WorkflowTask> existing = taskRepository.findByInstanceAndNodeKeyAndStatus(instance, node.getKey(), PENDING);
        for (SysUser user : assignees) {
            boolean exists = existing.stream()
                    .anyMatch(task -> user.getId().equals(task.getAssigneeId()) && nodeType.equals(task.getNodeType()));
            if (exists) {
                continue;
            }
            WorkflowTask task = new WorkflowTask();
            task.setInstance(instance);
            task.setNodeKey(node.getKey());
            task.setNodeName(node.getName());
            task.setNodeType(nodeType);
            task.setAssigneeId(user.getId());
            task.setAssigneeName(user.getDisplayName());
            task.setStatus(PENDING);
            task.setKind(kind);
            task.setReturnReason(returnReason);
            LocalDateTime created = LocalDateTime.now();
            task.setCreatedAt(created);
            task.setDueAt(dueAtOf(node, created, nodeType));
            taskRepository.save(task);
        }
    }

    private List<SysUser> resolveAssignees(WorkflowInstance instance, WorkflowGraph.WorkflowNode node) {
        String assigneeType = !StringUtils.hasText(node.getAssigneeType())
                ? STARTER
                : node.getAssigneeType().trim().toUpperCase(Locale.ROOT);
        List<SysUser> users = new ArrayList<>();
        if (USER.equals(assigneeType) && node.getAssigneeUserIds() != null) {
            for (Long id : node.getAssigneeUserIds()) {
                userRepository.findById(id).ifPresent(users::add);
            }
        } else if (FORM_FIELD.equals(assigneeType) && StringUtils.hasText(node.getAssigneeField())) {
            Map<String, String> values = formData.values(instance.getFormModule(), instance.getBizId());
            String raw = values.getOrDefault(node.getAssigneeField().trim(), "");
            addAssigneesFromRaw(users, raw);
        }
        if (users.isEmpty() && instance.getStarterId() != null && !isOrgAssignee(node)) {
            userRepository.findById(instance.getStarterId()).ifPresent(users::add);
        }
        if (users.isEmpty()) {
            SysUser current = currentOrNull();
            if (current != null) {
                users.add(current);
            }
        }
        return users.stream().distinct().toList();
    }

    private void addAssigneesFromRaw(List<SysUser> users, String raw) {
        if (!StringUtils.hasText(raw)) {
            return;
        }
        for (String part : raw.split("[,;、\\s]+")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            try {
                userRepository.findById(Long.parseLong(token)).ifPresent(users::add);
            } catch (NumberFormatException ignored) {
                userRepository.findByUsername(token).ifPresent(users::add);
                List<SysUser> named = userRepository.findByDisplayNameAndStatus(token, UserStatus.ENABLED);
                if (named.size() == 1) {
                    users.add(named.get(0));
                }
            }
        }
    }

    private WorkflowGraph.WorkflowNode pickNext(
            WorkflowGraph graph,
            WorkflowGraph.WorkflowNode from,
            Map<String, String> values
    ) {
        List<WorkflowGraph.WorkflowEdge> outgoing = graph.getEdges().stream()
                .filter(edge -> from.getKey().equals(edge.getFrom()))
                .toList();
        if (outgoing.isEmpty()) {
            return null;
        }
        WorkflowGraph.WorkflowEdge fallback = null;
        for (WorkflowGraph.WorkflowEdge edge : outgoing) {
            boolean empty = edge.getConditions() == null || edge.getConditions().isEmpty();
            if (edge.isDefault() || empty) {
                if (fallback == null) {
                    fallback = edge;
                }
                continue;
            }
            if (matches(edge.getConditions(), values)) {
                return nodeOrNull(graph, edge.getTo());
            }
        }
        if (fallback != null) {
            return nodeOrNull(graph, fallback.getTo());
        }
        return nodeOrNull(graph, outgoing.get(outgoing.size() - 1).getTo());
    }

    private boolean matches(List<WorkflowGraph.WorkflowCondition> conditions, Map<String, String> values) {
        Map<Integer, List<WorkflowGraph.WorkflowCondition>> groups = new LinkedHashMap<>();
        for (WorkflowGraph.WorkflowCondition condition : conditions) {
            int group = condition.getGroup() == null ? 0 : condition.getGroup();
            groups.computeIfAbsent(group, key -> new ArrayList<>()).add(condition);
        }
        if (groups.isEmpty()) {
            return true;
        }
        for (List<WorkflowGraph.WorkflowCondition> group : groups.values()) {
            boolean all = true;
            for (WorkflowGraph.WorkflowCondition condition : group) {
                if (!matches(condition, values)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(WorkflowGraph.WorkflowCondition condition, Map<String, String> values) {
        if (condition == null || !StringUtils.hasText(condition.getField())) {
            return true;
        }
        String actual = values.getOrDefault(condition.getField(), "");
        String expected = condition.getValue() == null ? "" : condition.getValue();
        String op = condition.getOp() == null ? "EQ" : condition.getOp().trim().toUpperCase(Locale.ROOT);
        return switch (op) {
            case "EQ" -> actual.equalsIgnoreCase(expected);
            case "NE" -> !actual.equalsIgnoreCase(expected);
            case "CONTAINS" -> actual.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
            case "EMPTY" -> !StringUtils.hasText(actual);
            case "NOT_EMPTY" -> StringUtils.hasText(actual);
            case "GT" -> compareNumber(actual, expected) > 0;
            case "GTE" -> compareNumber(actual, expected) >= 0;
            case "LT" -> compareNumber(actual, expected) < 0;
            case "LTE" -> compareNumber(actual, expected) <= 0;
            default -> actual.equalsIgnoreCase(expected);
        };
    }

    private int compareNumber(String left, String right) {
        try {
            return Double.compare(Double.parseDouble(left.trim()), Double.parseDouble(right.trim()));
        } catch (Exception e) {
            return left.compareToIgnoreCase(right);
        }
    }

    private WorkflowRuntimeView runtimeOf(
            WorkflowDefinition definition,
            WorkflowGraph graph,
            WorkflowInstance instance,
            String moduleCode,
            Long bizId
    ) {
        WorkflowGraph.WorkflowNode current = instance == null
                ? findStart(graph)
                : nodeOrNull(graph, instance.getCurrentNodeKey());
        if (current == null) {
            current = findStart(graph);
        }
        if (instance != null && DONE.equals(instance.getStatus())) {
            WorkflowGraph.WorkflowNode end = findEnd(graph);
            if (end != null) {
                current = end;
            }
        }
        WorkflowGraph.WorkflowNode next = pickNext(graph, current, formData.values(moduleCode, bizId));
        SysUser me = currentOrNull();
        boolean canHandle = canHandle(instance, graph, me);
        List<PreviousNodeView> previous = previousNodes(graph, instance, current);
        List<WorkflowTask> pendingApproval = instance == null
                ? List.of()
                : taskRepository.findByInstanceAndNodeKeyAndStatus(instance, current.getKey(), PENDING).stream()
                        .filter(task -> APPROVAL.equals(task.getNodeType()))
                        .toList();
        List<String> assignees = START.equals(normalizeType(current.getType())) && instance != null
                ? (instance.getStarterName() == null ? List.of() : List.of(instance.getStarterName()))
                : pendingApproval.stream().map(WorkflowTask::getAssigneeName).toList();
        boolean overdue = pendingApproval.stream().anyMatch(this::isOverdue);
        LocalDateTime dueAt = pendingApproval.stream()
                .map(WorkflowTask::getDueAt)
                .filter(item -> item != null)
                .min(LocalDateTime::compareTo)
                .orElse(null);
        List<WorkflowHistoryView> history = instance == null
                ? List.of()
                : historyRepository.findByInstanceOrderByIdAsc(instance).stream().map(this::toHistoryView).toList();
        return new WorkflowRuntimeView(
                definition.getId(),
                definition.getName(),
                moduleCode,
                bizId,
                current.getKey(),
                current.getName(),
                normalizeType(current.getType()),
                instance == null ? null : instance.getStatus(),
                applyRoleModes(downgradeIfNotHandler(fieldModes(current, moduleCode), instance, canHandle), moduleCode, bizId),
                next == null ? null : next.getKey(),
                next == null ? null : next.getName(),
                canHandle,
                canHandle && !previous.isEmpty(),
                canHandle,
                instance == null ? null : instance.getLastReturnReason(),
                instance == null ? null : instance.getLastReturnFromName(),
                previous,
                history,
                assignees,
                dueAt,
                overdue
        );
    }

    private WorkflowRuntimeView emptyRuntime(String moduleCode, Long bizId) {
        return new WorkflowRuntimeView(
                null, null, moduleCode, bizId, null, null, null, null,
                applyRoleModes(defaultModes(moduleCode), moduleCode, bizId),
                null, null, false, false, true, null, null, List.of(), List.of(), List.of(),
                null, false
        );
    }

    private List<PreviousNodeView> previousNodes(
            WorkflowGraph graph,
            WorkflowInstance instance,
            WorkflowGraph.WorkflowNode current
    ) {
        if (instance == null) {
            return List.of();
        }
        List<PreviousNodeView> result = new ArrayList<>();
        for (String key : visitedOf(instance)) {
            if (current != null && key.equals(current.getKey())) {
                continue;
            }
            WorkflowGraph.WorkflowNode item = nodeOrNull(graph, key);
            if (item != null) {
                String type = normalizeType(item.getType());
                if (APPROVAL.equals(type) || START.equals(type)) {
                    result.add(new PreviousNodeView(item.getKey(), item.getName()));
                }
            }
        }
        return result;
    }

    private WorkflowRuntimeView submitStart(
            WorkflowInstance instance,
            WorkflowGraph graph,
            WorkflowGraph.WorkflowNode current,
            SysUser actor,
            String comment,
            String moduleCode,
            Long bizId
    ) {
        instance.setLastReturnReason(null);
        instance.setLastReturnFromKey(null);
        instance.setLastReturnFromName(null);
        instance.setStatus(RUNNING);
        Map<String, String> values = formData.values(moduleCode, bizId);
        WorkflowGraph.WorkflowNode next = pickNext(graph, current, values);
        addHistory(
                instance,
                current.getKey(),
                current.getName(),
                next == null ? null : next.getKey(),
                next == null ? null : next.getName(),
                "SUBMIT",
                actor,
                trimToNull(comment)
        );
        if (next == null) {
            completeInstance(instance);
        } else {
            enterNode(instance, graph, next, NORMAL, null, 0);
        }
        return runtimeOf(instance.getDefinition(), graph, instance, moduleCode, bizId);
    }

    private boolean canHandle(WorkflowInstance instance, WorkflowGraph graph, SysUser me) {
        if (me == null) {
            return false;
        }
        if (instance == null) {
            return true;
        }
        if (DONE.equals(instance.getStatus())) {
            return false;
        }
        WorkflowGraph.WorkflowNode current = nodeOrNull(graph, instance.getCurrentNodeKey());
        if (current == null) {
            return false;
        }
        if (START.equals(normalizeType(current.getType()))) {
            return instance.getStarterId() == null || me.getId().equals(instance.getStarterId());
        }
        if (myPendingApproval(instance, me.getId()) != null) {
            return true;
        }
        return STARTER.equals(assigneeTypeOf(current)) && me.getId().equals(instance.getStarterId());
    }

    private WorkflowTask myPendingApproval(WorkflowInstance instance, Long userId) {
        return taskRepository.findByInstanceAndNodeKeyAndStatus(instance, instance.getCurrentNodeKey(), PENDING).stream()
                .filter(task -> APPROVAL.equals(task.getNodeType()) && userId.equals(task.getAssigneeId()))
                .findFirst()
                .orElse(null);
    }

    private void cancelPending(WorkflowInstance instance) {
        for (WorkflowTask task : taskRepository.findByInstanceAndStatus(instance, PENDING)) {
            if (APPROVAL.equals(task.getNodeType())) {
                task.setStatus(CANCELLED);
            }
        }
    }

    private void markVisited(WorkflowInstance instance, String key) {
        List<String> visited = visitedOf(instance);
        if (!visited.contains(key)) {
            visited.add(key);
        }
        instance.setVisitedKeys(visited);
    }

    private List<String> visitedOf(WorkflowInstance instance) {
        return instance.getVisitedKeys() == null ? new ArrayList<>() : new ArrayList<>(instance.getVisitedKeys());
    }

    private void addHistory(
            WorkflowInstance instance,
            String fromKey,
            String fromName,
            String toKey,
            String toName,
            String action,
            SysUser actor,
            String comment
    ) {
        WorkflowHistory history = new WorkflowHistory();
        history.setInstance(instance);
        history.setFromNodeKey(fromKey);
        history.setFromNodeName(fromName);
        history.setToNodeKey(toKey);
        history.setToNodeName(toName);
        history.setAction(action);
        history.setActorId(actor == null ? null : actor.getId());
        history.setActorName(resolveActorName(instance, action, actor == null ? null : actor.getDisplayName()));
        history.setComment(comment);
        historyRepository.save(history);
    }

    private List<InboxItemView> pendingTodo(Long userId, boolean overdueOnly) {
        List<InboxItemView> items = new ArrayList<>();
        for (String kind : List.of(NORMAL, RETURN)) {
            for (WorkflowTask task : taskRepository.findByAssigneeIdAndNodeTypeAndStatusAndKindOrderByIdDesc(
                    userId, APPROVAL, PENDING, kind)) {
                if (overdueOnly && !isOverdue(task)) {
                    continue;
                }
                items.add(toInbox(task, RETURN.equals(kind) ? "RETURNED" : "TODO"));
            }
        }
        for (WorkflowInstance draft : instanceRepository.findByStarterIdAndStatusOrderByIdDesc(userId, DRAFT)) {
            if (!StringUtils.hasText(draft.getLastReturnReason()) || overdueOnly) {
                continue;
            }
            items.add(toDraftInbox(draft, "RETURNED"));
        }
        items.sort(Comparator.comparing(InboxItemView::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return items;
    }

    private InboxItemView toInbox(WorkflowTask task, String inboxType) {
        WorkflowInstance instance = task.getInstance();
        return new InboxItemView(
                task.getId(),
                inboxType,
                StringUtils.hasText(instance.getTitle()) ? instance.getTitle() : labelOf(instance.getFormModule()),
                instance.getFormModule(),
                labelOf(instance.getFormModule()),
                instance.getBizId(),
                instance.getDefinition().getName(),
                task.getNodeKey(),
                task.getNodeName(),
                StringUtils.hasText(task.getReturnReason()) ? task.getReturnReason() : task.getComment(),
                statusLabel(task.getStatus()),
                task.getCreatedAt(),
                task.getHandledAt(),
                task.getDueAt(),
                isOverdue(task)
        );
    }

    private InboxItemView toDraftInbox(WorkflowInstance instance) {
        return toDraftInbox(instance, "DRAFT");
    }

    private InboxItemView toDraftInbox(WorkflowInstance instance, String inboxType) {
        return new InboxItemView(
                instance.getId(),
                inboxType,
                StringUtils.hasText(instance.getTitle()) ? instance.getTitle() : labelOf(instance.getFormModule()),
                instance.getFormModule(),
                labelOf(instance.getFormModule()),
                instance.getBizId(),
                instance.getDefinition().getName(),
                instance.getCurrentNodeKey(),
                currentNodeName(instance),
                instance.getLastReturnReason(),
                instance.getStatus() == null ? "草稿" : statusLabel(instance.getStatus()),
                instance.getCreatedAt(),
                null,
                null,
                false
        );
    }

    private String currentNodeName(WorkflowInstance instance) {
        WorkflowGraph.WorkflowNode node = nodeOrNull(graphOf(instance.getDefinition()), instance.getCurrentNodeKey());
        return node == null || !StringUtils.hasText(node.getName()) ? instance.getCurrentNodeKey() : node.getName();
    }

    public String statusLabel(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case PENDING -> "待处理";
            case DONE -> "已结束";
            case DRAFT -> "草稿";
            case RUNNING -> "进行中";
            case CANCELLED -> "已取消";
            default -> status;
        };
    }

    private WorkflowHistoryView toHistoryView(WorkflowHistory history) {
        String label = switch (history.getAction()) {
            case START -> "发起";
            case "SUBMIT" -> "提交";
            case "APPROVE" -> "同意";
            case CC -> "抄送";
            case RETURN -> "退回";
            case END -> "结束";
            default -> history.getAction();
        };
        String actorName = resolveActorName(history.getInstance(), history.getAction(), history.getActorName());
        if (StringUtils.hasText(actorName) && !actorName.equals(history.getActorName())) {
            history.setActorName(actorName);
        }
        return new WorkflowHistoryView(
                history.getAction(),
                label,
                history.getFromNodeName(),
                history.getToNodeName(),
                actorName,
                history.getComment(),
                history.getCreatedAt()
        );
    }

    private String resolveActorName(WorkflowInstance instance, String action, String storedName) {
        if (StringUtils.hasText(storedName)) {
            return storedName.trim();
        }
        if (!START.equals(action) || instance == null) {
            return storedName;
        }
        if (StringUtils.hasText(instance.getStarterName())) {
            return instance.getStarterName().trim();
        }
        String label = formData.starterLabel(instance.getFormModule(), instance.getBizId());
        return StringUtils.hasText(label) ? label : "公开提交";
    }

    private boolean canWrite(String moduleCode, Long bizId) {
        if (bizId == null) {
            return permissionService.currentAllowed(moduleCode, "CREATE")
                    || permissionService.currentAllowed(moduleCode, "EDIT");
        }
        return permissionService.currentAllowed(moduleCode, "EDIT");
    }

    private boolean canWrite(FormModule module, Long bizId) {
        return canWrite(module == null ? null : module.name(), bizId);
    }

    private Map<String, String> downgradeIfNotHandler(
            Map<String, String> modes,
            WorkflowInstance instance,
            boolean canHandle
    ) {
        if (instance == null || canHandle || DONE.equals(instance.getStatus())) {
            return modes;
        }
        Map<String, String> next = new LinkedHashMap<>(modes);
        next.replaceAll((key, value) -> FormFieldCatalog.isAction(key)
                ? value
                : EDITABLE.equals(value) ? VISIBLE : value);
        return next;
    }

    private Map<String, String> applyRoleModes(Map<String, String> source, String moduleCode, Long bizId) {
        Map<String, String> modes = new LinkedHashMap<>(source);
        if (canWrite(moduleCode, bizId)) {
            modes.replaceAll((key, value) -> {
                if (FormFieldCatalog.ACTION_SUBMIT.equals(key)
                        || FormFieldCatalog.ACTION_CANCEL.equals(key)
                        || FormFieldCatalog.ACTION_DELETE.equals(key)) {
                    return value;
                }
                return EDITABLE;
            });
        } else {
            modes.replaceAll((key, value) -> {
                if (FormFieldCatalog.ACTION_DRAFT.equals(key) || FormFieldCatalog.ACTION_SUBMIT.equals(key)) {
                    return HIDDEN;
                }
                if (FormFieldCatalog.isAction(key)) {
                    return value;
                }
                return EDITABLE.equals(value) ? VISIBLE : value;
            });
        }
        if (!permissionService.currentAllowed(moduleCode, "DELETE")) {
            modes.put(FormFieldCatalog.ACTION_DELETE, HIDDEN);
        }
        return modes;
    }

    private Map<String, String> applyRoleModes(Map<String, String> source, FormModule module, Long bizId) {
        return applyRoleModes(source, module == null ? null : module.name(), bizId);
    }

    private Map<String, String> fieldModes(WorkflowGraph.WorkflowNode node, String moduleCode) {
        Map<String, String> modes = defaultModes(moduleCode);
        if (node != null && node.getFields() != null) {
            node.getFields().forEach((key, value) -> {
                if (modes.containsKey(key)) {
                    modes.put(key, normalizeMode(value));
                }
            });
        }
        if (node != null && END.equals(normalizeType(node.getType()))) {
            Map<String, String> configured = node.getFields() == null ? Map.of() : node.getFields();
            modes.replaceAll((key, value) -> {
                if (FormFieldCatalog.isAction(key)) {
                    return configured.containsKey(key) ? normalizeMode(configured.get(key)) : HIDDEN;
                }
                return VISIBLE;
            });
        }
        return modes;
    }

    private Map<String, String> defaultModes(String moduleCode) {
        Map<String, String> modes = new LinkedHashMap<>();
        for (FormFieldCatalog.Field field : FormFieldCatalog.configurableFields(moduleCode)) {
            modes.put(field.key(), EDITABLE);
        }
        return modes;
    }

    private Map<String, String> defaultModes(FormModule module) {
        return defaultModes(module == null ? null : module.name());
    }

    private WorkflowGraph defaultGraph(FormModule module) {
        WorkflowGraph graph = new WorkflowGraph();
        WorkflowGraph.WorkflowNode fill = newNode("fill", "填写表单", APPROVAL, 320, 200, defaultModes(module));
        fill.setAssigneeType(STARTER);
        graph.getNodes().add(newNode("start", "开始", START, 80, 200, defaultModes(module)));
        graph.getNodes().add(fill);
        graph.getNodes().add(newNode("end", "结束", END, 560, 200, Map.of()));
        addEdge(graph, "start", "fill", null, true);
        addEdge(graph, "fill", "end", null, true);
        return graph;
    }

    private WorkflowGraph.WorkflowNode newNode(String key, String name, String type, int x, int y, Map<String, String> fields) {
        WorkflowGraph.WorkflowNode node = new WorkflowGraph.WorkflowNode();
        node.setKey(key);
        node.setName(name);
        node.setType(type);
        node.setX(x);
        node.setY(y);
        node.setFields(new LinkedHashMap<>(fields));
        node.setAssigneeUserIds(new ArrayList<>());
        return node;
    }

    private void addEdge(WorkflowGraph graph, String from, String to, String name, boolean isDefault) {
        WorkflowGraph.WorkflowEdge edge = new WorkflowGraph.WorkflowEdge();
        edge.setFrom(from);
        edge.setTo(to);
        edge.setName(name);
        edge.setDefault(isDefault);
        edge.setConditions(new ArrayList<>());
        graph.getEdges().add(edge);
    }

    private WorkflowGraph.WorkflowNode findEnd(WorkflowGraph graph) {
        return graph.getNodes().stream()
                .filter(node -> END.equals(normalizeType(node.getType())))
                .findFirst()
                .orElse(null);
    }

    private WorkflowGraph.WorkflowNode findStart(WorkflowGraph graph) {
        return graph.getNodes().stream()
                .filter(node -> START.equals(normalizeType(node.getType())))
                .findFirst()
                // 未配置流程节点的表单（如未挂流程的动态表单）：返回空，避免越界异常
                .orElseGet(() -> graph.getNodes().isEmpty() ? null : graph.getNodes().get(0));
    }

    private WorkflowGraph.WorkflowNode node(WorkflowGraph graph, String key) {
        WorkflowGraph.WorkflowNode found = nodeOrNull(graph, key);
        if (found == null) {
            throw ApiException.badRequest("节点不存在");
        }
        return found;
    }

    private WorkflowGraph.WorkflowNode nodeOrNull(WorkflowGraph graph, String key) {
        return graph.getNodes().stream().filter(item -> key.equals(item.getKey())).findFirst().orElse(null);
    }

    private WorkflowGraph graphOf(WorkflowDefinition definition) {
        WorkflowGraph graph = definition.getGraph();
        if (graph == null) {
            graph = new WorkflowGraph();
        }
        if (graph.getNodes() == null) {
            graph.setNodes(new ArrayList<>());
        }
        if (graph.getEdges() == null) {
            graph.setEdges(new ArrayList<>());
        }
        for (WorkflowGraph.WorkflowNode node : graph.getNodes()) {
            node.setType(normalizeType(node.getType()));
            if (node.getAssigneeUserIds() == null) {
                node.setAssigneeUserIds(new ArrayList<>());
            }
            if (node.getFields() == null) {
                node.setFields(new LinkedHashMap<>());
            }
        }
        for (WorkflowGraph.WorkflowEdge edge : graph.getEdges()) {
            if (edge.getConditions() == null) {
                edge.setConditions(new ArrayList<>());
            }
        }
        return graph;
    }

    private WorkflowGraph validated(WorkflowGraph graph, String moduleCode) {
        validateGraph(graph, moduleCode);
        return graph;
    }

    private WorkflowGraph validated(WorkflowGraph graph, FormModule module) {
        validateGraph(graph, module == null ? null : module.name());
        return graph;
    }

    private void validateGraph(WorkflowGraph graph, String moduleCode) {
        FormModule module = FormModule.fromNameOrNull(moduleCode);
        if (graph.getNodes() == null || graph.getNodes().isEmpty()) {
            throw ApiException.badRequest("请至少添加一个节点");
        }
        long starts = graph.getNodes().stream().filter(node -> START.equals(normalizeType(node.getType()))).count();
        long ends = graph.getNodes().stream().filter(node -> END.equals(normalizeType(node.getType()))).count();
        if (starts != 1) {
            throw ApiException.badRequest("流程必须有且仅有一个开始节点");
        }
        if (ends < 1) {
            throw ApiException.badRequest("流程至少需要一个结束节点");
        }
        Set<String> keys = new HashSet<>();
        for (WorkflowGraph.WorkflowNode node : graph.getNodes()) {
            if (!StringUtils.hasText(node.getKey()) || !StringUtils.hasText(node.getName())) {
                throw ApiException.badRequest("节点名称不能为空");
            }
            String type = normalizeType(node.getType());
            if (!NODE_TYPES.contains(node.getType()) && !NODE_TYPES.contains(type)) {
                throw ApiException.badRequest("节点类型无效，只能是开始、审批、抄送、分支、条件或结束");
            }
            node.setType(type);
            if (APPROVAL.equals(type)) {
                normalizeTimeout(node);
            } else {
                node.setTimeoutValue(null);
                node.setTimeoutUnit(null);
            }
            if (!keys.add(node.getKey())) {
                throw ApiException.badRequest("节点标识重复");
            }
            if (APPROVAL.equals(type) || CC.equals(type)) {
                String assigneeType = !StringUtils.hasText(node.getAssigneeType())
                        ? STARTER
                        : node.getAssigneeType().trim().toUpperCase(Locale.ROOT);
                if (!Set.of(USER, FORM_FIELD, STARTER, ORG_SUPERVISOR, ORG_CHAIN).contains(assigneeType)) {
                    throw ApiException.badRequest("办理人类型无效");
                }
                node.setAssigneeType(assigneeType);
                if (ORG_CHAIN.equals(assigneeType)) {
                    Integer levels = node.getAssigneeLevels();
                    if (levels != null && (levels < 0 || levels > 20)) {
                        throw ApiException.badRequest(node.getName() + " 向上级数必须是 0 到 20");
                    }
                } else {
                    node.setAssigneeLevels(null);
                }
                if (FORM_FIELD.equals(assigneeType) && !FormFieldCatalog.isMemberField(moduleCode, node.getAssigneeField())) {
                    throw ApiException.badRequest(node.getName() + " 请选择表单中的成员字段");
                }
                if (node.getAssigneeUserIds() == null) {
                    node.setAssigneeUserIds(new ArrayList<>());
                }
                if (USER.equals(assigneeType) && node.getAssigneeUserIds().stream().noneMatch(id -> id != null && id > 0)) {
                    throw ApiException.badRequest(node.getName() + " 请选择办理人");
                }
            }
            Map<String, String> cleaned = new LinkedHashMap<>();
            if (!END.equals(type) && !BRANCH.equals(type) && !CONDITION.equals(type) && !ROBOT.equals(type)) {
                for (FormFieldCatalog.Field field : FormFieldCatalog.fieldsOf(moduleCode)) {
                    String raw = node.getFields() == null ? EDITABLE : node.getFields().getOrDefault(field.key(), EDITABLE);
                    cleaned.put(field.key(), normalizeMode(raw));
                }
            }
            String actionFallback = END.equals(type) ? HIDDEN : EDITABLE;
            for (FormFieldCatalog.Field field : FormFieldCatalog.actionFields()) {
                String raw = node.getFields() == null
                        ? actionFallback
                        : node.getFields().getOrDefault(field.key(), actionFallback);
                cleaned.put(field.key(), normalizeMode(raw));
            }
            node.setFields(cleaned);
        }
        for (WorkflowGraph.WorkflowEdge edge : graph.getEdges()) {
            if (!keys.contains(edge.getFrom()) || !keys.contains(edge.getTo())) {
                throw ApiException.badRequest("连线引用了不存在的节点");
            }
            if (edge.getFrom().equals(edge.getTo())) {
                throw ApiException.badRequest("节点不能连接自己");
            }
            if (edge.getConditions() == null) {
                edge.setConditions(new ArrayList<>());
            }
            edge.setConditions(edge.getConditions().stream()
                    .filter(condition -> StringUtils.hasText(condition.getField()))
                    .toList());
            for (WorkflowGraph.WorkflowCondition condition : edge.getConditions()) {
                if (!StringUtils.hasText(condition.getField())) {
                    throw ApiException.badRequest("请选择分支判断字段");
                }
                boolean known = FormFieldCatalog.fieldsOf(moduleCode).stream()
                        .anyMatch(field -> field.key().equals(condition.getField()));
                if (!known) {
                    throw ApiException.badRequest("分支条件引用了不存在的表单字段");
                }
                String op = condition.getOp() == null ? "EQ" : condition.getOp().trim().toUpperCase(Locale.ROOT);
                if (!OPS.contains(op)) {
                    throw ApiException.badRequest("分支判断符无效");
                }
                condition.setOp(op);
            }
        }
    }

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

    /**
     * 扫描已超时未办理的审批待办，向节点上配置的提醒人派发提醒。
     * 提醒以抄送待办（nodeType=CC）落在提醒人的「抄送我」列表，不改动原审批待办。
     */
    @Transactional
    public int dispatchOverdueReminders() {
        LocalDateTime now = LocalDateTime.now();
        List<WorkflowTask> overdue = taskRepository.findByStatusAndNodeTypeAndDueAtBefore(PENDING, APPROVAL, now);
        int created = 0;
        for (WorkflowTask task : overdue) {
            WorkflowInstance instance = task.getInstance();
            if (instance == null || instance.getDefinition() == null) {
                continue;
            }
            WorkflowGraph.WorkflowNode node = nodeOrNull(graphOf(instance.getDefinition()), task.getNodeKey());
            if (node == null) {
                continue;
            }
            boolean escalated = shouldEscalate(node, task.getDueAt(), now);
            List<Long> remindees = escalated ? node.getEscalateRemindees() : node.getRemindees();
            if (remindees == null || remindees.isEmpty()) {
                continue;
            }
            created += createReminderTasks(instance, task, remindees, escalated, now);
        }
        if (created > 0) {
            log.info("超时提醒：新建 {} 条提醒待办", created);
        }
        return created;
    }

    private int createReminderTasks(
            WorkflowInstance instance,
            WorkflowTask source,
            List<Long> remindees,
            boolean escalated,
            LocalDateTime now
    ) {
        String suffix = escalated ? "_R2" : "_R1";
        String base = source.getNodeKey() == null ? "" : source.getNodeKey();
        String nodeKey = truncate(base, 64 - suffix.length()) + suffix;
        String label = escalated ? "超时升级提醒：" : "超时提醒：";
        String nodeName = truncate(label + (source.getNodeName() == null ? "" : source.getNodeName()), 80);
        List<WorkflowTask> existing = taskRepository.findByInstanceAndNodeKeyAndStatus(instance, nodeKey, PENDING);
        int created = 0;
        for (Long userId : remindees) {
            if (userId == null) {
                continue;
            }
            boolean exists = existing.stream()
                    .anyMatch(item -> userId.equals(item.getAssigneeId()) && CC.equals(item.getNodeType()));
            if (exists) {
                continue;
            }
            SysUser user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                continue;
            }
            WorkflowTask reminder = new WorkflowTask();
            reminder.setInstance(instance);
            reminder.setNodeKey(nodeKey);
            reminder.setNodeName(nodeName);
            reminder.setNodeType(CC);
            reminder.setAssigneeId(user.getId());
            reminder.setAssigneeName(truncate(user.getDisplayName(), 50));
            reminder.setStatus(PENDING);
            reminder.setKind(CC);
            reminder.setCreatedAt(now);
            taskRepository.save(reminder);
            created++;
        }
        return created;
    }

    private boolean shouldEscalate(WorkflowGraph.WorkflowNode node, LocalDateTime dueAt, LocalDateTime now) {
        Integer value = node.getEscalateAfterValue();
        if (value == null || value <= 0 || dueAt == null) {
            return false;
        }
        String unit = node.getEscalateAfterUnit() == null ? "" : node.getEscalateAfterUnit().trim().toUpperCase(Locale.ROOT);
        LocalDateTime at = "DAY".equals(unit) ? dueAt.plusDays(value) : dueAt.plusHours(value);
        return now.isAfter(at);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private void normalizeTimeout(WorkflowGraph.WorkflowNode node) {
        Integer value = node.getTimeoutValue();
        if (value == null || value <= 0) {
            node.setTimeoutValue(null);
            node.setTimeoutUnit(null);
        } else {
            if (value > 999) {
                throw ApiException.badRequest("超时时限必须是 1 到 999 的整数");
            }
            String unit = node.getTimeoutUnit() == null ? "" : node.getTimeoutUnit().trim().toUpperCase(Locale.ROOT);
            if (!"HOUR".equals(unit) && !"DAY".equals(unit)) {
                throw ApiException.badRequest("超时时限单位只能是小时或天");
            }
            node.setTimeoutUnit(unit);
        }
        // 升级提醒：需要先设置超时时限才有意义
        if (node.getTimeoutValue() == null) {
            node.setEscalateAfterValue(null);
            node.setEscalateAfterUnit(null);
            node.setRemindees(new ArrayList<>());
            node.setEscalateRemindees(new ArrayList<>());
            return;
        }
        Integer escalate = node.getEscalateAfterValue();
        if (escalate == null || escalate <= 0) {
            node.setEscalateAfterValue(null);
            node.setEscalateAfterUnit(null);
        } else {
            if (escalate > 999) {
                throw ApiException.badRequest("升级提醒时限必须是 1 到 999 的整数");
            }
            String unit = node.getEscalateAfterUnit() == null ? "" : node.getEscalateAfterUnit().trim().toUpperCase(Locale.ROOT);
            if (!"HOUR".equals(unit) && !"DAY".equals(unit)) {
                throw ApiException.badRequest("升级提醒时限单位只能是小时或天");
            }
            node.setEscalateAfterUnit(unit);
        }
        if (node.getRemindees() == null) {
            node.setRemindees(new ArrayList<>());
        }
        if (node.getEscalateRemindees() == null) {
            node.setEscalateRemindees(new ArrayList<>());
        }
    }

    private String normalizeType(String type) {
        String value = type == null ? APPROVAL : type.trim().toUpperCase(Locale.ROOT);
        if ("TASK".equals(value) || "USER_TASK".equals(value)) {
            return APPROVAL;
        }
        if ("GATEWAY".equals(value) || "EXCLUSIVE".equals(value)) {
            return BRANCH;
        }
        if ("COND".equals(value) || "BRANCH_CONDITION".equals(value)) {
            return CONDITION;
        }
        return value;
    }

    private String normalizeMode(String mode) {
        String value = mode == null ? EDITABLE : mode.trim().toUpperCase(Locale.ROOT);
        if (HIDDEN.equals(value) || VISIBLE.equals(value) || EDITABLE.equals(value)) {
            return value;
        }
        return EDITABLE;
    }

    private FormModule parseBindable(String code) {
        FormModule module = parseModule(code);
        if (!FormFieldCatalog.supported(code)) {
            throw ApiException.badRequest("该表单暂不支持绑定流程");
        }
        return module;
    }

    private String labelOf(String moduleCode) {
        FormModule module = FormModule.fromNameOrNull(moduleCode);
        if (module != null) {
            return module.label();
        }
        return formData.moduleLabel(moduleCode);
    }

    private FormModule parseModule(String code) {
        return FormModule.fromNameOrNull(code);
    }

    private String normalizeCode(String code) {
        String normalized = code.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (!normalized.matches("^[A-Z][A-Z0-9_]{1,31}$")) {
            throw ApiException.badRequest("流程编码需为 2-32 位大写字母、数字或下划线");
        }
        return normalized;
    }

    private WorkflowDefinition require(Long id) {
        return workflowRepository.findById(id).orElseThrow(() -> ApiException.notFound("流程不存在"));
    }

    private SysUser currentOrNull() {
        try {
            return currentUser.entity();
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional
    public void reassignPendingOrgTasks() {
        List<WorkflowInstance> running = instanceRepository.findByStatusIn(List.of(RUNNING, DRAFT));
        for (WorkflowInstance instance : running) {
            WorkflowGraph graph = graphOf(instance.getDefinition());
            WorkflowGraph.WorkflowNode current = nodeOrNull(graph, instance.getCurrentNodeKey());
            if (current == null || !APPROVAL.equals(normalizeType(current.getType())) || !isOrgAssignee(current)) {
                continue;
            }
            List<SysUser> expected = currentOrgAssignees(instance, current);
            List<WorkflowTask> pending = taskRepository.findByInstanceAndNodeKeyAndStatus(
                    instance, current.getKey(), PENDING);
            Set<Long> expectedIds = expected.stream().map(SysUser::getId).collect(java.util.stream.Collectors.toSet());
            Set<Long> pendingIds = pending.stream()
                    .filter(task -> APPROVAL.equals(task.getNodeType()))
                    .map(WorkflowTask::getAssigneeId)
                    .collect(java.util.stream.Collectors.toSet());
            if (expectedIds.equals(pendingIds)) {
                for (WorkflowTask task : pending) {
                    userRepository.findById(task.getAssigneeId()).ifPresent(user -> task.setAssigneeName(user.getDisplayName()));
                }
                continue;
            }
            for (WorkflowTask task : pending) {
                if (APPROVAL.equals(task.getNodeType())) {
                    task.setStatus(CANCELLED);
                }
            }
            if (expected.isEmpty()) {
                autoPassOrgNode(instance, graph, current, NORMAL, instance.getLastReturnReason(), 0);
            } else {
                createTasksFor(instance, current, expected, APPROVAL, NORMAL, instance.getLastReturnReason());
            }
        }
    }

    private void autoPassOrgNode(
            WorkflowInstance instance,
            WorkflowGraph graph,
            WorkflowGraph.WorkflowNode node,
            String taskKind,
            String returnReason,
            int depth
    ) {
        Map<String, String> values = formData.values(instance.getFormModule(), instance.getBizId());
        WorkflowGraph.WorkflowNode next = pickNext(graph, node, values);
        addHistory(
                instance,
                node.getKey(),
                node.getName(),
                next == null ? null : next.getKey(),
                next == null ? null : next.getName(),
                "APPROVE",
                currentOrNull(),
                "发起人没有上级主管，自动通过"
        );
        if (next == null) {
            completeInstance(instance);
        } else {
            enterNode(instance, graph, next, taskKind, returnReason, depth + 1);
        }
    }

    private boolean isOrgAssignee(WorkflowGraph.WorkflowNode node) {
        String type = assigneeTypeOf(node);
        return ORG_SUPERVISOR.equals(type) || ORG_CHAIN.equals(type);
    }

    private String assigneeTypeOf(WorkflowGraph.WorkflowNode node) {
        return !StringUtils.hasText(node.getAssigneeType())
                ? STARTER
                : node.getAssigneeType().trim().toUpperCase(Locale.ROOT);
    }

    private List<SysUser> currentOrgAssignees(WorkflowInstance instance, WorkflowGraph.WorkflowNode node) {
        List<List<SysUser>> chain = orgChain(instance, node);
        if (chain.isEmpty()) {
            return List.of();
        }
        if (ORG_SUPERVISOR.equals(assigneeTypeOf(node))) {
            return chain.get(0);
        }
        return orgResolver.currentLevel(chain, approvedActorIds(instance, node.getKey()));
    }

    private List<SysUser> orgCcAssignees(WorkflowInstance instance, WorkflowGraph.WorkflowNode node) {
        List<List<SysUser>> chain = orgChain(instance, node);
        if (ORG_SUPERVISOR.equals(assigneeTypeOf(node))) {
            return chain.isEmpty() ? List.of() : chain.get(0);
        }
        return orgResolver.flatten(chain);
    }

    private List<List<SysUser>> orgChain(WorkflowInstance instance, WorkflowGraph.WorkflowNode node) {
        if (instance.getStarterId() == null) {
            return List.of();
        }
        SysUser starter = userRepository.findById(instance.getStarterId()).orElse(null);
        if (starter == null) {
            return List.of();
        }
        Integer levels = ORG_CHAIN.equals(assigneeTypeOf(node)) ? node.getAssigneeLevels() : 1;
        if (ORG_SUPERVISOR.equals(assigneeTypeOf(node))) {
            levels = 1;
        }
        return orgResolver.chain(starter, levels);
    }

    private Set<Long> approvedActorIds(WorkflowInstance instance, String nodeKey) {
        Set<Long> ids = new HashSet<>();
        for (WorkflowHistory history : historyRepository.findByInstanceOrderByIdAsc(instance)) {
            if (!"APPROVE".equals(history.getAction()) || history.getActorId() == null) {
                continue;
            }
            if (nodeKey.equals(history.getFromNodeKey()) || nodeKey.equals(history.getToNodeKey())) {
                ids.add(history.getActorId());
            }
        }
        return ids;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private WorkflowView toView(WorkflowDefinition definition) {
        WorkflowGraph graph = graphOf(definition);
        return new WorkflowView(
                definition.getId(),
                definition.getName(),
                definition.getCode(),
                definition.getFormModule(),
                labelOf(definition.getFormModule()),
                definition.isPublished(),
                graph.getNodes(),
                graph.getEdges(),
                definition.getUpdatedAt()
        );
    }
}
