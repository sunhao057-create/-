package com.projectmgmt.workflow;

import com.projectmgmt.dynamic.FormDefinition;
import com.projectmgmt.dynamic.FormDefinitionRepository;
import com.projectmgmt.role.FormModule;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class FormFieldCatalog {

    private static FormDefinitionRepository definitionRepository;

    private static final Set<String> MEMBER_TYPES = Set.of("user", "selector", "relation", "ref", "member");

    @Autowired
    public void setDefinitionRepository(FormDefinitionRepository repo) {
        FormFieldCatalog.definitionRepository = repo;
    }

    private FormFieldCatalog() {
    }

    public static final String ACTION_CANCEL = "_action_cancel";
    public static final String ACTION_DRAFT = "_action_draft";
    public static final String ACTION_SUBMIT = "_action_submit";
    public static final String ACTION_DELETE = "_action_delete";

    public record Field(String key, String label, boolean member, String parentLabel) {
        public Field(String key, String label) {
            this(key, label, false, null);
        }

        public Field(String key, String label, boolean member) {
            this(key, label, member, null);
        }
    }

    public static List<Field> fieldsOf(String moduleCode) {
        FormModule module = FormModule.fromNameOrNull(moduleCode);
        if (module != null) {
            return fieldsOf(module);
        }
        return dynamicFields(moduleCode);
    }

    @SuppressWarnings("unchecked")
    private static List<Field> dynamicFields(String code) {
        if (definitionRepository == null || code == null) {
            return List.of();
        }
        FormDefinition def = definitionRepository.findByCode(code).orElse(null);
        if (def == null || def.getDesignJson() == null) {
            return List.of();
        }
        try {
            Map<String, Object> design = new tools.jackson.databind.json.JsonMapper()
                    .readValue(def.getDesignJson(), Map.class);
            Object fields = design.get("fields");
            if (!(fields instanceof List<?> list)) {
                return List.of();
            }
            List<Field> result = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> field)) {
                    continue;
                }
                Object keyObj = field.get("key");
                String key = keyObj == null ? "" : String.valueOf(keyObj);
                Object titleObj = field.get("title");
                String title = titleObj == null ? key : String.valueOf(titleObj);
                Object typeObj = field.get("type");
                String type = typeObj == null ? "" : String.valueOf(typeObj);
                boolean member = MEMBER_TYPES.contains(type);
                result.add(new Field(key, title, member));
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    public static List<Field> fieldsOf(FormModule module) {
        if (module != null && module.isTech()) {
            return com.projectmgmt.tech.TechCatalog.workflowFields(module);
        }
        return switch (module) {
            case PROJECT -> List.of(
                    new Field("name", "项目名称"),
                    new Field("code", "项目编码"),
                    new Field("customerId", "选择客户"),
                    new Field("contractId", "关联合同"),
                    new Field("companyName", "公司名称"),
                    new Field("companyCode", "公司代码"),
                    new Field("companyLevel", "公司等级"),
                    new Field("managerId", "项目经理", true),
                    new Field("regionId", "所属区域"),
                    new Field("regionLeader", "区域负责人", true),
                    new Field("overview", "项目情况"),
                    new Field("attachments", "附件")
            );
            case PAYMENT -> List.of(
                    new Field("projectId", "关联项目（可不选）"),
                    new Field("amount", "金额"),
                    new Field("reason", "付款事由"),
                    new Field("supplierId", "供应商"),
                    new Field("accountId", "付款账户"),
                    new Field("bankAccountId", "收款账户"),
                    new Field("payMethod", "付款方式"),
                    new Field("expectedPayDate", "期望付款日"),
                    new Field("attachments", "附件")
            );
            case EXPENSE -> List.of(
                    new Field("projectId", "关联项目"),
                    new Field("accountId", "付款账户"),
                    new Field("amount", "金额"),
                    new Field("reason", "报销事由"),
                    new Field("category", "报销类别"),
                    new Field("expenseDate", "报销日期"),
                    new Field("payeeName", "收款人"),
                    new Field("attachments", "附件")
            );
            case PURCHASE -> List.of(
                    new Field("projectId", "关联项目"),
                    new Field("supplierId", "供应商"),
                    new Field("warehouseId", "入库仓库"),
                    new Field("lines", "采购明细"),
                    new Field("itemName", "物料汇总"),
                    new Field("qty", "数量合计"),
                    new Field("unit", "单位"),
                    new Field("amount", "金额合计"),
                    new Field("reason", "采购事由"),
                    new Field("purchaseDate", "采购日期")
            );
            case PURCHASE_PAY -> List.of(
                    new Field("purchaseId", "采购申请"),
                    new Field("accountId", "付款账户"),
                    new Field("amount", "金额"),
                    new Field("reason", "付款事由"),
                    new Field("payDate", "付款日期")
            );
            case STOCK_OUT_PURCHASE -> List.of(
                    new Field("warehouseId", "仓库"),
                    new Field("purchaseId", "采购申请"),
                    new Field("itemName", "物料名称"),
                    new Field("qty", "数量"),
                    new Field("unit", "单位"),
                    new Field("reason", "出库事由"),
                    new Field("outDate", "出库日期")
            );
            case STOCK_OUT_PROJECT -> List.of(
                    new Field("warehouseId", "仓库"),
                    new Field("projectId", "关联项目"),
                    new Field("itemName", "物料名称"),
                    new Field("qty", "数量"),
                    new Field("unit", "单位"),
                    new Field("amount", "金额"),
                    new Field("reason", "出库事由"),
                    new Field("outDate", "出库日期")
            );
            case STOCK_TRANSFER -> List.of(
                    new Field("warehouseId", "调出仓库"),
                    new Field("targetWarehouseId", "调入仓库"),
                    new Field("lines", "调拨明细"),
                    new Field("itemName", "物料汇总"),
                    new Field("qty", "数量合计"),
                    new Field("unit", "单位"),
                    new Field("reason", "调拨事由"),
                    new Field("outDate", "调拨日期")
            );
            case DEVICE_REPAIR -> List.of(
                    new Field("deviceId", "设备"),
                    new Field("applyDate", "申请日期"),
                    new Field("clientCustomerId", "委托客户"),
                    new Field("serviceCustomerId", "服务客户"),
                    new Field("reason", "维修事由"),
                    new Field("attachments", "附件")
            );
            case DEVICE_MOVE -> List.of(
                    new Field("deviceId", "设备"),
                    new Field("applyDate", "申请日期"),
                    new Field("targetLocation", "搬迁后位置"),
                    new Field("targetDepartmentId", "搬迁后部门"),
                    new Field("reason", "搬迁事由"),
                    new Field("attachments", "附件")
            );
            case DEVICE_SCRAP -> List.of(
                    new Field("deviceId", "设备"),
                    new Field("applyDate", "申请日期"),
                    new Field("reason", "报废事由"),
                    new Field("attachments", "附件")
            );
            case DEVICE_MAINT -> List.of(
                    new Field("service", "维保服务"),
                    new Field("customerName", "客户名称"),
                    new Field("deviceCode", "设备编号"),
                    new Field("instrumentName", "仪器名称"),
                    new Field("instrumentModel", "仪器型号"),
                    new Field("vendor", "生产厂家"),
                    new Field("maintainer", "维保人员", true),
                    new Field("startTime", "维保开始时间"),
                    new Field("location", "维保定位")
            );
            case OA_ONBOARD -> List.of(
                    new Field("applicantName", "姓名"),
                    new Field("gender", "性别"),
                    new Field("mobile", "手机号"),
                    new Field("idNumber", "身份证号"),
                    new Field("loginUsername", "登录账号"),
                    new Field("departmentId", "入职部门"),
                    new Field("position", "岗位"),
                    new Field("jobLevel", "职级"),
                    new Field("expectedDate", "入职日期"),
                    new Field("managerId", "汇报对象", true),
                    new Field("probationMonths", "试用期(月)"),
                    new Field("reason", "入职说明"),
                    new Field("salary", "薪资"),
                    new Field("salaryLines", "工资组成"),
                    new Field("attachments", "附件")
            );
            case OA_TRANSFER -> List.of(
                    new Field("staffId", "申请人"),
                    new Field("applicantName", "姓名"),
                    new Field("departmentId", "现部门"),
                    new Field("position", "现岗位"),
                    new Field("targetDepartmentId", "转入部门"),
                    new Field("targetPosition", "转入岗位"),
                    new Field("expectedDate", "期望转岗日"),
                    new Field("reason", "转岗原因"),
                    new Field("salary", "薪资"),
                    new Field("salaryLines", "工资组成"),
                    new Field("attachments", "附件")
            );
            case OA_PROMOTION -> List.of(
                    new Field("staffId", "申请人"),
                    new Field("applicantName", "姓名"),
                    new Field("departmentId", "现部门"),
                    new Field("position", "现岗位"),
                    new Field("jobLevel", "现职级"),
                    new Field("targetDepartmentId", "拟调部门", true),
                    new Field("targetPosition", "拟晋升岗位"),
                    new Field("targetLevel", "拟晋升职级"),
                    new Field("expectedDate", "期望生效日"),
                    new Field("reason", "晋升理由"),
                    new Field("salary", "薪资"),
                    new Field("salaryLines", "工资组成"),
                    new Field("attachments", "附件")
            );
            case OA_SALARY -> List.of(
                    new Field("staffId", "申请人"),
                    new Field("applicantName", "姓名"),
                    new Field("departmentId", "现部门"),
                    new Field("position", "现岗位"),
                    new Field("jobLevel", "现职级"),
                    new Field("expectedDate", "期望生效日"),
                    new Field("reason", "调薪原因"),
                    new Field("salary", "薪资"),
                    new Field("salaryLines", "工资组成"),
                    new Field("attachments", "附件")
            );
            case OA_INTERVIEW -> List.of(
                    new Field("expectedDate", "应聘时间"),
                    new Field("attachments", "附件"),
                    new Field("applicantName", "姓名"),
                    new Field("gender", "性别"),
                    new Field("birthDate", "出生日期"),
                    new Field("ethnicity", "民族"),
                    new Field("idNumber", "身份证号码"),
                    new Field("maritalStatus", "婚否"),
                    new Field("mobile", "联系电话"),
                    new Field("email", "电子邮件"),
                    new Field("hukouAddress", "户籍地址"),
                    new Field("currentAddress", "现住址"),
                    new Field("school", "毕业学校"),
                    new Field("major", "专业"),
                    new Field("highestEducation", "最高学历"),
                    new Field("educationNote", "学历"),
                    new Field("educationLines", "教育经历"),
                    new Field("workLines", "工作经历"),
                    new Field("familyLines", "家庭情况"),
                    new Field("trainingLines", "培训机构")
            );
            case OA_SEAL -> List.of(
                    new Field("staffId", "申请人", true),
                    new Field("applicantName", "姓名"),
                    new Field("departmentId", "部门"),
                    new Field("expectedDate", "盖章日期"),
                    new Field("belongCompany", "归属公司"),
                    new Field("sealTypes", "盖章类型"),
                    new Field("reason", "用途说明"),
                    new Field("copyCount", "盖章文件份数"),
                    new Field("attachments", "需要盖章的文件")
            );
            case OA_QUALIFY -> List.of(
                    new Field("expectedDate", "申请时间"),
                    new Field("lendDate", "外借时间"),
                    new Field("staffId", "申请人", true),
                    new Field("departmentId", "部门"),
                    new Field("qualifyCompanies", "出借资质公司"),
                    new Field("qualifyItems", "出借资质"),
                    new Field("companyAuditorId", "公司资质审核人员", true),
                    new Field("financeAuditorId", "财务资质审核人员", true),
                    new Field("contractAuditorId", "合同资质审核人员", true),
                    new Field("companyAuditorNote", "公司资质审核人员1"),
                    new Field("financeAuditorNote", "财务资质审核人员1"),
                    new Field("contractAuditorNote", "合同资质审核人员1"),
                    new Field("contractDesc", "合同描述"),
                    new Field("otherCertNote", "其他证书说明"),
                    new Field("reason", "外借用途说明"),
                    new Field("returnDate", "归还时间"),
                    new Field("attachments", "附件")
            );
            case OA_TRIP -> List.of(
                    new Field("staffId", "申请人", true),
                    new Field("departmentId", "部门"),
                    new Field("companionIds", "同行人", true),
                    new Field("reason", "出差事由说明"),
                    new Field("fromCity", "出发城市"),
                    new Field("toCity", "目的地城市"),
                    new Field("travelMode", "出行方式"),
                    new Field("plateNo", "车牌号"),
                    new Field("mileage", "自驾车往返里程"),
                    new Field("expectedDate", "出发日期"),
                    new Field("returnPlanDate", "预计返回日期"),
                    new Field("managerId", "审核人", true),
                    new Field("reviewTime", "审核时间"),
                    new Field("actualReturnDate", "实际返回日期"),
                    new Field("durationHours", "出差时长 (H)"),
                    new Field("remark", "备注")
            );
            case OA_LEAVE -> List.of(
                    new Field("staffId", "姓名", true),
                    new Field("departmentId", "部门"),
                    new Field("holidayDays", "节假日天数"),
                    new Field("includeCompensatory", "是否包含调休"),
                    new Field("compensatoryDays", "调休天数"),
                    new Field("month", "月份"),
                    new Field("leaveType", "请假事由"),
                    new Field("kinship", "亲属关系"),
                    new Field("reason", "事由说明"),
                    new Field("expectedDate", "开始日期"),
                    new Field("endDate", "预计结束日期"),
                    new Field("leaveDays", "预计请假天数"),
                    new Field("actualEndDate", "结束日期"),
                    new Field("actualDays", "实际请假天数"),
                    new Field("attachments", "销假证明材料")
            );
            case OA_LOAN -> List.of(
                    new Field("staffId", "申请人", true),
                    new Field("departmentId", "部门"),
                    new Field("expectedDate", "借款日期"),
                    new Field("linkProject", "是否关联项目"),
                    new Field("customerId", "客户名称"),
                    new Field("projectId", "归属项目"),
                    new Field("belongCompany", "借款公司"),
                    new Field("amount", "借款金额"),
                    new Field("reason", "用途"),
                    new Field("accountId", "付款银行账户"),
                    new Field("payAmount", "本次付款金额"),
                    new Field("attachments", "附件")
            );
            case OA_LOAN_PAY -> List.of(
                    new Field("loanRequestId", "项目借款单"),
                    new Field("staffId", "申请人", true),
                    new Field("expectedDate", "付款日期"),
                    new Field("belongCompany", "借款公司"),
                    new Field("amount", "借款金额"),
                    new Field("reason", "用途"),
                    new Field("accountId", "付款银行账户"),
                    new Field("payAmount", "本次付款金额"),
                    new Field("attachments", "附件")
            );
            case DEPOSIT_APPLY -> List.of(
                    new Field("projectId", "选择项目"),
                    new Field("amount", "申请保证金金额"),
                    new Field("bizDate", "日期"),
                    new Field("remark", "备注"),
                    new Field("accountId", "付款账户"),
                    new Field("attachments", "附件")
            );
            case DEPOSIT_RETURN -> List.of(
                    new Field("projectId", "选择项目"),
                    new Field("applyId", "保证金申请单"),
                    new Field("amount", "申请保证金金额"),
                    new Field("bizDate", "日期"),
                    new Field("remark", "备注"),
                    new Field("accountId", "收款账户"),
                    new Field("attachments", "附件")
            );
            case BIDDING -> List.of(
                    new Field("customerId", "客户名称"),
                    new Field("salesLeadId", "销售负责人", true),
                    new Field("projectId", "项目名称"),
                    new Field("opportunityName", "商机名称/项目简述"),
                    new Field("bidAgent", "中物单位"),
                    new Field("tenderName", "招标文件项目名称"),
                    new Field("tenderCode", "招标编号"),
                    new Field("downloadDeadline", "标书投递截止时间"),
                    new Field("bidTime", "开标时间"),
                    new Field("description", "项目简述"),
                    new Field("announcementUrl", "公告网址链接"),
                    new Field("signedUp", "是否报名"),
                    new Field("signups", "报名单位"),
                    new Field("result", "最终结果"),
                    new Field("winAmount", "中标金额"),
                    new Field("winUnit", "中标单位"),
                    new Field("attachments", "附件")
            );
            case OA_LOAN_REPAY -> List.of(
                    new Field("staffId", "申请人", true),
                    new Field("expectedDate", "还款日期"),
                    new Field("loanRequestId", "借款单"),
                    new Field("linkProject", "是否关联项目"),
                    new Field("projectId", "项目主题"),
                    new Field("customerId", "客户名称"),
                    new Field("belongCompany", "借款公司"),
                    new Field("repayAmount", "还款金额"),
                    new Field("reason", "还款说明"),
                    new Field("accountId", "收款银行账户"),
                    new Field("attachments", "附件")
            );
            case OA_RESIGN -> List.of(
                    new Field("staffId", "申请人"),
                    new Field("applicantName", "姓名"),
                    new Field("departmentId", "所在部门"),
                    new Field("position", "岗位"),
                    new Field("resignType", "离职类型"),
                    new Field("expectedDate", "最后工作日"),
                    new Field("handoverUserId", "交接人", true),
                    new Field("reason", "离职原因"),
                    new Field("attachments", "附件")
            );
            case CONTRACT -> List.of(
                    new Field("name", "合同名称"),
                    new Field("customerId", "客户"),
                    new Field("amount", "合同金额"),
                    new Field("signDate", "签订日期"),
                    new Field("startDate", "开始日期"),
                    new Field("endDate", "结束日期"),
                    new Field("status", "合同状态"),
                    new Field("remark", "说明")
            );
            case CUSTOMER_APPLY -> List.of(
                    new Field("companyName", "公司名称"),
                    new Field("shortName", "客户简称"),
                    new Field("level", "客户等级"),
                    new Field("contactName", "联系人"),
                    new Field("contactPhone", "联系电话"),
                    new Field("address", "单位地址")
            );
            case CUSTOMER_ACCOUNT -> List.of(
                    new Field("customerId", "所属客户"),
                    new Field("displayName", "姓名"),
                    new Field("username", "登录账号"),
                    new Field("phone", "手机号"),
                    new Field("accountType", "账号类型"),
                    new Field("roleCode", "角色"),
                    new Field("departmentId", "所属部门")
            );
            case CUSTOMER -> List.of(
                    new Field("shortName", "客户简称"),
                    new Field("code", "客户编码"),
                    new Field("companyName", "公司名称"),
                    new Field("level", "客户等级"),
                    new Field("contacts", "联系人"),
                    new Field("bankAccounts", "银行账户")
            );
            case SUPPLIER -> List.of(
                    new Field("shortName", "供应商简称"),
                    new Field("code", "供应商编码"),
                    new Field("companyName", "公司名称"),
                    new Field("category", "供应商类型"),
                    new Field("contacts", "联系人"),
                    new Field("bankAccounts", "银行账户")
            );
            case DEVICE -> List.of(
                    new Field("name", "设备名称"),
                    new Field("model", "型号"),
                    new Field("serialNo", "序列号"),
                    new Field("location", "放置位置"),
                    new Field("departmentId", "所属部门"),
                    new Field("remark", "说明"),
                    new Field("enabled", "启用")
            );
            case WAREHOUSE -> List.of(
                    new Field("name", "仓库名称"),
                    new Field("address", "地址"),
                    new Field("keeper", "保管人"),
                    new Field("remark", "说明"),
                    new Field("enabled", "启用")
            );
            case BANK_ACCOUNT -> List.of(
                    new Field("name", "账户名称"),
                    new Field("bankName", "开户银行"),
                    new Field("accountName", "开户名"),
                    new Field("accountNo", "银行账号"),
                    new Field("branch", "开户支行"),
                    new Field("openingBalance", "期初余额"),
                    new Field("remark", "说明"),
                    new Field("enabled", "启用")
            );
            case OA_STAFF -> List.of(
                    new Field("displayName", "姓名"),
                    new Field("gender", "性别"),
                    new Field("idNumber", "身份证号"),
                    new Field("birthDate", "出生日期"),
                    new Field("mobile", "手机号"),
                    new Field("position", "岗位"),
                    new Field("hireDate", "入职日期")
            );
            default -> List.of();
        };
    }

    public static boolean supported(String moduleCode) {
        FormModule module = FormModule.fromNameOrNull(moduleCode);
        if (module != null) {
            return supported(module);
        }
        return definitionRepository != null
                && moduleCode != null
                && definitionRepository.findByCode(moduleCode).filter(FormDefinition::isEnabled).isPresent();
    }

    public static boolean supported(FormModule module) {
        return module == FormModule.PROJECT
                || module == FormModule.PAYMENT
                || module == FormModule.CUSTOMER
                || module == FormModule.CONTRACT
                || module == FormModule.SUPPLIER
                || module == FormModule.EXPENSE
                || module == FormModule.PURCHASE
                || module == FormModule.PURCHASE_PAY
                || module == FormModule.STOCK_OUT_PURCHASE
                || module == FormModule.STOCK_OUT_PROJECT
                || module == FormModule.STOCK_TRANSFER
                || module == FormModule.DEVICE_REPAIR
                || module == FormModule.DEVICE_MOVE
                || module == FormModule.DEVICE_SCRAP
                || module == FormModule.DEVICE_MAINT
                || module == FormModule.CUSTOMER_APPLY
                || module == FormModule.CUSTOMER_ACCOUNT
                || module == FormModule.OA_LOAN_REPAY
                || module == FormModule.DEPOSIT_APPLY
                || module == FormModule.DEPOSIT_RETURN
                || module == FormModule.BIDDING
                || module == FormModule.DEVICE
                || module == FormModule.WAREHOUSE
                || module == FormModule.BANK_ACCOUNT
                || module.isOa()
                || module.isTech();
    }

    public static boolean isMemberField(String moduleCode, String key) {
        return fieldsOf(moduleCode).stream().anyMatch(field -> field.key().equals(key) && field.member());
    }

    public static boolean isMemberField(FormModule module, String key) {
        return fieldsOf(module).stream().anyMatch(field -> field.key().equals(key) && field.member());
    }

    public static boolean isAction(String key) {
        return key != null && key.startsWith("_action_");
    }

    public static List<Field> actionFields() {
        return List.of(
                new Field(ACTION_CANCEL, "取消", false, "操作按钮"),
                new Field(ACTION_DRAFT, "暂存", false, "操作按钮"),
                new Field(ACTION_SUBMIT, "提交", false, "操作按钮"),
                new Field(ACTION_DELETE, "删除", false, "操作按钮")
        );
    }

    public static List<Field> configurableFields(String moduleCode) {
        List<Field> all = new ArrayList<>(fieldsOf(moduleCode));
        all.addAll(actionFields());
        return List.copyOf(all);
    }

    public static List<Field> configurableFields(FormModule module) {
        List<Field> all = new ArrayList<>(fieldsOf(module));
        all.addAll(actionFields());
        return List.copyOf(all);
    }
}
