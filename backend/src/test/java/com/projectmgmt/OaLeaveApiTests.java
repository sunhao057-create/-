package com.projectmgmt;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
class OaLeaveApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Test
    void createLeaveRequiresTypeAndDays() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        Integer deptId = JsonPath.read(mockMvc.perform(post("/api/org/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"请假部门" + System.currentTimeMillis() + "\",\"sortOrder\":1}")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");

        mockMvc.perform(post("/api/oa/leave")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "staffId": %d,
                                  "departmentId": %d,
                                  "expectedDate": "2026-09-07",
                                  "reason": "家中有事",
                                  "leave": {
                                    "holidayDays": 0,
                                    "includeCompensatory": false,
                                    "month": "2026-09",
                                    "leaveType": "PERSONAL",
                                    "endDate": "2026-09-08",
                                    "leaveDays": 2
                                  }
                                }
                                """.formatted(admin.getId(), deptId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("LEAVE"))
                .andExpect(jsonPath("$.data.leave.leaveType").value("PERSONAL"))
                .andExpect(jsonPath("$.data.leave.leaveTypeLabel").value("事假"))
                .andExpect(jsonPath("$.data.leave.leaveDays").value(2.00))
                .andExpect(jsonPath("$.data.leave.endDate").value("2026-09-08"));
    }
}
