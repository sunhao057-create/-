package com.projectmgmt.tech;

import com.projectmgmt.role.FormModule;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum TechKind {
    CUSTOMER_INSTRUMENT(FormModule.TECH_CUSTOMER_INSTRUMENT, "customer-instruments", "客户仪器信息表", "CI"),
    WECHAT_CONTACTS(FormModule.TECH_WECHAT_CONTACTS, "wechat-contacts", "客户微信资料库", "WC"),
    REPAIR_TRACK(FormModule.TECH_REPAIR_TRACK, "repair-track", "客户仪器送修信息追踪卡", "RT"),
    TICKET_POOL(FormModule.TECH_TICKET_POOL, "ticket-pool", "客户报修工单池", "TP"),
    SERVICE_REPORT(FormModule.TECH_SERVICE_REPORT, "service-report", "技术服务报告单", "SR"),
    MULTI_REPORT(FormModule.TECH_MULTI_REPORT, "multi-report", "多次技术服务报告单", "MR"),
    MOVE_CARD(FormModule.TECH_MOVE_CARD, "move-card", "设备搬迁记录卡", "MC"),
    MAINT_BASIC(FormModule.TECH_MAINT_BASIC, "maint-basic", "基础设备维保记录卡", "MB"),
    MAINT_LCMS(FormModule.TECH_MAINT_LCMS, "maint-lcms", "液质联用仪-设备维保记录卡", "ML"),
    MAINT_ICPMS(FormModule.TECH_MAINT_ICPMS, "maint-icpms", "ICP-MS-设备维保记录卡", "MI"),
    MAINT_ICP(FormModule.TECH_MAINT_ICP, "maint-icp", "ICP-设备维保记录卡", "MC2"),
    MAINT_AAS(FormModule.TECH_MAINT_AAS, "maint-aas", "原子吸收光谱仪-设备维保记录卡", "MA"),
    MAINT_LC(FormModule.TECH_MAINT_LC, "maint-lc", "液相色谱仪-设备维保记录卡", "ML2"),
    MAINT_GC(FormModule.TECH_MAINT_GC, "maint-gc", "气相色谱仪-设备维保记录卡", "MG"),
    MAINT_GCMS(FormModule.TECH_MAINT_GCMS, "maint-gcms", "气相色谱质谱仪-设备维保记录卡", "MG2"),
    MAINT_AFS(FormModule.TECH_MAINT_AFS, "maint-afs", "原子荧光光度计-设备维保记录卡", "MF"),
    MAINT_UV(FormModule.TECH_MAINT_UV, "maint-uv", "紫外可见分光光度计-设备维保记录卡", "MU"),
    MAINT_LEGACY(FormModule.TECH_MAINT_LEGACY, "maint-legacy", "设备维保记录卡(隐藏旧版）", "MLO"),
    SPECIAL_CONFIRM(FormModule.TECH_SPECIAL_CONFIRM, "special-confirm", "特殊仪器确认表", "SC"),
    REPAIR_CARD(FormModule.TECH_REPAIR_CARD, "repair-card", "设备维修记录卡", "RC"),
    INSTRUMENT_LIB(FormModule.TECH_INSTRUMENT_LIB, "instrument-lib", "仪器信息资料库", "IL"),
    WEIGHT_CALIB(FormModule.TECH_WEIGHT_CALIB, "weight-calib", "承重类维护后校准记录表", "WC2"),
    MAINT_RECORD(FormModule.DEVICE_MAINT, "maint-record", "维保服务记录", "WB");

    private final FormModule module;
    private final String slug;
    private final String label;
    private final String prefix;

    TechKind(FormModule module, String slug, String label, String prefix) {
        this.module = module;
        this.slug = slug;
        this.label = label;
        this.prefix = prefix;
    }

    public FormModule module() {
        return module;
    }

    public String slug() {
        return slug;
    }

    public String label() {
        return label;
    }

    public String prefix() {
        return prefix;
    }

    private static final Map<String, TechKind> BY_MODULE = Arrays.stream(values())
            .collect(Collectors.toMap(k -> k.module.name(), Function.identity()));
    private static final Map<String, TechKind> BY_SLUG = Arrays.stream(values())
            .collect(Collectors.toMap(TechKind::slug, Function.identity()));

    public static TechKind fromModule(String module) {
        return BY_MODULE.get(module);
    }

    public static TechKind fromModule(FormModule module) {
        return module == null ? null : BY_MODULE.get(module.name());
    }

    public String publicPath() {
        return "/public/tech/" + slug;
    }

    public static TechKind fromSlug(String slug) {
        TechKind kind = BY_SLUG.get(slug);
        if (kind == null) {
            throw new IllegalArgumentException("未知技术模块slug: " + slug);
        }
        return kind;
    }

    public static TechKind of(FormModule module) {
        return BY_MODULE.get(module.name());
    }

    public static TechKind of(String moduleName) {
        return BY_MODULE.get(moduleName);
    }
}
