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
class FinanceApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    CustomerRepository customerRepository;

    @Autowired
    RegionRepository regionRepository;

    @Test
    void adminCanManageBankAccountAndStaffCanViewAnalytics() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        var staff = userRepository.findByUsername("liuxiaolin").orElseThrow();
        String suffix = String.valueOf(System.currentTimeMillis());

        String created = mockMvc.perform(post("/api/finance/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "测试户%s",
                                  "bankName": "中国银行",
                                  "accountName": "智襄网",
                                  "accountNo": "ACCT%s",
                                  "branch": "高新区支行",
                                  "openingBalance": 1000,
                                  "enabled": true
                                }
                                """.formatted(suffix, suffix))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("测试户" + suffix))
                .andExpect(jsonPath("$.data.balance").value(1000.00))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer accountId = JsonPath.read(created, "$.data.id");

        mockMvc.perform(post("/api/finance/accounts/" + accountId + "/txns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "txnDate": "2026-08-30",
                                  "direction": "IN",
                                  "amount": "250",
                                  "summary": "回款入账"
                                }
                                """)
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.direction").value("IN"))
                .andExpect(jsonPath("$.data.amount").value(250.00));

        mockMvc.perform(get("/api/finance/accounts/" + accountId).with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.incomeAmount").value(250.00))
                .andExpect(jsonPath("$.data.balance").value(1250.00));

        mockMvc.perform(post("/api/finance/accounts/" + accountId + "/txns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "txnDate": "2026-08-31",
                                  "direction": "OUT",
                                  "amount": "50",
                                  "summary": "采购付款"
                                }
                                """)
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/finance/analytics/statement")
                        .param("accountId", String.valueOf(accountId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.openingBalance").value(1000.00))
                .andExpect(jsonPath("$.data.closingBalance").value(1200.00))
                .andExpect(jsonPath("$.data.lines.length()").value(2))
                .andExpect(jsonPath("$.data.lines[0].expense").value(50.00))
                .andExpect(jsonPath("$.data.lines[0].balance").value(1200.00))
                .andExpect(jsonPath("$.data.lines[1].income").value(250.00))
                .andExpect(jsonPath("$.data.lines[1].balance").value(1250.00))
                .andExpect(jsonPath("$.data.monthly[?(@.month=='2026-08')].incomeAmount").value(org.hamcrest.Matchers.hasItem(250.00)))
                .andExpect(jsonPath("$.data.monthly[?(@.month=='2026-08')].expenseAmount").value(org.hamcrest.Matchers.hasItem(50.00)));

        mockMvc.perform(get("/api/finance/analytics/statement")
                        .param("accountId", String.valueOf(accountId))
                        .with(user(new AuthUser(staff))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lines.length()").value(2));

        mockMvc.perform(get("/api/finance/analytics").with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kpis.accountCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.accounts[?(@.accountId==" + accountId + ")].balance").exists());

        mockMvc.perform(get("/api/finance/analytics").with(user(new AuthUser(staff))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/finance/accounts").with(user(new AuthUser(staff))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void expenseCanSkipProjectAndRejectOverBudget() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        var manager = userRepository.findByUsername("liuxiaolin").orElseThrow();
        var customer = customerRepository.findByCode("133").orElseThrow();
        var region = regionRepository.findByName("华东地区").orElseThrow();
        String suffix = String.valueOf(System.currentTimeMillis());

        String accountBody = mockMvc.perform(post("/api/finance/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "报销户%s",
                                  "bankName": "中国银行",
                                  "accountName": "智襄网",
                                  "accountNo": "EXP%s",
                                  "openingBalance": 50000,
                                  "enabled": true
                                }
                                """.formatted(suffix, suffix))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer accountId = JsonPath.read(accountBody, "$.data.id");

        mockMvc.perform(multipart("/api/finance/expenses")
                        .param("accountId", String.valueOf(accountId))
                        .param("amount", "8888")
                        .param("reason", "未关联项目的差旅")
                        .param("category", "TRAVEL")
                        .param("expenseDate", "2026-08-30")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.amount").value(8888.00))
                .andExpect(jsonPath("$.data.budgetDeducted").value(false));

        String projectBody = mockMvc.perform(multipart("/api/projects")
                        .param("name", "报销预算项目")
                        .param("code", "EXP-" + suffix)
                        .param("customerId", String.valueOf(customer.getId()))
                        .param("managerId", String.valueOf(manager.getId()))
                        .param("regionId", String.valueOf(region.getId()))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer projectId = JsonPath.read(projectBody, "$.data.id");

        String expenseBody = mockMvc.perform(multipart("/api/finance/expenses")
                        .param("projectId", String.valueOf(projectId))
                        .param("accountId", String.valueOf(accountId))
                        .param("amount", "300")
                        .param("reason", "办公用品")
                        .param("category", "OFFICE")
                        .param("expenseDate", "2026-08-30")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value(projectId))
                .andExpect(jsonPath("$.data.budgetDeducted").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer expenseId = JsonPath.read(expenseBody, "$.data.id");

        WorkflowTestSupport.complete(mockMvc, admin, "EXPENSE", expenseId);

        mockMvc.perform(get("/api/finance/expenses/" + expenseId).with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.budgetDeducted").value(true));

        mockMvc.perform(get("/api/finance/accounts/" + accountId).with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expenseAmount").value(300.00))
                .andExpect(jsonPath("$.data.balance").value(49700.00));

        mockMvc.perform(delete("/api/finance/expenses/" + expenseId).with(user(new AuthUser(admin))))
                .andExpect(status().isOk());
    }
}
