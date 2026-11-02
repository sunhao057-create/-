package com.projectmgmt.config;

import com.projectmgmt.finance.BankAccount;
import com.projectmgmt.finance.BankAccountRepository;
import com.projectmgmt.warehouse.Warehouse;
import com.projectmgmt.warehouse.WarehouseRepository;
import com.projectmgmt.customer.Customer;
import com.projectmgmt.customer.CustomerBankAccount;
import com.projectmgmt.customer.CustomerContact;
import com.projectmgmt.customer.CustomerRepository;
import com.projectmgmt.region.Region;
import com.projectmgmt.region.RegionRepository;
import com.projectmgmt.role.RoleService;
import com.projectmgmt.supplier.Supplier;
import com.projectmgmt.supplier.SupplierBankAccount;
import com.projectmgmt.supplier.SupplierContact;
import com.projectmgmt.supplier.SupplierRepository;
import com.projectmgmt.user.SysUser;
import com.projectmgmt.user.UserRepository;
import com.projectmgmt.user.UserStatus;
import com.projectmgmt.workflow.WorkflowService;
import com.projectmgmt.branding.BrandingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final RegionRepository regionRepository;
    private final RoleService roleService;
    private final WorkflowService workflowService;
    private final BrandingService brandingService;
    private final BankAccountRepository bankAccountRepository;
    private final WarehouseRepository warehouseRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.default-admin.username}")
    private String adminUsername;

    @Value("${app.default-admin.password}")
    private String adminPassword;

    @Value("${app.default-admin.display-name}")
    private String adminDisplayName;

    @Value("${app.seed-demo:true}")
    private boolean seedDemo;

    @Override
    @Transactional
    public void run(String... args) {
        roleService.ensureBuiltin();
        brandingService.ensureDefault();
        SysUser admin = ensureUser(adminUsername, adminPassword, adminDisplayName, "ADMIN", false);
        SysUser manager = ensureUser("liuxiaolin", "Staff123!", "刘小林", "USER", false);
        SysUser regionLeader = ensureUser("maxiang", "Staff123!", "马相", "USER", false);
        backfillPasswords();
        workflowService.seedDefault();
        if (!seedDemo) {
            return;
        }
        ensureCustomer("苏州冠礼", "苏州冠礼科技股份有限公司", "133", "一般重要");
        ensureCustomer("上海拓荆", "上海拓荆科技股份有限公司", "088", "重要");
        seedCustomerDetails("133", "苏州冠礼科技股份有限公司");
        ensureSupplier("苏州精工", "苏州精工机械有限公司", "S01", "设备");
        ensureSupplier("无锡材料", "无锡材料科技有限公司", "S02", "原材料");
        seedSupplierDetails("S01", "苏州精工机械有限公司");
        ensureRegion("华东地区", regionLeader);
        ensureRegion("华北地区", admin);
        ensureRegion("华南地区", manager);
        ensureBankAccount(admin);
        ensureWarehouse(admin);
    }

    private SysUser ensureUser(String username, String rawPassword, String displayName, String role, boolean mustChange) {
        return userRepository.findByUsername(username).orElseGet(() -> {
            SysUser user = new SysUser();
            user.setUsername(username);
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            user.setPasswordPlain(rawPassword);
            user.setDisplayName(displayName);
            user.setRole(role);
            user.setStatus(UserStatus.ENABLED);
            user.setMustChangePassword(mustChange);
            return userRepository.save(user);
        });
    }

    private void backfillPasswords() {
        for (SysUser user : userRepository.findAll()) {
            if (user.getPasswordPlain() != null && !user.getPasswordPlain().isBlank()) {
                continue;
            }
            if (adminUsername.equals(user.getUsername())) {
                user.setPasswordPlain(adminPassword);
            } else if ("liuxiaolin".equals(user.getUsername()) || "maxiang".equals(user.getUsername())) {
                user.setPasswordPlain("Staff123!");
            }
        }
    }

    private void ensureCustomer(String shortName, String companyName, String code, String level) {
        if (customerRepository.existsByCode(code)) {
            return;
        }
        Customer customer = new Customer();
        customer.setShortName(shortName);
        customer.setCompanyName(companyName);
        customer.setCode(code);
        customer.setLevel(level);
        customerRepository.save(customer);
    }

    private void seedCustomerDetails(String code, String companyName) {
        Customer customer = customerRepository.findByCode(code).orElse(null);
        if (customer == null || !customer.getContacts().isEmpty()) {
            return;
        }
        CustomerContact contact = new CustomerContact();
        contact.setCustomer(customer);
        contact.setName("张经理");
        contact.setTitle("采购负责人");
        contact.setPhone("13800000133");
        contact.setEmail("zhang@example.com");
        contact.setPrimaryContact(true);
        contact.setSortOrder(0);
        customer.getContacts().add(contact);

        CustomerBankAccount account = new CustomerBankAccount();
        account.setCustomer(customer);
        account.setAccountName(companyName);
        account.setBankName("中国银行苏州分行");
        account.setAccountNo("1234567890123456789");
        account.setBranch("工业园区支行");
        account.setPrimaryAccount(true);
        account.setSortOrder(0);
        customer.getBankAccounts().add(account);
    }

    private void ensureSupplier(String shortName, String companyName, String code, String category) {
        if (supplierRepository.existsByCode(code)) {
            return;
        }
        Supplier supplier = new Supplier();
        supplier.setShortName(shortName);
        supplier.setCompanyName(companyName);
        supplier.setCode(code);
        supplier.setCategory(category);
        supplierRepository.save(supplier);
    }

    private void seedSupplierDetails(String code, String companyName) {
        Supplier supplier = supplierRepository.findByCode(code).orElse(null);
        if (supplier == null || !supplier.getContacts().isEmpty()) {
            return;
        }
        SupplierContact contact = new SupplierContact();
        contact.setSupplier(supplier);
        contact.setName("赵工");
        contact.setTitle("销售经理");
        contact.setPhone("13700001001");
        contact.setEmail("zhao@example.com");
        contact.setPrimaryContact(true);
        contact.setSortOrder(0);
        supplier.getContacts().add(contact);

        SupplierBankAccount account = new SupplierBankAccount();
        account.setSupplier(supplier);
        account.setAccountName(companyName);
        account.setBankName("工商银行苏州分行");
        account.setAccountNo("622200001001");
        account.setBranch("高新区支行");
        account.setPrimaryAccount(true);
        account.setSortOrder(0);
        supplier.getBankAccounts().add(account);
    }

    private void ensureRegion(String name, SysUser leader) {
        if (regionRepository.existsByName(name)) {
            return;
        }
        Region region = new Region();
        region.setName(name);
        region.setLeader(leader);
        regionRepository.save(region);
    }

    private void ensureBankAccount(SysUser admin) {
        if (bankAccountRepository.count() > 0) {
            return;
        }
        BankAccount account = new BankAccount();
        account.setName("基本户");
        account.setBankName("中国银行苏州分行");
        account.setAccountName("智襄网");
        account.setAccountNo("512345678901");
        account.setBranch("高新区支行");
        account.setOpeningBalance(new java.math.BigDecimal("100000.00"));
        account.setEnabled(true);
        account.setCreatedBy(admin);
        bankAccountRepository.save(account);
    }

    private void ensureWarehouse(SysUser admin) {
        if (warehouseRepository.count() > 0) {
            return;
        }
        Warehouse warehouse = new Warehouse();
        warehouse.setName("总部仓");
        warehouse.setAddress("苏州市高新区");
        warehouse.setKeeper("仓管");
        warehouse.setEnabled(true);
        warehouse.setCreatedBy(admin);
        warehouseRepository.save(warehouse);
    }
}
