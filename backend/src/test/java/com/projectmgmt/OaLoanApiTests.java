package com.projectmgmt;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class OaLoanApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    CustomerRepository customerRepository;

    @Autowired
    RegionRepository regionRepository;

    @Test
    void loanWithProjectDeductsBudgetAndAllowsPayAndRepay() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        var manager = userRepository.findByUsername("liuxiaolin").orElseThrow();
        var customer = customerRepository.findByCode("133").orElseThrow();
        var region = regionRepository.findByName("华东地区").orElseThrow();
        Integer deptId = JsonPath.read(mockMvc.perform(post("/api/org/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"借款部门" + System.currentTimeMillis() + "\",\"sortOrder\":1}")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");
        Integer accountId = JsonPath.read(mockMvc.perform(post("/api/finance/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"借款户","bankName":"中国银行","accountName":"智襄网","accountNo":"JK%s","enabled":true}
                                """.formatted(System.nanoTime()))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");
        Integer projectId = JsonPath.read(mockMvc.perform(multipart("/api/projects")
                        .param("name", "借款测试项目")
                        .param("code", "JK-" + System.currentTimeMillis())
                        .param("customerId", String.valueOf(customer.getId()))
                        .param("managerId", String.valueOf(manager.getId()))
                        .param("regionId", String.valueOf(region.getId()))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");

        Integer loanId = JsonPath.read(mockMvc.perform(post("/api/oa/loan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "staffId": %d,
                                  "departmentId": %d,
                                  "expectedDate": "2026-09-03",
                                  "reason": "项目备用金",
                                  "loan": {
                                    "linkProject": true,
                                    "projectId": %d,
                                    "customerId": %d,
                                    "belongCompany": "西安赛仕智能科技有限公司",
                                    "amount": 10000,
                                    "payAmount": 4000,
                                    "accountId": %d
                                  }
                                }
                                """.formatted(admin.getId(), deptId, projectId, customer.getId(), accountId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loan.amount").value(10000.00))
                .andExpect(jsonPath("$.data.loan.projectId").value(projectId))
                .andExpect(jsonPath("$.data.applied").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");

        complete(admin, "OA_LOAN", loanId);

        mockMvc.perform(get("/api/oa/loan/" + loanId).with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applied").value(true))
                .andExpect(jsonPath("$.data.loan.paidTotal").value(4000.00))
                .andExpect(jsonPath("$.data.loan.remainingPay").value(6000.00))
                .andExpect(jsonPath("$.data.loan.repayStatus").value("未还款"));

        Integer payId = JsonPath.read(mockMvc.perform(post("/api/oa/loan-pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "staffId": %d,
                                  "departmentId": %d,
                                  "expectedDate": "2026-09-04",
                                  "reason": "补充打款",
                                  "loan": {
                                    "loanRequestId": %d,
                                    "accountId": %d,
                                    "payAmount": 6000
                                  }
                                }
                                """.formatted(admin.getId(), deptId, loanId, accountId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loan.loanRequestId").value(loanId))
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");
        complete(admin, "OA_LOAN_PAY", payId);

        mockMvc.perform(get("/api/oa/loan/" + loanId).with(user(new AuthUser(admin))))
                .andExpect(jsonPath("$.data.loan.paidTotal").value(10000.00))
                .andExpect(jsonPath("$.data.loan.remainingPay").value(0));

        Integer repayId = JsonPath.read(mockMvc.perform(post("/api/oa/loan-repay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "staffId": %d,
                                  "departmentId": %d,
                                  "expectedDate": "2026-09-05",
                                  "reason": "归还借款",
                                  "loan": {
                                    "loanRequestId": %d,
                                    "accountId": %d,
                                    "repayAmount": 10000
                                  }
                                }
                                """.formatted(admin.getId(), deptId, loanId, accountId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");
        complete(admin, "OA_LOAN_REPAY", repayId);

        mockMvc.perform(get("/api/oa/loan/" + loanId).with(user(new AuthUser(admin))))
                .andExpect(jsonPath("$.data.loan.repayStatus").value("已还清"))
                .andExpect(jsonPath("$.data.loan.remainingRepay").value(0));
    }

    private void complete(com.projectmgmt.user.SysUser actor, String module, Integer bizId) throws Exception {
        WorkflowTestSupport.complete(mockMvc, actor, module, bizId);
    }
}
