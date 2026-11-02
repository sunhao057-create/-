package com.projectmgmt;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectmgmt.customer.CustomerRepository;
import com.projectmgmt.security.AuthUser;
import com.projectmgmt.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class CustomerApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    CustomerRepository customerRepository;

    @Test
    void createCustomerWithContactsAndBankAccounts() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();

        MvcResult created = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shortName": "测试客户",
                                  "companyName": "测试客户有限公司",
                                  "code": "999",
                                  "level": "一般重要",
                                  "contacts": [
                                    {"name": "李工", "title": "项目对接", "phone": "13900000001", "email": "li@test.com", "primaryContact": true},
                                    {"name": "王财务", "title": "财务", "phone": "13900000002", "primaryContact": false}
                                  ],
                                  "bankAccounts": [
                                    {"accountName": "测试客户有限公司", "bankName": "招商银行", "accountNo": "622200001", "branch": "上海分行", "primaryAccount": true}
                                  ]
                                }
                                """)
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.shortName").value("测试客户"))
                .andExpect(jsonPath("$.data.contacts.length()").value(2))
                .andExpect(jsonPath("$.data.contacts[0].name").value("李工"))
                .andExpect(jsonPath("$.data.bankAccounts[0].accountNo").value("622200001"))
                .andReturn();

        Long id = customerRepository.findByCode("999").orElseThrow().getId();

        mockMvc.perform(get("/api/customers/" + id).with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contacts.length()").value(2))
                .andExpect(jsonPath("$.data.bankAccounts.length()").value(1));

        mockMvc.perform(put("/api/customers/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shortName": "测试客户",
                                  "companyName": "测试客户有限公司",
                                  "code": "999",
                                  "level": "重要",
                                  "contacts": [
                                    {"name": "赵总", "title": "总经理", "phone": "13900000003", "primaryContact": true}
                                  ],
                                  "bankAccounts": []
                                }
                                """)
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.level").value("重要"))
                .andExpect(jsonPath("$.data.contacts.length()").value(1))
                .andExpect(jsonPath("$.data.contacts[0].name").value("赵总"))
                .andExpect(jsonPath("$.data.bankAccounts.length()").value(0));

        mockMvc.perform(delete("/api/customers/" + id).with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
