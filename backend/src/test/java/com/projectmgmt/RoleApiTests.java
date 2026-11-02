package com.projectmgmt;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class RoleApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Test
    void adminCanManageRolesAndBuiltinUserCannotViewUsers() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        var staff = userRepository.findByUsername("liuxiaolin").orElseThrow();

        mockMvc.perform(get("/api/roles").with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[?(@.code=='ADMIN')].name").exists())
                .andExpect(jsonPath("$.data[?(@.code=='USER')].name").exists())
                .andExpect(jsonPath("$.data[?(@.code=='ADMIN')].permissions[?(@.module=='BRANDING')].edit")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='ADMIN')].permissions[?(@.module=='THEME')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='THEME')].view")
                        .value(org.hamcrest.Matchers.hasItem(false)))
                .andExpect(jsonPath("$.data[?(@.code=='ADMIN')].permissions[?(@.module=='PAYMENT')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='PAYMENT')].view")
                        .value(org.hamcrest.Matchers.hasItem(false)))
                .andExpect(jsonPath("$.data[?(@.code=='ADMIN')].permissions[?(@.module=='ORG')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='ORG')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='ORG')].delete")
                        .value(org.hamcrest.Matchers.hasItem(false)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='CONTRACT')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='ADMIN')].permissions[?(@.module=='CONTRACT')].create")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='ADMIN')].permissions[?(@.module=='PROJECT_ANALYTICS')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='ADMIN')].permissions[?(@.module=='PROJECT_ANALYTICS')].hasCreate")
                        .value(org.hamcrest.Matchers.hasItem(false)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='PROJECT_ANALYTICS')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='ADMIN')].permissions[?(@.module=='CUSTOMER_ANALYTICS')]")
                        .isEmpty())
                .andExpect(jsonPath("$.data[?(@.code=='ADMIN')].permissions[?(@.module=='BANK_ACCOUNT')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='ADMIN')].permissions[?(@.module=='EXPENSE')].create")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='ADMIN')].permissions[?(@.module=='BANK_ANALYTICS')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='ADMIN')].permissions[?(@.module=='BANK_ANALYTICS')].hasCreate")
                        .value(org.hamcrest.Matchers.hasItem(false)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='BANK_ACCOUNT')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='EXPENSE')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='BANK_ANALYTICS')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='ADMIN')].permissions[?(@.module=='PURCHASE')].create")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='ADMIN')].permissions[?(@.module=='PURCHASE_PAY')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='ADMIN')].permissions[?(@.module=='PURCHASE_ANALYTICS')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='ADMIN')].permissions[?(@.module=='PURCHASE_ANALYTICS')].hasCreate")
                        .value(org.hamcrest.Matchers.hasItem(false)))
                .andExpect(jsonPath("$.data[?(@.code=='ADMIN')].permissions[?(@.module=='WAREHOUSE')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='ADMIN')].permissions[?(@.module=='STOCK_OUT_PURCHASE')].create")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='ADMIN')].permissions[?(@.module=='STOCK_OUT_PROJECT')].create")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='ADMIN')].permissions[?(@.module=='STOCK_TRANSFER')].create")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='ADMIN')].permissions[?(@.module=='WAREHOUSE_ANALYTICS')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='ADMIN')].permissions[?(@.module=='WAREHOUSE_ANALYTICS')].hasCreate")
                        .value(org.hamcrest.Matchers.hasItem(false)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='PURCHASE')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='PURCHASE_PAY')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='PURCHASE_ANALYTICS')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='WAREHOUSE')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='STOCK_OUT_PURCHASE')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='STOCK_OUT_PROJECT')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='STOCK_TRANSFER')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='WAREHOUSE_ANALYTICS')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='ADMIN')].permissions[?(@.module=='DEVICE')].create")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='ADMIN')].permissions[?(@.module=='DEVICE_REPAIR')].create")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='ADMIN')].permissions[?(@.module=='DEVICE_MOVE')].create")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='ADMIN')].permissions[?(@.module=='DEVICE_SCRAP')].create")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='DEVICE')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='DEVICE_REPAIR')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='DEVICE_MOVE')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='DEVICE_SCRAP')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='OA_ONBOARD')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='OA_TRANSFER')].create")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='OA_PROMOTION')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='OA_RESIGN')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='OA_SALARY')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='OA_INTERVIEW')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='OA_SEAL')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='OA_QUALIFY')].create")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='OA_TRIP')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='OA_LEAVE')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='OA_LEAVE')].create")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='OA_LOAN')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='OA_LOAN_PAY')].create")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='OA_LOAN_REPAY')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='OA_STAFF')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='TECH_WECHAT_CONTACTS')].view")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='USER')].permissions[?(@.module=='TECH_WECHAT_CONTACTS')].create")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='ADMIN')].permissions[?(@.module=='OA_STAFF')].hasCreate")
                        .value(org.hamcrest.Matchers.hasItem(false)))
                .andExpect(jsonPath("$.data[?(@.code=='ADMIN')].permissions[?(@.module=='OA_STAFF')].hasDelete")
                        .value(org.hamcrest.Matchers.hasItem(false)))
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].permissions[?(@.module=='DEVICE')].hasPublicAccess")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].permissions[?(@.module=='PURCHASE')].hasPublicAccess")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].permissions[?(@.module=='WAREHOUSE')].hasPublicAccess")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].permissions[?(@.module=='BANK_ACCOUNT')].hasPublicAccess")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].permissions[?(@.module=='EXPENSE')].hasPublicAccess")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].permissions[?(@.module=='OA_ONBOARD')].hasPublicAccess")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].permissions[?(@.module=='OA_TRANSFER')].hasPublicAccess")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].permissions[?(@.module=='OA_PROMOTION')].hasPublicAccess")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].permissions[?(@.module=='OA_RESIGN')].hasPublicAccess")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].permissions[?(@.module=='OA_SALARY')].hasPublicAccess")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].permissions[?(@.module=='OA_INTERVIEW')].hasPublicAccess")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].permissions[?(@.module=='OA_SEAL')].hasPublicAccess")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].permissions[?(@.module=='OA_QUALIFY')].hasPublicAccess")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].permissions[?(@.module=='OA_TRIP')].hasPublicAccess")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].permissions[?(@.module=='OA_LEAVE')].hasPublicAccess")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].permissions[?(@.module=='OA_LOAN')].hasPublicAccess")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].permissions[?(@.module=='OA_LOAN_PAY')].hasPublicAccess")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].permissions[?(@.module=='OA_LOAN_REPAY')].hasPublicAccess")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].permissions[?(@.module=='OA_STAFF')].hasPublicAccess")
                        .value(org.hamcrest.Matchers.hasItem(false)))
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].permissions[?(@.module=='TECH_WECHAT_CONTACTS')].hasPublicAccess")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].permissions[?(@.module=='ORG')].hasPublicAccess")
                        .value(org.hamcrest.Matchers.hasItem(false)))
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].permissions[?(@.module=='DEPOSIT_APPLY')].hasPublicAccess")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].permissions[?(@.module=='DEPOSIT_RETURN')].hasPublicAccess")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].permissions[?(@.module=='BIDDING')].hasPublicAccess")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].permissions[?(@.module=='PAYMENT')].hasPublicAccess")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].permissions[?(@.module=='PROJECT')].hasPublicAccess")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].permissions[?(@.module=='USER')].hasPublicAccess")
                        .value(org.hamcrest.Matchers.hasItem(false)))
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].permissions[?(@.module=='WORKFLOW')].hasPublicAccess")
                        .value(org.hamcrest.Matchers.hasItem(false)))
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].permissions[?(@.module=='THEME')].hasPublicAccess")
                        .value(org.hamcrest.Matchers.hasItem(false)))
                .andExpect(jsonPath("$.data[?(@.code=='PUBLIC_GUEST')].permissions[?(@.module=='PROJECT_ANALYTICS')].hasPublicAccess")
                        .value(org.hamcrest.Matchers.hasItem(false)));

        var first = mockMvc.perform(post("/api/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "测试角色",
                                  "description": "仅查看项目",
                                  "permissions": [
                                    {"module":"PROJECT","view":true,"edit":false,"delete":false,"importExport":false},
                                    {"module":"CUSTOMER","view":false,"edit":false,"delete":false,"importExport":false},
                                    {"module":"USER","view":false,"edit":false,"delete":false,"importExport":false},
                                    {"module":"ROLE","view":false,"edit":false,"delete":false,"importExport":false}
                                  ]
                                }
                                """)
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.code").value(org.hamcrest.Matchers.matchesPattern("R[A-Z2-9]{8}")))
                .andExpect(jsonPath("$.data.permissions[?(@.module=='PROJECT')].view").value(true))
                .andExpect(jsonPath("$.data.permissions[?(@.module=='PROJECT_ANALYTICS')].view").value(false))
                .andExpect(jsonPath("$.data.permissions[?(@.module=='PURCHASE_ANALYTICS')].view").value(false))
                .andReturn();

        String firstCode = com.jayway.jsonpath.JsonPath.read(first.getResponse().getContentAsString(), "$.data.code");

        mockMvc.perform(post("/api/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "测试角色二",
                                  "permissions": []
                                }
                                """)
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value(org.hamcrest.Matchers.matchesPattern("R[A-Z2-9]{8}")))
                .andExpect(jsonPath("$.data.code").value(org.hamcrest.Matchers.not(firstCode)));

        mockMvc.perform(get("/api/users").with(user(new AuthUser(staff))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/projects").with(user(new AuthUser(staff))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/analytics/customers").with(user(new AuthUser(staff))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/analytics/projects").with(user(new AuthUser(staff))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
