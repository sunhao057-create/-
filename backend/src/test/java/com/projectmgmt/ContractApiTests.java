package com.projectmgmt;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.projectmgmt.customer.CustomerRepository;
import com.projectmgmt.region.RegionRepository;
import com.projectmgmt.security.AuthUser;
import com.projectmgmt.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ContractApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    CustomerRepository customerRepository;

    @Autowired
    RegionRepository regionRepository;

    @Test
    void createContractLinkProjectCollectAndAnalytics() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        var staff = userRepository.findByUsername("liuxiaolin").orElseThrow();
        var customer = customerRepository.findByCode("133").orElseThrow();
        var region = regionRepository.findByName("华东地区").orElseThrow();
        long stamp = System.currentTimeMillis();
        Integer accountId = JsonPath.read(mockMvc.perform(post("/api/finance/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"合同回款户%s","bankName":"中国银行","accountName":"智襄网","accountNo":"CTHK%s","enabled":true}
                                """.formatted(stamp, stamp))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");

        String roleBody = mockMvc.perform(post("/api/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "无合同权限%s",
                                  "permissions": [
                                    {"module":"PROJECT","view":true,"create":false,"edit":false,"delete":false,"importExport":false}
                                  ]
                                }
                                """.formatted(stamp))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String roleCode = JsonPath.read(roleBody, "$.data.code");
        String username = "nocontract" + stamp;
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Staff123!","displayName":"无合同","role":"%s"}
                                """.formatted(username, roleCode))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk());
        var noContract = userRepository.findByUsername(username).orElseThrow();
        noContract.setMustChangePassword(false);
        userRepository.save(noContract);

        mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"越权合同","customerId":%d,"amount":1}
                                """.formatted(customer.getId()))
                        .with(user(new AuthUser(noContract))))
                .andExpect(status().isForbidden());

        String created = mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "测试合同%s",
                                  "customerId": %d,
                                  "amount": 100000,
                                  "signDate": "2026-08-01",
                                  "startDate": "2026-08-01",
                                  "endDate": "2027-08-01",
                                  "status": "EXECUTING",
                                  "remark": "单元测试"
                                }
                                """.formatted(stamp, customer.getId()))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.customerId").value(customer.getId().intValue()))
                .andExpect(jsonPath("$.data.statusLabel").value("执行中"))
                .andExpect(jsonPath("$.data.amount").value(100000.00))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer contractId = JsonPath.read(created, "$.data.id");

        mockMvc.perform(get("/api/customers/" + customer.getId() + "/contracts")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id==" + contractId + ")].name").exists());

        mockMvc.perform(get("/api/contracts").with(user(new AuthUser(staff))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        String projectBody = mockMvc.perform(multipart("/api/projects")
                        .param("name", "合同关联立项" + stamp)
                        .param("code", "UT-HT-" + stamp)
                        .param("customerId", String.valueOf(customer.getId()))
                        .param("contractId", String.valueOf(contractId))
                        .param("managerId", String.valueOf(staff.getId()))
                        .param("regionId", String.valueOf(region.getId()))
                        .param("overview", "挂合同")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contractId").value(contractId))
                .andExpect(jsonPath("$.data.contractName").value("测试合同" + stamp))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer projectId = JsonPath.read(projectBody, "$.data.id");

        mockMvc.perform(post("/api/contracts/" + contractId + "/collections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": %d,
                                  "accountId": %d,
                                  "amount": 30000,
                                  "receivedDate": "2026-08-15",
                                  "method": "电汇",
                                  "remark": "首笔回款"
                                }
                                """.formatted(projectId, accountId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(30000.00))
                .andExpect(jsonPath("$.data.projectId").value(projectId));

        mockMvc.perform(get("/api/contracts/" + contractId)
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collectedAmount").value(30000.00))
                .andExpect(jsonPath("$.data.outstandingAmount").value(70000.00))
                .andExpect(jsonPath("$.data.projectCount").value(1));

        mockMvc.perform(delete("/api/contracts/" + contractId).with(user(new AuthUser(admin))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该合同已关联项目立项，不能删除"));

        mockMvc.perform(get("/api/analytics/customers")
                        .param("contractCode", "测试合同" + stamp)
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kpis.contractCount").value(1))
                .andExpect(jsonPath("$.data.kpis.projectCount").value(1))
                .andExpect(jsonPath("$.data.kpis.contractAmount").value(100000.00))
                .andExpect(jsonPath("$.data.kpis.collectedAmount").value(30000.00))
                .andExpect(jsonPath("$.data.kpis.outstandingAmount").value(70000.00))
                .andExpect(jsonPath("$.data.kpis.collectionRate").value(30.0))
                .andExpect(jsonPath("$.data.kpis.executingContractCount").value(1));

        mockMvc.perform(get("/api/analytics/projects")
                        .param("projectName", "合同关联立项" + stamp)
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projects[0].contractId").value(contractId))
                .andExpect(jsonPath("$.data.projects[0].collectedAmount").value(30000.00));
    }

    @Test
    void cannotDeleteCustomerWithContract() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        long stamp = System.currentTimeMillis();
        String code = "UT-HTC-" + stamp;

        String customerBody = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shortName": "合同客户",
                                  "companyName": "合同客户有限公司",
                                  "code": "%s",
                                  "level": "一般重要",
                                  "contacts": [],
                                  "bankAccounts": []
                                }
                                """.formatted(code))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer customerId = JsonPath.read(customerBody, "$.data.id");

        mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "不可删合同",
                                  "customerId": %d,
                                  "amount": 1,
                                  "status": "DRAFT"
                                }
                                """.formatted(customerId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/customers/" + customerId).with(user(new AuthUser(admin))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该客户已关联合同，不能删除"));
    }
}
