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
class BidApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    CustomerRepository customerRepository;

    @Autowired
    RegionRepository regionRepository;

    @Test
    void createBidAndCompleteWorkflow() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        var manager = userRepository.findByUsername("liuxiaolin").orElseThrow();
        var customer = customerRepository.findByCode("133").orElseThrow();
        var region = regionRepository.findByName("华东地区").orElseThrow();
        long stamp = System.currentTimeMillis();
        Integer projectId = JsonPath.read(mockMvc.perform(multipart("/api/projects")
                        .param("name", "招标项目")
                        .param("code", "ZB-P-" + stamp)
                        .param("customerId", String.valueOf(customer.getId()))
                        .param("managerId", String.valueOf(manager.getId()))
                        .param("regionId", String.valueOf(region.getId()))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");

        Integer bidId = JsonPath.read(mockMvc.perform(post("/api/bids")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": %s,
                                  "salesLeadId": %s,
                                  "projectId": %s,
                                  "opportunityName": "实验室设备招标",
                                  "bidAgent": "中物单位",
                                  "tenderName": "实验室设备采购",
                                  "tenderCode": "ZB-%s",
                                  "downloadDeadline": "2026-09-10T18:00",
                                  "bidTime": "2026-09-12T09:30",
                                  "description": "项目简述",
                                  "announcementUrl": "https://example.com/notice",
                                  "signedUp": true,
                                  "result": "LOST",
                                  "signups": [
                                    {"companyName": "西安赛仕智能科技有限公司", "bidderId": %s, "registrantName": "张三"}
                                  ]
                                }
                                """.formatted(customer.getId(), manager.getId(), projectId, stamp, manager.getId()))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenderCode").value("ZB-" + stamp))
                .andExpect(jsonPath("$.data.result").value("LOST"))
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");

        WorkflowTestSupport.complete(mockMvc, admin, "BIDDING", bidId);

        mockMvc.perform(get("/api/bids/" + bidId).with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value(projectId))
                .andExpect(jsonPath("$.data.signups[0].companyName").value("西安赛仕智能科技有限公司"));
    }
}
