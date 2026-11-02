package com.projectmgmt;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.projectmgmt.security.AuthUser;
import com.projectmgmt.user.SysUser;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

final class WorkflowTestSupport {

    private WorkflowTestSupport() {
    }

    static void submitStart(MockMvc mockMvc, AuthUser actor, String formModule, Object bizId) throws Exception {
        advanceOnce(mockMvc, actor, formModule, bizId, "提交");
    }

    static void complete(MockMvc mockMvc, SysUser actor, String formModule, Object bizId) throws Exception {
        complete(mockMvc, new AuthUser(actor), formModule, bizId);
    }

    static void complete(MockMvc mockMvc, AuthUser actor, String formModule, Object bizId) throws Exception {
        String status = null;
        for (int i = 0; i < 8; i++) {
            MvcResult result = advanceOnce(mockMvc, actor, formModule, bizId, "办理");
            status = JsonPath.read(result.getResponse().getContentAsString(), "$.data.status");
            if ("DONE".equals(status)) {
                return;
            }
        }
        throw new AssertionError("流程未结束，当前状态: " + status);
    }

    static MvcResult advanceOnce(MockMvc mockMvc, AuthUser actor, String formModule, Object bizId, String comment)
            throws Exception {
        return mockMvc.perform(post("/api/workflows/advance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"formModule\":\"%s\",\"bizId\":%s,\"comment\":\"%s\"}".formatted(
                                formModule, bizId, comment))
                        .with(user(actor)))
                .andExpect(status().isOk())
                .andReturn();
    }
}
