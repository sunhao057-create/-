package com.projectmgmt.mobile;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.mobile.dto.MobileFormField;
import com.projectmgmt.mobile.dto.MobileFormOption;
import com.projectmgmt.mobile.dto.MobileFormSchema;
import com.projectmgmt.oa.OaFormOptions;
import com.projectmgmt.oa.OaKind;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.tech.TechCatalog;
import com.projectmgmt.tech.TechKind;
import com.projectmgmt.workflow.FormFieldCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class MobileFormSchemaService {

    private static final Set<String> LEAVE_KEYS = Set.of(
            "holidayDays", "includeCompensatory", "compensatoryDays", "month", "leaveType",
            "kinship", "endDate", "leaveDays", "actualEndDate", "actualDays");
    private static final Set<String> TRIP_KEYS = Set.of(
            "companionIds", "fromCity", "toCity", "travelMode", "plateNo", "mileage",
            "returnPlanDate", "reviewTime", "actualReturnDate", "durationHours", "remark");
    private static final Set<String> INTERVIEW_KEYS = Set.of(
            "birthDate", "ethnicity", "maritalStatus", "email", "hukouAddress", "currentAddress",
            "school", "major", "highestEducation", "educationNote", "educationLines",
            "workLines", "familyLines", "trainingLines");
    private static final Set<String> SEAL_KEYS = Set.of("belongCompany", "sealTypes", "copyCount");
    private static final Set<String> QUALIFY_KEYS = Set.of(
            "lendDate", "returnDate", "qualifyCompanies", "qualifyItems", "companyAuditorId",
            "financeAuditorId", "contractAuditorId", "companyAuditorNote", "financeAuditorNote",
            "contractAuditorNote", "contractDesc", "otherCertNote");
    private static final Set<String> LOAN_KEYS = Set.of(
            "linkProject", "projectId", "customerId", "belongCompany", "amount", "payAmount",
            "accountId", "loanRequestId", "repayAmount");
    private static final Set<String> TEXTAREA = Set.of(
            "reason", "overview", "remark", "description", "contractDesc", "otherCertNote",
            "companyAuditorNote", "financeAuditorNote", "contractAuditorNote", "educationNote");
    private static final Set<String> NUMBER = Set.of(
            "amount", "qty", "salary", "holidayDays", "compensatoryDays", "leaveDays", "actualDays",
            "mileage", "durationHours", "probationMonths", "copyCount", "winAmount", "payAmount",
            "repayAmount", "openingBalance");
    private static final Set<String> BOOLEAN = Set.of(
            "includeCompensatory", "signedUp", "isPublic", "linkProject", "enabled");
    private static final Set<String> DATE = Set.of(
            "expectedDate", "endDate", "actualEndDate", "returnPlanDate", "actualReturnDate",
            "lendDate", "returnDate", "birthDate", "applyDate", "expenseDate", "payDate",
            "purchaseDate", "outDate", "bizDate", "signDate", "startDate", "expectedPayDate",
            "downloadDeadline", "bidTime", "hireDate");
    private static final Set<String> HINT = Set.of();
    private static final Set<String> SUBTABLE = Set.of(
            "salaryLines", "educationLines", "workLines", "familyLines", "trainingLines", "lines",
            "contacts", "bankAccounts", "signups");

    private final PermissionService permissionService;

    public MobileFormSchemaService(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public MobileFormSchema schema(String moduleName, String path) {
        FormModule module = resolveModule(moduleName, path);
        permissionService.require(module, "VIEW");
        String family = familyOf(module);
        String slug = slugOf(module);
        List<MobileFormField> fields = new ArrayList<>();
        if (module.isTech()) {
            fields.add(field("title", "标题", "text", false, "root", "title", null, List.of(), List.of(), null));
            fields.add(field("isPublic", "允许公开", "boolean", false, "root", "isPublic", null, List.of(), List.of(), null));
            TechCatalog.Kind kind = TechCatalog.of(module);
            for (TechCatalog.Field src : kind.fields()) {
                fields.add(fromTech(src));
                if ("subtable".equals(src.type())) {
                    for (TechCatalog.Field sub : src.subfields()) {
                        if ("attachment".equals(sub.type())) {
                            fields.add(fromTech(sub));
                        }
                    }
                }
            }
        } else if (module.isMiniContent()) {
            fields.add(field("title", "标题", "text", true, "root", "title", null, List.of(), List.of(), null));
            fields.add(field("category", "分类", "text", false, "root", "category", null, List.of(), List.of(), null));
            fields.add(field("summary", "摘要", "textarea", false, "root", "summary", null, List.of(), List.of(), null));
            fields.add(field("body", "正文", "textarea", false, "root", "body", null, List.of(), List.of(), null));
            fields.add(field("sortOrder", "排序", "number", false, "root", "sortOrder", null, List.of(), List.of(), null));
            fields.add(field("enabled", "启用", "boolean", false, "root", "enabled", null, List.of(), List.of(), null));
        } else {
            if (module.isOa() || module == FormModule.PROJECT || module == FormModule.CUSTOMER || module == FormModule.SUPPLIER) {
                fields.add(field("isPublic", "允许公开", "boolean", false, "root", "isPublic", null, List.of(), List.of(), null));
            }
            for (FormFieldCatalog.Field src : FormFieldCatalog.fieldsOf(module)) {
                if ("code".equals(src.key())) {
                    boolean auto = module.isOa() || module.isTech();
                    if (auto) {
                        fields.add(field(
                                src.key(),
                                src.label(),
                                "hint",
                                false,
                                "root",
                                src.key(),
                                null,
                                List.of(),
                                List.of(),
                                "保存后自动生成"));
                    } else {
                        fields.add(field(src.key(), src.label(), "text", true, "root", src.key(), null, List.of(), List.of(), null));
                    }
                    continue;
                }
                fields.add(fromCatalog(module, src));
                if ("loginUsername".equals(src.key()) && module == FormModule.OA_ONBOARD) {
                    fields.add(field(
                            "password",
                            "登录密码",
                            "password",
                            false,
                            "root",
                            "password",
                            null,
                            List.of(),
                            List.of(),
                            "仅新建账号时填写"));
                }
            }
        }
        boolean canCreate = module.hasCreate() && permissionService.currentAllowed(module, "CREATE");
        boolean canEdit = module.hasEdit() && permissionService.currentAllowed(module, "EDIT");
        return new MobileFormSchema(
                module.name(),
                module.label(),
                family,
                slug,
                saveUrl(module, slug),
                saveEncoding(module),
                canCreate,
                canEdit,
                List.copyOf(fields));
    }

    private MobileFormField fromTech(TechCatalog.Field src) {
        String type = src.type() == null ? "text" : src.type();
        if ("attachment".equals(type) || "files".equals(src.key())) {
            return field(src.key(), src.name(), "attachment", false, "extra", src.key(), null, List.of(), List.of(),
                    null, src.key());
        }
        if ("subtable".equals(type)) {
            List<MobileFormField> columns = new ArrayList<>();
            for (TechCatalog.Field sub : src.subfields()) {
                if ("attachment".equals(sub.type())) {
                    continue;
                }
                columns.add(fromTech(sub));
            }
            return field(src.key(), src.name(), "subtable", src.required(), "extra", src.key(), null, List.of(), columns, null);
        }
        String mapped = switch (type) {
            case "textarea" -> "textarea";
            case "number" -> "number";
            case "datetime", "date" -> "date";
            case "boolean" -> "boolean";
            case "select" -> "select";
            case "multiselect" -> "multiselect";
            case "member" -> "member";
            default -> "text";
        };
        List<MobileFormOption> options = new ArrayList<>();
        for (String option : src.options()) {
            options.add(new MobileFormOption(option, option));
        }
        String lookup = "member".equals(mapped) ? "staff" : null;
        return field(src.key(), src.name(), mapped, src.required(), "extra", src.key(), lookup, options, List.of(), null);
    }

    private MobileFormField fromCatalog(FormModule module, FormFieldCatalog.Field src) {
        String key = src.key();
        String extra = extraOf(module, key);
        String saveKey = saveKeyOf(key);
        if ("attachments".equals(key) || "files".equals(key)) {
            String fileKey = module == FormModule.BIDDING ? "SCREENSHOT" : key;
            return field(key, src.label(), "attachment", false, extra, saveKey, null, List.of(), List.of(), null, fileKey);
        }
        if (HINT.contains(key)) {
            return field(key, src.label(), "hint", false, extra, saveKey, null, List.of(), List.of(), "请在电脑端维护", null);
        }
        if (SUBTABLE.contains(key)) {
            return field(key, src.label(), "subtable", src.member(), extra, saveKey, null, List.of(), columnsOf(key), null);
        }
        if (BOOLEAN.contains(key)) {
            return field(key, src.label(), "boolean", false, extra, saveKey, null, List.of(), List.of(), null);
        }
        List<MobileFormOption> options = optionsOf(key);
        if (!options.isEmpty()) {
            String type = "sealTypes".equals(key) || "qualifyItems".equals(key) || "qualifyCompanies".equals(key)
                    ? "multiselect"
                    : "select";
            return field(key, src.label(), type, false, extra, saveKey, null, options, List.of(), null);
        }
        String lookup = lookupOf(module, key, src.member());
        if (lookup != null) {
            String type = "companionIds".equals(key) ? "members" : "lookup";
            return field(key, src.label(), type, false, extra, saveKey, lookup, List.of(), List.of(), null);
        }
        if (DATE.contains(key) || key.endsWith("Date") || key.endsWith("Time")) {
            return field(key, src.label(), "date", false, extra, saveKey, null, List.of(), List.of(), null);
        }
        if (NUMBER.contains(key) || key.toLowerCase(Locale.ROOT).contains("amount") || key.toLowerCase(Locale.ROOT).contains("qty")) {
            return field(key, src.label(), "number", false, extra, saveKey, null, List.of(), List.of(), null);
        }
        if (TEXTAREA.contains(key)) {
            return field(key, src.label(), "textarea", false, extra, saveKey, null, List.of(), List.of(), null);
        }
        if ("password".equals(key)) {
            return field(key, src.label(), "password", false, extra, saveKey, null, List.of(), List.of(), null);
        }
        return field(key, src.label(), "text", false, extra, saveKey, null, List.of(), List.of(), null);
    }

    private static List<MobileFormField> columnsOf(String key) {
        if ("signups".equals(key)) {
            return List.of(
                    field("companyName", "报名单位", "text", true, null, "companyName", null, List.of(), List.of(), null, null),
                    field("bidderId", "投标人", "lookup", false, null, "bidderId", "staff", List.of(), List.of(), null, null),
                    field("registrantName", "报名登记人", "text", false, null, "registrantName", null, List.of(), List.of(), null, null));
        }
        if ("salaryLines".equals(key)) {
            return List.of(
                    field("itemName", "项目", "text", true, null, "itemName", null, List.of(), List.of(), null),
                    field("amount", "金额", "number", true, null, "amount", null, List.of(), List.of(), null));
        }
        if ("contacts".equals(key)) {
            return List.of(
                    field("name", "姓名", "text", true, null, "name", null, List.of(), List.of(), null),
                    field("title", "职务", "text", false, null, "title", null, List.of(), List.of(), null),
                    field("phone", "电话", "text", false, null, "phone", null, List.of(), List.of(), null),
                    field("email", "邮箱", "text", false, null, "email", null, List.of(), List.of(), null));
        }
        if ("bankAccounts".equals(key)) {
            return List.of(
                    field("accountName", "开户名", "text", true, null, "accountName", null, List.of(), List.of(), null),
                    field("bankName", "开户银行", "text", true, null, "bankName", null, List.of(), List.of(), null),
                    field("accountNo", "账号", "text", true, null, "accountNo", null, List.of(), List.of(), null),
                    field("branch", "支行", "text", false, null, "branch", null, List.of(), List.of(), null));
        }
        if ("lines".equals(key)) {
            return List.of(
                    field("itemName", "物料", "text", true, null, "itemName", null, List.of(), List.of(), null),
                    field("qty", "数量", "text", false, null, "qty", null, List.of(), List.of(), null),
                    field("unit", "单位", "text", false, null, "unit", null, List.of(), List.of(), null),
                    field("amount", "金额", "text", false, null, "amount", null, List.of(), List.of(), null));
        }
        return List.of(
                field("title", "名称", "text", false, null, "title", null, List.of(), List.of(), null),
                field("subtitle", "说明", "text", false, null, "subtitle", null, List.of(), List.of(), null),
                field("extra", "补充", "text", false, null, "extra", null, List.of(), List.of(), null),
                field("startYm", "开始", "text", false, null, "startYm", null, List.of(), List.of(), null),
                field("endYm", "结束", "text", false, null, "endYm", null, List.of(), List.of(), null));
    }

    private static List<MobileFormOption> optionsOf(String key) {
        return switch (key) {
            case "gender" -> List.of(
                    new MobileFormOption("MALE", "男"),
                    new MobileFormOption("FEMALE", "女"));
            case "leaveType" -> labeled(OaFormOptions.LEAVE_TYPES, OaFormOptions::leaveTypeLabel);
            case "kinship" -> labeled(OaFormOptions.KINSHIPS, OaFormOptions::kinshipLabel);
            case "travelMode" -> labeled(OaFormOptions.TRAVEL_MODES, OaFormOptions::travelModeLabel);
            case "belongCompany", "qualifyCompanies" -> strings(OaFormOptions.COMPANIES);
            case "sealTypes" -> strings(OaFormOptions.SEAL_TYPES);
            case "qualifyItems" -> strings(OaFormOptions.QUALIFY_ITEMS);
            case "resignType" -> List.of(
                    new MobileFormOption("VOLUNTARY", "主动离职"),
                    new MobileFormOption("NEGOTIATED", "协商解除"),
                    new MobileFormOption("OTHER", "其他"));
            case "payMethod" -> List.of(
                    new MobileFormOption("WIRE", "电汇"),
                    new MobileFormOption("CHECK", "支票"),
                    new MobileFormOption("CASH", "现金"),
                    new MobileFormOption("OTHER", "其他"));
            case "maritalStatus" -> List.of(
                    new MobileFormOption("未婚", "未婚"),
                    new MobileFormOption("已婚", "已婚"),
                    new MobileFormOption("离异", "离异"));
            default -> List.of();
        };
    }

    private static List<MobileFormOption> strings(List<String> values) {
        List<MobileFormOption> options = new ArrayList<>();
        for (String value : values) {
            options.add(new MobileFormOption(value, value));
        }
        return options;
    }

    private static List<MobileFormOption> labeled(List<String> values, java.util.function.Function<String, String> labels) {
        List<MobileFormOption> options = new ArrayList<>();
        for (String value : values) {
            String label = labels.apply(value);
            options.add(new MobileFormOption(value, label == null ? value : label));
        }
        return options;
    }

    private static String lookupOf(FormModule module, String key, boolean member) {
        if (member || "staffId".equals(key) || key.endsWith("UserId") || "managerId".equals(key)
                || "salesLeadId".equals(key) || key.endsWith("AuditorId") || "bidderId".equals(key)) {
            return "staff";
        }
        if ("departmentId".equals(key) || "targetDepartmentId".equals(key)) {
            return "dept";
        }
        if ("customerId".equals(key)) {
            return "customer";
        }
        if ("projectId".equals(key)) {
            return "project";
        }
        if ("supplierId".equals(key)) {
            return "supplier";
        }
        if ("accountId".equals(key) || "bankAccountId".equals(key)) {
            return "account";
        }
        if ("warehouseId".equals(key) || "targetWarehouseId".equals(key)) {
            return "warehouse";
        }
        if ("deviceId".equals(key)) {
            return "device";
        }
        if ("contractId".equals(key)) {
            return "contract";
        }
        if ("regionId".equals(key)) {
            return "region";
        }
        if ("purchaseId".equals(key)) {
            return "purchase";
        }
        if ("loanRequestId".equals(key)) {
            return "loan";
        }
        if ("applyId".equals(key)) {
            return "depositApply";
        }
        return null;
    }

    private static String extraOf(FormModule module, String key) {
        if (module == FormModule.OA_LEAVE && LEAVE_KEYS.contains(key)) {
            return "leave";
        }
        if (module == FormModule.OA_TRIP && TRIP_KEYS.contains(key)) {
            return "trip";
        }
        if (module == FormModule.OA_INTERVIEW && INTERVIEW_KEYS.contains(key)) {
            return "interview";
        }
        if (module == FormModule.OA_SEAL && SEAL_KEYS.contains(key)) {
            return "seal";
        }
        if (module == FormModule.OA_QUALIFY && QUALIFY_KEYS.contains(key)) {
            return "qualify";
        }
        if ((module == FormModule.OA_LOAN || module == FormModule.OA_LOAN_PAY || module == FormModule.OA_LOAN_REPAY)
                && LOAN_KEYS.contains(key)) {
            return "loan";
        }
        return "root";
    }

    private static String saveKeyOf(String key) {
        if ("qualifyCompanies".equals(key)) {
            return "companies";
        }
        if ("qualifyItems".equals(key)) {
            return "items";
        }
        return key;
    }

    private static MobileFormField field(
            String key,
            String label,
            String type,
            boolean required,
            String extra,
            String saveKey,
            String lookup,
            List<MobileFormOption> options,
            List<MobileFormField> columns,
            String hint
    ) {
        return field(key, label, type, required, extra, saveKey, lookup, options, columns, hint, null);
    }

    private static MobileFormField field(
            String key,
            String label,
            String type,
            boolean required,
            String extra,
            String saveKey,
            String lookup,
            List<MobileFormOption> options,
            List<MobileFormField> columns,
            String hint,
            String fileKey
    ) {
        return new MobileFormField(
                key,
                label,
                type,
                required,
                extra,
                saveKey,
                lookup,
                options,
                columns,
                hint,
                fileKey);
    }

    private FormModule resolveModule(String moduleName, String path) {
        if (moduleName != null && !moduleName.isBlank()) {
            try {
                return FormModule.valueOf(moduleName.trim());
            } catch (IllegalArgumentException ex) {
                throw ApiException.badRequest("未知模块");
            }
        }
        if (path == null || path.isBlank()) {
            throw ApiException.badRequest("请指定模块");
        }
        String clean = path.split("\\?")[0].replaceFirst("^/", "");
        String[] parts = clean.split("/");
        if (parts.length >= 2 && "oa".equals(parts[0])) {
            return OaKind.fromSlug(parts[1]).module();
        }
        if (parts.length >= 2 && "tech".equals(parts[0])) {
            return TechKind.fromSlug(parts[1]).module();
        }
        throw ApiException.badRequest("无法识别表单模块");
    }

    private static String familyOf(FormModule module) {
        if (module.isOa()) {
            return "oa";
        }
        if (module.isTech()) {
            return "tech";
        }
        return "biz";
    }

    private static String slugOf(FormModule module) {
        OaKind oa = OaKind.fromModule(module);
        if (oa != null) {
            return oa.slug();
        }
        TechKind tech = TechKind.fromModule(module);
        if (tech != null) {
            return tech.slug();
        }
        return module.name().toLowerCase(Locale.ROOT);
    }

    private static String saveUrl(FormModule module, String slug) {
        if (module.isMiniContent()) {
            return "/api/portal/" + module.name();
        }
        if (module == FormModule.OA_STAFF) {
            return "/api/personnel";
        }
        if (module.isOa()) {
            return "/api/oa/" + slug;
        }
        if (module.isTech()) {
            return "/api/tech/" + slug;
        }
        return switch (module) {
            case PROJECT -> "/api/projects";
            case PAYMENT -> "/api/payments";
            case EXPENSE -> "/api/finance/expenses";
            case PURCHASE -> "/api/purchase";
            case PURCHASE_PAY -> "/api/purchase/payments";
            case CONTRACT -> "/api/contracts";
            case CUSTOMER -> "/api/customers";
            case SUPPLIER -> "/api/suppliers";
            case DEVICE -> "/api/devices";
            case DEVICE_REPAIR -> "/api/devices/repairs";
            case DEVICE_MOVE -> "/api/devices/moves";
            case DEVICE_SCRAP -> "/api/devices/scraps";
            case DEVICE_MAINT -> "/api/devices/maint-records";
            case CUSTOMER_APPLY -> "/api/customer-opens";
            case CUSTOMER_ACCOUNT -> "/api/customer-accounts";
            case BIDDING -> "/api/bids";
            case DEPOSIT_APPLY -> "/api/deposit-applies";
            case DEPOSIT_RETURN -> "/api/deposit-returns";
            case STOCK_OUT_PURCHASE -> "/api/warehouse/purchase-out";
            case STOCK_OUT_PROJECT -> "/api/warehouse/project-out";
            case STOCK_TRANSFER -> "/api/warehouse/transfer";
            case WAREHOUSE -> "/api/warehouse";
            case BANK_ACCOUNT -> "/api/finance/accounts";
            case OA_STAFF -> "/api/personnel";
            default -> "";
        };
    }

    private static String saveEncoding(FormModule module) {
        return switch (module) {
            case PROJECT, PAYMENT, EXPENSE, DEPOSIT_APPLY, DEPOSIT_RETURN -> "form";
            default -> "json";
        };
    }
}
