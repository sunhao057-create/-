package com.projectmgmt.workflow;

import com.projectmgmt.contract.Contract;
import com.projectmgmt.contract.ContractRepository;
import com.projectmgmt.customer.Customer;
import com.projectmgmt.customer.CustomerRepository;
import com.projectmgmt.finance.Expense;
import com.projectmgmt.finance.ExpenseRepository;
import com.projectmgmt.payment.Payment;
import com.projectmgmt.payment.PaymentRepository;
import com.projectmgmt.project.Project;
import com.projectmgmt.project.ProjectRepository;
import com.projectmgmt.purchase.Purchase;
import com.projectmgmt.purchase.PurchasePay;
import com.projectmgmt.purchase.PurchasePayRepository;
import com.projectmgmt.purchase.PurchaseRepository;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.supplier.Supplier;
import com.projectmgmt.supplier.SupplierRepository;
import com.projectmgmt.deposit.Deposit;
import com.projectmgmt.deposit.DepositRepository;
import com.projectmgmt.bid.Bid;
import com.projectmgmt.bid.BidRepository;
import com.projectmgmt.device.DeviceMaintService;
import com.projectmgmt.device.DeviceWorkOrder;
import com.projectmgmt.device.DeviceWorkOrderRepository;
import com.projectmgmt.oa.OaKind;
import com.projectmgmt.oa.OaRequest;
import com.projectmgmt.oa.OaRequestRepository;
import com.projectmgmt.tech.TechCatalog;
import com.projectmgmt.tech.TechRecord;
import com.projectmgmt.tech.TechRecordRepository;
import com.projectmgmt.warehouse.StockOut;
import com.projectmgmt.warehouse.StockOutRepository;
import com.projectmgmt.dynamic.FormDataRecord;
import com.projectmgmt.dynamic.FormDataRecordRepository;
import com.projectmgmt.dynamic.FormDefinition;
import com.projectmgmt.dynamic.FormDefinitionRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class WorkflowFormData {

    private final ProjectRepository projectRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final PaymentRepository paymentRepository;
    private final ExpenseRepository expenseRepository;
    private final ContractRepository contractRepository;
    private final PurchaseRepository purchaseRepository;
    private final PurchasePayRepository purchasePayRepository;
    private final StockOutRepository stockOutRepository;
    private final DeviceWorkOrderRepository deviceWorkOrderRepository;
    private final DepositRepository depositRepository;
    private final BidRepository bidRepository;
    private final OaRequestRepository oaRequestRepository;
    private final TechRecordRepository techRecordRepository;
    @Lazy private final FormDefinitionRepository formDefinitionRepository;
    @Lazy private final FormDataRecordRepository formDataRecordRepository;

    @Transactional(readOnly = true)
    public Map<String, String> values(String moduleCode, Long bizId) {
        FormModule module = FormModule.fromNameOrNull(moduleCode);
        if (module != null) {
            return values(module, bizId);
        }
        return dynamicValues(moduleCode, bizId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> dynamicValues(String code, Long bizId) {
        Map<String, String> values = new LinkedHashMap<>();
        if (bizId == null || code == null) {
            return values;
        }
        FormDataRecord record = formDataRecordRepository.findById(bizId).orElse(null);
        if (record == null || record.getValuesJson() == null) {
            return values;
        }
        try {
            Map<String, Object> raw = new JsonMapper().readValue(record.getValuesJson(), Map.class);
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                values.put(entry.getKey(), entry.getValue() == null ? null : String.valueOf(entry.getValue()));
            }
        } catch (Exception e) {
            // ignore
        }
        return values;
    }

    @Transactional(readOnly = true)
    public Map<String, String> values(FormModule module, Long bizId) {
        Map<String, String> values = new LinkedHashMap<>();
        if (bizId == null) {
            return values;
        }
        if (module == FormModule.DEVICE_MAINT) {
            TechRecord item = techRecordRepository.findById(bizId).orElse(null);
            if (item == null || item.getKind() != DeviceMaintService.KIND) {
                return values;
            }
            values.putAll(DeviceMaintService.workflowValues(item, readMap(item.getExtraJson())));
            return values;
        }
        if (module != null && module.isTech()) {
            TechRecord item = techRecordRepository.findById(bizId).orElse(null);
            if (item == null) {
                return values;
            }
            put(values, "code", item.getCode());
            put(values, "title", item.getTitle());
            Map<String, Object> extra = readMap(item.getExtraJson());
            for (TechCatalog.Field field : TechCatalog.of(item.getKind()).fields()) {
                put(values, field.key(), stringify(extra.get(field.key())));
            }
            return values;
        }
        if (module == FormModule.PROJECT) {
            Project project = projectRepository.findById(bizId).orElse(null);
            if (project == null) {
                return values;
            }
            put(values, "name", project.getName());
            put(values, "code", project.getCode());
            put(values, "customerId", project.getCustomer() == null ? null : String.valueOf(project.getCustomer().getId()));
            put(values, "contractId", project.getContract() == null ? null : String.valueOf(project.getContract().getId()));
            put(values, "companyName", project.getCompanyName());
            put(values, "companyCode", project.getCompanyCode());
            put(values, "companyLevel", project.getCompanyLevel());
            put(values, "managerId", project.getManager() == null ? null : String.valueOf(project.getManager().getId()));
            put(values, "regionId", project.getRegion() == null ? null : String.valueOf(project.getRegion().getId()));
            put(values, "regionLeader", project.getRegionLeader() == null ? null : String.valueOf(project.getRegionLeader().getId()));
            put(values, "overview", project.getOverview());
            put(values, "attachments", project.getAttachments() == null ? "0" : String.valueOf(project.getAttachments().size()));
            return values;
        }
        if (module == FormModule.PAYMENT) {
            Payment payment = paymentRepository.findById(bizId).orElse(null);
            if (payment == null) {
                return values;
            }
            put(values, "projectId", payment.getProject() == null ? null : String.valueOf(payment.getProject().getId()));
            put(values, "amount", payment.getAmount() == null ? null : payment.getAmount().toPlainString());
            put(values, "reason", payment.getReason());
            put(values, "supplierId", payment.getSupplier() == null ? null : String.valueOf(payment.getSupplier().getId()));
            put(values, "accountId", payment.getAccount() == null ? null : String.valueOf(payment.getAccount().getId()));
            put(values, "bankAccountId", payment.getBankAccountId() == null ? null : String.valueOf(payment.getBankAccountId()));
            put(values, "payMethod", payment.getPayMethod() == null ? null : payment.getPayMethod().name());
            put(values, "expectedPayDate", payment.getExpectedPayDate() == null ? null : payment.getExpectedPayDate().toString());
            put(values, "attachments", payment.getFiles() == null ? "0" : String.valueOf(payment.getFiles().size()));
            return values;
        }
        if (module == FormModule.EXPENSE) {
            Expense expense = expenseRepository.findById(bizId).orElse(null);
            if (expense == null) {
                return values;
            }
            put(values, "projectId", expense.getProject() == null ? null : String.valueOf(expense.getProject().getId()));
            put(values, "accountId", expense.getAccount() == null ? null : String.valueOf(expense.getAccount().getId()));
            put(values, "amount", expense.getAmount() == null ? null : expense.getAmount().toPlainString());
            put(values, "reason", expense.getReason());
            put(values, "category", expense.getCategory() == null ? null : expense.getCategory().name());
            put(values, "expenseDate", expense.getExpenseDate() == null ? null : expense.getExpenseDate().toString());
            put(values, "payeeName", expense.getPayeeName());
            put(values, "attachments", expense.getFiles() == null ? "0" : String.valueOf(expense.getFiles().size()));
            return values;
        }
        if (module == FormModule.DEPOSIT_APPLY || module == FormModule.DEPOSIT_RETURN) {
            Deposit item = depositRepository.findById(bizId).orElse(null);
            if (item == null) {
                return values;
            }
            put(values, "projectId", item.getProject() == null ? null : String.valueOf(item.getProject().getId()));
            put(values, "applyId", item.getApply() == null ? null : String.valueOf(item.getApply().getId()));
            put(values, "accountId", item.getAccount() == null ? null : String.valueOf(item.getAccount().getId()));
            put(values, "amount", item.getAmount() == null ? null : item.getAmount().toPlainString());
            put(values, "bizDate", item.getBizDate() == null ? null : item.getBizDate().toString());
            put(values, "remark", item.getRemark());
            put(values, "attachments", item.getFiles() == null ? "0" : String.valueOf(item.getFiles().size()));
            return values;
        }
        if (module == FormModule.BIDDING) {
            Bid item = bidRepository.findById(bizId).orElse(null);
            if (item == null) {
                return values;
            }
            put(values, "customerId", item.getCustomer() == null ? null : String.valueOf(item.getCustomer().getId()));
            put(values, "projectId", item.getProject() == null ? null : String.valueOf(item.getProject().getId()));
            put(values, "salesLeadId", item.getSalesLead() == null ? null : String.valueOf(item.getSalesLead().getId()));
            put(values, "tenderName", item.getTenderName());
            put(values, "tenderCode", item.getTenderCode());
            put(values, "result", item.getResult() == null ? null : item.getResult().name());
            put(values, "winAmount", item.getWinAmount() == null ? null : item.getWinAmount().toPlainString());
            put(values, "attachments", item.getFiles() == null ? "0" : String.valueOf(item.getFiles().size()));
            return values;
        }
        if (module == FormModule.PURCHASE) {
            Purchase purchase = purchaseRepository.findById(bizId).orElse(null);
            if (purchase == null) {
                return values;
            }
            put(values, "projectId", purchase.getProject() == null ? null : String.valueOf(purchase.getProject().getId()));
            put(values, "supplierId", purchase.getSupplier() == null ? null : String.valueOf(purchase.getSupplier().getId()));
            put(values, "warehouseId", purchase.getWarehouse() == null ? null : String.valueOf(purchase.getWarehouse().getId()));
            put(values, "lines", purchase.getLines() == null || purchase.getLines().isEmpty()
                    ? null
                    : purchase.getLines().stream()
                            .map(line -> line.getItemName()
                                    + " " + (line.getQty() == null ? "" : line.getQty().toPlainString())
                                    + line.getUnit()
                                    + " " + (line.getAmount() == null ? "" : line.getAmount().toPlainString()))
                            .collect(Collectors.joining("；")));
            put(values, "itemName", purchase.getItemName());
            put(values, "qty", purchase.getQty() == null ? null : purchase.getQty().toPlainString());
            put(values, "unit", purchase.getUnit());
            put(values, "amount", purchase.getAmount() == null ? null : purchase.getAmount().toPlainString());
            put(values, "reason", purchase.getReason());
            put(values, "purchaseDate", purchase.getPurchaseDate() == null ? null : purchase.getPurchaseDate().toString());
            return values;
        }
        if (module == FormModule.PURCHASE_PAY) {
            PurchasePay pay = purchasePayRepository.findById(bizId).orElse(null);
            if (pay == null) {
                return values;
            }
            put(values, "purchaseId", pay.getPurchase() == null ? null : String.valueOf(pay.getPurchase().getId()));
            put(values, "accountId", pay.getAccount() == null ? null : String.valueOf(pay.getAccount().getId()));
            put(values, "amount", pay.getAmount() == null ? null : pay.getAmount().toPlainString());
            put(values, "reason", pay.getReason());
            put(values, "payDate", pay.getPayDate() == null ? null : pay.getPayDate().toString());
            return values;
        }
        if (module == FormModule.STOCK_OUT_PURCHASE || module == FormModule.STOCK_OUT_PROJECT
                || module == FormModule.STOCK_TRANSFER) {
            StockOut out = stockOutRepository.findById(bizId).orElse(null);
            if (out == null) {
                return values;
            }
            put(values, "warehouseId", out.getWarehouse() == null ? null : String.valueOf(out.getWarehouse().getId()));
            put(values, "targetWarehouseId", out.getTargetWarehouse() == null ? null : String.valueOf(out.getTargetWarehouse().getId()));
            put(values, "purchaseId", out.getPurchase() == null ? null : String.valueOf(out.getPurchase().getId()));
            put(values, "projectId", out.getProject() == null ? null : String.valueOf(out.getProject().getId()));
            put(values, "itemName", out.getItemName());
            put(values, "qty", out.getQty() == null ? null : out.getQty().toPlainString());
            put(values, "unit", out.getUnit());
            put(values, "amount", out.getAmount() == null ? null : out.getAmount().toPlainString());
            put(values, "reason", out.getReason());
            put(values, "outDate", out.getOutDate() == null ? null : out.getOutDate().toString());
            return values;
        }
        if (module == FormModule.DEVICE_REPAIR || module == FormModule.DEVICE_MOVE || module == FormModule.DEVICE_SCRAP) {
            DeviceWorkOrder order = deviceWorkOrderRepository.findById(bizId).orElse(null);
            if (order == null) {
                return values;
            }
            put(values, "deviceId", order.getDevice() == null ? null : String.valueOf(order.getDevice().getId()));
            put(values, "reason", order.getReason());
            put(values, "applyDate", order.getApplyDate() == null ? null : order.getApplyDate().toString());
            put(values, "targetLocation", order.getTargetLocation());
            put(values, "targetDepartmentId", order.getTargetDepartment() == null ? null : String.valueOf(order.getTargetDepartment().getId()));
            put(values, "attachments", order.getFiles() == null ? "0" : String.valueOf(order.getFiles().size()));
            return values;
        }
        if (OaKind.fromModule(module) != null) {
            OaRequest item = oaRequestRepository.findById(bizId).orElse(null);
            if (item == null) {
                return values;
            }
            put(values, "applicantName", item.getApplicantName());
            put(values, "staffId", item.getStaff() == null ? null : String.valueOf(item.getStaff().getId()));
            put(values, "loginUsername", item.getLoginUsername());
            put(values, "gender", item.getGender());
            put(values, "mobile", item.getMobile());
            put(values, "idNumber", item.getIdNumber());
            put(values, "departmentId", item.getDepartment() == null ? null : String.valueOf(item.getDepartment().getId()));
            put(values, "position", item.getPosition());
            put(values, "jobLevel", item.getJobLevel());
            put(values, "targetDepartmentId", item.getTargetDepartment() == null ? null : String.valueOf(item.getTargetDepartment().getId()));
            put(values, "targetPosition", item.getTargetPosition());
            put(values, "targetLevel", item.getTargetLevel());
            put(values, "expectedDate", item.getExpectedDate() == null ? null : item.getExpectedDate().toString());
            put(values, "managerId", item.getManager() == null ? null : String.valueOf(item.getManager().getId()));
            put(values, "handoverUserId", item.getHandover() == null ? null : String.valueOf(item.getHandover().getId()));
            put(values, "probationMonths", item.getProbationMonths() == null ? null : String.valueOf(item.getProbationMonths()));
            put(values, "resignType", item.getResignType());
            put(values, "reason", item.getReason());
            put(values, "salary", item.getSalary() == null ? null : item.getSalary().toPlainString());
            put(values, "salaryLines", item.getSalaryLines() == null || item.getSalaryLines().isEmpty()
                    ? null
                    : item.getSalaryLines().stream()
                            .map(line -> line.getItemName() + " " + (line.getAmount() == null ? "" : line.getAmount().toPlainString()))
                            .collect(Collectors.joining("；")));
            put(values, "birthDate", item.getBirthDate() == null ? null : item.getBirthDate().toString());
            put(values, "ethnicity", item.getEthnicity());
            put(values, "maritalStatus", item.getMaritalStatus());
            put(values, "email", item.getEmail());
            put(values, "hukouAddress", item.getHukouAddress());
            put(values, "currentAddress", item.getCurrentAddress());
            put(values, "school", item.getSchool());
            put(values, "major", item.getMajor());
            put(values, "highestEducation", item.getHighestEducation());
            put(values, "educationNote", item.getEducationNote());
            put(values, "attachments", item.getFiles() == null ? "0" : String.valueOf(item.getFiles().size()));
            return values;
        }
        if (module == FormModule.CONTRACT) {
            Contract contract = contractRepository.findById(bizId).orElse(null);
            if (contract == null) {
                return values;
            }
            put(values, "name", contract.getName());
            put(values, "customerId", contract.getCustomer() == null ? null : String.valueOf(contract.getCustomer().getId()));
            put(values, "amount", contract.getAmount() == null ? null : contract.getAmount().toPlainString());
            put(values, "signDate", contract.getSignDate() == null ? null : contract.getSignDate().toString());
            put(values, "startDate", contract.getStartDate() == null ? null : contract.getStartDate().toString());
            put(values, "endDate", contract.getEndDate() == null ? null : contract.getEndDate().toString());
            put(values, "status", contract.getStatus() == null ? null : contract.getStatus().name());
            put(values, "remark", contract.getRemark());
            return values;
        }
        if (module == FormModule.CUSTOMER) {
            Customer customer = customerRepository.findById(bizId).orElse(null);
            if (customer == null) {
                return values;
            }
            put(values, "shortName", customer.getShortName());
            put(values, "code", customer.getCode());
            put(values, "companyName", customer.getCompanyName());
            put(values, "level", customer.getLevel());
            put(values, "contacts", customer.getContacts() == null ? "" : customer.getContacts().stream()
                    .map(item -> item.getName() == null ? "" : item.getName())
                    .collect(Collectors.joining(",")));
            put(values, "bankAccounts", customer.getBankAccounts() == null ? "" : customer.getBankAccounts().stream()
                    .map(item -> item.getAccountNo() == null ? "" : item.getAccountNo())
                    .collect(Collectors.joining(",")));
            return values;
        }
        if (module == FormModule.SUPPLIER) {
            Supplier supplier = supplierRepository.findById(bizId).orElse(null);
            if (supplier == null) {
                return values;
            }
            put(values, "shortName", supplier.getShortName());
            put(values, "code", supplier.getCode());
            put(values, "companyName", supplier.getCompanyName());
            put(values, "category", supplier.getCategory());
            put(values, "contacts", supplier.getContacts() == null ? "" : supplier.getContacts().stream()
                    .map(item -> item.getName() == null ? "" : item.getName())
                    .collect(Collectors.joining(",")));
            put(values, "bankAccounts", supplier.getBankAccounts() == null ? "" : supplier.getBankAccounts().stream()
                    .map(item -> item.getAccountNo() == null ? "" : item.getAccountNo())
                    .collect(Collectors.joining(",")));
        }
        return values;
    }

    @Transactional(readOnly = true)
    public String title(String moduleCode, Long bizId) {
        FormModule module = FormModule.fromNameOrNull(moduleCode);
        if (module != null) {
            return title(module, bizId);
        }
        if (bizId == null) {
            return moduleCode;
        }
        FormDataRecord record = formDataRecordRepository.findById(bizId).orElse(null);
        if (record != null && StringUtils.hasText(record.getTitle())) {
            return record.getTitle();
        }
        return moduleCode + " #" + bizId;
    }

    @Transactional(readOnly = true)
    public String title(FormModule module, Long bizId) {
        if (bizId == null) {
            return module.label();
        }
        if (module == FormModule.PROJECT) {
            return projectRepository.findById(bizId).map(Project::getName).orElse(module.label() + " #" + bizId);
        }
        if (module == FormModule.PAYMENT) {
            return paymentRepository.findById(bizId).map(Payment::getCode).orElse(module.label() + " #" + bizId);
        }
        if (module == FormModule.EXPENSE) {
            return expenseRepository.findById(bizId).map(Expense::getCode).orElse(module.label() + " #" + bizId);
        }
        if (module == FormModule.DEPOSIT_APPLY || module == FormModule.DEPOSIT_RETURN) {
            return depositRepository.findById(bizId).map(Deposit::getCode).orElse(module.label() + " #" + bizId);
        }
        if (module == FormModule.BIDDING) {
            return bidRepository.findById(bizId).map(Bid::getCode).orElse(module.label() + " #" + bizId);
        }
        if (module == FormModule.PURCHASE) {
            return purchaseRepository.findById(bizId).map(Purchase::getCode).orElse(module.label() + " #" + bizId);
        }
        if (module == FormModule.PURCHASE_PAY) {
            return purchasePayRepository.findById(bizId).map(PurchasePay::getCode).orElse(module.label() + " #" + bizId);
        }
        if (module == FormModule.STOCK_OUT_PURCHASE || module == FormModule.STOCK_OUT_PROJECT
                || module == FormModule.STOCK_TRANSFER) {
            return stockOutRepository.findById(bizId).map(StockOut::getCode).orElse(module.label() + " #" + bizId);
        }
        if (module == FormModule.DEVICE_REPAIR || module == FormModule.DEVICE_MOVE || module == FormModule.DEVICE_SCRAP) {
            return deviceWorkOrderRepository.findById(bizId).map(DeviceWorkOrder::getCode).orElse(module.label() + " #" + bizId);
        }
        if (module == FormModule.DEVICE_MAINT) {
            return techRecordRepository.findById(bizId).map(TechRecord::getTitle).orElse(module.label() + " #" + bizId);
        }
        if (OaKind.fromModule(module) != null) {
            return oaRequestRepository.findById(bizId).map(OaRequest::getCode).orElse(module.label() + " #" + bizId);
        }
        if (module == FormModule.CONTRACT) {
            return contractRepository.findById(bizId).map(Contract::getName).orElse(module.label() + " #" + bizId);
        }
        if (module == FormModule.CUSTOMER) {
            return customerRepository.findById(bizId).map(Customer::getShortName).orElse(module.label() + " #" + bizId);
        }
        if (module == FormModule.SUPPLIER) {
            return supplierRepository.findById(bizId).map(Supplier::getShortName).orElse(module.label() + " #" + bizId);
        }
        if (module != null && module.isTech()) {
            return techRecordRepository.findById(bizId).map(TechRecord::getTitle).orElse(module.label() + " #" + bizId);
        }
        return module.label() + " #" + bizId;
    }

    /** 模块展示名：固定模块取枚举标签，动态表单取表单标题。 */
    @Transactional(readOnly = true)
    public String moduleLabel(String moduleCode) {
        FormModule module = FormModule.fromNameOrNull(moduleCode);
        if (module != null) {
            return module.label();
        }
        if (!StringUtils.hasText(moduleCode)) {
            return moduleCode;
        }
        return formDefinitionRepository.findByCode(moduleCode)
                .map(FormDefinition::getTitle)
                .filter(StringUtils::hasText)
                .orElse(moduleCode);
    }

    @Transactional(readOnly = true)
    public String starterLabel(String moduleCode, Long bizId) {
        FormModule module = FormModule.fromNameOrNull(moduleCode);
        if (module != null) {
            return starterLabel(module, bizId);
        }
        if (bizId == null) {
            return "公开提交";
        }
        FormDataRecord record = formDataRecordRepository.findById(bizId).orElse(null);
        if (record != null && record.getCreatedBy() != null
                && StringUtils.hasText(record.getCreatedBy().getDisplayName())) {
            return record.getCreatedBy().getDisplayName();
        }
        return "公开提交";
    }

    @Transactional(readOnly = true)
    public String starterLabel(FormModule module, Long bizId) {
        if (module == null || bizId == null) {
            return "公开提交";
        }
        if (OaKind.fromModule(module) != null) {
            OaRequest item = oaRequestRepository.findById(bizId).orElse(null);
            if (item == null) {
                return "公开提交";
            }
            if (item.getCreatedBy() != null && StringUtils.hasText(item.getCreatedBy().getDisplayName())) {
                return item.getCreatedBy().getDisplayName();
            }
            if (item.getStaff() != null && StringUtils.hasText(item.getStaff().getDisplayName())) {
                return item.getStaff().getDisplayName();
            }
            if (StringUtils.hasText(item.getApplicantName())) {
                String name = item.getApplicantName().trim();
                return "PUBLIC".equalsIgnoreCase(item.getSubmitSource()) ? name + "（公开提交）" : name;
            }
            return "PUBLIC".equalsIgnoreCase(item.getSubmitSource()) ? "公开提交" : "发起人";
        }
        if (module.isTech() || module == FormModule.DEVICE_MAINT) {
            TechRecord item = techRecordRepository.findById(bizId).orElse(null);
            if (item != null && item.getCreatedBy() != null && StringUtils.hasText(item.getCreatedBy().getDisplayName())) {
                return item.getCreatedBy().getDisplayName();
            }
        }
        return "公开提交";
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = tools.jackson.databind.json.JsonMapper.shared().readValue(json, Map.class);
            return map == null ? Map.of() : map;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String stringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof java.util.Collection<?> items) {
            return items.stream().map(this::stringify).filter(text -> !text.isBlank()).collect(Collectors.joining(","));
        }
        if (value instanceof Map<?, ?> row) {
            Object nested = row.get("id");
            if (nested == null) {
                nested = row.get("value");
            }
            if (nested == null) {
                nested = row.get("name");
            }
            return stringify(nested);
        }
        return String.valueOf(value);
    }

    private void put(Map<String, String> values, String key, String value) {
        values.put(key, value == null ? "" : value);
    }
}
