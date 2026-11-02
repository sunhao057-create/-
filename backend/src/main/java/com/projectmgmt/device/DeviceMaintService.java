package com.projectmgmt.device;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.common.PageResult;
import com.projectmgmt.org.OrgDeptScope;
import com.projectmgmt.role.DataScopeQuery;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.tech.TechCatalog;
import com.projectmgmt.tech.TechKind;
import com.projectmgmt.tech.TechRecord;
import com.projectmgmt.tech.TechRecordRepository;
import com.projectmgmt.tech.TechService;
import com.projectmgmt.user.SysUser;
import com.projectmgmt.user.UserRepository;
import com.projectmgmt.workflow.WorkflowService;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class DeviceMaintService {

    public static final FormModule MODULE = FormModule.DEVICE_MAINT;
    public static final TechKind KIND = TechKind.MAINT_RECORD;
    public static final String SERVICE_KEY = "service";

    /** 轻流「维保服务记录」分组里的记录卡，不含门户和隐藏旧版。 */
    private static final List<TechKind> SERVICES = List.of(
            TechKind.MAINT_BASIC,
            TechKind.MAINT_LCMS,
            TechKind.MAINT_ICPMS,
            TechKind.MAINT_ICP,
            TechKind.MAINT_AAS,
            TechKind.MAINT_LC,
            TechKind.MAINT_GC,
            TechKind.MAINT_GCMS,
            TechKind.MAINT_AFS,
            TechKind.MAINT_UV);

    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private final TechRecordRepository recordRepository;
    private final DeviceRepository deviceRepository;
    private final OrgDeptScope orgDeptScope;
    private final TechService techService;
    private final WorkflowService workflowService;
    private final PermissionService permissionService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public record Option(String slug, String label) {
    }

    public record DeviceBrief(
            Long id,
            String serviceLabel,
            String title,
            String customerName,
            String startTime,
            Long deviceId,
            String deviceCode,
            String deviceName) {
    }

    @Transactional(readOnly = true)
    public List<DeviceBrief> briefsForDevice(String deviceCode) {
        if (!StringUtils.hasText(deviceCode)) {
            return List.of();
        }
        return recordRepository.findByKindAndExtraJsonContainingOrderByIdDesc(KIND, deviceCode.trim()).stream()
                .map(record -> {
                    Map<String, Object> extra = readExtra(record.getExtraJson());
                    TechKind service = findService(text(extra.get(SERVICE_KEY)));
                    TechCatalog.Kind schema = service == null ? null : TechCatalog.of(service);
                    return new DeviceBrief(
                            record.getId(),
                            service == null ? "维保" : labelOf(service),
                            record.getTitle(),
                            show(schema, extra, "客户名称"),
                            show(schema, extra, "维保开始时间"),
                            null,
                            deviceCode,
                            null);
                })
                .toList();
    }

    /** 本公司设备上的维保记录，供小程序「维保服务」查看。 */
    @Transactional(readOnly = true)
    public List<DeviceBrief> briefsForCompany() {
        Set<Long> companyIds = orgDeptScope.companyDepartmentIds();
        Map<String, Device> byCode = new LinkedHashMap<>();
        for (Device device : deviceRepository.findAll()) {
            if (device.getDepartment() == null || !StringUtils.hasText(device.getCode())) {
                continue;
            }
            if (companyIds != null && !companyIds.contains(device.getDepartment().getId())) {
                continue;
            }
            byCode.put(device.getCode(), device);
        }
        if (byCode.isEmpty()) {
            return List.of();
        }
        return recordRepository.findByKindOrderByIdDesc(KIND).stream()
                .map(record -> briefForCompany(record, byCode))
                .filter(Objects::nonNull)
                .toList();
    }

    private DeviceBrief briefForCompany(TechRecord record, Map<String, Device> byCode) {
        String extraText = record.getExtraJson() == null ? "" : record.getExtraJson();
        Device matched = null;
        for (Map.Entry<String, Device> entry : byCode.entrySet()) {
            if (extraText.contains(entry.getKey())) {
                matched = entry.getValue();
                break;
            }
        }
        if (matched == null) {
            return null;
        }
        Map<String, Object> extra = readExtra(record.getExtraJson());
        TechKind service = findService(text(extra.get(SERVICE_KEY)));
        TechCatalog.Kind schema = service == null ? null : TechCatalog.of(service);
        return new DeviceBrief(
                record.getId(),
                service == null ? "维保" : labelOf(service),
                record.getTitle(),
                show(schema, extra, "客户名称"),
                show(schema, extra, "维保开始时间"),
                matched.getId(),
                matched.getCode(),
                matched.getName());
    }

    @Transactional(readOnly = true)
    public List<Option> services() {
        permissionService.require(MODULE, "VIEW");
        return SERVICES.stream().map(kind -> new Option(kind.slug(), labelOf(kind))).toList();
    }

    @Transactional(readOnly = true)
    public TechCatalog.Kind schema(String slug) {
        permissionService.require(MODULE, "VIEW");
        return TechCatalog.of(requireService(slug));
    }

    @Transactional(readOnly = true)
    public PageResult<DeviceMaintView> list(String keyword, int page, int size) {
        permissionService.require(MODULE, "VIEW");
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 200);
        Long ownerId = permissionService.ownFilterUserId(MODULE);
        Specification<TechRecord> spec = Specification.where(DataScopeQuery.<TechRecord>createdBy(ownerId))
                .and((root, query, cb) -> cb.equal(root.get("kind"), KIND));
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("code")), like),
                    cb.like(cb.lower(root.get("extraJson")), like)));
        }
        Page<TechRecord> result = recordRepository.findAll(
                spec,
                PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "id")));
        List<DeviceMaintView> items = result.getContent().stream().map(this::toView).toList();
        return new PageResult<>(items, result.getTotalElements(), safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public DeviceMaintView detail(Long id) {
        permissionService.require(MODULE, "VIEW");
        TechRecord record = requireRecord(id);
        permissionService.requireOwnRecord(MODULE, record.getCreatedBy());
        return toView(record);
    }

    @Transactional
    public DeviceMaintView create(Map<String, Object> body, SysUser creator) {
        permissionService.require(MODULE, "CREATE");
        TechKind service = requireService(text(body == null ? null : body.get(SERVICE_KEY)));
        Map<String, Object> extra = body == null ? new LinkedHashMap<>() : new LinkedHashMap<>(body);
        extra.put(SERVICE_KEY, service.slug());
        validate(service, extra);
        TechRecord record = new TechRecord();
        record.setKind(KIND);
        record.setCreatedBy(creator);
        record.setExtraJson(writeJson(extra));
        record.setFilesJson("[]");
        record.setTitle(buildTitle(service, extra));
        record.setCode(nextCode());
        TechRecord saved = recordRepository.saveAndFlush(record);
        workflowService.start(MODULE, saved.getId());
        return toView(saved);
    }

    @Transactional
    public DeviceMaintView update(Long id, Map<String, Object> body) {
        permissionService.require(MODULE, "EDIT");
        TechRecord record = requireRecord(id);
        permissionService.requireOwnRecord(MODULE, record.getCreatedBy());
        Map<String, Object> current = readExtra(record.getExtraJson());
        TechKind service = requireService(text(current.get(SERVICE_KEY)));
        Map<String, Object> extra = body == null ? new LinkedHashMap<>() : new LinkedHashMap<>(body);
        extra.put(SERVICE_KEY, service.slug());
        validate(service, extra);
        record.setExtraJson(writeJson(extra));
        record.setTitle(buildTitle(service, extra));
        return toView(recordRepository.save(record));
    }

    @Transactional
    public void delete(Long id) {
        permissionService.require(MODULE, "DELETE");
        TechRecord record = requireRecord(id);
        permissionService.requireOwnRecord(MODULE, record.getCreatedBy());
        recordRepository.delete(record);
    }

    @Transactional
    public void addFiles(Long id, List<MultipartFile> files, String fieldKey) {
        requireWriter(id);
        techService.addFiles(KIND, id, files, fieldKey);
    }

    @Transactional
    public void removeFile(Long id, Long fileId) {
        requireWriter(id);
        techService.removeFile(KIND, id, fileId);
    }

    public static String labelOf(TechKind kind) {
        if (kind == TechKind.MAINT_BASIC) {
            return "基础设施维保记录卡";
        }
        return kind == null ? "" : kind.label();
    }

    public static TechKind findService(String slug) {
        if (!StringUtils.hasText(slug)) {
            return null;
        }
        for (TechKind kind : SERVICES) {
            if (kind.slug().equals(slug)) {
                return kind;
            }
        }
        return null;
    }

    public static Map<String, String> workflowValues(TechRecord item, Map<String, Object> extra) {
        Map<String, String> values = new LinkedHashMap<>();
        if (item == null) {
            return values;
        }
        values.put("code", item.getCode());
        values.put("title", item.getTitle());
        TechKind service = findService(text(extra.get(SERVICE_KEY)));
        values.put("service", service == null ? text(extra.get(SERVICE_KEY)) : labelOf(service));
        TechCatalog.Kind schema = service == null ? null : TechCatalog.of(service);
        values.put("customerName", rawNamed(schema, extra, "客户名称"));
        values.put("deviceCode", rawNamed(schema, extra, "设备编号"));
        values.put("instrumentName", rawNamed(schema, extra, "仪器名称"));
        values.put("instrumentModel", rawNamed(schema, extra, "仪器型号"));
        values.put("vendor", rawNamed(schema, extra, "生产厂家"));
        values.put("maintainer", rawNamed(schema, extra, "维保人员"));
        values.put("startTime", rawNamed(schema, extra, "维保开始时间"));
        values.put("location", rawNamed(schema, extra, "维保定位"));
        if (schema != null) {
            for (TechCatalog.Field field : schema.fields()) {
                values.put(field.key(), stringify(extra.get(field.key())));
            }
        }
        return values;
    }

    private void requireWriter(Long id) {
        try {
            permissionService.require(MODULE, "EDIT");
        } catch (RuntimeException ex) {
            permissionService.require(MODULE, "CREATE");
        }
        TechRecord record = requireRecord(id);
        permissionService.requireOwnRecord(MODULE, record.getCreatedBy());
    }

    private TechRecord requireRecord(Long id) {
        TechRecord record = recordRepository.findById(id).orElseThrow(() -> ApiException.notFound("记录不存在"));
        if (record.getKind() != KIND) {
            throw ApiException.notFound("记录不存在");
        }
        return record;
    }

    private TechKind requireService(String slug) {
        TechKind kind = findService(slug);
        if (kind == null) {
            throw ApiException.badRequest("请选择维保服务");
        }
        return kind;
    }

    private void validate(TechKind service, Map<String, Object> extra) {
        for (TechCatalog.Field field : TechCatalog.of(service).fields()) {
            if (!field.required()) {
                continue;
            }
            if ("attachment".equals(field.type()) || "image".equals(field.type()) || "subtable".equals(field.type())) {
                continue;
            }
            if (!present(extra.get(field.key()))) {
                throw ApiException.badRequest("请填写" + field.name());
            }
        }
    }

    private boolean present(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof List<?> list) {
            return !list.isEmpty();
        }
        return StringUtils.hasText(String.valueOf(value));
    }

    private String nextCode() {
        long seq = recordRepository.countByKind(KIND) + 1;
        return "WB" + LocalDate.now().format(CODE_DATE) + String.format("%03d", seq);
    }

    private String buildTitle(TechKind service, Map<String, Object> extra) {
        TechCatalog.Kind schema = TechCatalog.of(service);
        String customer = rawNamed(schema, extra, "客户名称");
        String instrument = rawNamed(schema, extra, "仪器名称");
        if (StringUtils.hasText(customer)) {
            return labelOf(service) + "-" + customer;
        }
        if (StringUtils.hasText(instrument)) {
            return labelOf(service) + "-" + instrument;
        }
        return labelOf(service);
    }

    private DeviceMaintView toView(TechRecord item) {
        Map<String, Object> extra = readExtra(item.getExtraJson());
        TechKind service = findService(text(extra.get(SERVICE_KEY)));
        TechCatalog.Kind schema = service == null ? null : TechCatalog.of(service);
        var runtime = workflowService.runtime(MODULE.name(), item.getId());
        String creatorName = item.getCreatedBy() == null ? "" : item.getCreatedBy().getDisplayName();
        return new DeviceMaintView(
                item.getId(),
                item.getCode(),
                service == null ? text(extra.get(SERVICE_KEY)) : service.slug(),
                service == null ? "" : labelOf(service),
                item.getTitle(),
                show(schema, extra, "客户名称"),
                show(schema, extra, "设备编号"),
                show(schema, extra, "仪器名称"),
                show(schema, extra, "仪器型号"),
                show(schema, extra, "生产厂家"),
                show(schema, extra, "维保人员"),
                show(schema, extra, "维保开始时间"),
                show(schema, extra, "维保定位"),
                extra,
                runtime.status(),
                runtime.statusLabel(),
                creatorName,
                readFiles(item.getFilesJson()),
                item.getCreatedAt() == null ? null : item.getCreatedAt().toString());
    }

    private String show(TechCatalog.Kind schema, Map<String, Object> extra, String name) {
        TechCatalog.Field field = named(schema, name);
        if (field == null) {
            return "";
        }
        Object raw = extra.get(field.key());
        if (raw == null) {
            return "";
        }
        if ("member".equals(field.type())) {
            return userName(raw);
        }
        return stringify(raw);
    }

    private String userName(Object raw) {
        String text = stringify(raw);
        try {
            return userRepository.findById(Long.parseLong(text))
                    .map(user -> StringUtils.hasText(user.getDisplayName()) ? user.getDisplayName() : user.getUsername())
                    .orElse(text);
        } catch (NumberFormatException ex) {
            return text;
        }
    }

    private static TechCatalog.Field named(TechCatalog.Kind schema, String name) {
        if (schema == null) {
            return null;
        }
        for (TechCatalog.Field field : schema.fields()) {
            if (name.equals(field.name())) {
                return field;
            }
        }
        return null;
    }

    private static String rawNamed(TechCatalog.Kind schema, Map<String, Object> extra, String name) {
        TechCatalog.Field field = named(schema, name);
        if (field == null || extra == null) {
            return "";
        }
        return stringify(extra.get(field.key()));
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String stringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof List<?> list) {
            List<String> parts = new ArrayList<>();
            for (Object item : list) {
                String part = stringify(item);
                if (StringUtils.hasText(part)) {
                    parts.add(part);
                }
            }
            return String.join("、", parts);
        }
        if (value instanceof Map<?, ?> map) {
            Object nested = map.get("name");
            if (nested == null) {
                nested = map.get("label");
            }
            if (nested == null) {
                nested = map.get("value");
            }
            return nested == null ? "" : String.valueOf(nested);
        }
        return String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readExtra(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            return map == null ? new LinkedHashMap<>() : map;
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readFiles(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<Map<String, Object>> list = objectMapper.readValue(json, List.class);
            return list == null ? List.of() : list;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("序列化失败", ex);
        }
    }
}
