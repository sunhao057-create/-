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
class TechApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Test
    void wechatContactsCreateAndList() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        mockMvc.perform(post("/api/tech/wechat-contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "isPublic": false,
                                  "extra": {
                                    "field_129037661": "测试客户",
                                    "field_129037662": "13800001111"
                                  }
                                }
                                """)
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("wechat-contacts"))
                .andExpect(jsonPath("$.data.kindLabel").value("客户微信资料库"))
                .andExpect(jsonPath("$.data.code").isNotEmpty());

        mockMvc.perform(get("/api/tech/wechat-contacts").with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }
}
