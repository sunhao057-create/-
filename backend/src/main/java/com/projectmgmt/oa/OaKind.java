package com.projectmgmt.oa;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.role.FormModule;

public enum OaKind {
    INTERVIEW("interview", "面试申请", FormModule.OA_INTERVIEW, "MS"),
    ONBOARD("onboard", "入职申请", FormModule.OA_ONBOARD, "RZ"),
    TRANSFER("transfer", "转岗申请", FormModule.OA_TRANSFER, "ZG"),
    PROMOTION("promotion", "晋升申请", FormModule.OA_PROMOTION, "JS"),
    RESIGN("resign", "离职申请", FormModule.OA_RESIGN, "LZ"),
    SALARY("salary", "薪资调整", FormModule.OA_SALARY, "XZ"),
    SEAL("seal", "盖章申请", FormModule.OA_SEAL, "GZ"),
    QUALIFY("qualify", "资质借出", FormModule.OA_QUALIFY, "ZZ"),
    TRIP("trip", "出差申请", FormModule.OA_TRIP, "CC"),
    LEAVE("leave", "请假申请", FormModule.OA_LEAVE, "QJ"),
    LOAN("loan", "项目借款", FormModule.OA_LOAN, "JK"),
    LOAN_PAY("loan-pay", "借款付款单", FormModule.OA_LOAN_PAY, "JKF"),
    LOAN_REPAY("loan-repay", "借款还款单", FormModule.OA_LOAN_REPAY, "JKH");

    private final String slug;
    private final String label;
    private final FormModule module;
    private final String codePrefix;

    OaKind(String slug, String label, FormModule module, String codePrefix) {
        this.slug = slug;
        this.label = label;
        this.module = module;
        this.codePrefix = codePrefix;
    }

    public String slug() {
        return slug;
    }

    public String label() {
        return label;
    }

    public FormModule module() {
        return module;
    }

    public String codePrefix() {
        return codePrefix;
    }

    public String adminPath() {
        return "/oa/" + slug;
    }

    public String publicPath() {
        return "/public/oa/" + slug;
    }

    public static OaKind fromSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            throw ApiException.badRequest("请选择申请类型");
        }
        for (OaKind kind : values()) {
            if (kind.slug.equalsIgnoreCase(slug.trim())) {
                return kind;
            }
        }
        throw ApiException.notFound("申请类型不存在");
    }

    public static OaKind of(FormModule module) {
        OaKind kind = fromModule(module);
        if (kind == null) {
            throw ApiException.badRequest("不是 OA 申请模块");
        }
        return kind;
    }

    public boolean hasSalary() {
        return this == ONBOARD || this == TRANSFER || this == PROMOTION || this == SALARY;
    }

    public boolean guestApplicant() {
        return this == ONBOARD || this == INTERVIEW;
    }

    public static OaKind fromModule(FormModule module) {
        if (module == null) {
            return null;
        }
        for (OaKind kind : values()) {
            if (kind.module == module) {
                return kind;
            }
        }
        return null;
    }

    public static OaKind fromModule(String moduleName) {
        if (moduleName == null) {
            return null;
        }
        FormModule module = FormModule.fromNameOrNull(moduleName);
        return fromModule(module);
    }
}
