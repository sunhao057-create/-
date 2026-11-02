package com.projectmgmt.tech;

import com.projectmgmt.role.FormModule;
import com.projectmgmt.workflow.FormFieldCatalog;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public final class TechCatalog {

    private TechCatalog() {
    }

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final Map<TechKind, Kind> CACHE = new ConcurrentHashMap<>();
    private static List<Kind> allKinds;

    public record Field(
            String key,
            String name,
            String type,
            boolean required,
            boolean applicant,
            String section,
            int span,
            List<Field> subfields,
            List<String> options) {

        public Field {
            if (subfields == null) {
                subfields = List.of();
            }
            if (options == null) {
                options = List.of();
            }
        }
    }

    public record Kind(
            TechKind kind,
            String appKey,
            String slug,
            String label,
            String prefix,
            String group,
            String groupLabel,
            List<Field> fields,
            List<Column> listColumns) {
        public String module() {
            return kind.module().name();
        }
    }

    public record Column(String key, String name, String source) {
    }

    public static Kind of(TechKind kind) {
        return CACHE.computeIfAbsent(kind, TechCatalog::load);
    }

    public static Kind of(FormModule module) {
        TechKind kind = TechKind.of(module);
        if (kind == null) {
            return null;
        }
        return of(kind);
    }

    /** 已迁到 TECH_* 原生模块的旧轻流 appKey，流程引擎与侧栏不再使用。 */
    public static boolean isLegacyAppKey(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        for (Kind kind : kinds()) {
            if (code.equals(kind.appKey())) {
                return true;
            }
        }
        return false;
    }

    public static List<Kind> kinds() {
        if (allKinds == null) {
            synchronized (TechCatalog.class) {
                if (allKinds == null) {
                    List<Kind> list = new ArrayList<>();
                    for (TechKind kind : TechKind.values()) {
                        list.add(of(kind));
                    }
                    allKinds = List.copyOf(list);
                }
            }
        }
        return allKinds;
    }

    public static List<FormFieldCatalog.Field> workflowFields(FormModule module) {
        Kind kind = of(module);
        if (kind == null) {
            return List.of();
        }
        List<FormFieldCatalog.Field> fields = new ArrayList<>();
        for (Field f : kind.fields()) {
            boolean member = isMemberType(f.type());
            fields.add(new FormFieldCatalog.Field(f.key(), f.name(), member));
            for (Field sub : f.subfields()) {
                fields.add(new FormFieldCatalog.Field(sub.key(), sub.name(), isMemberType(sub.type())));
            }
        }
        return fields;
    }

    private static boolean isMemberType(String type) {
        return "member".equals(type) || "user".equals(type) || "selector".equals(type)
                || "relation".equals(type) || "ref".equals(type);
    }

    private static Kind load(TechKind kind) {
        try (InputStream in = new ClassPathResource("tech/catalog.json").getInputStream()) {
            JsonNode root = MAPPER.readTree(in);
            for (JsonNode node : root) {
                if (kind.name().equals(node.path("enumName").asText())) {
                    return parseKind(kind, node);
                }
            }
            return emptyKind(kind);
        } catch (IOException e) {
            throw new IllegalStateException("加载 tech catalog.json 失败: " + e.getMessage(), e);
        }
    }

    private static Kind parseKind(TechKind kind, JsonNode node) {
        List<Field> fields = new ArrayList<>();
        JsonNode fieldsNode = node.path("fields");
        if (fieldsNode.isArray()) {
            for (JsonNode f : fieldsNode) {
                fields.add(parseField(f));
            }
        }
        List<Column> columns = new ArrayList<>();
        JsonNode colsNode = node.path("listColumns");
        if (colsNode.isArray()) {
            for (JsonNode c : colsNode) {
                columns.add(new Column(
                        c.path("key").asText(),
                        c.path("name").asText(),
                        c.path("source").asText("extra")));
            }
        }
        return new Kind(
                kind,
                node.path("appKey").asText(),
                node.path("slug").asText(),
                node.path("label").asText(),
                node.path("prefix").asText(),
                node.path("group").asText(),
                node.path("groupLabel").asText(),
                List.copyOf(fields),
                List.copyOf(columns));
    }

    private static Field parseField(JsonNode f) {
        List<Field> subs = new ArrayList<>();
        JsonNode subsNode = f.path("subfields");
        if (subsNode.isArray()) {
            for (JsonNode s : subsNode) {
                subs.add(parseField(s));
            }
        }
        List<String> options = new ArrayList<>();
        JsonNode optsNode = f.path("options");
        if (optsNode.isArray()) {
            for (JsonNode o : optsNode) {
                options.add(o.asText());
            }
        }
        return new Field(
                f.path("key").asText(),
                f.path("name").asText(),
                f.path("type").asText("text"),
                f.path("required").asBoolean(false),
                f.path("applicant").asBoolean(false),
                f.path("section").asText(""),
                f.path("span").asInt(12),
                List.copyOf(subs),
                List.copyOf(options));
    }

    private static Kind emptyKind(TechKind kind) {
        return new Kind(kind, "", kind.slug(), kind.label(), kind.prefix(), "", "", List.of(), List.of());
    }
}
