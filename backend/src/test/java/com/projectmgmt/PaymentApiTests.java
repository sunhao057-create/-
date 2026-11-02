package com.projectmgmt;

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
import com.projectmgmt.region.RegionRepository;
import com.projectmgmt.security.AuthUser;
import com.projectmgmt.supplier.SupplierRepository;
import com.projectmgmt.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    CustomerRepository customerRepository;

    @Autowired
    RegionRepository regionRepository;

    @Autowired
    SupplierRepository supplierRepository;

    @Test
    void createAdvanceAndDeleteRestoresLedger() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        var manager = userRepository.findByUsername("liuxiaolin").orElseThrow();
        var customer = customerRepository.findByCode("133").orElseThrow();
        var region = regionRepository.findByName("华东地区").orElseThrow();
        var supplier = supplierRepository.findByCode("S01").orElseThrow();
        String supplierBody = mockMvc.perform(get("/api/suppliers/" + supplier.getId()).with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer bankId = JsonPath.read(supplierBody, "$.data.bankAccounts[0].id");
        Integer accountId = JsonPath.read(mockMvc.perform(post("/api/finance/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"付款户","bankName":"中国银行","accountName":"智襄网","accountNo":"P%s","enabled":true}
                                """.formatted(System.nanoTime()))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");
        String projectCode = "PAY-" + System.currentTimeMillis();

        String projectBody = mockMvc.perform(multipart("/api/projects")
                        .param("name", "付款测试项目")
                        .param("code", projectCode)
                        .param("customerId", String.valueOf(customer.getId()))
                        .param("managerId", String.valueOf(manager.getId()))
                        .param("regionId", String.valueOf(region.getId()))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer projectId = JsonPath.read(projectBody, "$.data.id");

        String paymentBody = mockMvc.perform(multipart("/api/payments")
                        .param("projectId", String.valueOf(projectId))
                        .param("amount", "1200")
                        .param("reason", "采购预付款")
                        .param("supplierId", String.valueOf(supplier.getId()))
                        .param("accountId", String.valueOf(accountId))
                        .param("bankAccountId", String.valueOf(bankId))
                        .param("payMethod", "电汇")
                        .param("expectedPayDate", "2026-08-28")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value(projectId))
                .andExpect(jsonPath("$.data.amount").value(1200.00))
                .andExpect(jsonPath("$.data.budgetDeducted").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer paymentId = JsonPath.read(paymentBody, "$.data.id");

        mockMvc.perform(delete("/api/projects/" + projectId).with(user(new AuthUser(admin))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该项目仍有付款申请，请先删除后再删项目"));

        mockMvc.perform(get("/api/inbox").param("type", "DRAFT").with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.formModule=='PAYMENT' && @.bizId==" + paymentId + ")]").exists());

        WorkflowTestSupport.complete(mockMvc, admin, "PAYMENT", paymentId);

        mockMvc.perform(get("/api/payments/" + paymentId).with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.budgetDeducted").value(true))
                .andExpect(jsonPath("$.data.paidTotal").value(1200.00));

        mockMvc.perform(post("/api/workflows/advance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"formModule\":\"PAYMENT\",\"bizId\":%s}".formatted(paymentId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/payments/" + paymentId).with(user(new AuthUser(admin))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/payments/" + paymentId).with(user(new AuthUser(admin))))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCanMarkPaymentPublic() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        var manager = userRepository.findByUsername("liuxiaolin").orElseThrow();
        var customer = customerRepository.findByCode("133").orElseThrow();
        var region = regionRepository.findByName("华东地区").orElseThrow();
        var supplier = supplierRepository.findByCode("S01").orElseThrow();
        String supplierBody = mockMvc.perform(get("/api/suppliers/" + supplier.getId()).with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer bankId = JsonPath.read(supplierBody, "$.data.bankAccounts[0].id");
        Integer accountId = JsonPath.read(mockMvc.perform(post("/api/finance/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"付款户","bankName":"中国银行","accountName":"智襄网","accountNo":"P%s","enabled":true}
                                """.formatted(System.nanoTime()))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");
        String projectCode = "PAY-PUB-" + System.currentTimeMillis();
        String projectBody = mockMvc.perform(multipart("/api/projects")
                        .param("name", "付款公开项目")
                        .param("code", projectCode)
                        .param("customerId", String.valueOf(customer.getId()))
                        .param("managerId", String.valueOf(manager.getId()))
                        .param("regionId", String.valueOf(region.getId()))
                        .param("isPublic", "true")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer projectId = JsonPath.read(projectBody, "$.data.id");

        String created = mockMvc.perform(multipart("/api/payments")
                        .param("projectId", String.valueOf(projectId))
                        .param("amount", "100")
                        .param("reason", "公开标记")
                        .param("supplierId", String.valueOf(supplier.getId()))
                        .param("accountId", String.valueOf(accountId))
                        .param("bankAccountId", String.valueOf(bankId))
                        .param("payMethod", "WIRE")
                        .param("expectedPayDate", "2026-09-01")
                        .param("isPublic", "true")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPublic").value(true))
                .andExpect(jsonPath("$.data.submitSource").value("ADMIN"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer paymentId = JsonPath.read(created, "$.data.id");

        mockMvc.perform(put("/api/payments/" + paymentId)
                        .param("projectId", String.valueOf(projectId))
                        .param("amount", "100")
                        .param("reason", "公开标记")
                        .param("supplierId", String.valueOf(supplier.getId()))
                        .param("accountId", String.valueOf(accountId))
                        .param("bankAccountId", String.valueOf(bankId))
                        .param("payMethod", "WIRE")
                        .param("expectedPayDate", "2026-09-01")
                        .param("isPublic", "false")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPublic").value(false));
    }

    @Test
    void paymentWithoutProjectStillPostsLedger() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        var supplier = supplierRepository.findByCode("S01").orElseThrow();
        String supplierBody = mockMvc.perform(get("/api/suppliers/" + supplier.getId()).with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer bankId = JsonPath.read(supplierBody, "$.data.bankAccounts[0].id");
        Integer accountId = JsonPath.read(mockMvc.perform(post("/api/finance/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"付款户","bankName":"中国银行","accountName":"智襄网","accountNo":"P%s","enabled":true}
                                """.formatted(System.nanoTime()))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");

        String paymentBody = mockMvc.perform(multipart("/api/payments")
                        .param("amount", "500")
                        .param("reason", "公司日常付款")
                        .param("supplierId", String.valueOf(supplier.getId()))
                        .param("accountId", String.valueOf(accountId))
                        .param("bankAccountId", String.valueOf(bankId))
                        .param("payMethod", "电汇")
                        .param("expectedPayDate", "2026-09-02")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.budgetDeducted").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer paymentId = JsonPath.read(paymentBody, "$.data.id");

        WorkflowTestSupport.complete(mockMvc, admin, "PAYMENT", paymentId);

        mockMvc.perform(get("/api/payments/" + paymentId).with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.budgetDeducted").value(false));

        mockMvc.perform(get("/api/finance/accounts/" + accountId + "/txns").with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.sourceType=='PAYMENT' && @.sourceId==" + paymentId + ")].direction")
                        .value(org.hamcrest.Matchers.hasItem("OUT")));
    }
}
