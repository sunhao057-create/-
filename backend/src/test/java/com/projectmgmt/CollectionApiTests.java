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
import com.projectmgmt.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CollectionApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    CustomerRepository customerRepository;

    @Autowired
    RegionRepository regionRepository;

    @Test
    void projectCollectionSyncsContractAndCustomerAndIsRoleGated() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        var staff = userRepository.findByUsername("liuxiaolin").orElseThrow();
        var customer = customerRepository.findByCode("133").orElseThrow();
        var region = regionRepository.findByName("华东地区").orElseThrow();
        long stamp = System.currentTimeMillis();
        Integer accountId = JsonPath.read(mockMvc.perform(post("/api/finance/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"回款户%s","bankName":"中国银行","accountName":"智襄网","accountNo":"HKACCT%s","enabled":true}
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
                                  "name": "无回款权限%s",
                                  "permissions": [
                                    {"module":"PROJECT","view":true,"create":false,"edit":false,"delete":false,"importExport":false},
                                    {"module":"CONTRACT","view":true,"create":false,"edit":true,"delete":false,"importExport":false}
                                  ]
                                }
                                """.formatted(stamp))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String roleCode = JsonPath.read(roleBody, "$.data.code");
        String username = "nocollect" + stamp;
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Staff123!","displayName":"无回款","role":"%s"}
                                """.formatted(username, roleCode))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk());
        var noCollect = userRepository.findByUsername(username).orElseThrow();
        noCollect.setMustChangePassword(false);
        userRepository.save(noCollect);

        String created = mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "回款合同%s",
                                  "customerId": %d,
                                  "amount": 100000,
                                  "signDate": "2026-08-01",
                                  "status": "EXECUTING"
                                }
                                """.formatted(stamp, customer.getId()))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer contractId = JsonPath.read(created, "$.data.id");

        String projectBody = mockMvc.perform(multipart("/api/projects")
                        .param("name", "回款立项" + stamp)
                        .param("code", "UT-HK-" + stamp)
                        .param("customerId", String.valueOf(customer.getId()))
                        .param("contractId", String.valueOf(contractId))
                        .param("managerId", String.valueOf(staff.getId()))
                        .param("regionId", String.valueOf(region.getId()))
                        .param("overview", "回款联动")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer projectId = JsonPath.read(projectBody, "$.data.id");

        mockMvc.perform(post("/api/collections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": %d,
                                  "amount": 40000,
                                  "receivedDate": "2026-08-20",
                                  "method": "电汇",
                                  "remark": "项目回款"
                                }
                                """.formatted(projectId))
                        .with(user(new AuthUser(noCollect))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/collections").with(user(new AuthUser(noCollect))))
                .andExpect(status().isOk());

        String receipt = mockMvc.perform(post("/api/collections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": %d,
                                  "accountId": %d,
                                  "amount": 40000,
                                  "receivedDate": "2026-08-20",
                                  "method": "电汇",
                                  "remark": "项目回款"
                                }
                                """.formatted(projectId, accountId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(40000.00))
                .andExpect(jsonPath("$.data.projectId").value(projectId))
                .andExpect(jsonPath("$.data.contractId").value(contractId))
                .andExpect(jsonPath("$.data.customerId").value(customer.getId().intValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer collectionId = JsonPath.read(receipt, "$.data.id");

        mockMvc.perform(get("/api/contracts/" + contractId).with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collectedAmount").value(40000.00))
                .andExpect(jsonPath("$.data.outstandingAmount").value(60000.00));

        mockMvc.perform(get("/api/customers/" + customer.getId() + "/contracts")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id==" + contractId + ")].collectedAmount").value(40000.00))
                .andExpect(jsonPath("$.data[?(@.id==" + contractId + ")].outstandingAmount").value(60000.00));

        mockMvc.perform(get("/api/analytics/customers")
                        .param("contractCode", "回款合同" + stamp)
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kpis.collectedAmount").value(40000.00))
                .andExpect(jsonPath("$.data.kpis.outstandingAmount").value(60000.00));

        mockMvc.perform(get("/api/analytics/projects")
                        .param("projectName", "回款立项" + stamp)
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projects[0].collectedAmount").value(40000.00));

        mockMvc.perform(get("/api/collections").with(user(new AuthUser(staff))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id==" + collectionId + ")].projectId").value(projectId));

        mockMvc.perform(post("/api/collections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": %d,
                                  "accountId": %d,
                                  "amount": 70000,
                                  "receivedDate": "2026-08-21",
                                  "method": "电汇"
                                }
                                """.formatted(projectId, accountId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("回款金额不能超过合同待回款"));

        mockMvc.perform(put("/api/collections/" + collectionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": %d,
                                  "accountId": %d,
                                  "amount": 25000,
                                  "receivedDate": "2026-08-22",
                                  "method": "支票",
                                  "remark": "调减"
                                }
                                """.formatted(projectId, accountId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(25000.00));

        mockMvc.perform(get("/api/contracts/" + contractId).with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collectedAmount").value(25000.00))
                .andExpect(jsonPath("$.data.outstandingAmount").value(75000.00));

        mockMvc.perform(delete("/api/collections/" + collectionId).with(user(new AuthUser(admin))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/contracts/" + contractId).with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collectedAmount").value(0))
                .andExpect(jsonPath("$.data.outstandingAmount").value(100000.00));
    }

    @Test
    void projectWithoutContractCannotCollect() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        var staff = userRepository.findByUsername("liuxiaolin").orElseThrow();
        var customer = customerRepository.findByCode("133").orElseThrow();
        var region = regionRepository.findByName("华东地区").orElseThrow();
        long stamp = System.currentTimeMillis();

        String projectBody = mockMvc.perform(multipart("/api/projects")
                        .param("name", "无合同立项" + stamp)
                        .param("code", "UT-HK0-" + stamp)
                        .param("customerId", String.valueOf(customer.getId()))
                        .param("managerId", String.valueOf(staff.getId()))
                        .param("regionId", String.valueOf(region.getId()))
                        .param("overview", "无合同")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer projectId = JsonPath.read(projectBody, "$.data.id");

        mockMvc.perform(post("/api/collections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": %d,
                                  "amount": 100,
                                  "receivedDate": "2026-08-20",
                                  "method": "电汇"
                                }
                                """.formatted(projectId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该立项未关联合同，无法登记回款"));
    }
}
