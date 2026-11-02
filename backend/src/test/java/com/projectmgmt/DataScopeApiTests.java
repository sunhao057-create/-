package com.projectmgmt;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.projectmgmt.customer.CustomerRepository;
import com.projectmgmt.region.RegionRepository;
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
class DataScopeApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    CustomerRepository customerRepository;

    @Autowired
    RegionRepository regionRepository;

    @Test
    void ownScopeOnlySeesSubmittedRecordsAndCreateIsSeparateFromEdit() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        var manager = userRepository.findByUsername("liuxiaolin").orElseThrow();
        var customer = customerRepository.findByCode("133").orElseThrow();
        var region = regionRepository.findByName("华东地区").orElseThrow();
        String suffix = String.valueOf(System.currentTimeMillis());

        String roleBody = mockMvc.perform(post("/api/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "仅个人数据",
                                  "permissions": [
                                    {"module":"PROJECT","view":true,"create":true,"edit":true,"delete":false,"importExport":false,"dataScope":"OWN"},
                                    {"module":"PAYMENT","view":true,"create":false,"edit":true,"delete":false,"importExport":false,"dataScope":"OWN"},
                                    {"module":"CUSTOMER","view":true,"create":true,"edit":true,"delete":false,"importExport":false,"dataScope":"OWN"}
                                  ]
                                }
                                """)
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions[?(@.module=='PROJECT')].dataScope").value(Matchers.hasItem("OWN")))
                .andExpect(jsonPath("$.data.permissions[?(@.module=='PROJECT')].create").value(Matchers.hasItem(true)))
                .andExpect(jsonPath("$.data.permissions[?(@.module=='PROJECT')].hasDataScope").value(Matchers.hasItem(true)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String roleCode = JsonPath.read(roleBody, "$.data.code");

        String username = "own" + suffix;
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Staff123!","displayName":"个人数据员","role":"%s"}
                                """.formatted(username, roleCode))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk());
        var ownUser = enableLogin(username);

        String adminProjectCode = "ADM-" + suffix;
        String adminProjectBody = mockMvc.perform(multipart("/api/projects")
                        .param("name", "管理员项目")
                        .param("code", adminProjectCode)
                        .param("customerId", String.valueOf(customer.getId()))
                        .param("managerId", String.valueOf(manager.getId()))
                        .param("regionId", String.valueOf(region.getId()))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer adminProjectId = JsonPath.read(adminProjectBody, "$.data.id");

        mockMvc.perform(get("/api/projects").with(user(new AuthUser(ownUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.code=='" + adminProjectCode + "')]").value(Matchers.hasSize(0)));

        mockMvc.perform(get("/api/projects/" + adminProjectId).with(user(new AuthUser(ownUser))))
                .andExpect(status().isForbidden());

        String ownProjectCode = "OWN-" + suffix;
        mockMvc.perform(multipart("/api/projects")
                        .param("name", "个人项目")
                        .param("code", ownProjectCode)
                        .param("customerId", String.valueOf(customer.getId()))
                        .param("managerId", String.valueOf(manager.getId()))
                        .param("regionId", String.valueOf(region.getId()))
                        .with(user(new AuthUser(ownUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value(ownProjectCode));

        mockMvc.perform(get("/api/projects").with(user(new AuthUser(ownUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].code").value(ownProjectCode));

        mockMvc.perform(get("/api/customers").with(user(new AuthUser(ownUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code=='133')]").value(Matchers.hasSize(0)));

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shortName": "个人客户",
                                  "companyName": "个人客户有限公司",
                                  "code": "C%s",
                                  "level": "一般重要"
                                }
                                """.formatted(suffix.substring(suffix.length() - 6)))
                        .with(user(new AuthUser(ownUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shortName").value("个人客户"));

        mockMvc.perform(get("/api/customers").with(user(new AuthUser(ownUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.shortName=='个人客户')]").value(Matchers.hasSize(1)));

        String viewOnlyBody = mockMvc.perform(post("/api/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "仅查看项目",
                                  "permissions": [
                                    {"module":"PROJECT","view":true,"create":false,"edit":true,"delete":false,"importExport":false,"dataScope":"ALL"}
                                  ]
                                }
                                """)
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String viewOnlyCode = JsonPath.read(viewOnlyBody, "$.data.code");
        String viewer = "view" + suffix;
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Staff123!","displayName":"仅编辑不能新增","role":"%s"}
                                """.formatted(viewer, viewOnlyCode))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk());
        var viewerUser = enableLogin(viewer);
        mockMvc.perform(multipart("/api/projects")
                        .param("name", "不应创建")
                        .param("code", "NO-" + suffix)
                        .param("customerId", String.valueOf(customer.getId()))
                        .param("managerId", String.valueOf(manager.getId()))
                        .param("regionId", String.valueOf(region.getId()))
                        .with(user(new AuthUser(viewerUser))))
                .andExpect(status().isForbidden());
    }

    private com.projectmgmt.user.SysUser enableLogin(String username) {
        var user = userRepository.findByUsername(username).orElseThrow();
        user.setMustChangePassword(false);
        return userRepository.save(user);
    }
}
