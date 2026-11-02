package com.projectmgmt;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class SupplierApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    SupplierRepository supplierRepository;

    @Test
    void createSupplierWithContactsAndBankAccounts() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();

        mockMvc.perform(post("/api/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shortName": "测试供应商",
                                  "companyName": "测试供应商有限公司",
                                  "code": "S999",
                                  "category": "设备",
                                  "contacts": [
                                    {"name": "陈工", "title": "销售", "phone": "13700000001", "email": "chen@test.com", "primaryContact": true},
                                    {"name": "周会计", "title": "财务", "phone": "13700000002", "primaryContact": false}
                                  ],
                                  "bankAccounts": [
                                    {"accountName": "测试供应商有限公司", "bankName": "工商银行", "accountNo": "622200009", "branch": "苏州分行", "primaryAccount": true}
                                  ]
                                }
                                """)
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.shortName").value("测试供应商"))
                .andExpect(jsonPath("$.data.contacts.length()").value(2))
                .andExpect(jsonPath("$.data.contacts[0].name").value("陈工"))
                .andExpect(jsonPath("$.data.bankAccounts[0].accountNo").value("622200009"));

        Long id = supplierRepository.findByCode("S999").orElseThrow().getId();

        mockMvc.perform(get("/api/suppliers/" + id).with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contacts.length()").value(2))
                .andExpect(jsonPath("$.data.bankAccounts.length()").value(1));

        mockMvc.perform(put("/api/suppliers/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shortName": "测试供应商",
                                  "companyName": "测试供应商有限公司",
                                  "code": "S999",
                                  "category": "原材料",
                                  "contacts": [
                                    {"name": "孙总", "title": "总经理", "phone": "13700000003", "primaryContact": true}
                                  ],
                                  "bankAccounts": []
                                }
                                """)
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category").value("原材料"))
                .andExpect(jsonPath("$.data.contacts.length()").value(1))
                .andExpect(jsonPath("$.data.contacts[0].name").value("孙总"))
                .andExpect(jsonPath("$.data.bankAccounts.length()").value(0));

        mockMvc.perform(delete("/api/suppliers/" + id).with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
