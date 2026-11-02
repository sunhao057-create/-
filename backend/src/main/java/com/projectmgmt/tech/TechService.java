package com.projectmgmt.tech;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.common.PageResult;
import com.projectmgmt.profile.FileStorageService;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.user.SysUser;
import com.projectmgmt.user.UserRepository;
import com.projectmgmt.workflow.WorkflowService;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class TechService {

    private final TechRecordRepository recordRepository;
    private final FileStorageService fileStorageService;
    private final WorkflowService workflowService;
    private final PermissionService permissionService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    @Transactional(readOnly = true)
    public PageResult<TechRecordView> list(TechKind kind, String keyword, int page, int size) {
        permissionService.require(kind.module(), "VIEW");
        Page<TechRecord> pageResult = recordRepository.search(kind, keyword, PageRequest.of(page - 1, size));
        List<TechRecordView> items = pageResult.getContent().stream().map(this::toView).toList();
        return new PageResult<>(items, pageResult.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public List<TechRecord> listPublic(TechKind kind, String keyword) {
        return recordRepository.search(kind, keyword, PageRequest.of(0, 500)).getContent();
    }

    @Transactional(readOnly = true)
    public TechRecord requirePublic(TechKind kind, Long id) {
        TechRecord record = recordRepository.findById(id).orElseThrow(() -> ApiException.notFound("记录不存在"));
        if (record.getKind() != kind) {
            throw ApiException.notFound("记录不存在");
        }
        return record;
    }

    @Transactional
    public TechRecord createPublic(TechKind kind, UpsertTechRecord request, List<MultipartFile> files) {
        TechRecord record = new TechRecord();
        record.setKind(kind);
        record.setExtraJson(writeJson(request.extra() == null ? Map.of() : request.extra()));
        record.setFilesJson("[]");
        record.setTitle(buildTitle(kind, request.extra()));
        record.setCode(generateCode(kind));
        TechRecord saved = recordRepository.saveAndFlush(record);
        if (files != null && !files.isEmpty()) {
            attachFiles(saved, files, null);
        }
        workflowService.start(kind.module(), saved.getId());
        return saved;
    }

    @Transactional
    public TechRecord create(TechKind kind, Map<String, Object> extra, SysUser creator) {
        permissionService.require(kind.module(), "CREATE");
        TechRecord record = new TechRecord();
        record.setKind(kind);
        record.setCreatedBy(creator);
        record.setExtraJson(writeJson(extra == null ? Map.of() : extra));
        record.setFilesJson("[]");
        record.setTitle(buildTitle(kind, extra));
        record.setCode(generateCode(kind));
        TechRecord saved = recordRepository.saveAndFlush(record);
        workflowService.start(kind.module(), saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public TechRecordView toView(TechRecord item) {
        String creatorName = item.getCreatedBy() == null ? "" : item.getCreatedBy().getDisplayName();
        var runtime = workflowService.runtime(item.getKind().module().name(), item.getId());
        return new TechRecordView(
                item.getId(),
                item.getCode(),
                item.getKind().name(),
                item.getKind().label(),
                item.getTitle(),
                readExtra(item.getExtraJson()),
                false,
                null,
                creatorName,
                runtime.status(),
                runtime.statusLabel(),
                readFiles(item.getFilesJson()),
                item.getCreatedAt() == null ? null : item.getCreatedAt().toString(),
                item.getUpdatedAt() == null ? null : item.getUpdatedAt().toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readExtra(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private List<Map<String, Object>> readFiles(String json) {
        List<Map<String, Object>> list = readFileList(json);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> file : list) {
            Map<String, Object> entry = new LinkedHashMap<>(file);
            if (!entry.containsKey("originalName") && entry.containsKey("name")) {
                entry.put("originalName", entry.get("name"));
            }
            result.add(entry);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public TechRecordView detail(TechKind kind, Long id) {
        permissionService.require(kind.module(), "VIEW");
        return toView(requireRecord(kind, id));
    }

    @Transactional
    public TechRecordView update(TechKind kind, Long id, Map<String, Object> extra) {
        permissionService.require(kind.module(), "EDIT");
        TechRecord record = requireRecord(kind, id);
        Map<String, Object> merged = new LinkedHashMap<>(readExtra(record.getExtraJson()));
        if (extra != null) {
            merged.putAll(extra);
        }
        record.setExtraJson(writeJson(merged));
        record.setTitle(buildTitle(kind, merged));
        return toView(record);
    }

    @Transactional
    public void delete(TechKind kind, Long id) {
        permissionService.require(kind.module(), "DELETE");
        TechRecord record = requireRecord(kind, id);
        recordRepository.delete(record);
    }

    @Transactional(readOnly = true)
    public TechCatalog.Kind schema(TechKind kind) {
        permissionService.require(kind.module(), "VIEW");
        return TechCatalog.of(kind);
    }

    @Transactional
    public void addFiles(TechKind kind, Long bizId, List<MultipartFile> files, String fieldKey) {
        TechRecord record = requireRecord(kind, bizId);
        attachFiles(record, files, fieldKey);
    }

    @Transactional
    public void removeFile(TechKind kind, Long bizId, Long fileId) {
        TechRecord record = requireRecord(kind, bizId);
        List<Map<String, Object>> fileList = readFileList(record.getFilesJson());
        fileList.removeIf(f -> fileId.equals(f.get("id")));
        record.setFilesJson(writeJson(fileList));
    }

    @Transactional(readOnly = true)
    public TechLookupsView lookups() {
        List<TechLookupsView.FieldOption> fields = new ArrayList<>();
        List<String> groups = new ArrayList<>();
        for (TechCatalog.Kind kind : TechCatalog.kinds()) {
            for (TechCatalog.Field f : kind.fields()) {
                fields.add(new TechLookupsView.FieldOption(f.key(), f.name(), f.type(), f.options()));
            }
            if (StringUtils.hasText(kind.groupLabel()) && !groups.contains(kind.groupLabel())) {
                groups.add(kind.groupLabel());
            }
        }
        return new TechLookupsView(fields, groups);
    }

    private TechRecord requireRecord(TechKind kind, Long id) {
        TechRecord record = recordRepository.findById(id).orElseThrow(() -> ApiException.notFound("记录不存在"));
        if (record.getKind() != kind) {
            throw ApiException.notFound("记录不存在");
        }
        return record;
    }

    private void attachFiles(TechRecord record, List<MultipartFile> files, String fieldKey) {
        if (files == null || files.isEmpty()) {
            return;
        }
        List<Map<String, Object>> fileList = readFileList(record.getFilesJson());
        for (MultipartFile file : files) {
            FileStorageService.StoredFile stored = fileStorageService.saveTechFile(file);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", System.nanoTime());
            entry.put("name", stored.originalName());
            entry.put("url", stored.url());
            entry.put("size", stored.size());
            entry.put("contentType", stored.contentType());
            if (StringUtils.hasText(fieldKey)) {
                entry.put("fieldKey", fieldKey);
            }
            fileList.add(entry);
        }
        record.setFilesJson(writeJson(fileList));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readFileList(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String generateCode(TechKind kind) {
        String datePart = LocalDate.now().format(CODE_DATE);
        long count = recordRepository.count() + 1;
        return kind.prefix() + datePart + String.format("%03d", count);
    }

    private String buildTitle(TechKind kind, Map<String, Object> extra) {
        if (extra == null) {
            return kind.label();
        }
        for (TechCatalog.Field f : TechCatalog.of(kind).fields()) {
            Object value = extra.get(f.key());
            if (value != null && !String.valueOf(value).isBlank()) {
                return kind.label() + "-" + value;
            }
        }
        return kind.label();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("序列化失败", e);
        }
    }
}
