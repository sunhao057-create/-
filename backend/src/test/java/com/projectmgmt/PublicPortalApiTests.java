package com.projectmgmt;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.projectmgmt.customer.CustomerRepository;
import com.projectmgmt.portal.SubmitRateLimiter;
import com.projectmgmt.region.RegionRepository;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.role.RoleRepository;
import com.projectmgmt.security.AuthUser;
import com.projectmgmt.supplier.SupplierRepository;
import com.projectmgmt.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PublicPortalApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    CustomerRepository customerRepository;

    @Autowired
    SupplierRepository supplierRepository;

    @Autowired
    RegionRepository regionRepository;

    @Autowired
    SubmitRateLimiter submitRateLimiter;

    @BeforeEach
    void resetLimiter() {
        submitRateLimiter.reset();
    }

    @Test
    void publicGuestRoleIsBuiltinAndCannotBeAssignedOrDeleted() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        var guest = roleRepository.findByCode(PermissionService.PUBLIC_GUEST_CODE).orElseThrow();

        mockMvc.perform(get("/api/roles").with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].name").exists())
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].builtin")
                        .value(org.hamcrest.Matchers.hasItem(true)));

        mockMvc.perform(delete("/api/roles/" + guest.getId()).with(user(new AuthUser(admin))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("内置角色不能删除"));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"guesttry","displayName":"试","role":"PUBLIC_GUEST","password":"Guest1234!"}
                                """)
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("不能把账号设为公开访客"));
    }

    @Test
    void guestPublicFlagsPersistAndOtherRolesIgnoreThem() throws Exception {
        saveGuest(perm("CUSTOMER", true, true, true, false, true));

        mockMvc.perform(get("/api/roles/" + guestId()).with(user(new AuthUser(admin()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions[?(@.module=='CUSTOMER')].publicAccess")
                        .value(hasItem(true)))
                .andExpect(jsonPath("$.data.permissions[?(@.module=='CUSTOMER')].publicDisplay")
                        .value(hasItem(true)))
                .andExpect(jsonPath("$.data.permissions[?(@.module=='CUSTOMER')].publicSubmit")
                        .value(hasItem(true)))
                .andExpect(jsonPath("$.data.permissions[?(@.module=='CUSTOMER')].publicQuery")
                        .value(hasItem(false)))
                .andExpect(jsonPath("$.data.permissions[?(@.module=='CUSTOMER')].publicExport")
                        .value(hasItem(true)));

        mockMvc.perform(post("/api/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"忽略公开开关","permissions":[
                                  {"module":"CUSTOMER","view":true,"create":true,"edit":true,"publicAccess":true,"publicDisplay":true,
                                   "publicExport":true,"delete":false,"importExport":false}
                                ]}
                                """)
                        .with(user(new AuthUser(admin()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions[?(@.module=='CUSTOMER')].publicAccess")
                        .value(hasItem(false)))
                .andExpect(jsonPath("$.data.permissions[?(@.module=='CUSTOMER')].publicDisplay")
                        .value(hasItem(false)))
                .andExpect(jsonPath("$.data.permissions[?(@.module=='CUSTOMER')].publicExport")
                        .value(hasItem(false)));
    }

    @Test
    void anonymousConfigReflectsGuestFlags() throws Exception {
        saveGuest(perm("CUSTOMER", true, true, false, true, true));

        mockMvc.perform(get("/api/public/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customer.publicAccess").value(true))
                .andExpect(jsonPath("$.data.customer.display").value(true))
                .andExpect(jsonPath("$.data.customer.submit").value(false))
                .andExpect(jsonPath("$.data.customer.query").value(true))
                .andExpect(jsonPath("$.data.customer.export").value(true))
                .andExpect(jsonPath("$.data.project.publicAccess").value(false))
                .andExpect(jsonPath("$.data.supplier.publicAccess").value(false))
                .andExpect(jsonPath("$.data.payment.publicAccess").value(false))
                .andExpect(jsonPath("$.data.modules[?(@.module=='PAYMENT')].path")
                        .value(hasItem("/public/payments")))
                .andExpect(jsonPath("$.data.modules[?(@.module=='PAYMENT')].moduleLabel")
                        .value(hasItem("付款申请")));

        saveGuest(perm("PAYMENT", true, true, false, true, true));
        mockMvc.perform(get("/api/public/config"))
                .andExpect(jsonPath("$.data.payment.publicAccess").value(true))
                .andExpect(jsonPath("$.data.payment.display").value(true))
                .andExpect(jsonPath("$.data.payment.submit").value(false))
                .andExpect(jsonPath("$.data.payment.query").value(true))
                .andExpect(jsonPath("$.data.payment.export").value(true))
                .andExpect(jsonPath("$.data.modules[?(@.module=='PAYMENT')].flags.publicAccess")
                        .value(hasItem(true)));
    }

    @Test
    void publicPaymentClosedAndSubmitFlow() throws Exception {
        saveGuest("[]");
        mockMvc.perform(get("/api/public/payments"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该内容未开放公开办理"));
        mockMvc.perform(get("/api/public/payments/lookups"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该内容未开放公开办理"));
        mockMvc.perform(multipart("/api/public/payments")
                        .param("projectId", "1")
                        .param("amount", "10")
                        .param("reason", "x")
                        .param("supplierId", "1")
                        .param("accountId", "1")
                        .param("bankAccountId", "1")
                        .param("payMethod", "WIRE")
                        .param("expectedPayDate", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该表单未开放填报"));

        saveGuest(perm("PAYMENT", true, true, true, true, false));
        var admin = admin();
        var manager = userRepository.findByUsername("liuxiaolin").orElseThrow();
        var customer = customerRepository.findByCode("133").orElseThrow();
        var region = regionRepository.findByName("华东地区").orElseThrow();
        var supplier = supplierRepository.findByCode("S01").orElseThrow();
        supplier.setPublic(true);
        supplierRepository.save(supplier);
        Integer bankId = JsonPath.read(
                mockMvc.perform(get("/api/suppliers/" + supplier.getId()).with(user(new AuthUser(admin))))
                        .andReturn().getResponse().getContentAsString(),
                "$.data.bankAccounts[0].id");
        Integer accountId = JsonPath.read(mockMvc.perform(post("/api/finance/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"公开付款户","bankName":"中国银行","accountName":"智襄网","accountNo":"PUBPAY%s","enabled":true}
                                """.formatted(System.nanoTime()))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");

        Integer privateProjectId = JsonPath.read(
                mockMvc.perform(multipart("/api/projects")
                                .param("name", "未公开项目")
                                .param("code", "PRIV-" + System.currentTimeMillis())
                                .param("customerId", String.valueOf(customer.getId()))
                                .param("managerId", String.valueOf(manager.getId()))
                                .param("regionId", String.valueOf(region.getId()))
                                .param("isPublic", "false")
                                .with(user(new AuthUser(admin))))
                        .andReturn().getResponse().getContentAsString(),
                "$.data.id");
        Integer publicProjectId = JsonPath.read(
                mockMvc.perform(multipart("/api/projects")
                                .param("name", "已公开项目")
                                .param("code", "PUBP-" + System.currentTimeMillis())
                                .param("customerId", String.valueOf(customer.getId()))
                                .param("managerId", String.valueOf(manager.getId()))
                                .param("regionId", String.valueOf(region.getId()))
                                .param("isPublic", "true")
                                .with(user(new AuthUser(admin))))
                        .andReturn().getResponse().getContentAsString(),
                "$.data.id");

        mockMvc.perform(get("/api/public/payments/lookups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projects[?(@.id==" + publicProjectId + ")]").exists())
                .andExpect(jsonPath("$.data.projects[?(@.id==" + privateProjectId + ")]").doesNotExist())
                .andExpect(jsonPath("$.data.suppliers[?(@.id==" + supplier.getId() + ")]").exists());

        mockMvc.perform(multipart("/api/public/payments")
                        .param("projectId", String.valueOf(privateProjectId))
                        .param("amount", "50")
                        .param("reason", "未公开项目")
                        .param("supplierId", String.valueOf(supplier.getId()))
                        .param("accountId", String.valueOf(accountId))
                        .param("bankAccountId", String.valueOf(bankId))
                        .param("payMethod", "WIRE")
                        .param("expectedPayDate", "2026-09-01"))
                .andExpect(status().isBadRequest());

        supplier.setPublic(false);
        supplierRepository.save(supplier);
        mockMvc.perform(multipart("/api/public/payments")
                        .param("projectId", String.valueOf(publicProjectId))
                        .param("amount", "50")
                        .param("reason", "未公开供应商")
                        .param("supplierId", String.valueOf(supplier.getId()))
                        .param("accountId", String.valueOf(accountId))
                        .param("bankAccountId", String.valueOf(bankId))
                        .param("payMethod", "WIRE")
                        .param("expectedPayDate", "2026-09-01"))
                .andExpect(status().isBadRequest());
        supplier.setPublic(true);
        supplierRepository.save(supplier);

        String submitted = mockMvc.perform(multipart("/api/public/payments")
                        .param("projectId", String.valueOf(publicProjectId))
                        .param("amount", "80")
                        .param("reason", "访客付款事由")
                        .param("supplierId", String.valueOf(supplier.getId()))
                        .param("accountId", String.valueOf(accountId))
                        .param("bankAccountId", String.valueOf(bankId))
                        .param("payMethod", "WIRE")
                        .param("expectedPayDate", "2026-09-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").exists())
                .andExpect(jsonPath("$.data.budgetDeducted").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer paymentId = JsonPath.read(submitted, "$.data.id");
        String code = JsonPath.read(submitted, "$.data.code");

        mockMvc.perform(get("/api/public/payments"))
                .andExpect(jsonPath("$.data[*].code").value(not(hasItem(code))));
        mockMvc.perform(get("/api/public/payments/" + paymentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("内容不存在"));

        mockMvc.perform(get("/api/payments/" + paymentId).with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submitSource").value("PUBLIC"))
                .andExpect(jsonPath("$.data.isPublic").value(false))
                .andExpect(jsonPath("$.data.budgetDeducted").value(false))
                .andExpect(jsonPath("$.data.workflowStatus").exists());

        mockMvc.perform(put("/api/payments/" + paymentId)
                        .param("projectId", String.valueOf(publicProjectId))
                        .param("amount", "80")
                        .param("reason", "访客付款事由")
                        .param("supplierId", String.valueOf(supplier.getId()))
                        .param("accountId", String.valueOf(accountId))
                        .param("bankAccountId", String.valueOf(bankId))
                        .param("payMethod", "WIRE")
                        .param("expectedPayDate", "2026-09-01")
                        .param("isPublic", "true")
                        .with(user(new AuthUser(admin))))
                .andExpect(jsonPath("$.data.isPublic").value(true));

        mockMvc.perform(get("/api/public/payments"))
                .andExpect(jsonPath("$.data[?(@.code=='" + code + "')].reason").value(hasItem("访客付款事由")));
        mockMvc.perform(get("/api/public/payments/" + paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountNo").exists())
                .andExpect(jsonPath("$.data.availableAmount").doesNotExist());

        mockMvc.perform(multipart("/api/public/payments")
                        .param("amount", "30")
                        .param("reason", "不关联项目")
                        .param("supplierId", String.valueOf(supplier.getId()))
                        .param("accountId", String.valueOf(accountId))
                        .param("bankAccountId", String.valueOf(bankId))
                        .param("payMethod", "WIRE")
                        .param("expectedPayDate", "2026-09-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void publicPaymentQueryOnlyNeedsKeyword() throws Exception {
        saveGuest(perm("PAYMENT", true, false, false, true, false));
        mockMvc.perform(get("/api/public/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void publicCustomerListHidesUnpublishedAndSubmitStaysPrivate() throws Exception {
        saveGuest(perm("CUSTOMER", true, true, true, true));
        var seed = customerRepository.findByCode("133").orElseThrow();
        seed.setPublic(true);
        customerRepository.save(seed);

        mockMvc.perform(get("/api/public/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code=='133')].companyName")
                        .value(hasItem("苏州冠礼科技股份有限公司")))
                .andExpect(jsonPath("$.data[?(@.code=='133')].contacts").exists());

        String suffix = String.valueOf(System.currentTimeMillis());
        String body = mockMvc.perform(post("/api/public/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shortName":"公开填报%s","companyName":"公开填报公司%s","contactName":"访客","contactPhone":"13800000000"}
                                """.formatted(suffix, suffix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer id = JsonPath.read(body, "$.data.id");
        String code = JsonPath.read(body, "$.data.code");

        mockMvc.perform(get("/api/public/customers"))
                .andExpect(jsonPath("$.data[*].code").value(not(hasItem(code))));
        mockMvc.perform(get("/api/public/customers").param("keyword", code))
                .andExpect(jsonPath("$.data[*].code").value(not(hasItem(code))));
        mockMvc.perform(get("/api/public/customers/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("内容不存在"));
    }

    @Test
    void publicSupplierListHidesUnpublishedAndSubmitStaysPrivate() throws Exception {
        saveGuest(perm("SUPPLIER", true, true, true, true));
        var seed = supplierRepository.findByCode("S01").orElseThrow();
        seed.setPublic(true);
        supplierRepository.save(seed);

        mockMvc.perform(get("/api/public/suppliers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code=='S01')].shortName").value(hasItem("苏州精工")));

        String suffix = String.valueOf(System.currentTimeMillis());
        String body = mockMvc.perform(post("/api/public/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shortName":"公开供%s","companyName":"公开供应商%s","contactName":"访客","contactPhone":"13900000000"}
                                """.formatted(suffix, suffix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category").value("原材料"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String code = JsonPath.read(body, "$.data.code");
        mockMvc.perform(get("/api/public/suppliers"))
                .andExpect(jsonPath("$.data[*].code").value(not(hasItem(code))));
    }

    @Test
    void publicProjectListHidesUnpublishedAndOmitsStaff() throws Exception {
        saveGuest(perm("PROJECT", true, true, true, true));
        var admin = admin();
        var manager = userRepository.findByUsername("liuxiaolin").orElseThrow();
        var customer = customerRepository.findByCode("133").orElseThrow();
        var region = regionRepository.findByName("华东地区").orElseThrow();
        String publicCode = "PUB-ADM-" + System.currentTimeMillis();

        String created = mockMvc.perform(multipart("/api/projects")
                        .param("name", "公开项目样例")
                        .param("code", publicCode)
                        .param("customerId", String.valueOf(customer.getId()))
                        .param("managerId", String.valueOf(manager.getId()))
                        .param("regionId", String.valueOf(region.getId()))
                        .param("overview", "概况")
                        .param("isPublic", "true")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPublic").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer projectId = JsonPath.read(created, "$.data.id");

        mockMvc.perform(get("/api/public/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code=='" + publicCode + "')].name").value(hasItem("公开项目样例")))
                .andExpect(jsonPath("$.data[?(@.code=='" + publicCode + "')].manager").doesNotExist());

        mockMvc.perform(get("/api/public/projects/" + projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.manager").doesNotExist())
                .andExpect(jsonPath("$.data.managerName").exists())
                .andExpect(jsonPath("$.data.regionName").exists());

        String suffix = String.valueOf(System.currentTimeMillis());
        String body = mockMvc.perform(post("/api/public/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"访客项目%s","companyName":"访客公司%s","overview":"公开填报","contactName":"访客","contactPhone":"13700000000"}
                                """.formatted(suffix, suffix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.manager").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String submittedCode = JsonPath.read(body, "$.data.code");
        mockMvc.perform(get("/api/public/projects"))
                .andExpect(jsonPath("$.data[*].code").value(not(hasItem(submittedCode))));
    }

    @Test
    void publicProgressUpdatesPublicProject() throws Exception {
        saveGuest(perm("PROJECT", true, true, true, true));
        var admin = admin();
        var manager = userRepository.findByUsername("liuxiaolin").orElseThrow();
        var customer = customerRepository.findByCode("133").orElseThrow();
        var region = regionRepository.findByName("华东地区").orElseThrow();
        String publicCode = "PUB-PG-" + System.currentTimeMillis();
        String created = mockMvc.perform(multipart("/api/projects")
                        .param("name", "公开进展项目")
                        .param("code", publicCode)
                        .param("customerId", String.valueOf(customer.getId()))
                        .param("managerId", String.valueOf(manager.getId()))
                        .param("regionId", String.valueOf(region.getId()))
                        .param("overview", "原概况")
                        .param("isPublic", "true")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer projectId = JsonPath.read(created, "$.data.id");

        mockMvc.perform(get("/api/public/config"))
                .andExpect(jsonPath("$.data.modules[?(@.module=='PROJECT_PROGRESS')].moduleLabel")
                        .value(hasItem("项目情况")));

        mockMvc.perform(multipart("/api/public/projects/" + projectId + "/progress")
                        .param("content", "公开进展已更新")
                        .param("percent", "45"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("公开进展已更新"))
                .andExpect(jsonPath("$.data.submitSourceLabel").value("公开"));

        mockMvc.perform(get("/api/public/projects/" + projectId))
                .andExpect(jsonPath("$.data.overview").value("公开进展已更新"))
                .andExpect(jsonPath("$.data.progressPercent").value(45));
        mockMvc.perform(get("/api/public/projects/" + projectId + "/progress"))
                .andExpect(jsonPath("$.data[0].content").value("公开进展已更新"));
    }

    @Test
    void queryOnlyReturnsEmptyWithoutKeyword() throws Exception {
        saveGuest(perm("CUSTOMER", true, false, false, true));
        var seed = customerRepository.findByCode("133").orElseThrow();
        seed.setPublic(true);
        customerRepository.save(seed);

        mockMvc.perform(get("/api/public/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/public/customers").param("keyword", "苏州冠礼"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code=='133')].shortName").value(hasItem("苏州冠礼")));
    }

    @Test
    void closedModuleReturnsChineseErrors() throws Exception {
        saveGuest("[]");
        mockMvc.perform(get("/api/public/customers"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该内容未开放公开办理"));
        mockMvc.perform(post("/api/public/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shortName":"甲","companyName":"甲公司","contactName":"乙","contactPhone":"1"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该表单未开放填报"));
    }

    @Test
    void adminCanMarkCustomerPublicAndFilterLists() throws Exception {
        saveGuest(perm("CUSTOMER", true, true, false, false));
        String code = "PUB-FLT-" + System.currentTimeMillis();
        String created = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shortName":"筛选客户","companyName":"筛选客户公司","code":"%s","level":"一般重要","isPublic":true,"contacts":[],"bankAccounts":[]}
                                """.formatted(code))
                        .with(user(new AuthUser(admin()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPublic").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer id = JsonPath.read(created, "$.data.id");

        mockMvc.perform(get("/api/public/customers"))
                .andExpect(jsonPath("$.data[*].code").value(hasItem(code)));
        mockMvc.perform(get("/api/customers").param("publicFilter", "YES").with(user(new AuthUser(admin()))))
                .andExpect(jsonPath("$.data[*].code").value(hasItem(code)));
        mockMvc.perform(get("/api/customers").param("publicFilter", "NO").with(user(new AuthUser(admin()))))
                .andExpect(jsonPath("$.data[*].code").value(not(hasItem(code))));

        mockMvc.perform(put("/api/customers/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shortName":"筛选客户","companyName":"筛选客户公司","code":"%s","level":"一般重要","isPublic":false,"contacts":[],"bankAccounts":[]}
                                """.formatted(code))
                        .with(user(new AuthUser(admin()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPublic").value(false));
        mockMvc.perform(get("/api/public/customers"))
                .andExpect(jsonPath("$.data[*].code").value(not(hasItem(code))));
    }

    @Test
    void publicSubmitRateLimitedPerIp() throws Exception {
        saveGuest(perm("CUSTOMER", true, false, true, false));
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/public/customers")
                            .header("X-Forwarded-For", "203.0.113.8")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"shortName":"限流%s","companyName":"限流公司%s","contactName":"访客","contactPhone":"13600000000"}
                                    """.formatted(i, System.nanoTime())))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/public/customers")
                        .header("X-Forwarded-For", "203.0.113.8")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shortName":"限流超","companyName":"限流超公司","contactName":"访客","contactPhone":"13600000000"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("提交过于频繁，请稍后再试"));
    }

    private com.projectmgmt.user.SysUser admin() {
        return userRepository.findByUsername("admin").orElseThrow();
    }

    private Long guestId() {
        return roleRepository.findByCode(PermissionService.PUBLIC_GUEST_CODE).orElseThrow().getId();
    }

    private void saveGuest(String permissionsJson) throws Exception {
        mockMvc.perform(put("/api/roles/" + guestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"公开访客\",\"description\":\"未登录访客的公开办理权限\",\"permissions\":"
                                + permissionsJson + "}")
                        .with(user(new AuthUser(admin()))))
                .andExpect(status().isOk());
    }

    private static String perm(String module, boolean access, boolean display, boolean submit, boolean query) {
        return perm(module, access, display, submit, query, false);
    }

    private static String perm(
            String module,
            boolean access,
            boolean display,
            boolean submit,
            boolean query,
            boolean export
    ) {
        return "[{\"module\":\"" + module + "\",\"publicAccess\":" + access
                + ",\"publicDisplay\":" + display + ",\"publicSubmit\":" + submit
                + ",\"publicQuery\":" + query + ",\"publicExport\":" + export
                + ",\"view\":false,\"create\":false,\"edit\":false,\"delete\":false,\"importExport\":false}]";
    }
}
