package com.projectmgmt;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.projectmgmt.security.AuthUser;
import com.projectmgmt.user.UserRepository;
import com.projectmgmt.user.UserSalaryItem;
import com.projectmgmt.user.UserSalaryItemRepository;
import com.projectmgmt.user.UserStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OaApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    UserSalaryItemRepository userSalaryItemRepository;

    @Test
    void onboardCreatesLoginAccountAfterApproval() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        String suffix = String.valueOf(System.currentTimeMillis());
        String username = "rz" + suffix.substring(suffix.length() - 8);
        Integer deptId = createDept(admin, "OA入职部门" + suffix);

        Integer bizId = JsonPath.read(mockMvc.perform(post("/api/oa/onboard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "applicantName": "测试入职%s",
                                  "mobile": "13800000000",
                                  "departmentId": %d,
                                  "position": "工程师",
                                  "jobLevel": "P1",
                                  "expectedDate": "2026-09-10",
                                  "reason": "公开招聘入职",
                                  "loginUsername": "%s",
                                  "password": "Onboard123!",
                                  "salary": 99999,
                                  "salaryLines": [{"itemName":"基本工资","amount":99999}]
                                }
                                """.formatted(suffix, deptId, username))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginUsername").value(username))
                .andExpect(jsonPath("$.data.hasLoginPassword").value(true))
                .andExpect(jsonPath("$.data.applied").value(false))
                .andExpect(jsonPath("$.data.salary").value(org.hamcrest.Matchers.nullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");

        complete(admin, "OA_ONBOARD", bizId);

        var created = userRepository.findByUsername(username).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("USER", created.getRole());
        org.junit.jupiter.api.Assertions.assertEquals("工程师", created.getPosition());
        org.junit.jupiter.api.Assertions.assertEquals("P1", created.getJobLevel());
        org.junit.jupiter.api.Assertions.assertEquals(UserStatus.ENABLED, created.getStatus());
        org.junit.jupiter.api.Assertions.assertFalse(created.isMustChangePassword());
        org.junit.jupiter.api.Assertions.assertEquals(java.time.LocalDate.of(2026, 9, 10), created.getHireDate());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"Onboard123!\"}".formatted(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    void transferAndPromotionUpdateStaffOrgAndResignDisablesAccount() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        String suffix = String.valueOf(System.currentTimeMillis());
        String username = "yg" + suffix.substring(suffix.length() - 8);
        Integer fromDept = createDept(admin, "OA原部门" + suffix);
        Integer toDept = createDept(admin, "OA新部门" + suffix);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"%s",
                                  "password":"Staff123!",
                                  "displayName":"待转岗%s",
                                  "role":"USER",
                                  "departmentId":%s,
                                  "deptLeader":false,
                                  "position":"专员",
                                  "jobLevel":"P1"
                                }
                                """.formatted(username, suffix, fromDept))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk());
        var staff = userRepository.findByUsername(username).orElseThrow();
        staff.setMustChangePassword(false);
        userRepository.save(staff);

        Integer transferId = JsonPath.read(mockMvc.perform(post("/api/oa/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "staffId": %d,
                                  "applicantName": "待转岗%s",
                                  "departmentId": %d,
                                  "position": "专员",
                                  "targetDepartmentId": %d,
                                  "targetPosition": "主管",
                                  "expectedDate": "2026-09-20",
                                  "reason": "业务需要转岗"
                                }
                                """.formatted(staff.getId(), suffix, fromDept, toDept))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");
        complete(admin, "OA_TRANSFER", transferId);
        var moved = userRepository.findByUsername(username).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(toDept.longValue(), moved.getDepartment().getId());
        org.junit.jupiter.api.Assertions.assertEquals("主管", moved.getPosition());

        Integer promoId = JsonPath.read(mockMvc.perform(post("/api/oa/promotion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "staffId": %d,
                                  "applicantName": "待转岗%s",
                                  "departmentId": %d,
                                  "position": "主管",
                                  "jobLevel": "P1",
                                  "targetPosition": "经理",
                                  "targetLevel": "M2",
                                  "expectedDate": "2026-10-01",
                                  "reason": "业绩达标晋升"
                                }
                                """.formatted(staff.getId(), suffix, toDept))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");
        complete(admin, "OA_PROMOTION", promoId);
        var promoted = userRepository.findByUsername(username).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("经理", promoted.getPosition());
        org.junit.jupiter.api.Assertions.assertEquals("M2", promoted.getJobLevel());
        org.junit.jupiter.api.Assertions.assertEquals(toDept.longValue(), promoted.getDepartment().getId());

        Integer resignId = JsonPath.read(mockMvc.perform(post("/api/oa/resign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "staffId": %d,
                                  "applicantName": "待转岗%s",
                                  "departmentId": %d,
                                  "position": "经理",
                                  "resignType": "VOLUNTARY",
                                  "expectedDate": "2026-10-31",
                                  "reason": "个人原因离职"
                                }
                                """.formatted(staff.getId(), suffix, toDept))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");
        complete(admin, "OA_RESIGN", resignId);
        org.junit.jupiter.api.Assertions.assertEquals(
                UserStatus.DISABLED,
                userRepository.findByUsername(username).orElseThrow().getStatus()
        );
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"Staff123!\"}".formatted(username)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("账号已停用"));
    }

    @Test
    void salaryCopiedOnTransferAndReviewCanChangeOnboardSalary() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        String suffix = String.valueOf(System.currentTimeMillis());
        Integer deptId = createDept(admin, "OA薪资部门" + suffix);

        String username = "xz" + suffix.substring(suffix.length() - 8);
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"%s",
                                  "password":"Staff123!",
                                  "displayName":"调薪员工%s",
                                  "role":"USER",
                                  "departmentId":%s,
                                  "deptLeader":false,
                                  "position":"专员",
                                  "jobLevel":"P1"
                                }
                                """.formatted(username, suffix, deptId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk());
        var staff = userRepository.findByUsername(username).orElseThrow();
        staff.setMustChangePassword(false);
        staff.setSalary(new BigDecimal("10000.00"));
        userRepository.save(staff);
        UserSalaryItem item = new UserSalaryItem();
        item.setUser(staff);
        item.setItemName("基本工资");
        item.setAmount(new BigDecimal("8000.00"));
        item.setSortOrder(0);
        userSalaryItemRepository.save(item);

        mockMvc.perform(post("/api/oa/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "staffId": %d,
                                  "applicantName": "调薪员工%s",
                                  "departmentId": %d,
                                  "position": "专员",
                                  "targetDepartmentId": %d,
                                  "targetPosition": "主管",
                                  "expectedDate": "2026-09-20",
                                  "reason": "转岗并核对薪资"
                                }
                                """.formatted(staff.getId(), suffix, deptId, deptId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.salary").value(10000.00))
                .andExpect(jsonPath("$.data.salaryLines[0].itemName").value("基本工资"));

        Integer salaryId = JsonPath.read(mockMvc.perform(post("/api/oa/salary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "staffId": %d,
                                  "applicantName": "调薪员工%s",
                                  "departmentId": %d,
                                  "position": "专员",
                                  "expectedDate": "2026-10-01",
                                  "reason": "岗位津贴调整"
                                }
                                """.formatted(staff.getId(), suffix, deptId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.salary").value(10000.00))
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");
        WorkflowTestSupport.submitStart(mockMvc, new AuthUser(admin), "OA_SALARY", salaryId);
        advance(admin, "OA_SALARY", salaryId, "填写完成");
        mockMvc.perform(put("/api/oa/salary/" + salaryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "staffId": %d,
                                  "applicantName": "调薪员工%s",
                                  "departmentId": %d,
                                  "position": "专员",
                                  "expectedDate": "2026-10-01",
                                  "reason": "岗位津贴调整",
                                  "salary": 12000,
                                  "salaryLines": [
                                    {"itemName":"基本工资","amount":8000},
                                    {"itemName":"岗位津贴","amount":4000}
                                  ]
                                }
                                """.formatted(staff.getId(), suffix, deptId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.salary").value(12000.00))
                .andExpect(jsonPath("$.data.salaryLines.length()").value(2));
        WorkflowTestSupport.complete(mockMvc, admin, "OA_SALARY", salaryId);
        var adjusted = userRepository.findByUsername(username).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(0, new BigDecimal("12000.00").compareTo(adjusted.getSalary()));
        org.junit.jupiter.api.Assertions.assertEquals(2, userSalaryItemRepository.countByUser_Id(adjusted.getId()));

        String onboardUser = "rzs" + suffix.substring(suffix.length() - 8);
        Integer onboardId = JsonPath.read(mockMvc.perform(post("/api/oa/onboard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "applicantName": "入职核薪%s",
                                  "mobile": "13800000001",
                                  "departmentId": %d,
                                  "position": "工程师",
                                  "jobLevel": "P1",
                                  "expectedDate": "2026-09-10",
                                  "reason": "入职核薪",
                                  "loginUsername": "%s",
                                  "password": "Onboard123!"
                                }
                                """.formatted(suffix, deptId, onboardUser))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.salary").value(org.hamcrest.Matchers.nullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");
        WorkflowTestSupport.submitStart(mockMvc, new AuthUser(admin), "OA_ONBOARD", onboardId);
        advance(admin, "OA_ONBOARD", onboardId, "填写完成");
        mockMvc.perform(put("/api/oa/onboard/" + onboardId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "applicantName": "入职核薪%s",
                                  "mobile": "13800000001",
                                  "departmentId": %d,
                                  "position": "工程师",
                                  "jobLevel": "P1",
                                  "expectedDate": "2026-09-10",
                                  "reason": "入职核薪",
                                  "loginUsername": "%s",
                                  "salary": 9000,
                                  "salaryLines": [{"itemName":"基本工资","amount":9000}]
                                }
                                """.formatted(suffix, deptId, onboardUser))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.salary").value(9000.00));
        WorkflowTestSupport.complete(mockMvc, admin, "OA_ONBOARD", onboardId);
        var onboarded = userRepository.findByUsername(onboardUser).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(0, new BigDecimal("9000.00").compareTo(onboarded.getSalary()));
        org.junit.jupiter.api.Assertions.assertEquals(1, userSalaryItemRepository.countByUser_Id(onboarded.getId()));
    }

    @Test
    void interviewSubmitWithoutStaffAccount() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        String suffix = String.valueOf(System.currentTimeMillis());
        mockMvc.perform(post("/api/oa/interview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "applicantName": "面试者%s",
                                  "mobile": "13900001111",
                                  "gender": "MALE",
                                  "expectedDate": "2026-09-15",
                                  "interview": {
                                    "ethnicity": "汉",
                                    "maritalStatus": "SINGLE",
                                    "school": "测试大学",
                                    "major": "计算机",
                                    "highestEducation": "本科",
                                    "educationLines": [{"title":"测试大学","subtitle":"计算机","startYm":"2018-09","endYm":"2022-06"}]
                                  }
                                }
                                """.formatted(suffix))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kindLabel").value("面试申请"))
                .andExpect(jsonPath("$.data.mobile").value("13900001111"))
                .andExpect(jsonPath("$.data.interview.school").value("测试大学"))
                .andExpect(jsonPath("$.data.interview.educationLines[0].title").value("测试大学"));
    }

    @Test
    void sealAndQualifySubmitWithNestedFields() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        String suffix = String.valueOf(System.currentTimeMillis());
        Integer deptId = createDept(admin, "OA盖章部门" + suffix);

        mockMvc.perform(post("/api/oa/seal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "applicantName": "盖章人%s",
                                  "staffId": %d,
                                  "departmentId": %d,
                                  "expectedDate": "2026-09-03",
                                  "reason": "项目合同盖章",
                                  "seal": {
                                    "belongCompany": "西安赛仕智能科技有限公司",
                                    "sealTypes": ["公章", "财务章"],
                                    "copyCount": "2"
                                  }
                                }
                                """.formatted(suffix, admin.getId(), deptId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kindLabel").value("盖章申请"))
                .andExpect(jsonPath("$.data.seal.belongCompany").value("西安赛仕智能科技有限公司"))
                .andExpect(jsonPath("$.data.seal.copyCount").value("2"));

        mockMvc.perform(post("/api/oa/qualify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "applicantName": "借证人%s",
                                  "staffId": %d,
                                  "departmentId": %d,
                                  "expectedDate": "2026-09-03",
                                  "qualify": {
                                    "lendDate": "2026-09-04",
                                    "returnDate": "2026-09-10",
                                    "companies": ["西安赛仕智能科技有限公司"],
                                    "items": ["公章", "营业执照副本"]
                                  }
                                }
                                """.formatted(suffix, admin.getId(), deptId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kindLabel").value("资质借出"))
                .andExpect(jsonPath("$.data.qualify.lendDate").value("2026-09-04"))
                .andExpect(jsonPath("$.data.qualify.items[0]").value("公章"));

        mockMvc.perform(post("/api/oa/trip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "applicantName": "出差人%s",
                                  "staffId": %d,
                                  "departmentId": %d,
                                  "expectedDate": "2026-09-10",
                                  "reason": "拜访客户XX项目",
                                  "trip": {
                                    "fromCity": "陕西省 / 西安市 / 碑林区",
                                    "toCity": "北京市 / 北京市 / 海淀区",
                                    "travelMode": "PUBLIC",
                                    "plateNo": "无",
                                    "mileage": 0,
                                    "durationHours": 16
                                  }
                                }
                                """.formatted(suffix, admin.getId(), deptId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kindLabel").value("出差申请"))
                .andExpect(jsonPath("$.data.trip.toCity").value("北京市 / 北京市 / 海淀区"))
                .andExpect(jsonPath("$.data.trip.travelMode").value("PUBLIC"));
    }

    @Test
    void personnelPortalListsStaffAndRespectsViewPermission() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        var staff = userRepository.findByUsername("liuxiaolin").orElseThrow();

        mockMvc.perform(get("/api/personnel").with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[?(@.displayName=='系统管理员')]").exists());

        mockMvc.perform(get("/api/personnel").with(user(new AuthUser(staff))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/personnel/" + admin.getId()).with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("系统管理员"));

        mockMvc.perform(get("/api/personnel/" + admin.getId() + "/requests").with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());

        mockMvc.perform(get("/api/personnel/" + admin.getId() + "/notices").with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    private Integer createDept(com.projectmgmt.user.SysUser admin, String name) throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/org/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","sortOrder":1}
                                """.formatted(name))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");
    }

    private void complete(com.projectmgmt.user.SysUser actor, String module, Integer bizId) throws Exception {
        WorkflowTestSupport.complete(mockMvc, actor, module, bizId);
    }

    private void advance(com.projectmgmt.user.SysUser actor, String module, Integer bizId, String comment) throws Exception {
        mockMvc.perform(post("/api/workflows/advance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"formModule\":\"%s\",\"bizId\":%s,\"comment\":\"%s\"}".formatted(module, bizId, comment))
                        .with(user(new AuthUser(actor))))
                .andExpect(status().isOk());
    }
}
