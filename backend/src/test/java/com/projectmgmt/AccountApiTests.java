package com.projectmgmt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import com.projectmgmt.security.AuthUser;
import com.projectmgmt.user.SysUser;
import com.projectmgmt.user.UserRepository;
import com.projectmgmt.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AccountApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void loginCookiePersistsUntilLogout() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin123!\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie session = login.getResponse().getCookie("PMSESSION");
        assertThat(session).isNotNull();
        assertThat(session.getMaxAge()).isEqualTo((int) java.time.Duration.ofDays(399).toSeconds());

        mockMvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"));

        mockMvc.perform(post("/api/auth/logout").cookie(session))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginSuccessAndRejectNormalUserFromAdminApi() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("admin"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));

        SysUser staff = new SysUser();
        staff.setUsername("staff01");
        staff.setPasswordHash(passwordEncoder.encode("Staff123!"));
        staff.setDisplayName("普通员工");
        staff.setRole("USER");
        staff.setStatus(UserStatus.ENABLED);
        staff.setMustChangePassword(false);
        userRepository.save(staff);

        AuthUser principal = new AuthUser(userRepository.findByUsername("staff01").orElseThrow());
        mockMvc.perform(get("/api/users").with(user(principal)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanSeeUserPasswords() throws Exception {
        AuthUser admin = new AuthUser(userRepository.findByUsername("admin").orElseThrow());
        mockMvc.perform(get("/api/users").param("keyword", "admin").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].username").value("admin"))
                .andExpect(jsonPath("$.data.items[0].password").value("Admin123!"));
    }

    @Test
    void adminCanDeleteOtherUserButNotSelf() throws Exception {
        AuthUser admin = new AuthUser(userRepository.findByUsername("admin").orElseThrow());
        String username = "deluser" + System.currentTimeMillis();

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Staff123!","displayName":"待删账号","role":"USER"}
                                """.formatted(username))
                        .with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists());

        Long id = userRepository.findByUsername(username).orElseThrow().getId();

        mockMvc.perform(delete("/api/users/" + admin.getId()).with(user(admin)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/users/" + id).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
