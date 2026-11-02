package com.projectmgmt;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    CustomerRepository customerRepository;

    @Autowired
    RegionRepository regionRepository;

    @Test
    void createProjectWithAttachment() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        var manager = userRepository.findByUsername("liuxiaolin").orElseThrow();
        var customer = customerRepository.findByCode("133").orElseThrow();
        var region = regionRepository.findByName("华东地区").orElseThrow();

        mockMvc.perform(multipart("/api/projects")
                        .file(new MockMultipartFile("files", "合同.pdf", "application/pdf", "demo".getBytes()))
                        .param("name", "苏州冠礼133-003上海拓荆软件编程现场端费用12000")
                        .param("code", "133-003")
                        .param("customerId", String.valueOf(customer.getId()))
                        .param("managerId", String.valueOf(manager.getId()))
                        .param("regionId", String.valueOf(region.getId()))
                        .param("overview", "暂无内容")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.code").value("133-003"))
                .andExpect(jsonPath("$.data.statusLabel").value("已立项"))
                .andExpect(jsonPath("$.data.companyCode").value("133"))
                .andExpect(jsonPath("$.data.attachments[0].originalName").value("合同.pdf"));
    }

    @Test
    void updateAndDeleteProject() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        var manager = userRepository.findByUsername("liuxiaolin").orElseThrow();
        var customer = customerRepository.findByCode("133").orElseThrow();
        var region = regionRepository.findByName("华东地区").orElseThrow();
        String code = "UT-P-" + System.currentTimeMillis();

        String body = mockMvc.perform(multipart("/api/projects")
                        .param("name", "待改项目")
                        .param("code", code)
                        .param("customerId", String.valueOf(customer.getId()))
                        .param("managerId", String.valueOf(manager.getId()))
                        .param("regionId", String.valueOf(region.getId()))
                        .param("overview", "原稿")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer id = JsonPath.read(body, "$.data.id");

        mockMvc.perform(multipart("/api/projects/" + id)
                        .param("name", "已改项目")
                        .param("code", code)
                        .param("customerId", String.valueOf(customer.getId()))
                        .param("managerId", String.valueOf(manager.getId()))
                        .param("regionId", String.valueOf(region.getId()))
                        .param("overview", "改后")
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("已改项目"))
                .andExpect(jsonPath("$.data.overview").value("改后"));

        mockMvc.perform(delete("/api/projects/" + id).with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/projects/" + id).with(user(new AuthUser(admin))))
                .andExpect(status().isNotFound());
    }

    @Test
    void progressUpdatesProjectOverviewAndKeepsHistory() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        var manager = userRepository.findByUsername("liuxiaolin").orElseThrow();
        var customer = customerRepository.findByCode("133").orElseThrow();
        var region = regionRepository.findByName("华东地区").orElseThrow();
        String code = "UT-PG-" + System.currentTimeMillis();

        String created = mockMvc.perform(multipart("/api/projects")
                        .param("name", "进展项目")
                        .param("code", code)
                        .param("customerId", String.valueOf(customer.getId()))
                        .param("managerId", String.valueOf(manager.getId()))
                        .param("regionId", String.valueOf(region.getId()))
                        .param("overview", "原稿概况")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer id = JsonPath.read(created, "$.data.id");

        mockMvc.perform(multipart("/api/projects/" + id + "/progress")
                        .file(new MockMultipartFile("files", "现场.png", "image/png", "img".getBytes()))
                        .param("content", "完成基础施工")
                        .param("percent", "30")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("完成基础施工"))
                .andExpect(jsonPath("$.data.percent").value(30))
                .andExpect(jsonPath("$.data.attachments[0].originalName").value("现场.png"));

        mockMvc.perform(get("/api/projects/" + id).with(user(new AuthUser(admin))))
                .andExpect(jsonPath("$.data.overview").value("完成基础施工"))
                .andExpect(jsonPath("$.data.progressPercent").value(30));

        mockMvc.perform(multipart("/api/projects/" + id + "/progress")
                        .param("content", "设备进场调试")
                        .param("percent", "80")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/projects/" + id).with(user(new AuthUser(admin))))
                .andExpect(jsonPath("$.data.overview").value("设备进场调试"))
                .andExpect(jsonPath("$.data.progressPercent").value(80));
        mockMvc.perform(get("/api/projects/" + id + "/progress").with(user(new AuthUser(admin))))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].content").value("设备进场调试"))
                .andExpect(jsonPath("$.data[1].content").value("完成基础施工"));
    }

    @Test
    void editAfterWorkflowCompleted() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        var manager = userRepository.findByUsername("liuxiaolin").orElseThrow();
        var customer = customerRepository.findByCode("133").orElseThrow();
        var region = regionRepository.findByName("华北地区").orElseThrow();
        String code = "UT-DONE-" + System.currentTimeMillis();

        String body = mockMvc.perform(multipart("/api/projects")
                        .param("name", "流程结束前")
                        .param("code", code)
                        .param("customerId", String.valueOf(customer.getId()))
                        .param("managerId", String.valueOf(manager.getId()))
                        .param("regionId", String.valueOf(region.getId()))
                        .param("overview", "原稿")
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer id = JsonPath.read(body, "$.data.id");

        WorkflowTestSupport.complete(mockMvc, admin, "PROJECT", id);

        mockMvc.perform(multipart("/api/projects/" + id)
                        .param("name", "流程结束后改名")
                        .param("code", code)
                        .param("customerId", String.valueOf(customer.getId()))
                        .param("managerId", String.valueOf(manager.getId()))
                        .param("regionId", String.valueOf(region.getId()))
                        .param("overview", "结束后可改")
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("流程结束后改名"))
                .andExpect(jsonPath("$.data.overview").value("结束后可改"));
    }
}
