package com.projectmgmt.importdata;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.customer.CustomerRepository;
import com.projectmgmt.customer.CustomerService;
import com.projectmgmt.customer.dto.BankAccountRequest;
import com.projectmgmt.customer.dto.ContactRequest;
import com.projectmgmt.customer.dto.UpsertCustomerRequest;
import com.projectmgmt.importdata.CsvFiles.CsvRow;
import com.projectmgmt.importdata.CsvFiles.CsvTable;
import com.projectmgmt.payment.PaymentService;
import com.projectmgmt.project.ProjectRepository;
import com.projectmgmt.project.ProjectService;
import com.projectmgmt.region.RegionRepository;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.RoleService;
import com.projectmgmt.role.dto.UpsertRoleRequest;
import com.projectmgmt.supplier.SupplierRepository;
import com.projectmgmt.supplier.SupplierService;
import com.projectmgmt.supplier.dto.UpsertSupplierRequest;
import com.projectmgmt.user.SysUser;
import com.projectmgmt.user.UserRepository;
import com.projectmgmt.user.UserService;
import com.projectmgmt.user.UserStatus;
import com.projectmgmt.user.dto.CreateUserRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class CsvImportService {

    private static final Set<String> CUSTOMER_LEVELS = Set.of("一般重要", "重要", "非常重要");
    private static final Set<String> SUPPLIER_CATEGORIES = Set.of("原材料", "设备", "外协", "服务");

    private final CustomerService customerService;
    private final CustomerRepository customerRepository;
    private final SupplierService supplierService;
    private final SupplierRepository supplierRepository;
    private final ProjectService projectService;
    private final ProjectRepository projectRepository;
    private final PaymentService paymentService;
    private final com.projectmgmt.finance.BankAccountRepository bankAccountRepository;
    private final UserService userService;
    private final UserRepository userRepository;
    private final RoleService roleService;
    private final RegionRepository regionRepository;

    public byte[] template(FormModule module) {
        requireImportable(module);
        return CsvTemplates.of(module).bytes();
    }

    public String filename(FormModule module) {
        requireImportable(module);
        return CsvTemplates.of(module).filename();
    }

    public ImportResult importFile(FormModule module, MultipartFile file, SysUser actor) {
        requireImportable(module);
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("请上传模板文件");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
            throw ApiException.badRequest("请将电子表格另存为逗号分隔文本后再上传，或直接使用下载的模板");
        }
        if (!name.isBlank() && !name.endsWith(".csv") && !name.endsWith(".txt")) {
            throw ApiException.badRequest("仅支持下载的模板文件");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw ApiException.badRequest("读取文件失败");
        }
        CsvTable table = CsvFiles.parse(bytes, CsvTemplates.of(module).headers());
        List<String> errors = new ArrayList<>();
        int created = 0;
        for (CsvRow row : table.rows()) {
            try {
                importRow(module, row, actor);
                created++;
            } catch (ApiException ex) {
                String message = ex.getMessage();
                if (message == null || !message.startsWith("第")) {
                    message = "第" + row.lineNo() + "行：" + message;
                }
                errors.add(message);
            } catch (RuntimeException ex) {
                errors.add("第" + row.lineNo() + "行：导入失败");
            }
        }
        return new ImportResult(created, errors.size(), errors);
    }

    private void importRow(FormModule module, CsvRow row, SysUser actor) {
        switch (module) {
            case CUSTOMER -> importCustomer(row, actor);
            case SUPPLIER -> importSupplier(row, actor);
            case PROJECT -> importProject(row, actor);
            case PAYMENT -> importPayment(row, actor);
            case USER -> importUser(row);
            case ROLE -> importRole(row);
            default -> throw ApiException.badRequest(module.label() + "不支持导入");
        }
    }

    private void importCustomer(CsvRow row, SysUser actor) {
        String code = CsvFiles.require(row, "客户编码");
        if (customerRepository.existsByCode(code)) {
            throw ApiException.badRequest("第" + row.lineNo() + "行：客户编码已存在");
        }
        String level = CsvFiles.require(row, "等级");
        if (!CUSTOMER_LEVELS.contains(level)) {
            throw ApiException.badRequest("第" + row.lineNo() + "行：等级只能是 一般重要 / 重要 / 非常重要");
        }
        customerService.create(
                new UpsertCustomerRequest(
                        CsvFiles.require(row, "客户简称"),
                        CsvFiles.require(row, "公司名称"),
                        code,
                        level,
                        optionalContact(row),
                        optionalBank(row),
                        null
                ),
                actor
        );
    }

    private void importSupplier(CsvRow row, SysUser actor) {
        String code = CsvFiles.require(row, "供应商编码");
        if (supplierRepository.existsByCode(code)) {
            throw ApiException.badRequest("第" + row.lineNo() + "行：供应商编码已存在");
        }
        String category = CsvFiles.require(row, "类型");
        if (!SUPPLIER_CATEGORIES.contains(category)) {
            throw ApiException.badRequest("第" + row.lineNo() + "行：类型只能是 原材料 / 设备 / 外协 / 服务");
        }
        supplierService.create(
                new UpsertSupplierRequest(
                        CsvFiles.require(row, "供应商简称"),
                        CsvFiles.require(row, "公司名称"),
                        code,
                        category,
                        optionalSupplierContact(row),
                        optionalSupplierBank(row),
                        null
                ),
                actor
        );
    }

    private void importProject(CsvRow row, SysUser actor) {
        String code = CsvFiles.require(row, "项目编码");
        if (projectRepository.existsByCode(code)) {
            throw ApiException.badRequest("第" + row.lineNo() + "行：项目编码已存在");
        }
        var customer = customerRepository.findByCode(CsvFiles.require(row, "客户编码"))
                .orElseThrow(() -> ApiException.badRequest("第" + row.lineNo() + "行：客户编码不存在"));
        var manager = userRepository.findByUsername(CsvFiles.require(row, "项目经理用户名"))
                .orElseThrow(() -> ApiException.badRequest("第" + row.lineNo() + "行：项目经理用户名不存在"));
        if (manager.getStatus() != UserStatus.ENABLED) {
            throw ApiException.badRequest("第" + row.lineNo() + "行：项目经理账号已停用");
        }
        var region = regionRepository.findByName(CsvFiles.require(row, "区域名称"))
                .orElseThrow(() -> ApiException.badRequest("第" + row.lineNo() + "行：区域名称不存在"));
        String overview = CsvFiles.cell(row, "项目概述");
        projectService.create(
                CsvFiles.require(row, "项目名称"),
                code,
                customer.getId(),
                null,
                manager.getId(),
                region.getId(),
                overview,
                null,
                actor,
                List.of()
        );
    }

    private void importPayment(CsvRow row, SysUser actor) {
        var projectCode = CsvFiles.cell(row, "项目编码");
        Long projectId = null;
        if (StringUtils.hasText(projectCode)) {
            projectId = projectRepository.findByCode(projectCode.trim())
                    .orElseThrow(() -> ApiException.badRequest("第" + row.lineNo() + "行：项目编码不存在"))
                    .getId();
        }
        var supplier = supplierRepository.findByCode(CsvFiles.require(row, "供应商编码"))
                .orElseThrow(() -> ApiException.badRequest("第" + row.lineNo() + "行：供应商编码不存在"));
        supplier.getBankAccounts().size();
        if (supplier.getBankAccounts().isEmpty()) {
            throw ApiException.badRequest("第" + row.lineNo() + "行：供应商没有银行账户");
        }
        String accountNo = CsvFiles.cell(row, "银行账号");
        Long bankId = supplier.getBankAccounts().stream()
                .filter(item -> !StringUtils.hasText(accountNo) || accountNo.equals(item.getAccountNo()))
                .findFirst()
                .or(() -> supplier.getBankAccounts().stream().filter(item -> item.isPrimaryAccount()).findFirst())
                .orElse(supplier.getBankAccounts().get(0))
                .getId();
        var payer = bankAccountRepository.findByAccountNo(CsvFiles.require(row, "付款账户账号"))
                .orElseThrow(() -> ApiException.badRequest("第" + row.lineNo() + "行：付款账户账号不存在"));
        paymentService.create(
                projectId,
                CsvFiles.require(row, "金额"),
                CsvFiles.require(row, "付款事由"),
                supplier.getId(),
                payer.getId(),
                bankId,
                CsvFiles.require(row, "付款方式"),
                CsvFiles.require(row, "期望付款日"),
                actor,
                List.of(),
                false
        );
    }

    private void importUser(CsvRow row) {
        String username = CsvFiles.require(row, "用户名");
        if (!username.matches("^[a-zA-Z0-9_]{4,32}$")) {
            throw ApiException.badRequest("第" + row.lineNo() + "行：用户名为 4-32 位字母、数字或下划线");
        }
        String roleCode;
        try {
            roleCode = roleService.requireByNameOrCode(CsvFiles.require(row, "角色名称")).getCode();
        } catch (ApiException ex) {
            throw ApiException.badRequest("第" + row.lineNo() + "行：" + ex.getMessage());
        }
        userService.create(new CreateUserRequest(
                username,
                CsvFiles.require(row, "密码"),
                CsvFiles.require(row, "姓名"),
                roleCode,
                null,
                null,
                null,
                null,
                null
        ));
    }

    private void importRole(CsvRow row) {
        roleService.create(new UpsertRoleRequest(
                null,
                CsvFiles.require(row, "角色名称"),
                blankToNull(CsvFiles.cell(row, "说明")),
                List.of()
        ));
    }

    private List<ContactRequest> optionalContact(CsvRow row) {
        String name = CsvFiles.cell(row, "联系人姓名");
        if (name.isBlank()) {
            return List.of();
        }
        return List.of(new ContactRequest(
                name,
                null,
                blankToNull(CsvFiles.cell(row, "联系人电话")),
                blankToNull(CsvFiles.cell(row, "联系人邮箱")),
                true
        ));
    }

    private List<com.projectmgmt.supplier.dto.ContactRequest> optionalSupplierContact(CsvRow row) {
        String name = CsvFiles.cell(row, "联系人姓名");
        if (name.isBlank()) {
            return List.of();
        }
        return List.of(new com.projectmgmt.supplier.dto.ContactRequest(
                name,
                null,
                blankToNull(CsvFiles.cell(row, "联系人电话")),
                blankToNull(CsvFiles.cell(row, "联系人邮箱")),
                true
        ));
    }

    private List<BankAccountRequest> optionalBank(CsvRow row) {
        String accountName = CsvFiles.cell(row, "账户名称");
        String bankName = CsvFiles.cell(row, "开户银行");
        String accountNo = CsvFiles.cell(row, "银行账号");
        if (accountName.isBlank() && bankName.isBlank() && accountNo.isBlank()) {
            return List.of();
        }
        if (accountName.isBlank() || bankName.isBlank() || accountNo.isBlank()) {
            throw ApiException.badRequest("第" + row.lineNo() + "行：银行账户需同时填写账户名称、开户银行和银行账号");
        }
        return List.of(new BankAccountRequest(accountName, bankName, accountNo, null, true));
    }

    private List<com.projectmgmt.supplier.dto.BankAccountRequest> optionalSupplierBank(CsvRow row) {
        String accountName = CsvFiles.cell(row, "账户名称");
        String bankName = CsvFiles.cell(row, "开户银行");
        String accountNo = CsvFiles.cell(row, "银行账号");
        if (accountName.isBlank() && bankName.isBlank() && accountNo.isBlank()) {
            return List.of();
        }
        if (accountName.isBlank() || bankName.isBlank() || accountNo.isBlank()) {
            throw ApiException.badRequest("第" + row.lineNo() + "行：银行账户需同时填写账户名称、开户银行和银行账号");
        }
        return List.of(new com.projectmgmt.supplier.dto.BankAccountRequest(accountName, bankName, accountNo, null, true));
    }

    private static void requireImportable(FormModule module) {
        if (!module.hasImport() || module == FormModule.OA_STAFF) {
            throw ApiException.badRequest(module.label() + "不支持导入");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
