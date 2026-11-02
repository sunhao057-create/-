package com.projectmgmt;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
class VoidLinkApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    CustomerRepository customerRepository;

    @Autowired
    RegionRepository regionRepository;

    @Test
    void voidedProjectAndContractCannotBeLinkedAgain() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        var staff = userRepository.findByUsername("liuxiaolin").orElseThrow();
        var customer = customerRepository.findByCode("133").orElseThrow();
        var region = regionRepository.findByName("华东地区").orElseThrow();
        long stamp = System.currentTimeMillis();
        Integer accountId = JsonPath.read(mockMvc.perform(post("/api/finance/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"作废回款户%s","bankName":"中国银行","accountName":"智襄网","accountNo":"VOIDHK%s","enabled":true}
                                """.formatted(stamp, stamp))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");

        String contractBody = mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"作废关联合同%s","customerId":%d,"amount":100000,"status":"SIGNED"}
                                """.formatted(stamp, customer.getId()))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer contractId = JsonPath.read(contractBody, "$.data.id");

        String projectBody = mockMvc.perform(multipart("/api/projects")
                        .param("name", "作废关联立项" + stamp)
                        .param("code", "UT-VOID-" + stamp)
                        .param("customerId", String.valueOf(customer.getId()))
                        .param("contractId", String.valueOf(contractId))
                        .param("managerId", String.valueOf(staff.getId()))
                        .param("regionId", String.valueOf(region.getId()))
                        .param("overview", "作废测试")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer projectId = JsonPath.read(projectBody, "$.data.id");

        mockMvc.perform(post("/api/projects/" + projectId + "/void").with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VOIDED"))
                .andExpect(jsonPath("$.data.statusLabel").value("已作废"));

        mockMvc.perform(get("/api/projects").param("linkable", "true").param("size", "500")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id==" + projectId + ")]").isEmpty());

        mockMvc.perform(get("/api/projects")
                        .param("linkable", "true")
                        .param("keepId", String.valueOf(projectId))
                        .param("size", "500")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id==" + projectId + ")].status").value("VOIDED"));

        mockMvc.perform(post("/api/collections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": %d,
                                  "amount": 100,
                                  "receivedDate": "2026-09-01",
                                  "method": "电汇"
                                }
                                """.formatted(projectId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该项目已作废，不能再关联"));

        mockMvc.perform(post("/api/projects/" + projectId + "/restore").with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INITIATED"));

        mockMvc.perform(post("/api/contracts/" + contractId + "/void").with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VOIDED"));

        mockMvc.perform(get("/api/contracts").param("linkable", "true").param("size", "500")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id==" + contractId + ")]").isEmpty());

        mockMvc.perform(post("/api/collections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": %d,
                                  "amount": 100,
                                  "receivedDate": "2026-09-01",
                                  "method": "电汇"
                                }
                                """.formatted(projectId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该合同已作废，不能再关联"));

        mockMvc.perform(post("/api/contracts/" + contractId + "/collections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 100,
                                  "receivedDate": "2026-09-01",
                                  "method": "电汇"
                                }
                                """)
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该合同已作废，不能再关联"));

        mockMvc.perform(multipart("/api/projects")
                        .param("name", "再关联作废合同" + stamp)
                        .param("code", "UT-VOID2-" + stamp)
                        .param("customerId", String.valueOf(customer.getId()))
                        .param("contractId", String.valueOf(contractId))
                        .param("managerId", String.valueOf(staff.getId()))
                        .param("regionId", String.valueOf(region.getId()))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该合同已作废，不能再关联"));

        mockMvc.perform(post("/api/contracts/" + contractId + "/restore").with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EXECUTING"));

        mockMvc.perform(post("/api/collections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": %d,
                                  "accountId": %d,
                                  "amount": 100,
                                  "receivedDate": "2026-09-01",
                                  "method": "电汇"
                                }
                                """.formatted(projectId, accountId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(100.00));
    }
}
