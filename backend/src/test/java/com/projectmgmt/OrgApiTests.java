package com.projectmgmt;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
class OrgApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Test
    void departmentTreeAndUserAssignment() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        String suffix = String.valueOf(System.currentTimeMillis());

        String hqBody = mockMvc.perform(post("/api/org/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"总部%s","sortOrder":1}
                                """.formatted(suffix))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("总部" + suffix))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer hqId = JsonPath.read(hqBody, "$.data.id");

        String eastBody = mockMvc.perform(post("/api/org/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"华东%s","parentId":%s,"sortOrder":1}
                                """.formatted(suffix, hqId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parentId").value(hqId))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer eastId = JsonPath.read(eastBody, "$.data.id");

        mockMvc.perform(put("/api/org/departments/" + hqId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"总部%s","parentId":%s,"sortOrder":1}
                                """.formatted(suffix, eastId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("上级部门不能形成循环"));

        String staffName = "orgstaff" + suffix;
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Staff123!","displayName":"组织员工","role":"USER","departmentId":%s,"deptLeader":false}
                                """.formatted(staffName, eastId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.departmentId").value(eastId))
                .andExpect(jsonPath("$.data.deptLeader").value(false));

        mockMvc.perform(delete("/api/org/departments/" + eastId).with(user(new AuthUser(admin))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请先把该部门人员调整到其他部门"));

        var staff = userRepository.findByUsername("liuxiaolin").orElseThrow();
        mockMvc.perform(get("/api/org/departments").with(user(new AuthUser(staff))))
                .andExpect(status().isOk());
    }

    @Test
    void assignMultipleMembersAtOnce() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        String suffix = String.valueOf(System.currentTimeMillis());
        Integer deptId = JsonPath.read(mockMvc.perform(post("/api/org/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"批量部门%s","sortOrder":1}
                                """.formatted(suffix))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");
        Integer oneId = JsonPath.read(mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"batcha%s","password":"Staff123!","displayName":"批量甲","role":"USER"}
                                """.formatted(suffix.substring(suffix.length() - 8)))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");
        Integer twoId = JsonPath.read(mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"batchb%s","password":"Staff123!","displayName":"批量乙","role":"USER"}
                                """.formatted(suffix.substring(suffix.length() - 8)))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");
        mockMvc.perform(post("/api/org/departments/" + deptId + "/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userIds":[%s,%s],"deptLeader":false}
                                """.formatted(oneId, twoId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id==%s)]".formatted(oneId)).exists())
                .andExpect(jsonPath("$.data[?(@.id==%s)]".formatted(twoId)).exists());
    }

    @Test
    void rolePermissionsGateOrgViewEditAndDelete() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        String suffix = String.valueOf(System.currentTimeMillis());

        Integer hqId = JsonPath.read(mockMvc.perform(post("/api/org/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"权限总部%s","sortOrder":1}
                                """.formatted(suffix))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");
        Integer eastId = JsonPath.read(mockMvc.perform(post("/api/org/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"权限华东%s","parentId":%s,"sortOrder":1}
                                """.formatted(suffix, hqId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");

        String viewRole = createRole(admin, "组织只看" + suffix, """
                [{"module":"ORG","view":true,"create":false,"edit":false,"delete":false}]
                """);
        String editRole = createRole(admin, "组织编辑" + suffix, """
                [{"module":"ORG","view":true,"create":false,"edit":true,"delete":false}]
                """);
        String hrRole = createRole(admin, "账号无组织" + suffix, """
                [{"module":"USER","view":true,"create":true,"edit":true,"delete":false},{"module":"ORG","view":false,"create":false,"edit":false,"delete":false}]
                """);

        String viewerName = "orgview" + suffix;
        String editorName = "orgedit" + suffix;
        String hrName = "orghr" + suffix;
        String memberName = "orgmem" + suffix;
        createUser(admin, viewerName, "只看组织", viewRole, hqId, false);
        createUser(admin, editorName, "编辑组织", editRole, hqId, false);
        createUser(admin, hrName, "账号编辑", hrRole, null, false);
        createUser(admin, memberName, "待调人员", "USER", eastId, false);
        var viewer = userRepository.findByUsername(viewerName).orElseThrow();
        var editor = userRepository.findByUsername(editorName).orElseThrow();
        var hr = userRepository.findByUsername(hrName).orElseThrow();
        var member = userRepository.findByUsername(memberName).orElseThrow();

        mockMvc.perform(get("/api/org/departments").with(user(new AuthUser(viewer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id==%s)].name".formatted(hqId)).exists());
        mockMvc.perform(get("/api/org/departments/" + hqId + "/members").with(user(new AuthUser(viewer))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/org/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"禁止新建%s","sortOrder":9}
                                """.formatted(suffix))
                        .with(user(new AuthUser(viewer))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/org/departments/" + hqId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"禁止改名%s","sortOrder":1}
                                """.formatted(suffix))
                        .with(user(new AuthUser(viewer))))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/org/departments/" + eastId).with(user(new AuthUser(viewer))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/org/departments/" + hqId + "/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%s,"deptLeader":false}
                                """.formatted(member.getId()))
                        .with(user(new AuthUser(viewer))))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/org/departments/" + eastId + "/members/" + member.getId())
                        .with(user(new AuthUser(viewer))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/org/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"编辑不可新建%s","sortOrder":9}
                                """.formatted(suffix))
                        .with(user(new AuthUser(editor))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/org/departments/" + hqId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"权限总部%s","sortOrder":2}
                                """.formatted(suffix))
                        .with(user(new AuthUser(editor))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sortOrder").value(2));
        mockMvc.perform(delete("/api/org/departments/" + eastId).with(user(new AuthUser(editor))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/org/departments/" + hqId + "/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%s,"deptLeader":false}
                                """.formatted(member.getId()))
                        .with(user(new AuthUser(editor))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id==%s)]".formatted(member.getId())).exists());

        mockMvc.perform(get("/api/org/departments").with(user(new AuthUser(hr))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/users/" + member.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"待调人员","role":"USER","departmentId":%s,"deptLeader":false}
                                """.formatted(eastId))
                        .with(user(new AuthUser(hr))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/users/" + member.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"待调人员改名","role":"USER","departmentId":%s,"deptLeader":false}
                                """.formatted(hqId))
                        .with(user(new AuthUser(hr))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("待调人员改名"))
                .andExpect(jsonPath("$.data.departmentId").value(hqId));
    }

    private String createRole(com.projectmgmt.user.SysUser admin, String name, String permissionsJson) throws Exception {
        String body = mockMvc.perform(post("/api/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","permissions":%s}
                                """.formatted(name, permissionsJson))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.data.code");
    }

    @Test
    void orgSupervisorAndChainThenReassignPending() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        String suffix = String.valueOf(System.currentTimeMillis());

        Integer hqId = JsonPath.read(mockMvc.perform(post("/api/org/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"总部链" + suffix + "\",\"sortOrder\":1}")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");
        Integer eastId = JsonPath.read(mockMvc.perform(post("/api/org/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"华东链" + suffix + "\",\"parentId\":" + hqId + ",\"sortOrder\":1}")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");

        String staffName = "ogstaff" + suffix;
        String midName = "ogmid" + suffix;
        String topName = "ogtop" + suffix;
        String altName = "ogalt" + suffix;
        createUser(admin, staffName, "链上员工", eastId, false);
        createUser(admin, midName, "华东主管", eastId, true);
        createUser(admin, topName, "总部主管", hqId, true);
        createUser(admin, altName, "新华东主管", eastId, false);

        var staff = userRepository.findByUsername(staffName).orElseThrow();
        var mid = userRepository.findByUsername(midName).orElseThrow();
        var top = userRepository.findByUsername(topName).orElseThrow();
        var alt = userRepository.findByUsername(altName).orElseThrow();

        String wfCode = "ORG" + suffix;
        long bizId = System.currentTimeMillis();
        String created = mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "组织审批",
                                  "code": "%s",
                                  "formModule": "CUSTOMER",
                                  "graph": {
                                    "nodes": [
                                      {"key":"start","name":"开始","type":"START","x":80,"y":160,"fields":{}},
                                      {"key":"review","name":"审核","type":"APPROVAL","x":280,"y":160,"fields":{},"assigneeType":"ORG_SUPERVISOR"},
                                      {"key":"end","name":"结束","type":"END","x":480,"y":160,"fields":{}}
                                    ],
                                    "edges": [
                                      {"from":"start","to":"review","isDefault":true},
                                      {"from":"review","to":"end","isDefault":true}
                                    ]
                                  }
                                }
                                """.formatted(wfCode))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String wfId = created.replaceAll(".*\"id\":(\\d+).*", "$1");
        mockMvc.perform(post("/api/workflows/" + wfId + "/publish").with(user(new AuthUser(admin))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/workflows/runtime")
                        .param("module", "CUSTOMER")
                        .param("bizId", String.valueOf(bizId))
                        .with(user(new AuthUser(staff))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentNodeKey").value("start"))
                .andExpect(jsonPath("$.data.canAdvance").value(true));
        WorkflowTestSupport.submitStart(mockMvc, new AuthUser(staff), "CUSTOMER", bizId);

        mockMvc.perform(get("/api/workflows/runtime")
                        .param("module", "CUSTOMER")
                        .param("bizId", String.valueOf(bizId))
                        .with(user(new AuthUser(staff))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentNodeKey").value("review"))
                .andExpect(jsonPath("$.data.currentAssignees").value(Matchers.hasItem("华东主管")))
                .andExpect(jsonPath("$.data.canAdvance").value(false));

        mockMvc.perform(get("/api/workflows/runtime")
                        .param("module", "CUSTOMER")
                        .param("bizId", String.valueOf(bizId))
                        .with(user(new AuthUser(mid))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canAdvance").value(true));

        mockMvc.perform(put("/api/users/" + alt.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"新华东主管","role":"USER","departmentId":%s,"deptLeader":true}
                                """.formatted(eastId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/users/" + mid.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"华东主管","role":"USER","departmentId":%s,"deptLeader":false}
                                """.formatted(eastId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/workflows/runtime")
                        .param("module", "CUSTOMER")
                        .param("bizId", String.valueOf(bizId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentAssignees").value(Matchers.hasItem("新华东主管")))
                .andExpect(jsonPath("$.data.currentAssignees").value(Matchers.not(Matchers.hasItem("华东主管"))));

        long chainBiz = bizId + 1;
        String chainCode = "CHN" + suffix;
        String chainCreated = mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "逐级审批",
                                  "code": "%s",
                                  "formModule": "SUPPLIER",
                                  "graph": {
                                    "nodes": [
                                      {"key":"start","name":"开始","type":"START","x":80,"y":160,"fields":{}},
                                      {"key":"review","name":"逐级","type":"APPROVAL","x":280,"y":160,"fields":{},"assigneeType":"ORG_CHAIN","assigneeLevels":0},
                                      {"key":"end","name":"结束","type":"END","x":480,"y":160,"fields":{}}
                                    ],
                                    "edges": [
                                      {"from":"start","to":"review","isDefault":true},
                                      {"from":"review","to":"end","isDefault":true}
                                    ]
                                  }
                                }
                                """.formatted(chainCode))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String chainId = chainCreated.replaceAll(".*\"id\":(\\d+).*", "$1");
        mockMvc.perform(post("/api/workflows/" + chainId + "/publish").with(user(new AuthUser(admin))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/workflows/runtime")
                        .param("module", "SUPPLIER")
                        .param("bizId", String.valueOf(chainBiz))
                        .with(user(new AuthUser(staff))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentNodeKey").value("start"));
        WorkflowTestSupport.submitStart(mockMvc, new AuthUser(staff), "SUPPLIER", chainBiz);

        mockMvc.perform(get("/api/workflows/runtime")
                        .param("module", "SUPPLIER")
                        .param("bizId", String.valueOf(chainBiz))
                        .with(user(new AuthUser(staff))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentNodeKey").value("review"))
                .andExpect(jsonPath("$.data.currentAssignees").value(Matchers.hasItem("新华东主管")));

        mockMvc.perform(post("/api/workflows/advance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"formModule":"SUPPLIER","bizId":%s,"comment":"华东同意"}
                                """.formatted(chainBiz))
                        .with(user(new AuthUser(alt))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentNodeKey").value("review"))
                .andExpect(jsonPath("$.data.currentAssignees").value(Matchers.hasItem("总部主管")));

        mockMvc.perform(post("/api/workflows/advance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"formModule":"SUPPLIER","bizId":%s,"comment":"总部同意"}
                                """.formatted(chainBiz))
                        .with(user(new AuthUser(top))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DONE"));

        mockMvc.perform(post("/api/workflows/" + wfId + "/unpublish").with(user(new AuthUser(admin))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/workflows/" + chainId + "/unpublish").with(user(new AuthUser(admin))))
                .andExpect(status().isOk());
    }

    private void createUser(com.projectmgmt.user.SysUser admin, String username, String name, Integer deptId, boolean leader)
            throws Exception {
        createUser(admin, username, name, "USER", deptId, leader);
    }

    private void createUser(
            com.projectmgmt.user.SysUser admin,
            String username,
            String name,
            String role,
            Integer deptId,
            boolean leader
    ) throws Exception {
        String deptJson = deptId == null ? "null" : String.valueOf(deptId);
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Staff123!","displayName":"%s","role":"%s","departmentId":%s,"deptLeader":%s}
                                """.formatted(username, name, role, deptJson, leader))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk());
        var created = userRepository.findByUsername(username).orElseThrow();
        created.setMustChangePassword(false);
        userRepository.save(created);
    }
}
