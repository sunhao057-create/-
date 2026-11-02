package com.projectmgmt.role;

import java.util.Arrays;
import java.util.List;

public enum FormModule {
    PROJECT("项目立项"),
    COLLECTION("项目回款"),
    DEPOSIT_APPLY("项目保证金申请"),
    DEPOSIT_RETURN("项目保证金退回"),
    BIDDING("招标信息"),
    PAYMENT("付款申请"),
    CUSTOMER("客户管理"),
    CONTRACT("合同申请"),
    SUPPLIER("供应商管理"),
    USER("账号管理"),
    ROLE("角色管理"),
    ORG("组织架构"),
    WORKFLOW("流程引擎"),
    FORM_FLOW("表单流程"),
    BRANDING("系统名称/图标"),
    THEME("主题风格"),
    PROJECT_ANALYTICS("项目统计分析"),
    BANK_ACCOUNT("银行账户"),
    EXPENSE("报销申请"),
    BANK_ANALYTICS("银行视图"),
    PURCHASE("采购申请"),
    PURCHASE_PAY("采购付款"),
    PURCHASE_ANALYTICS("采购统计"),
    WAREHOUSE("仓库信息"),
    STOCK_OUT_PURCHASE("采购出库"),
    STOCK_OUT_PROJECT("项目出库"),
    STOCK_TRANSFER("仓库调拨"),
    WAREHOUSE_ANALYTICS("仓库统计"),
    DEVICE("设备信息"),
    DEVICE_REPAIR("设备维修工单"),
    DEVICE_MOVE("设备搬迁工单"),
    DEVICE_SCRAP("设备报废工单"),
    DEVICE_MAINT("维保服务记录"),
    MINI_BANNER("小程序轮播图"),
    MINI_KNOWLEDGE("知识库"),
    MINI_COMPANY("公司介绍"),
    MINI_SERVICE("服务内容"),
    CUSTOMER_APPLY("新建客户"),
    CUSTOMER_ACCOUNT("新建客户账号"),
    OA_INTERVIEW("面试申请"),
    OA_ONBOARD("入职申请"),
    OA_TRANSFER("转岗申请"),
    OA_PROMOTION("晋升申请"),
    OA_RESIGN("离职申请"),
    OA_SALARY("薪资调整"),
    OA_SEAL("盖章申请"),
    OA_QUALIFY("资质借出"),
    OA_TRIP("出差申请"),
    OA_LEAVE("请假申请"),
    OA_LOAN("项目借款"),
    OA_LOAN_PAY("借款付款单"),
    OA_LOAN_REPAY("借款还款单"),
    OA_STAFF("人员门户"),
    TECH_CUSTOMER_INSTRUMENT("客户仪器信息表"),
    TECH_WECHAT_CONTACTS("客户微信资料库"),
    TECH_REPAIR_TRACK("客户仪器送修信息追踪卡"),
    TECH_TICKET_POOL("客户报修工单池"),
    TECH_SERVICE_REPORT("技术服务报告单"),
    TECH_MULTI_REPORT("多次技术服务报告单"),
    TECH_MOVE_CARD("设备搬迁记录卡"),
    TECH_MAINT_BASIC("基础设备维保记录卡"),
    TECH_MAINT_LCMS("液质联用仪-设备维保记录卡"),
    TECH_MAINT_ICPMS("ICP-MS-设备维保记录卡"),
    TECH_MAINT_ICP("ICP-设备维保记录卡"),
    TECH_MAINT_AAS("原子吸收光谱仪-设备维保记录卡"),
    TECH_MAINT_LC("液相色谱仪-设备维保记录卡"),
    TECH_MAINT_GC("气相色谱仪-设备维保记录卡"),
    TECH_MAINT_GCMS("气相色谱质谱仪-设备维保记录卡"),
    TECH_MAINT_AFS("原子荧光光度计-设备维保记录卡"),
    TECH_MAINT_UV("紫外可见分光光度计-设备维保记录卡"),
    TECH_MAINT_LEGACY("设备维保记录卡(隐藏旧版）"),
    TECH_SPECIAL_CONFIRM("特殊仪器确认表"),
    TECH_REPAIR_CARD("设备维修记录卡"),
    TECH_INSTRUMENT_LIB("仪器信息资料库"),
    TECH_WEIGHT_CALIB("承重类维护后校准记录表"),
    TECH_PM_PROJECT("蜜蜂e工-搬迁/维保项目管理"),
    TECH_PM_STATUS("蜜蜂e工-搬迁/维保项目状态变更"),
    TECH_PM_CHECKIN("搬迁/维保签到表"),
    TECH_PM_MAINT_DAILY("维保项目日报"),
    TECH_PM_MOVE_DAILY("搬迁项目日报"),
    TECH_PM_MAKEUP("搬迁补签表（隔日补签）");

    private final String label;

    FormModule(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean hasDataScope() {
        return this == PROJECT || this == COLLECTION || this == DEPOSIT_APPLY || this == DEPOSIT_RETURN
                || this == BIDDING
                || this == PAYMENT || this == CUSTOMER || this == CONTRACT || this == SUPPLIER
                || this == BANK_ACCOUNT || this == EXPENSE
                || this == PURCHASE || this == PURCHASE_PAY
                || this == WAREHOUSE || this == STOCK_OUT_PURCHASE || this == STOCK_OUT_PROJECT || this == STOCK_TRANSFER
                || this == DEVICE || this == DEVICE_REPAIR || this == DEVICE_MOVE || this == DEVICE_SCRAP
                || this == DEVICE_MAINT
                || this == CUSTOMER_APPLY
                || this == CUSTOMER_ACCOUNT
                || isOa()
                || isTech()
                || isAnalytics();
    }

    public boolean isOa() {
        return this == OA_ONBOARD || this == OA_TRANSFER || this == OA_PROMOTION || this == OA_RESIGN
                || this == OA_SALARY || this == OA_INTERVIEW || this == OA_SEAL || this == OA_QUALIFY
                || this == OA_TRIP || this == OA_LEAVE || this == OA_LOAN || this == OA_LOAN_PAY || this == OA_LOAN_REPAY
                || this == OA_STAFF;
    }

    public boolean isTech() {
        return name().startsWith("TECH_");
    }

    public boolean isMiniContent() {
        return this == MINI_BANNER || this == MINI_KNOWLEDGE || this == MINI_COMPANY || this == MINI_SERVICE;
    }

    public boolean isAnalytics() {
        return this == PROJECT_ANALYTICS || this == BANK_ANALYTICS
                || this == PURCHASE_ANALYTICS || this == WAREHOUSE_ANALYTICS;
    }

    /** 旧角色补权限时：有对应业务查看权则默认打开统计视图。 */
    public FormModule analyticsSource() {
        if (this == PROJECT_ANALYTICS) {
            return PROJECT;
        }
        if (this == BANK_ANALYTICS) {
            return BANK_ACCOUNT;
        }
        if (this == PURCHASE_ANALYTICS) {
            return PURCHASE;
        }
        if (this == WAREHOUSE_ANALYTICS) {
            return WAREHOUSE;
        }
        return null;
    }

    /** 流程引擎、系统名称、统计视图不单独提供「新增」。 */
    public boolean hasCreate() {
        return !isAnalytics() && this != WORKFLOW && this != BRANDING && this != THEME
                && this != OA_STAFF;
    }

    public boolean hasEdit() {
        return !isAnalytics();
    }

    public boolean hasDelete() {
        return !isAnalytics() && this != OA_STAFF;
    }

    public boolean retired() {
        return this == PROJECT
                || this == COLLECTION
                || this == DEPOSIT_APPLY
                || this == DEPOSIT_RETURN
                || this == BIDDING
                || this == PAYMENT
                || this == CONTRACT
                || this == SUPPLIER
                || this == PROJECT_ANALYTICS
                || this == BANK_ACCOUNT
                || this == EXPENSE
                || this == BANK_ANALYTICS
                || this == PURCHASE
                || this == PURCHASE_PAY
                || this == PURCHASE_ANALYTICS
                || this == WAREHOUSE
                || this == STOCK_OUT_PURCHASE
                || this == STOCK_OUT_PROJECT
                || this == STOCK_TRANSFER
                || this == WAREHOUSE_ANALYTICS
                || isOa()
                || this == TECH_PM_PROJECT
                || this == TECH_PM_STATUS
                || this == TECH_PM_CHECKIN
                || this == TECH_PM_MAINT_DAILY
                || this == TECH_PM_MOVE_DAILY
                || this == TECH_PM_MAKEUP;
    }

    public boolean canBePublic() {
        if (this == CUSTOMER_APPLY || this == CUSTOMER_ACCOUNT) {
            return false;
        }
        return !retired() && hasDataScope() && hasCreate();
    }

    /** 可在表单内设置审批流程的模块。 */
    public boolean hasFlow() {
        return this != WORKFLOW && this != BRANDING && this != THEME
                && this != USER && this != ROLE && this != ORG
                && this != OA_STAFF && !isAnalytics() && !retired();
    }

    /** 可在表单内创建数据查看视图的模块。 */
    public boolean hasManageView() {
        return hasFlow();
    }

    public boolean hasImport() {
        if (this == OA_STAFF) {
            return true;
        }
        return hasCreate() && this != ORG && this != ROLE && this != CONTRACT
                && this != BANK_ACCOUNT && this != EXPENSE
                && this != COLLECTION && this != DEPOSIT_APPLY && this != DEPOSIT_RETURN && this != BIDDING
                && this != PURCHASE && this != PURCHASE_PAY
                && this != WAREHOUSE && this != STOCK_OUT_PURCHASE && this != STOCK_OUT_PROJECT
                && this != STOCK_TRANSFER
                && this != DEVICE && this != DEVICE_REPAIR && this != DEVICE_MOVE && this != DEVICE_SCRAP
                && this != DEVICE_MAINT
                && this != CUSTOMER_APPLY
                && this != CUSTOMER_ACCOUNT
                && !isMiniContent()
                && !isOa()
                && !isTech();
    }

    public static List<FormModule> all() {
        return Arrays.stream(values()).filter(module -> !module.retired()).toList();
    }

    public static FormModule fromNameOrNull(String name) {
        if (name == null) {
            return null;
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
