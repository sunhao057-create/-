package com.projectmgmt;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectmgmt.security.AuthUser;
import com.projectmgmt.user.UserRepository;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ListExportApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Test
    void listExportReturnsXlsxAndRejectsDeviceModule() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();

        byte[] xlsx = mockMvc.perform(post("/api/lists/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"module\":\"PROJECT\"}")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", Matchers.containsString("spreadsheetml")))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        org.assertj.core.api.Assertions.assertThat(xlsx.length).isGreaterThan(100);
        org.assertj.core.api.Assertions.assertThat(xlsx[0]).isEqualTo((byte) 'P');
        org.assertj.core.api.Assertions.assertThat(xlsx[1]).isEqualTo((byte) 'K');

        mockMvc.perform(post("/api/lists/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"module\":\"DEVICE\"}")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请使用设备台账导出"));

        mockMvc.perform(post("/api/lists/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"module\":\"OA_STAFF\"}")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", Matchers.containsString("spreadsheetml")));
    }
}
