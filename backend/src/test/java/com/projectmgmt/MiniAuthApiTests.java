package com.projectmgmt;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class MiniAuthApiTests {

    @Autowired
    MockMvc mockMvc;

    @Test
    void miniLoginTokenCanReadWorkbenchAndSso() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/mini-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.user.username").value("admin"))
                .andExpect(jsonPath("$.data.token").isString())
                .andReturn();

        String token = jsonField(login.getResponse().getContentAsString(), "token");

        mockMvc.perform(get("/api/mobile/workbench").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].title").value("日常办公"))
                .andExpect(jsonPath("$.data[0].items[0].label").value("面试"));

        mockMvc.perform(get("/api/mobile/form-schema").param("module", "OA_LEAVE").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.module").value("OA_LEAVE"))
                .andExpect(jsonPath("$.data.slug").value("leave"))
                .andExpect(jsonPath("$.data.saveUrl").value("/api/oa/leave"));

        mockMvc.perform(get("/api/mobile/form-schema").param("module", "DEVICE").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.module").value("DEVICE"))
                .andExpect(jsonPath("$.data.canCreate").value(true))
                .andExpect(jsonPath("$.data.saveUrl").value("/api/devices"));

        MvcResult ticket = mockMvc.perform(post("/api/auth/mini-ticket").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ticket").isString())
                .andReturn();
        String code = jsonField(ticket.getResponse().getContentAsString(), "ticket");

        mockMvc.perform(get("/api/auth/mini-sso").param("ticket", code).param("next", "/m/app/oa/leave/new"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/m/app/oa/leave/new"));
    }

    private static String jsonField(String json, String name) {
        String key = "\"" + name + "\":\"";
        int start = json.indexOf(key);
        if (start < 0) {
            throw new IllegalStateException(json);
        }
        start += key.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }
}
