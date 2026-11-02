package com.projectmgmt.portal;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.customer.Customer;
import com.projectmgmt.customer.CustomerBankAccount;
import com.projectmgmt.customer.CustomerContact;
import com.projectmgmt.customer.CustomerRepository;
import com.projectmgmt.customer.dto.BankAccountRequest;
import com.projectmgmt.customer.dto.ContactRequest;
import com.projectmgmt.finance.BankAccount;
import com.projectmgmt.finance.BankAccountRepository;
import com.projectmgmt.payment.Payment;
import com.projectmgmt.payment.PaymentRepository;
import com.projectmgmt.payment.PaymentService;
import com.projectmgmt.oa.OaKind;
import com.projectmgmt.oa.OaLookupsView;
import com.projectmgmt.oa.OaRequest;
import com.projectmgmt.oa.OaRequestView;
import com.projectmgmt.oa.OaService;
import com.projectmgmt.tech.PublicTechView;
import com.projectmgmt.tech.TechCatalog;
import com.projectmgmt.tech.TechKind;
import com.projectmgmt.tech.TechLookupsView;
import com.projectmgmt.tech.TechService;
import com.projectmgmt.tech.UpsertTechRecord;
import com.projectmgmt.oa.UpsertOaRequest;
import com.projectmgmt.export.ExcelSheets;
import com.projectmgmt.profile.FileStorageService;
import com.projectmgmt.project.Project;
import com.projectmgmt.project.ProjectAttachment;
import com.projectmgmt.project.ProjectProgressService;
import com.projectmgmt.project.ProjectProgressView;
import com.projectmgmt.project.ProjectRepository;
import com.projectmgmt.project.ProjectStatus;
import com.projectmgmt.region.Region;
import com.projectmgmt.region.RegionRepository;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.supplier.Supplier;
import com.projectmgmt.supplier.SupplierBankAccount;
import com.projectmgmt.supplier.SupplierContact;
import com.projectmgmt.supplier.SupplierRepository;
import com.projectmgmt.user.SysUser;
import com.projectmgmt.user.UserRepository;
import com.projectmgmt.user.UserStatus;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class PublicPortalService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final PublicAccess publicAccess;
    private final SubmitRateLimiter rateLimiter;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final ProjectRepository projectRepository;
    private final RegionRepository regionRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final BankAccountRepository bankAccountRepository;
    private final ProjectProgressService projectProgressService;
    private final OaService oaService;
    private final TechService techService;

    @Transactional(readOnly = true)
    public PublicConfigView config() {
        List<PublicModuleConfig> modules = new ArrayList<>(FormModule.all().stream()
                .filter(module -> module == FormModule.DEVICE
                        || module == FormModule.DEVICE_REPAIR
                        || module == FormModule.DEVICE_MOVE
                        || module == FormModule.DEVICE_SCRAP)
                .filter(FormModule::canBePublic)
                .map(module -> new PublicModuleConfig(
                        module.name(),
                        module.label(),
                        publicPath(module),
                        publicAccess.flags(module)
                ))
                .filter(item -> item.path() != null && !"/public".equals(item.path()))
                .toList());
        return new PublicConfigView(
                publicAccess.flags(FormModule.PROJECT),
                publicAccess.flags(FormModule.CUSTOMER),
                publicAccess.flags(FormModule.SUPPLIER),
                publicAccess.flags(FormModule.PAYMENT),
                modules
        );
    }

    private String publicPath(FormModule module) {
        if (module != null && module.isTech()) {
            TechKind kind = TechKind.fromModule(module);
            return kind == null ? "/public" : kind.publicPath();
        }
        return switch (module) {
            case PROJECT -> "/public/projects";
            case PAYMENT -> "/public/payments";
            case CUSTOMER -> "/public/customers";
            case SUPPLIER -> "/public/suppliers";
            case OA_ONBOARD -> "/public/oa/onboard";
            case OA_TRANSFER -> "/public/oa/transfer";
            case OA_PROMOTION -> "/public/oa/promotion";
            case OA_SALARY -> "/public/oa/salary";
            case OA_INTERVIEW -> "/public/oa/interview";
            case OA_SEAL -> "/public/oa/seal";
            case OA_QUALIFY -> "/public/oa/qualify";
            case OA_TRIP -> "/public/oa/trip";
            case OA_LEAVE -> "/public/oa/leave";
            case OA_LOAN -> "/public/oa/loan";
            case OA_LOAN_PAY -> "/public/oa/loan-pay";
            case OA_LOAN_REPAY -> "/public/oa/loan-repay";
            case OA_RESIGN -> "/public/oa/resign";
            default -> "/public";
        };
    }

    @Transactional(readOnly = true)
    public PublicLookupsView lookups() {
        PublicModuleFlags flags = publicAccess.flags(FormModule.PROJECT);
        if (!flags.publicAccess()) {
            throw ApiException.badRequest("该内容未开放公开办理");
        }
        List<PublicCustomerView> customers = customerRepository.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
                .filter(Customer::isPublic)
                .map(this::toPublicCustomer)
                .toList();
        List<PublicStaffOption> staff = userRepository.findByStatusOrderByDisplayNameAsc(UserStatus.ENABLED).stream()
                .map(user -> new PublicStaffOption(user.getId(), user.getDisplayName(), user.getAvatarUrl()))
                .toList();
        List<PublicRegionOption> regions = regionRepository.findAll(Sort.by("id")).stream()
                .map(region -> new PublicRegionOption(
                        region.getId(),
                        region.getName(),
                        displayName(region.getLeader()),
                        region.getLeader() == null ? null : region.getLeader().getAvatarUrl()
                ))
                .toList();
        return new PublicLookupsView(customers, staff, regions);
    }

    @Transactional(readOnly = true)
    public List<PublicCustomerView> listCustomers(String keyword) {
        publicAccess.requireListable(FormModule.CUSTOMER);
        PublicModuleFlags flags = publicAccess.flags(FormModule.CUSTOMER);
        if (!flags.display() && !StringUtils.hasText(keyword)) {
            return List.of();
        }
        return customerRepository.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
                .filter(Customer::isPublic)
                .filter(matchesCustomer(keyword))
                .map(this::toPublicCustomer)
                .toList();
    }

    @Transactional(readOnly = true)
    public PublicCustomerView getCustomer(Long id) {
        publicAccess.requireReadable(FormModule.CUSTOMER);
        Customer customer = customerRepository.findById(id)
                .filter(Customer::isPublic)
                .orElseThrow(() -> ApiException.notFound("内容不存在"));
        return toPublicCustomer(customer);
    }

    @Transactional
    public PublicCustomerView submitCustomer(PublicCustomerSubmitRequest request, String ip) {
        publicAccess.requireSubmit(FormModule.CUSTOMER);
        rateLimiter.check(ip, "CUSTOMER");
        Customer customer = new Customer();
        customer.setShortName(request.shortName().trim());
        customer.setCompanyName(request.companyName().trim());
        customer.setCode(resolveCode(request.code(), customerRepository::existsByCode, "客户编码已存在"));
        customer.setLevel(StringUtils.hasText(request.level()) ? request.level().trim() : "一般重要");
        customer.setPublic(false);
        customer.setSubmitSource("PUBLIC");
        addCustomerContacts(customer, request);
        addCustomerBanks(customer, request.bankAccounts());
        return toPublicCustomer(customerRepository.save(customer));
    }

    @Transactional(readOnly = true)
    public List<PublicSupplierView> listSuppliers(String keyword) {
        publicAccess.requireListable(FormModule.SUPPLIER);
        PublicModuleFlags flags = publicAccess.flags(FormModule.SUPPLIER);
        if (!flags.display() && !StringUtils.hasText(keyword)) {
            return List.of();
        }
        return supplierRepository.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
                .filter(Supplier::isPublic)
                .filter(matchesSupplier(keyword))
                .map(this::toPublicSupplier)
                .toList();
    }

    @Transactional(readOnly = true)
    public PublicSupplierView getSupplier(Long id) {
        publicAccess.requireReadable(FormModule.SUPPLIER);
        Supplier supplier = supplierRepository.findById(id)
                .filter(Supplier::isPublic)
                .orElseThrow(() -> ApiException.notFound("内容不存在"));
        return toPublicSupplier(supplier);
    }

    @Transactional
    public PublicSupplierView submitSupplier(PublicSupplierSubmitRequest request, String ip) {
        publicAccess.requireSubmit(FormModule.SUPPLIER);
        rateLimiter.check(ip, "SUPPLIER");
        Supplier supplier = new Supplier();
        supplier.setShortName(request.shortName().trim());
        supplier.setCompanyName(request.companyName().trim());
        supplier.setCode(resolveCode(request.code(), supplierRepository::existsByCode, "供应商编码已存在"));
        supplier.setCategory(StringUtils.hasText(request.category()) ? request.category().trim() : "原材料");
        supplier.setPublic(false);
        supplier.setSubmitSource("PUBLIC");
        addSupplierContacts(supplier, request);
        addSupplierBanks(supplier, request.bankAccounts());
        return toPublicSupplier(supplierRepository.save(supplier));
    }

    @Transactional(readOnly = true)
    public List<PublicProjectView> listProjects(String keyword) {
        publicAccess.requireListable(FormModule.PROJECT);
        PublicModuleFlags flags = publicAccess.flags(FormModule.PROJECT);
        if (!flags.display() && !StringUtils.hasText(keyword)) {
            return List.of();
        }
        return projectRepository.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
                .filter(Project::isPublic)
                .filter(matchesProject(keyword))
                .map(this::toPublicProject)
                .toList();
    }

    @Transactional(readOnly = true)
    public PublicProjectView getProject(Long id) {
        publicAccess.requireReadable(FormModule.PROJECT);
        Project project = projectRepository.findById(id)
                .filter(Project::isPublic)
                .orElseThrow(() -> ApiException.notFound("内容不存在"));
        return toPublicProject(project);
    }

    @Transactional
    public PublicProjectView submitProject(PublicProjectSubmitRequest request, List<MultipartFile> files, String ip) {
        publicAccess.requireSubmit(FormModule.PROJECT);
        rateLimiter.check(ip, "PROJECT");
        SysUser fallbackAdmin = userRepository.findByUsername("admin")
                .orElseThrow(() -> ApiException.badRequest("暂无法提交，请联系管理员"));
        SysUser manager = request.managerId() == null
                ? fallbackAdmin
                : requireEnabledUser(request.managerId());
        Region region = resolveRegion(request.regionId());
        Customer customer = resolveCustomer(request);
        Project project = new Project();
        project.setName(request.name().trim());
        project.setCode(resolveCode(request.code(), projectRepository::existsByCode, "项目编码已存在"));
        project.setCustomer(customer);
        project.setCompanyName(customer.getCompanyName());
        project.setCompanyCode(customer.getCode());
        project.setCompanyLevel(customer.getLevel());
        project.setManager(manager);
        project.setRegion(region);
        project.setRegionLeader(region.getLeader());
        project.setOverview(request.overview() == null ? "" : request.overview().trim());
        project.setStatus(ProjectStatus.INITIATED);
        project.setCreatedBy(fallbackAdmin);
        project.setPublic(false);
        project.setSubmitSource("PUBLIC");
        addProjectFiles(project, files);
        return toPublicProject(projectRepository.save(project));
    }

    @Transactional(readOnly = true)
    public List<ProjectProgressView> listProjectProgress(Long projectId) {
        publicAccess.requireReadable(FormModule.PROJECT);
        return projectProgressService.listPublic(projectId);
    }

    @Transactional
    public ProjectProgressView submitProjectProgress(
            Long projectId,
            String content,
            Integer percent,
            List<MultipartFile> files,
            String ip
    ) {
        publicAccess.requireSubmit(FormModule.PROJECT);
        rateLimiter.check(ip, "PROJECT_PROGRESS");
        return projectProgressService.createPublic(projectId, content, percent, files);
    }

    @Transactional(readOnly = true)
    public List<PublicProjectOption> progressLookups() {
        PublicModuleFlags flags = publicAccess.flags(FormModule.PROJECT);
        if (!flags.publicAccess()) {
            throw ApiException.badRequest("该内容未开放公开办理");
        }
        return projectRepository.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
                .filter(Project::isPublic)
                .map(project -> new PublicProjectOption(project.getId(), project.getName(), project.getCode()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PublicPaymentLookupsView paymentLookups() {
        PublicModuleFlags flags = publicAccess.flags(FormModule.PAYMENT);
        if (!flags.publicAccess()) {
            throw ApiException.badRequest("该内容未开放公开办理");
        }
        if (!flags.submit()) {
            throw ApiException.badRequest("该表单未开放填报");
        }
        List<PublicProjectOption> projects = projectRepository.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
                .filter(Project::isPublic)
                .map(project -> new PublicProjectOption(project.getId(), project.getName(), project.getCode()))
                .toList();
        List<PublicSupplierView> suppliers = supplierRepository.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
                .filter(Supplier::isPublic)
                .map(this::toPublicSupplier)
                .toList();
        List<PublicCompanyAccountView> accounts = bankAccountRepository.findAll(Sort.by("id")).stream()
                .filter(BankAccount::isEnabled)
                .map(item -> new PublicCompanyAccountView(item.getId(), item.getName(), item.getBankName(), item.getAccountNo()))
                .toList();
        return new PublicPaymentLookupsView(projects, suppliers, accounts);
    }

    @Transactional(readOnly = true)
    public List<PublicPaymentView> listPayments(String keyword) {
        publicAccess.requireListable(FormModule.PAYMENT);
        PublicModuleFlags flags = publicAccess.flags(FormModule.PAYMENT);
        if (!flags.display() && !StringUtils.hasText(keyword)) {
            return List.of();
        }
        return paymentRepository.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
                .filter(Payment::isPublic)
                .filter(matchesPayment(keyword))
                .map(this::toPublicPayment)
                .toList();
    }

    @Transactional(readOnly = true)
    public PublicPaymentView getPayment(Long id) {
        publicAccess.requireReadable(FormModule.PAYMENT);
        Payment payment = paymentRepository.findById(id)
                .filter(Payment::isPublic)
                .orElseThrow(() -> ApiException.notFound("内容不存在"));
        return toPublicPayment(payment);
    }

    @Transactional
    public PublicPaymentView submitPayment(
            Long projectId,
            String amount,
            String reason,
            Long supplierId,
            Long accountId,
            Long bankAccountId,
            String payMethod,
            String expectedPayDate,
            List<MultipartFile> files,
            String ip
    ) {
        publicAccess.requireSubmit(FormModule.PAYMENT);
        rateLimiter.check(ip, "PAYMENT");
        Payment saved = paymentService.createPublic(
                projectId,
                amount,
                reason,
                supplierId,
                accountId,
                bankAccountId,
                payMethod,
                expectedPayDate,
                files
        );
        return toPublicPayment(saved);
    }

    @Transactional(readOnly = true)
    public OaLookupsView oaLookups(String kindSlug) {
        OaKind kind = OaKind.fromSlug(kindSlug);
        publicAccess.requireSubmit(kind.module());
        return oaService.lookups();
    }

    @Transactional(readOnly = true)
    public List<PublicOaView> listOa(String kindSlug, String keyword) {
        OaKind kind = OaKind.fromSlug(kindSlug);
        publicAccess.requireListable(kind.module());
        PublicModuleFlags flags = publicAccess.flags(kind.module());
        if (!flags.display() && !StringUtils.hasText(keyword)) {
            return List.of();
        }
        return oaService.listPublic(kind, keyword).stream().map(this::toPublicOa).toList();
    }

    @Transactional(readOnly = true)
    public PublicOaView getOa(String kindSlug, Long id) {
        OaKind kind = OaKind.fromSlug(kindSlug);
        publicAccess.requireReadable(kind.module());
        return toPublicOa(oaService.requirePublic(kind, id));
    }

    @Transactional
    public PublicOaView submitOa(
            String kindSlug,
            UpsertOaRequest request,
            List<MultipartFile> files,
            String ip
    ) {
        OaKind kind = OaKind.fromSlug(kindSlug);
        publicAccess.requireSubmit(kind.module());
        rateLimiter.check(ip, kind.module().name());
        return toPublicOa(oaService.createPublic(kind, request, files));
    }

    @Transactional(readOnly = true)
    public TechLookupsView techLookups(String kindSlug) {
        TechKind kind = TechKind.fromSlug(kindSlug);
        publicAccess.requireSubmit(kind.module());
        return techService.lookups();
    }

    @Transactional(readOnly = true)
    public TechCatalog.Kind techSchema(String kindSlug) {
        TechKind kind = TechKind.fromSlug(kindSlug);
        publicAccess.requireSubmit(kind.module());
        return TechCatalog.of(kind);
    }

    @Transactional(readOnly = true)
    public List<PublicTechView> listTech(String kindSlug, String keyword) {
        TechKind kind = TechKind.fromSlug(kindSlug);
        publicAccess.requireListable(kind.module());
        PublicModuleFlags flags = publicAccess.flags(kind.module());
        if (!flags.display() && !StringUtils.hasText(keyword)) {
            return List.of();
        }
        return techService.listPublic(kind, keyword).stream().map(item -> PublicTechView.from(techService.toView(item))).toList();
    }

    @Transactional(readOnly = true)
    public PublicTechView getTech(String kindSlug, Long id) {
        TechKind kind = TechKind.fromSlug(kindSlug);
        publicAccess.requireReadable(kind.module());
        return PublicTechView.from(techService.toView(techService.requirePublic(kind, id)));
    }

    @Transactional
    public PublicTechView submitTech(String kindSlug, Map<String, Object> extra, List<MultipartFile> files, String ip) {
        TechKind kind = TechKind.fromSlug(kindSlug);
        publicAccess.requireSubmit(kind.module());
        rateLimiter.check(ip, kind.module().name());
        return PublicTechView.from(techService.toView(techService.createPublic(kind, new UpsertTechRecord(false, extra), files)));
    }

    private PublicOaView toPublicOa(OaRequest item) {
        OaRequestView view = oaService.toView(item);
        return new PublicOaView(
                view.id(),
                view.code(),
                view.kind(),
                view.kindLabel(),
                view.applicantName(),
                view.gender(),
                view.genderLabel(),
                view.mobile(),
                view.departmentName(),
                view.position(),
                view.jobLevel(),
                view.targetDepartmentName(),
                view.targetPosition(),
                view.targetLevel(),
                view.expectedDate(),
                view.managerName(),
                view.handoverName(),
                view.probationMonths(),
                view.resignType(),
                view.resignTypeLabel(),
                view.reason(),
                view.loginUsername(),
                view.applied(),
                view.interview(),
                view.seal(),
                view.qualify(),
                view.trip(),
                view.loan(),
                view.leave(),
                view.files()
        );
    }

    private Customer resolveCustomer(PublicProjectSubmitRequest request) {
        if (request.customerId() != null) {
            return customerRepository.findById(request.customerId())
                    .filter(Customer::isPublic)
                    .orElseThrow(() -> ApiException.badRequest("请选择客户"));
        }
        if (!StringUtils.hasText(request.companyName())) {
            throw ApiException.badRequest("请选择客户");
        }
        return customerRepository.findFirstByCompanyName(request.companyName().trim())
                .orElseGet(() -> createPlaceholderCustomer(
                        request.companyName().trim(),
                        request.contactName(),
                        request.contactPhone()
                ));
    }

    private Region resolveRegion(Long regionId) {
        if (regionId != null) {
            return regionRepository.findById(regionId)
                    .orElseThrow(() -> ApiException.badRequest("请选择所属区域"));
        }
        return regionRepository.findAll(Sort.by("id")).stream()
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest("暂无法提交，请联系管理员"));
    }

    private SysUser requireEnabledUser(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ENABLED)
                .orElseThrow(() -> ApiException.badRequest("请选择项目经理"));
    }

    private Customer createPlaceholderCustomer(String companyName, String contactName, String contactPhone) {
        Customer customer = new Customer();
        customer.setCompanyName(companyName);
        customer.setShortName(companyName.length() > 50 ? companyName.substring(0, 50) : companyName);
        customer.setCode(nextCode(customerRepository::existsByCode));
        customer.setLevel("一般重要");
        customer.setPublic(false);
        customer.setSubmitSource("PUBLIC");
        if (StringUtils.hasText(contactName)) {
            CustomerContact contact = new CustomerContact();
            contact.setCustomer(customer);
            contact.setName(contactName.trim());
            contact.setPhone(blankToNull(contactPhone));
            contact.setPrimaryContact(true);
            contact.setSortOrder(0);
            customer.getContacts().add(contact);
        }
        return customerRepository.save(customer);
    }

    private void addCustomerContacts(Customer customer, PublicCustomerSubmitRequest request) {
        List<ContactRequest> contacts = request.contacts();
        if (contacts != null && !contacts.isEmpty()) {
            boolean primaryAssigned = false;
            int order = 0;
            for (ContactRequest item : contacts) {
                boolean primary = Boolean.TRUE.equals(item.primaryContact()) && !primaryAssigned;
                if (primary) {
                    primaryAssigned = true;
                }
                CustomerContact contact = new CustomerContact();
                contact.setCustomer(customer);
                contact.setName(item.name().trim());
                contact.setTitle(blankToNull(item.title()));
                contact.setPhone(blankToNull(item.phone()));
                contact.setEmail(blankToNull(item.email()));
                contact.setPrimaryContact(primary);
                contact.setSortOrder(order++);
                customer.getContacts().add(contact);
            }
            if (!primaryAssigned && !customer.getContacts().isEmpty()) {
                customer.getContacts().get(0).setPrimaryContact(true);
            }
            return;
        }
        if (StringUtils.hasText(request.contactName())) {
            CustomerContact contact = new CustomerContact();
            contact.setCustomer(customer);
            contact.setName(request.contactName().trim());
            contact.setPhone(blankToNull(request.contactPhone()));
            contact.setPrimaryContact(true);
            contact.setSortOrder(0);
            customer.getContacts().add(contact);
        }
    }

    private void addCustomerBanks(Customer customer, List<BankAccountRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        boolean primaryAssigned = false;
        int order = 0;
        for (BankAccountRequest item : requests) {
            boolean primary = Boolean.TRUE.equals(item.primaryAccount()) && !primaryAssigned;
            if (primary) {
                primaryAssigned = true;
            }
            CustomerBankAccount account = new CustomerBankAccount();
            account.setCustomer(customer);
            account.setAccountName(item.accountName().trim());
            account.setBankName(item.bankName().trim());
            account.setAccountNo(item.accountNo().trim());
            account.setBranch(blankToNull(item.branch()));
            account.setPrimaryAccount(primary);
            account.setSortOrder(order++);
            customer.getBankAccounts().add(account);
        }
        if (!primaryAssigned && !customer.getBankAccounts().isEmpty()) {
            customer.getBankAccounts().get(0).setPrimaryAccount(true);
        }
    }

    private void addSupplierContacts(Supplier supplier, PublicSupplierSubmitRequest request) {
        List<com.projectmgmt.supplier.dto.ContactRequest> contacts = request.contacts();
        if (contacts != null && !contacts.isEmpty()) {
            boolean primaryAssigned = false;
            int order = 0;
            for (com.projectmgmt.supplier.dto.ContactRequest item : contacts) {
                boolean primary = Boolean.TRUE.equals(item.primaryContact()) && !primaryAssigned;
                if (primary) {
                    primaryAssigned = true;
                }
                SupplierContact contact = new SupplierContact();
                contact.setSupplier(supplier);
                contact.setName(item.name().trim());
                contact.setTitle(blankToNull(item.title()));
                contact.setPhone(blankToNull(item.phone()));
                contact.setEmail(blankToNull(item.email()));
                contact.setPrimaryContact(primary);
                contact.setSortOrder(order++);
                supplier.getContacts().add(contact);
            }
            if (!primaryAssigned && !supplier.getContacts().isEmpty()) {
                supplier.getContacts().get(0).setPrimaryContact(true);
            }
            return;
        }
        if (StringUtils.hasText(request.contactName())) {
            SupplierContact contact = new SupplierContact();
            contact.setSupplier(supplier);
            contact.setName(request.contactName().trim());
            contact.setPhone(blankToNull(request.contactPhone()));
            contact.setPrimaryContact(true);
            contact.setSortOrder(0);
            supplier.getContacts().add(contact);
        }
    }

    private void addSupplierBanks(Supplier supplier, List<com.projectmgmt.supplier.dto.BankAccountRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        boolean primaryAssigned = false;
        int order = 0;
        for (com.projectmgmt.supplier.dto.BankAccountRequest item : requests) {
            boolean primary = Boolean.TRUE.equals(item.primaryAccount()) && !primaryAssigned;
            if (primary) {
                primaryAssigned = true;
            }
            SupplierBankAccount account = new SupplierBankAccount();
            account.setSupplier(supplier);
            account.setAccountName(item.accountName().trim());
            account.setBankName(item.bankName().trim());
            account.setAccountNo(item.accountNo().trim());
            account.setBranch(blankToNull(item.branch()));
            account.setPrimaryAccount(primary);
            account.setSortOrder(order++);
            supplier.getBankAccounts().add(account);
        }
        if (!primaryAssigned && !supplier.getBankAccounts().isEmpty()) {
            supplier.getBankAccounts().get(0).setPrimaryAccount(true);
        }
    }

    private void addProjectFiles(Project project, List<MultipartFile> files) {
        if (files == null) {
            return;
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            FileStorageService.StoredFile stored = fileStorageService.saveProjectFile(file);
            ProjectAttachment attachment = new ProjectAttachment();
            attachment.setProject(project);
            attachment.setOriginalName(stored.originalName());
            attachment.setUrl(stored.url());
            attachment.setSize(stored.size());
            attachment.setContentType(stored.contentType());
            project.getAttachments().add(attachment);
        }
    }

    private String resolveCode(String requested, Predicate<String> exists, String existsMessage) {
        if (StringUtils.hasText(requested)) {
            String code = requested.trim();
            if (exists.test(code)) {
                throw ApiException.badRequest(existsMessage);
            }
            return code;
        }
        return nextCode(exists);
    }

    private String nextCode(Predicate<String> exists) {
        String prefix = "PUB" + LocalDate.now().format(DAY);
        for (int i = 1; i < 10000; i++) {
            String code = prefix + String.format(Locale.ROOT, "%04d", i);
            if (!exists.test(code)) {
                return code;
            }
        }
        throw ApiException.badRequest("编码生成失败，请稍后再试");
    }

    private Predicate<Customer> matchesCustomer(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return item -> true;
        }
        String needle = keyword.trim().toLowerCase(Locale.ROOT);
        return item -> contains(item.getShortName(), needle)
                || contains(item.getCompanyName(), needle)
                || contains(item.getCode(), needle);
    }

    private Predicate<Supplier> matchesSupplier(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return item -> true;
        }
        String needle = keyword.trim().toLowerCase(Locale.ROOT);
        return item -> contains(item.getShortName(), needle)
                || contains(item.getCompanyName(), needle)
                || contains(item.getCode(), needle);
    }

    private Predicate<Project> matchesProject(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return item -> true;
        }
        String needle = keyword.trim().toLowerCase(Locale.ROOT);
        return item -> contains(item.getName(), needle)
                || contains(item.getCode(), needle)
                || contains(item.getCompanyName(), needle);
    }

    private Predicate<Payment> matchesPayment(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return item -> true;
        }
        String needle = keyword.trim().toLowerCase(Locale.ROOT);
        return item -> contains(item.getCode(), needle)
                || contains(item.getReason(), needle);
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String displayName(SysUser user) {
        return user == null ? null : user.getDisplayName();
    }

    private PublicCustomerView toPublicCustomer(Customer customer) {
        return new PublicCustomerView(
                customer.getId(),
                customer.getShortName(),
                customer.getCompanyName(),
                customer.getCode(),
                customer.getLevel(),
                customer.getContacts() == null ? List.of() : customer.getContacts().stream()
                        .map(item -> new PublicContactView(
                                item.getName(),
                                item.getTitle(),
                                item.getPhone(),
                                item.getEmail(),
                                item.isPrimaryContact()
                        ))
                        .toList(),
                customer.getBankAccounts() == null ? List.of() : customer.getBankAccounts().stream()
                        .map(item -> new PublicBankAccountView(
                                item.getId(),
                                item.getAccountName(),
                                item.getBankName(),
                                item.getAccountNo(),
                                item.getBranch(),
                                item.isPrimaryAccount()
                        ))
                        .toList()
        );
    }

    private PublicSupplierView toPublicSupplier(Supplier supplier) {
        return new PublicSupplierView(
                supplier.getId(),
                supplier.getShortName(),
                supplier.getCompanyName(),
                supplier.getCode(),
                supplier.getCategory(),
                supplier.getContacts() == null ? List.of() : supplier.getContacts().stream()
                        .map(item -> new PublicContactView(
                                item.getName(),
                                item.getTitle(),
                                item.getPhone(),
                                item.getEmail(),
                                item.isPrimaryContact()
                        ))
                        .toList(),
                supplier.getBankAccounts() == null ? List.of() : supplier.getBankAccounts().stream()
                        .map(item -> new PublicBankAccountView(
                                item.getId(),
                                item.getAccountName(),
                                item.getBankName(),
                                item.getAccountNo(),
                                item.getBranch(),
                                item.isPrimaryAccount()
                        ))
                        .toList()
        );
    }

    private PublicProjectView toPublicProject(Project project) {
        Customer customer = project.getCustomer();
        Region region = project.getRegion();
        return new PublicProjectView(
                project.getId(),
                project.getName(),
                project.getCode(),
                customer == null ? null : customer.getId(),
                customer == null ? null : customer.getShortName(),
                project.getCompanyName(),
                project.getCompanyCode(),
                project.getCompanyLevel(),
                project.getManager() == null ? null : project.getManager().getId(),
                displayName(project.getManager()),
                region == null ? null : region.getId(),
                region == null ? null : region.getName(),
                displayName(project.getRegionLeader() != null ? project.getRegionLeader() : (region == null ? null : region.getLeader())),
                project.getStatus() == ProjectStatus.INITIATED ? "已立项" : project.getStatus().name(),
                project.getOverview(),
                project.getProgressPercent(),
                project.getAttachments() == null ? List.of() : project.getAttachments().stream()
                        .map(item -> new PublicAttachmentView(item.getId(), item.getOriginalName(), item.getUrl()))
                        .toList()
        );
    }

    private PublicPaymentView toPublicPayment(Payment payment) {
        payment.getFiles().size();
        if (payment.getAccount() != null) {
            payment.getAccount().getName();
        }
        return new PublicPaymentView(
                payment.getId(),
                payment.getCode(),
                payment.getProject() == null ? null : payment.getProject().getId(),
                payment.getProject() == null ? null : payment.getProject().getName(),
                payment.getProject() == null ? null : payment.getProject().getCode(),
                payment.getAmount(),
                payment.getReason(),
                payment.getSupplier().getId(),
                payment.getSupplier().getShortName(),
                payment.getSupplier().getCompanyName(),
                payment.getPayeeName(),
                payment.getBankName(),
                payment.getAccountNo(),
                payment.getBranch(),
                payment.getPayMethod().name(),
                payment.getPayMethod().label(),
                payment.getExpectedPayDate(),
                payment.getAccount() == null ? null : payment.getAccount().getId(),
                payment.getAccount() == null ? null : payment.getAccount().getName(),
                payment.getFiles() == null ? List.of() : payment.getFiles().stream()
                        .map(item -> new PublicAttachmentView(item.getId(), item.getOriginalName(), item.getUrl()))
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> export(String kind, List<Long> ids, String keyword) {
        String type = kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "project" -> {
                publicAccess.requireExport(FormModule.PROJECT);
                var rows = pick(listProjects(keyword), ids, PublicProjectView::id);
                yield ExcelSheets.workbook("项目列表", "项目列表",
                        new String[] {"项目名称", "项目编码", "公司名称", "状态"},
                        rows.stream().map(row -> new String[] {
                                ExcelSheets.text(row.name()),
                                ExcelSheets.text(row.code()),
                                ExcelSheets.text(row.companyName()),
                                ExcelSheets.text(row.status())
                        }).toList());
            }
            case "customer" -> {
                publicAccess.requireExport(FormModule.CUSTOMER);
                var rows = pick(listCustomers(keyword), ids, PublicCustomerView::id);
                yield ExcelSheets.workbook("客户列表", "客户列表",
                        new String[] {"客户简称", "公司名称", "客户编码", "等级"},
                        rows.stream().map(row -> new String[] {
                                ExcelSheets.text(row.shortName()),
                                ExcelSheets.text(row.companyName()),
                                ExcelSheets.text(row.code()),
                                ExcelSheets.text(row.level())
                        }).toList());
            }
            case "supplier" -> {
                publicAccess.requireExport(FormModule.SUPPLIER);
                var rows = pick(listSuppliers(keyword), ids, PublicSupplierView::id);
                yield ExcelSheets.workbook("供应商列表", "供应商列表",
                        new String[] {"供应商简称", "公司名称", "供应商编码", "类型"},
                        rows.stream().map(row -> new String[] {
                                ExcelSheets.text(row.shortName()),
                                ExcelSheets.text(row.companyName()),
                                ExcelSheets.text(row.code()),
                                ExcelSheets.text(row.category())
                        }).toList());
            }
            case "payment" -> {
                publicAccess.requireExport(FormModule.PAYMENT);
                var rows = pick(listPayments(keyword), ids, PublicPaymentView::id);
                yield ExcelSheets.workbook("付款申请", "付款申请",
                        new String[] {"编码", "项目", "供应商", "金额", "期望付款日"},
                        rows.stream().map(row -> new String[] {
                                ExcelSheets.text(row.code()),
                                ExcelSheets.text(row.projectName()),
                                ExcelSheets.text(row.supplierShortName()),
                                ExcelSheets.text(row.amount()),
                                ExcelSheets.text(row.expectedPayDate())
                        }).toList());
            }
            case "oa-onboard", "oa-transfer", "oa-promotion", "oa-resign", "oa-salary", "oa-interview", "oa-seal", "oa-qualify", "oa-trip", "oa-leave", "oa-loan", "oa-loan-pay", "oa-loan-repay" -> {
                String slug = type.substring("oa-".length());
                OaKind oaKind = OaKind.fromSlug(slug);
                publicAccess.requireExport(oaKind.module());
                var rows = pick(listOa(oaKind.slug(), keyword), ids, PublicOaView::id);
                yield ExcelSheets.workbook(oaKind.label(), oaKind.label(),
                        new String[] {"编码", "申请人", "部门", "岗位", "日期"},
                        rows.stream().map(row -> new String[] {
                                ExcelSheets.text(row.code()),
                                ExcelSheets.text(row.applicantName()),
                                ExcelSheets.text(row.departmentName()),
                                ExcelSheets.text(row.position()),
                                ExcelSheets.text(row.expectedDate())
                        }).toList());
            }
            default -> {
                if (type.startsWith("tech-")) {
                    TechKind techKind = TechKind.fromSlug(type.substring("tech-".length()));
                    publicAccess.requireExport(techKind.module());
                    var rows = pick(listTech(techKind.slug(), keyword), ids, PublicTechView::id);
                    yield ExcelSheets.workbook(techKind.label(), techKind.label(),
                            new String[] {"编码", "标题"},
                            rows.stream().map(row -> new String[] {
                                    ExcelSheets.text(row.code()),
                                    ExcelSheets.text(row.title())
                            }).toList());
                }
                throw ApiException.badRequest("不支持的导出类型");
            }
        };
    }

    private static <T> List<T> pick(List<T> rows, List<Long> ids, ToLongFunction<T> idFn) {
        if (ids == null || ids.isEmpty()) {
            return rows;
        }
        Set<Long> wanted = new LinkedHashSet<>(ids);
        return rows.stream().filter(row -> wanted.contains(idFn.applyAsLong(row))).toList();
    }
}
