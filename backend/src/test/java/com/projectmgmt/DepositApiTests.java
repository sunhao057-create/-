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
class DepositApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    CustomerRepository customerRepository;

    @Autowired
    RegionRepository regionRepository;

    @Test
    void applyThenReturnPostsLedgerAndBlocksOverReturn() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        var manager = userRepository.findByUsername("liuxiaolin").orElseThrow();
        var customer = customerRepository.findByCode("133").orElseThrow();
        var region = regionRepository.findByName("华东地区").orElseThrow();
        long stamp = System.currentTimeMillis();
        Integer accountId = JsonPath.read(mockMvc.perform(post("/api/finance/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"保证金户%s","bankName":"中国银行","accountName":"智襄网","accountNo":"BZ%s","enabled":true}
                                """.formatted(stamp, stamp))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");
        Integer projectId = JsonPath.read(mockMvc.perform(multipart("/api/projects")
                        .param("name", "保证金项目")
                        .param("code", "BZ-P-" + stamp)
                        .param("customerId", String.valueOf(customer.getId()))
                        .param("managerId", String.valueOf(manager.getId()))
                        .param("regionId", String.valueOf(region.getId()))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");

        Integer applyId = JsonPath.read(mockMvc.perform(multipart("/api/deposit-applies")
                        .param("projectId", String.valueOf(projectId))
                        .param("accountId", String.valueOf(accountId))
                        .param("amount", "10000")
                        .param("bizDate", "2026-09-05")
                        .param("remark", "投标保证金")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value(projectId))
                .andExpect(jsonPath("$.data.ledgerPosted").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");

        mockMvc.perform(multipart("/api/deposit-returns")
                        .param("projectId", String.valueOf(projectId))
                        .param("applyId", String.valueOf(applyId))
                        .param("accountId", String.valueOf(accountId))
                        .param("amount", "1000")
                        .param("bizDate", "2026-09-06")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("审批入账后再办理退回")));

        complete(admin, "DEPOSIT_APPLY", applyId);

        mockMvc.perform(get("/api/deposit-applies/" + applyId).with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ledgerPosted").value(true))
                .andExpect(jsonPath("$.data.remainingAmount").value(10000.00));

        mockMvc.perform(get("/api/finance/accounts/" + accountId).with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expenseAmount").value(10000.00));

        mockMvc.perform(multipart("/api/deposit-returns")
                        .param("projectId", String.valueOf(projectId))
                        .param("applyId", String.valueOf(applyId))
                        .param("accountId", String.valueOf(accountId))
                        .param("amount", "10000.01")
                        .param("bizDate", "2026-09-06")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("不能大于剩余保证金")));

        Integer returnId = JsonPath.read(mockMvc.perform(multipart("/api/deposit-returns")
                        .param("projectId", String.valueOf(projectId))
                        .param("applyId", String.valueOf(applyId))
                        .param("accountId", String.valueOf(accountId))
                        .param("amount", "4000")
                        .param("bizDate", "2026-09-06")
                        .param("remark", "部分退回")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applyId").value(applyId))
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");
        complete(admin, "DEPOSIT_RETURN", returnId);

        mockMvc.perform(get("/api/deposit-applies/" + applyId).with(user(new AuthUser(admin))))
                .andExpect(jsonPath("$.data.remainingAmount").value(6000.00))
                .andExpect(jsonPath("$.data.returned").value(false));
        mockMvc.perform(get("/api/finance/accounts/" + accountId).with(user(new AuthUser(admin))))
                .andExpect(jsonPath("$.data.incomeAmount").value(4000.00))
                .andExpect(jsonPath("$.data.expenseAmount").value(10000.00));

        mockMvc.perform(delete("/api/deposit-applies/" + applyId).with(user(new AuthUser(admin))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("已有退回单")));
    }

    private void complete(com.projectmgmt.user.SysUser actor, String module, Integer bizId) throws Exception {
        WorkflowTestSupport.complete(mockMvc, actor, module, bizId);
    }
}
