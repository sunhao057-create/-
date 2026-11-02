package com.projectmgmt;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class BrandingApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Test
    void publicGetAndAdminUpdateName() throws Exception {
        mockMvc.perform(get("/api/branding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").isString());

        AuthUser admin = new AuthUser(userRepository.findByUsername("admin").orElseThrow());
        mockMvc.perform(put("/api/branding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"智襄项目管理\"}")
                        .with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("智襄项目管理"));

        mockMvc.perform(get("/api/branding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("智襄项目管理"));

        AuthUser staff = new AuthUser(userRepository.findByUsername("liuxiaolin").orElseThrow());
        mockMvc.perform(put("/api/branding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"无权修改\"}")
                        .with(user(staff)))
                .andExpect(status().isForbidden());
    }

    @Test
    void themeIdPersistsOnServerAndRejectsUnknownPreset() throws Exception {
        mockMvc.perform(get("/api/branding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.themeId").value("qingflow"));

        AuthUser admin = new AuthUser(userRepository.findByUsername("admin").orElseThrow());
        mockMvc.perform(put("/api/branding/theme")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"themeId\":\"business\"}")
                        .with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.themeId").value("business"))
                .andExpect(jsonPath("$.data.name").isString());

        mockMvc.perform(get("/api/branding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.themeId").value("business"));

        mockMvc.perform(put("/api/branding/theme")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"themeId\":\"not-a-theme\"}")
                        .with(user(admin)))
                .andExpect(status().isBadRequest());

        AuthUser staff = new AuthUser(userRepository.findByUsername("liuxiaolin").orElseThrow());
        mockMvc.perform(put("/api/branding/theme")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"themeId\":\"oled\"}")
                        .with(user(staff)))
                .andExpect(status().isForbidden());
    }
}
