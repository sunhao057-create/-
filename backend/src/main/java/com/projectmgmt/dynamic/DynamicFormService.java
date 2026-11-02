package com.projectmgmt.dynamic;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.common.PageResult;
import com.projectmgmt.customer.Customer;
import com.projectmgmt.customer.CustomerRepository;
import com.projectmgmt.project.Project;
import com.projectmgmt.project.ProjectRepository;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.user.SysUser;
import com.projectmgmt.workflow.WorkflowService;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Service
public class DynamicFormService {

    private static final Logger log = LoggerFactory.getLogger(DynamicFormService.class);

    public static final String DRAFT = "DRAFT";
    public static final String RUNNING = "RUNNING";
    public static final String DONE = "DONE";
    public static final String CANCELLED = "CANCELLED";

    private final FormDefinitionRepository definitionRepository;
    private final FormDataRecordRepository recordRepository;
    private final PermissionService permissionService;
    private final CustomerRepository customerRepository;
    private final ProjectRepository projectRepository;
    private final WorkflowService workflowService;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public DynamicFormService(
            FormDefinitionRepository definitionRepository,
            FormDataRecordRepository recordRepository,
            PermissionService permissionService,
            CustomerRepository customerRepository,
            ProjectRepository projectRepository,
            @Lazy WorkflowService workflowService) {
        this.definitionRepository = definitionRepository;
        this.recordRepository = recordRepository;
        this.permissionService = permissionService;
        this.customerRepository = customerRepository;
        this.projectRepository = projectRepository;
        this.workflowService = workflowService;
    }

    /** 轻流「客户信息」应用编码，本系统由固定「客户管理」模块承载。 */
    private static final String CUSTOMER_REF_CODE = "fd5a2f91";
    /** 轻流客户表「客户名称」字段。 */
    private static final String CUSTOMER_NAME_KEY = "q172213225";
    /** 轻流客户表「客户名称缩写」字段。 */
    private static final String CUSTOMER_SHORT_NAME_KEY = "q408030149";
    /** 轻流「项目」应用编码，本系统由固定「项目立项」模块承载。 */
    private static final String PROJECT_REF_CODE = "8rlbff055g01";

    private static final Pattern PURE_ARITH_PATTERN = Pattern.compile("^[\\d\\s+\\-*/().]+$");
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{(\\w+)}");

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listDefinitions(String packageCode) {
        List<FormDefinition> defs = StringUtils.hasText(packageCode)
                ? definitionRepository.findAllByPackageCodeOrderByIdAsc(packageCode)
                : definitionRepository.findAllByEnabledTrueOrderByPackageCodeAscIdAsc();
        return defs.stream().map(def -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", def.getId());
            item.put("code", def.getCode());
            item.put("title", def.getTitle());
            item.put("packageCode", def.getPackageCode());
            item.put("enabled", def.isEnabled());
            return item;
        }).toList();
    }

    @Transactional(readOnly = true)
    public FormDefinition requireDefinition(String code) {
        return definitionRepository.findByCode(code)
                .orElseThrow(() -> ApiException.notFound("表单不存在: " + code));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> definitionDetail(String code) {
        FormDefinition def = requireDefinition(code);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", def.getId());
        result.put("code", def.getCode());
        result.put("title", def.getTitle());
        result.put("packageCode", def.getPackageCode());
        result.put("enabled", def.isEnabled());
        result.put("designJson", def.getDesignJson());
        result.put("graphJson", def.getGraphJson());
        result.put("serialJson", def.getSerialJson());
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> stripAuxiliaryFields(Map<String, Object> design) {
        if (design == null) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>(design);
        Object fieldsObj = copy.get("fields");
        if (fieldsObj instanceof List<?> fields) {
            List<Object> cleaned = new ArrayList<>();
            for (Object f : fields) {
                if (f instanceof Map<?, ?> field) {
                    String title = String.valueOf(field.get("title"));
                    if (auxiliaryTitle(title)) {
                        continue;
                    }
                    cleaned.add(f);
                }
            }
            copy.put("fields", cleaned);
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private List<Object> dropEmptySections(List<Object> fields) {
        if (fields == null) {
            return List.of();
        }
        List<Object> result = new ArrayList<>();
        for (Object f : fields) {
            if (f instanceof Map<?, ?> field) {
                if ("section".equals(String.valueOf(field.get("type")))) {
                    Object children = field.get("children");
                    if (children instanceof List<?> list && list.isEmpty()) {
                        continue;
                    }
                }
                result.add(f);
            }
        }
        return result;
    }

    private boolean auxiliaryTitle(String title) {
        if (title == null) {
            return false;
        }
        return title.contains("辅助") || title.contains("流水") || title.contains("隐藏");
    }

    @Transactional(readOnly = true)
    public PageResult<Map<String, Object>> listData(String code, int page, int size, String keyword, String status, SysUser user) {
        FormDefinition def = requireDefinition(code);
        permissionService.require(code, "VIEW");
        Long userId = permissionService.unrestricted(code) ? null : user.getId();
        Page<FormDataRecord> pageResult = recordRepository.search(def.getId(), userId, keyword, status, PageRequest.of(page - 1, size));
        List<Map<String, Object>> items = pageResult.getContent().stream()
                .map(r -> toRow(def, r, false))
                .toList();
        return new PageResult<>(items, pageResult.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> myInbox(SysUser user) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        List<com.projectmgmt.workflow.WorkflowTask> tasks = workflowService.pendingTasks(user.getId());
        for (com.projectmgmt.workflow.WorkflowTask t : tasks) {
            com.projectmgmt.workflow.WorkflowInstance inst = t.getInstance();
            if (inst == null || inst.getFormModule() == null) {
                continue;
            }
            if (com.projectmgmt.role.FormModule.fromNameOrNull(inst.getFormModule()) != null) {
                continue;
            }
            FormDataRecord record = recordRepository.findById(inst.getBizId()).orElse(null);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", t.getId());
            item.put("nodeName", t.getNodeName());
            item.put("type", t.getNodeType());
            item.put("recordTitle", record == null ? null : record.getTitle());
            item.put("definitionCode", inst.getFormModule());
            item.put("recordId", inst.getBizId());
            items.add(item);
        }
        result.put("total", items.size());
        result.put("items", items);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getData(String code, Long id, SysUser user) {
        FormDefinition def = requireDefinition(code);
        FormDataRecord record = recordRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("记录不存在"));
        permissionService.require(code, "VIEW");
        boolean pendingAssignee = workflowService.isPendingAssignee(code, id, user);
        Map<String, Object> row = toRow(def, record, true);
        row.put("canEdit", Objects.equals(record.getCreatedBy().getId(), user.getId()) || pendingAssignee);
        return row;
    }

    @Transactional
    public Map<String, Object> createData(String code, Map<String, Object> body, SysUser user) {
        FormDefinition def = requireDefinition(code);
        permissionService.require(code, "CREATE");
        Map<String, Object> values = body == null ? Map.of() : body;
        validate(def, values);
        recomputeFormulas(def, values);
        FormDataRecord record = new FormDataRecord();
        record.setDefinition(def);
        record.setCreatedBy(user);
        record.setValuesJson(writeJson(values));
        record.setTitle(buildTitle(def, values, null));
        record.setStatus(DRAFT);
        applySerial(def, record, values, null);
        FormDataRecord saved = recordRepository.saveAndFlush(record);
        boolean submitted = Boolean.TRUE.equals(body.get("submitted"));
        if (submitted) {
            startUnifiedFlow(def, saved, user, true);
        }
        return toRow(def, saved, true);
    }

    @Transactional
    public Map<String, Object> updateData(String code, Long id, Map<String, Object> body, SysUser user) {
        FormDefinition def = requireDefinition(code);
        FormDataRecord record = recordRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("记录不存在"));
        boolean pendingAssignee = workflowService.isPendingAssignee(code, id, user);
        if (!Objects.equals(record.getCreatedBy().getId(), user.getId()) && !pendingAssignee) {
            throw ApiException.forbidden("无权修改");
        }
        Map<String, Object> values = body == null ? Map.of() : body;
        validate(def, values);
        recomputeFormulas(def, values);
        record.setValuesJson(writeJson(values));
        record.setTitle(buildTitle(def, values, record));
        applySerial(def, record, values, record.getSerialNo());
        recordRepository.save(record);
        boolean submitted = Boolean.TRUE.equals(body.get("submitted"));
        if (submitted && DRAFT.equals(record.getStatus())) {
            startUnifiedFlow(def, record, user, true);
        }
        return toRow(def, record, true);
    }

    @Transactional
    public void deleteData(String code, Long id, SysUser user) {
        FormDefinition def = requireDefinition(code);
        FormDataRecord record = recordRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("记录不存在"));
        if (!Objects.equals(record.getCreatedBy().getId(), user.getId())) {
            throw ApiException.forbidden("无权删除");
        }
        if (RUNNING.equals(workflowService.statusOf(code, id))) {
            throw ApiException.badRequest("流程进行中，无法删除");
        }
        workflowService.cancel(code, id);
        recordRepository.delete(record);
    }

    @Transactional
    public Map<String, Object> importHistorical(String code, Map<String, Object> body, SysUser user) {
        FormDefinition def = requireDefinition(code);
        permissionService.require(code, "CREATE");
        Map<String, Object> values = body == null ? Map.of() : body;
        FormDataRecord record = new FormDataRecord();
        record.setDefinition(def);
        record.setCreatedBy(user);
        record.setValuesJson(writeJson(values));
        record.setTitle(buildTitle(def, values, null));
        record.setStatus(DONE);
        record.setSourceKey(values.get("sourceKey") == null ? null : String.valueOf(values.get("sourceKey")));
        applySerial(def, record, values, null);
        FormDataRecord saved = recordRepository.saveAndFlush(record);
        return toRow(def, saved, false);
    }

    @Transactional
    public Map<String, Object> importHistoricalBatch(String code, List<Map<String, Object>> rows, SysUser user) {
        Map<String, Object> result = new LinkedHashMap<>();
        int success = 0;
        List<String> errors = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            try {
                importHistorical(code, row, user);
                success++;
            } catch (Exception e) {
                errors.add(e.getMessage());
            }
        }
        result.put("success", success);
        result.put("errors", errors);
        return result;
    }

    @SuppressWarnings("unchecked")
    private void validate(FormDefinition def, Map<String, Object> values) {
        Map<String, Object> design = readJson(def.getDesignJson(), Map.class);
        if (design == null) {
            return;
        }
        Object fieldsObj = design.get("fields");
        if (!(fieldsObj instanceof List<?> fields)) {
            return;
        }
        for (Object f : fields) {
            if (!(f instanceof Map<?, ?> field)) {
                continue;
            }
            String key = String.valueOf(field.get("key"));
            boolean required = Boolean.TRUE.equals(field.get("required"));
            if (required && !values.containsKey(key)) {
                throw ApiException.badRequest("缺少必填字段: " + field.get("title"));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void recomputeFormulas(FormDefinition def, Map<String, Object> values) {
        Map<String, Object> design = readJson(def.getDesignJson(), Map.class);
        if (design == null) {
            return;
        }
        Object fieldsObj = design.get("fields");
        if (!(fieldsObj instanceof List<?> fields)) {
            return;
        }
        for (Object f : fields) {
            if (!(f instanceof Map<?, ?> field)) {
                continue;
            }
            Object formula = field.get("formula");
            if (formula == null) {
                continue;
            }
            String expr = String.valueOf(formula);
            try {
                String replaced = VAR_PATTERN.matcher(expr).replaceAll((MatchResult m) -> {
                    String key = m.group(1);
                    Object val = values.get(key);
                    return val == null ? "0" : String.valueOf(val);
                });
                if (PURE_ARITH_PATTERN.matcher(replaced).matches()) {
                    double result = evalArith(replaced);
                    values.put(String.valueOf(field.get("key")), result);
                }
            } catch (Exception e) {
                log.debug("公式计算失败: {}", expr, e);
            }
        }
    }

    private double evalArith(String expr) {
        return new Object() {
            int pos = -1, ch;
            void nextChar() {
                ch = (++pos < expr.length()) ? expr.charAt(pos) : -1;
            }
            boolean eat(int charToEat) {
                while (ch == ' ') nextChar();
                if (ch == charToEat) {
                    nextChar();
                    return true;
                }
                return false;
            }
            double parse() {
                nextChar();
                double x = parseExpression();
                return x;
            }
            double parseExpression() {
                double x = parseTerm();
                while (true) {
                    if (eat('+')) x += parseTerm();
                    else if (eat('-')) x -= parseTerm();
                    else return x;
                }
            }
            double parseTerm() {
                double x = parseFactor();
                while (true) {
                    if (eat('*')) x *= parseFactor();
                    else if (eat('/')) x /= parseFactor();
                    else return x;
                }
            }
            double parseFactor() {
                if (eat('+')) return parseFactor();
                if (eat('-')) return -parseFactor();
                double x;
                int startPos = pos;
                if (eat('(')) {
                    x = parseExpression();
                    eat(')');
                } else {
                    while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                    x = Double.parseDouble(expr.substring(startPos, pos));
                }
                return x;
            }
        }.parse();
    }

    private Double toDouble(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(val));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    String buildTitle(FormDefinition def, Map<String, Object> values, FormDataRecord existing) {
        Map<String, Object> design = readJson(def.getDesignJson(), Map.class);
        String titleField = design == null ? null : String.valueOf(design.get("titleField"));
        if (StringUtils.hasText(titleField) && values.containsKey(titleField)) {
            Object val = values.get(titleField);
            if (val != null && !String.valueOf(val).isBlank()) {
                return def.getTitle() + "-" + val;
            }
        }
        return def.getTitle() + "-" + (existing == null ? System.currentTimeMillis() : existing.getId());
    }

    @SuppressWarnings("unchecked")
    void applySerial(FormDefinition def, FormDataRecord record, Map<String, Object> values, String existingSerial) {
        if (StringUtils.hasText(existingSerial)) {
            record.setSerialNo(existingSerial);
            return;
        }
        String serialJson = def.getSerialJson();
        if (!StringUtils.hasText(serialJson)) {
            return;
        }
        try {
            Map<String, Object> cfg = objectMapper.readValue(serialJson, Map.class);
            String prefix = String.valueOf(cfg.getOrDefault("prefix", ""));
            String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
            long count = recordRepository.countByDefinitionIdAndStatus(def.getId(), DONE) + 1;
            String serial = prefix + datePart + String.format("%03d", count);
            record.setSerialNo(serial);
        } catch (Exception e) {
            log.debug("流水号生成失败", e);
        }
    }

    private Map<String, Object> toRow(FormDefinition def, FormDataRecord r, boolean detail) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", r.getId());
        row.put("code", def.getCode());
        row.put("title", r.getTitle());
        row.put("serialNo", r.getSerialNo());
        row.put("status", r.getStatus());
        row.put("sourceKey", r.getSourceKey());
        row.put("values", readJson(r.getValuesJson(), Map.class));
        row.put("createdBy", r.getCreatedBy() == null ? null : r.getCreatedBy().getDisplayName());
        row.put("createdAt", r.getCreatedAt());
        row.put("updatedAt", r.getUpdatedAt());
        if (detail) {
            row.put("flow", flowOf(def, r));
        }
        return row;
    }

    private Map<String, Object> flowOf(FormDefinition def, FormDataRecord r) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            com.projectmgmt.workflow.dto.WorkflowRuntimeView rt = workflowService.runtime(def.getCode(), r.getId());
            result.put("instanceId", rt.processId());
            result.put("status", rt.status());
            result.put("currentNode", rt.currentNodeName());
            result.put("fieldAuth", rt.fields() == null ? Map.of() : rt.fields());
            List<Map<String, Object>> history = new ArrayList<>();
            if (rt.history() != null) {
                for (com.projectmgmt.workflow.dto.WorkflowRuntimeView.WorkflowHistoryView h : rt.history()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("nodeName", h.toNodeName());
                    item.put("action", h.action());
                    item.put("comment", h.comment());
                    item.put("assigneeName", h.actorName());
                    item.put("createdAt", h.createdAt());
                    item.put("doneAt", h.createdAt());
                    history.add(item);
                }
            }
            result.put("history", history);
        } catch (Exception e) {
            result.put("instanceId", null);
            result.put("status", DRAFT);
            result.put("currentNode", null);
            result.put("fieldAuth", Map.of());
            result.put("history", List.of());
        }
        return result;
    }

    private int fieldCount(FormDefinition def) {
        Map<String, Object> design = readJson(def.getDesignJson(), Map.class);
        if (design == null) {
            return 0;
        }
        Object fields = design.get("fields");
        return fields instanceof List<?> list ? list.size() : 0;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> lookupRecords(String code, String keyword, int limit, String displayKey,
                                                   String refKey, String refValue) {
        // 轻流「客户信息」应用（fd5a2f91）在本系统中由固定「客户管理」模块承载，
        // 关联选择器按同级字段回退到该模块，避免客户名称等关联字段无处可选。
        if (CUSTOMER_REF_CODE.equals(code)) {
            return lookupCustomers(keyword, limit, displayKey);
        }
        // 轻流「项目」应用（8rlbff055g01）在本系统中由固定「项目立项」模块承载，同样按回退处理
        if (PROJECT_REF_CODE.equals(code)) {
            return lookupProjects(keyword, limit, displayKey);
        }
        FormDefinition def = requireDefinition(code);
        List<FormDataRecord> records = recordRepository.findAllByDefinitionIdOrderByIdDesc(def.getId());
        // 仪器库这类「一条记录存多行明细」的表单：displayKey 落在子表列上时按行拆分成可选项，
        // 使下单时能按「仪器」粒度从客户仪器库中拉取（名称/品牌/型号/序列码/编码）
        String subtableKey = subtableKeyForColumn(def, displayKey);
        List<Map<String, Object>> result = new ArrayList<>();
        for (FormDataRecord r : records) {
            Map<String, Object> values = readJson(r.getValuesJson(), Map.class);
            if (values == null) {
                continue;
            }
            // 级联过滤：按当前单据同级字段的取值（如「客户名称」）筛选目标记录，
            // 让下单时只从所选客户的仪器库中拉取仪器。
            if (!matchesRef(values, r.getTitle(), refKey, refValue)) {
                continue;
            }
            if (subtableKey != null) {
                Object rowsObj = values.get(subtableKey);
                if (rowsObj instanceof List<?> rows) {
                    for (Object rowObj : rows) {
                        if (!(rowObj instanceof Map<?, ?> row)) {
                            continue;
                        }
                        Map<String, Object> rowValues = new LinkedHashMap<>();
                        for (Map.Entry<?, ?> entry : row.entrySet()) {
                            rowValues.put(String.valueOf(entry.getKey()), entry.getValue());
                        }
                        String rowLabel = rowValues.get(displayKey) == null
                                ? "" : String.valueOf(rowValues.get(displayKey)).trim();
                        if (rowLabel.isEmpty()) {
                            continue;
                        }
                        if (StringUtils.hasText(keyword) && !containsKeyword(rowValues, keyword)) {
                            continue;
                        }
                        Map<String, Object> rowItem = new LinkedHashMap<>();
                        rowItem.put("id", r.getId());
                        rowItem.put("title", rowLabel);
                        rowItem.put("label", rowLabel);
                        // 回传子表行值，供前端按 displayKey 回填设备名称/型号/厂家/序列码/仪器编码
                        rowItem.put("values", rowValues);
                        result.add(rowItem);
                        if (result.size() >= limit) {
                            return result;
                        }
                    }
                }
                continue;
            }
            // 关联选择器取 displayKey 指定的字段值作为显示文本（与线上 ref 字段一致）
            String label = StringUtils.hasText(displayKey) && values.get(displayKey) != null
                    ? String.valueOf(values.get(displayKey)).trim()
                    : "";
            if (label.isEmpty()) {
                label = r.getTitle() == null ? "" : r.getTitle();
            }
            if (StringUtils.hasText(keyword) && !label.contains(keyword)) {
                boolean match = false;
                for (Object v : values.values()) {
                    if (v != null && String.valueOf(v).contains(keyword)) {
                        match = true;
                        break;
                    }
                }
                if (!match) {
                    continue;
                }
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", r.getId());
            item.put("title", r.getTitle());
            item.put("label", label);
            item.put("values", values);
            result.add(item);
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    /** displayKey 命中某个子表列时返回该子表字段 key；命中顶层字段或未命中时返回 null。 */
    private String subtableKeyForColumn(FormDefinition def, String displayKey) {
        if (!StringUtils.hasText(displayKey)) {
            return null;
        }
        Map<String, Object> design = readJson(def.getDesignJson(), Map.class);
        if (design == null) {
            return null;
        }
        Object fields = design.get("fields");
        if (!(fields instanceof List<?> list)) {
            return null;
        }
        for (Object field : list) {
            if (field instanceof Map<?, ?> map && displayKey.equals(String.valueOf(map.get("key")))) {
                return null;
            }
        }
        for (Object field : list) {
            if (!(field instanceof Map<?, ?> map)) {
                continue;
            }
            if (!"subtable".equals(String.valueOf(map.get("type")))) {
                continue;
            }
            Object columns = map.get("columns");
            if (!(columns instanceof List<?> columnList)) {
                continue;
            }
            for (Object column : columnList) {
                if (column instanceof Map<?, ?> columnMap && displayKey.equals(String.valueOf(columnMap.get("key")))) {
                    return String.valueOf(map.get("key"));
                }
            }
        }
        return null;
    }

    private boolean containsKeyword(Map<String, Object> values, String keyword) {
        for (Object value : values.values()) {
            if (value != null && String.valueOf(value).contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /** 级联过滤：目标记录指定字段的文本等于当前单据同级字段取值时命中；未配置过滤条件时一律命中。 */
    private boolean matchesRef(Map<String, Object> values, String title, String refKey, String refValue) {
        if (!StringUtils.hasText(refKey) || !StringUtils.hasText(refValue)) {
            return true;
        }
        String expected = refValue.trim();
        Object value = values.get(refKey);
        if (value != null && expected.equals(cellText(value))) {
            return true;
        }
        // 仪器库以「客户名称」为记录标题，字段缺失时按标题兜底匹配
        return title != null && expected.equals(title.trim());
    }

    private String cellText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Map<?, ?> map) {
            Object label = map.get("label");
            if (label == null) {
                label = map.get("title");
            }
            if (label == null) {
                label = map.get("value");
            }
            return label == null ? "" : String.valueOf(label).trim();
        }
        return String.valueOf(value).trim();
    }

    /** 固定客户模块的关联选择：轻流客户应用的编码回退到「客户管理」列表。 */
    private List<Map<String, Object>> lookupCustomers(String keyword, int limit, String displayKey) {
        Specification<Customer> spec = (root, query, cb) -> {
            if (!StringUtils.hasText(keyword)) {
                return cb.conjunction();
            }
            String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("shortName")), like),
                    cb.like(cb.lower(root.get("companyName")), like),
                    cb.like(cb.lower(root.get("code")), like));
        };
        List<Map<String, Object>> result = new ArrayList<>();
        for (Customer customer : customerRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "id"))) {
            // 客户名称默认取公司名称；客户名称缩写取简称（对应轻流客户表字段 q172213225 / q408030149）
            String label = CUSTOMER_SHORT_NAME_KEY.equals(displayKey) ? customer.getShortName() : customer.getCompanyName();
            if (!StringUtils.hasText(label)) {
                continue;
            }
            Map<String, Object> values = new LinkedHashMap<>();
            values.put(CUSTOMER_NAME_KEY, customer.getCompanyName());
            values.put(CUSTOMER_SHORT_NAME_KEY, customer.getShortName());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", customer.getId());
            item.put("title", label.trim());
            item.put("label", label.trim());
            item.put("values", values);
            result.add(item);
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    /** 固定项目模块的关联选择：轻流项目应用的编码回退到「项目立项」列表。 */
    private List<Map<String, Object>> lookupProjects(String keyword, int limit, String displayKey) {
        Specification<Project> spec = (root, query, cb) -> {
            if (!StringUtils.hasText(keyword)) {
                return cb.conjunction();
            }
            String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("code")), like),
                    cb.like(cb.lower(root.get("companyName")), like));
        };
        List<Map<String, Object>> result = new ArrayList<>();
        for (Project project : projectRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "id"))) {
            String label = StringUtils.hasText(project.getName()) ? project.getName() : project.getCode();
            if (!StringUtils.hasText(label)) {
                continue;
            }
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("name", project.getName());
            values.put("code", project.getCode());
            values.put("companyName", project.getCompanyName());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", project.getId());
            item.put("title", label.trim());
            item.put("label", label.trim());
            item.put("values", values);
            result.add(item);
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    @Transactional
    public FormDataRecord createRobotRecord(String targetCode, FormDataRecord source, SysUser user) {
        FormDefinition targetDef = requireDefinition(targetCode);
        FormDefinition sourceDef = source.getDefinition();
        Map<String, Object> sourceValues = readJson(source.getValuesJson(), Map.class);
        Map<String, Object> targetValues = mapByTitle(sourceDef, targetDef, sourceValues == null ? Map.of() : sourceValues);
        FormDataRecord record = new FormDataRecord();
        record.setDefinition(targetDef);
        record.setCreatedBy(user);
        record.setValuesJson(writeJson(targetValues));
        record.setTitle(buildTitle(targetDef, targetValues, null));
        record.setStatus(DRAFT);
        applySerial(targetDef, record, targetValues, null);
        return recordRepository.saveAndFlush(record);
    }

    @Transactional
    public void syncWorkflowStatus(Long recordId, String status) {
        FormDataRecord record = recordRepository.findById(recordId).orElse(null);
        if (record != null) {
            record.setStatus(status);
        }
    }

    private boolean startUnifiedFlow(FormDefinition def, FormDataRecord record, SysUser user, boolean submit) {
        workflowService.start(def.getCode(), record.getId());
        record.setStatus(RUNNING);
        return true;
    }

    @Transactional
    public FormDataRecord createRobotRecord(String targetCode, Long sourceRecordId, SysUser user) {
        FormDataRecord source = recordRepository.findById(sourceRecordId)
                .orElseThrow(() -> ApiException.notFound("源记录不存在"));
        return createRobotRecord(targetCode, source, user);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapByTitle(FormDefinition sourceDef, FormDefinition targetDef, Map<String, Object> sourceValues) {
        Map<String, String> sourceTitles = collectFieldTitles(readJson(sourceDef.getDesignJson(), Map.class));
        Map<String, String> targetTitles = collectFieldTitles(readJson(targetDef.getDesignJson(), Map.class));
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> srcEntry : sourceTitles.entrySet()) {
            String targetKey = null;
            for (Map.Entry<String, String> tgtEntry : targetTitles.entrySet()) {
                if (Objects.equals(srcEntry.getValue(), tgtEntry.getValue())) {
                    targetKey = tgtEntry.getKey();
                    break;
                }
            }
            if (targetKey != null && sourceValues.containsKey(srcEntry.getKey())) {
                result.put(targetKey, sourceValues.get(srcEntry.getKey()));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> collectFieldTitles(Map<String, Object> design) {
        Map<String, String> result = new LinkedHashMap<>();
        if (design == null) {
            return result;
        }
        Object fields = design.get("fields");
        if (!(fields instanceof List<?> list)) {
            return result;
        }
        for (Object f : list) {
            if (!(f instanceof Map<?, ?> field)) {
                continue;
            }
            String key = String.valueOf(field.get("key"));
            String title = String.valueOf(field.get("title"));
            result.put(key, title);
        }
        return result;
    }

    <T> T readJson(String json, Class<T> type) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            return null;
        }
    }

    String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("序列化失败", e);
        }
    }

    JsonNode readTree(String json) {
        if (!StringUtils.hasText(json)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }
}
