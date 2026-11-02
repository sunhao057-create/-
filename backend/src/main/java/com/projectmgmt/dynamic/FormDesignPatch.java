package com.projectmgmt.dynamic;

import tools.jackson.databind.json.JsonMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 在轻流种子 design 上改展示：隐藏字段、改标题、改关联、追加新字段。不删 key。 */
public final class FormDesignPatch {

    private FormDesignPatch() {
    }

    @SuppressWarnings("unchecked")
    public static String apply(String designJson, Map<String, Object> override, JsonMapper mapper) {
        if (designJson == null || designJson.isBlank()) {
            return designJson;
        }
        if (override == null) {
            override = Map.of();
        }
        Map<String, Object> design = mapper.readValue(designJson, Map.class);
        Object rawFields = design.get("fields");
        if (!(rawFields instanceof List<?> list)) {
            return designJson;
        }
        List<Map<String, Object>> fields = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> raw) {
                fields.add((Map<String, Object>) raw);
            }
        }

        Set<String> showKeys = new LinkedHashSet<>();
        Object show = override.get("showKeys");
        if (show instanceof List<?> showList) {
            for (Object key : showList) {
                if (key != null) {
                    showKeys.add(String.valueOf(key));
                }
            }
        }
        boolean hideOthers = Boolean.TRUE.equals(override.get("hideOthers"));
        Map<String, Object> relabel = mapOf(override.get("relabel"));
        List<Map<String, Object>> relations = listOfMaps(override.get("relations"));
        List<Map<String, Object>> addFields = listOfMaps(override.get("addFields"));
        Map<String, Object> fieldPatches = mapOf(override.get("fieldPatches"));

        if (override.get("titleField") != null) {
            design.put("titleField", override.get("titleField"));
        }

        Set<String> existing = new LinkedHashSet<>();
        for (Map<String, Object> field : fields) {
            String key = String.valueOf(field.get("key"));
            existing.add(key);
            if (!relabel.isEmpty() && relabel.containsKey(key)) {
                field.put("title", relabel.get(key));
            }
            if (hideOthers && !showKeys.isEmpty() && !"section".equals(String.valueOf(field.get("type"))) && !showKeys.contains(key)) {
                field.put("hide", true);
            }
            applyFieldPatch(field, mapOf(fieldPatches.get(key)));
            applyRelation(field, relations);
            Object columns = field.get("columns");
            if (columns instanceof List<?> cols) {
                for (Object col : cols) {
                    if (!(col instanceof Map<?, ?> colRaw)) {
                        continue;
                    }
                    Map<String, Object> column = (Map<String, Object>) colRaw;
                    String colKey = String.valueOf(column.get("key"));
                    if (!relabel.isEmpty() && relabel.containsKey(colKey)) {
                        column.put("title", relabel.get(colKey));
                    }
                    applyRelation(column, relations);
                    normalizeLink(column);
                }
            }
            promoteProductName(field);
            normalizeLink(field);
            promoteReportFile(field);
        }
        linkInstrumentFields(fields);
        linkPrevMaintenance(fields);
        for (Map<String, Object> extra : addFields) {
            Object key = extra.get("key");
            if (key == null || existing.contains(String.valueOf(key))) {
                continue;
            }
            fields.add(extra);
            existing.add(String.valueOf(key));
        }
        design.put("fields", fields);
        return mapper.writeValueAsString(design);
    }

    private static void applyFieldPatch(Map<String, Object> field, Map<String, Object> patch) {
        if (patch.isEmpty()) {
            return;
        }
        copyIfPresent(patch, field, "title");
        copyIfPresent(patch, field, "hide");
        copyIfPresent(patch, field, "mergeWith");
        copyIfPresent(patch, field, "mergeAlso");
        copyIfPresent(patch, field, "mergeLabel");
        copyIfPresent(patch, field, "mergeOtherLabel");
    }

    private static void copyIfPresent(Map<String, Object> from, Map<String, Object> to, String key) {
        if (from.containsKey(key) && from.get(key) != null) {
            to.put(key, from.get(key));
        }
    }

    private static void applyRelation(Map<String, Object> field, List<Map<String, Object>> relations) {
        String key = String.valueOf(field.get("key"));
        for (Map<String, Object> rel : relations) {
            if (!key.equals(String.valueOf(rel.get("key")))) {
                continue;
            }
            if (rel.get("type") != null) {
                field.put("type", rel.get("type"));
            } else {
                field.put("type", "relation");
            }
            if (rel.get("refTarget") != null) {
                field.put("refTarget", rel.get("refTarget"));
            }
            if (rel.get("displayKey") != null) {
                field.put("displayKey", rel.get("displayKey"));
            }
            if (rel.get("displayTitle") != null) {
                field.put("displayTitle", rel.get("displayTitle"));
            }
        }
        fillDefaultDisplay(field);
    }

    /** 轻流目标未迁入时改接到本系统表；辅助流水/历史数量改文本，避免空下拉。 */
    private static void normalizeLink(Map<String, Object> field) {
        String target = text(field.get("refTarget"));
        String title = text(field.get("title"));
        if (target.isEmpty()) {
            if ("relation".equals(text(field.get("type"))) || "ref".equals(text(field.get("type")))) {
                demoteToText(field);
            }
            return;
        }
        if ("4a0fdb7b".equals(target)) {
            field.put("type", "relation");
            field.put("refTarget", "beb22af6");
            if (title.contains("编号")) {
                field.put("displayKey", "q129065087");
                field.put("displayTitle", "设备编号");
            } else if (title.contains("型号")) {
                field.put("displayKey", "q129065089");
                field.put("displayTitle", "仪器型号");
            } else if (title.contains("厂家")) {
                field.put("displayKey", "q129065095");
                field.put("displayTitle", "生产厂家");
            } else {
                field.put("displayKey", "q129065088");
                field.put("displayTitle", "仪器名称");
            }
            return;
        }
        if ("fd5a2f91".equals(target)) {
            if (title.contains("地址")) {
                demoteToText(field);
                return;
            }
            field.put("type", "relation");
            field.put("refTarget", "8rlbff055g01");
            if (title.contains("缩写")) {
                field.put("displayKey", "q408030150");
                field.put("displayTitle", "客户名称缩写");
            } else {
                field.put("displayKey", "q292708496");
                field.put("displayTitle", "客户名称");
            }
            return;
        }
        if ("86514b90".equals(target)) {
            field.put("type", "relation");
            field.put("refTarget", "a4dc7e7c");
            if (title.contains("电话")) {
                field.put("displayKey", "q129037662");
                field.put("displayTitle", "电话");
            } else {
                field.put("displayKey", "q129037661");
                field.put("displayTitle", "姓名");
            }
            return;
        }
        if ("932oo9f7tc01".equals(target)) {
            if (isBankDetailTitle(title)) {
                demoteToText(field);
            } else {
                field.put("type", "relation");
                field.put("refTarget", "8ts5ue7tks01");
                field.put("displayKey", "q291836138");
                field.put("displayTitle", "公司名称");
            }
            return;
        }
        if (DEAD_TARGETS.contains(target)) {
            demoteToText(field);
            return;
        }
        fillDefaultDisplay(field);
    }

    /**
     * 轻流维保/搬迁：选客户仪器一条设备后带出编号、名称、型号、厂家。
     * 报修里的设备名称/型号/厂家同样接到客户仪器。
     */
    private static void linkInstrumentFields(List<Map<String, Object>> fields) {
        for (Map<String, Object> field : fields) {
            String[] mapped = instrumentDisplay(text(field.get("title")));
            if (mapped == null) {
                continue;
            }
            String target = text(field.get("refTarget"));
            if (!target.isEmpty() && !"beb22af6".equals(target) && !"4a0fdb7b".equals(target)) {
                continue;
            }
            field.put("refTarget", "beb22af6");
            field.put("displayKey", mapped[0]);
            field.put("displayTitle", mapped[1]);
            if ("1".equals(mapped[2]) || "relation".equals(text(field.get("type")))) {
                field.put("type", "relation");
            }
        }
    }

    /** 选上次维保单时带出上次维保结束时间。 */
    private static void linkPrevMaintenance(List<Map<String, Object>> fields) {
        Map<String, Object> prev = null;
        Map<String, Object> ended = null;
        Map<String, Object> lastTime = null;
        for (Map<String, Object> field : fields) {
            String title = text(field.get("title"));
            if (title.contains("上次维保次数") || title.contains("上次维保单")) {
                prev = field;
            } else if (title.contains("本次维保结束时间")) {
                ended = field;
            } else if (title.contains("上次维保时间")) {
                lastTime = field;
            }
        }
        if (prev == null || ended == null || lastTime == null) {
            return;
        }
        String target = text(prev.get("refTarget"));
        if (target.isEmpty()) {
            return;
        }
        lastTime.put("refTarget", target);
        lastTime.put("displayKey", ended.get("key"));
        lastTime.put("displayTitle", ended.get("title"));
    }

    /** 性能报告等按附件上传，不只当照片。 */
    private static void promoteReportFile(Map<String, Object> field) {
        if (!"image".equals(text(field.get("type")))) {
            return;
        }
        String title = text(field.get("title"));
        if (title.contains("报告") || title.contains("附件")) {
            field.put("type", "file");
        }
    }

    /** [displayKey, displayTitle, picker=1] */
    private static String[] instrumentDisplay(String title) {
        return switch (title) {
            case "设备编号", "选择仪器" -> new String[] {"q129065087", "设备编号", "1"};
            case "仪器名称" -> new String[] {"q129065088", "仪器名称", "1"};
            case "设备名称" -> new String[] {"q129065088", "仪器名称", "0"};
            case "仪器型号", "设备型号" -> new String[] {"q129065089", "仪器型号", "0"};
            case "生产厂家", "设备厂家" -> new String[] {"q129065095", "生产厂家", "0"};
            case "资产唯一标识" -> new String[] {"q467587846", "资产唯一标识", "0"};
            case "归属科室", "维保科室", "科室" -> new String[] {"q129065113", "归属科室", "0"};
            default -> null;
        };
    }

    private static void promoteProductName(Map<String, Object> field) {
        Object columns = field.get("columns");
        if (!(columns instanceof List<?> cols)) {
            return;
        }
        boolean hasProduct = false;
        for (Object item : cols) {
            if (item instanceof Map<?, ?> raw && "8rl894fd7o01".equals(String.valueOf(raw.get("refTarget")))) {
                hasProduct = true;
                break;
            }
        }
        if (!hasProduct) {
            return;
        }
        for (Object item : cols) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> column = (Map<String, Object>) raw;
            String title = text(column.get("title"));
            if ("产品名称".equals(title) || "名称".equals(title)) {
                column.put("type", "relation");
                column.put("refTarget", "8rl894fd7o01");
                column.put("displayKey", "q290314016");
                column.put("displayTitle", "名称");
            }
        }
    }

    private static void fillDefaultDisplay(Map<String, Object> field) {
        String target = text(field.get("refTarget"));
        if (target.isEmpty() || field.get("displayKey") != null) {
            return;
        }
        String[] mapped = TARGET_DISPLAY.get(target);
        if (mapped == null) {
            return;
        }
        field.put("displayKey", mapped[0]);
        if (field.get("displayTitle") == null) {
            field.put("displayTitle", mapped[1]);
        }
    }

    private static void demoteToText(Map<String, Object> field) {
        String type = text(field.get("type"));
        if ("relation".equals(type) || "ref".equals(type)) {
            field.put("type", "text");
        }
        field.remove("refTarget");
        field.remove("displayKey");
        field.remove("refFill");
    }

    private static boolean isBankDetailTitle(String title) {
        return title.contains("开户") || title.contains("银行名称") || "账号".equals(title) || title.endsWith("账号");
    }

    private static String text(Object raw) {
        return raw == null ? "" : String.valueOf(raw);
    }

    private static final Set<String> DEAD_TARGETS = Set.of(
            "8u9eh78tlg01",
            "8usc6bvtvc01",
            "8tukus31hk02",
            "8tv5ucftm402",
            "8tum65splg01",
            "dn36lhdcdc02");

    private static final Map<String, String[]> TARGET_DISPLAY = Map.ofEntries(
            Map.entry("8rlbff055g01", new String[] {"q290317298", "项目名称"}),
            Map.entry("8ts5ue7tks01", new String[] {"q291836138", "公司名称"}),
            Map.entry("8rl894fd7o01", new String[] {"q290314016", "名称"}),
            Map.entry("8rlavttl7401", new String[] {"q290316094", "供应商名称"}),
            Map.entry("8sjlaqupd801", new String[] {"q291830171", "仓库名称"}),
            Map.entry("8rlbmv813s02", new String[] {"q292399356", "销售订单主题"}),
            Map.entry("8tv6a7p1m402", new String[] {"q292397733", "采购订单主题"}),
            Map.entry("8ukiskb5ug02", new String[] {"q292483423", "发票号"}),
            Map.entry("8ut8e3p5ug02", new String[] {"q303385536", "订单主题"}),
            Map.entry("8ur98kvdrc02", new String[] {"q292599004", "报价主题"}),
            Map.entry("689f58d1", new String[] {"q293665869", "工单主题"}),
            Map.entry("8uqpm42ht001", new String[] {"q292575453", "仪器名称"}),
            Map.entry("8tul8i1llg01", new String[] {"q417698946", "物流单号"}),
            Map.entry("8tvh2vaplg02", new String[] {"q430275250", "物流单号"}),
            Map.entry("a4dc7e7c", new String[] {"q129037661", "姓名"}),
            Map.entry("beb22af6", new String[] {"q129065084", "客户名称"}),
            Map.entry("dndcpmpodc01", new String[] {"q435053849", "流水金额"}));

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapOf(Object raw) {
        return raw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object raw) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                out.add((Map<String, Object>) map);
            }
        }
        return out;
    }
}
