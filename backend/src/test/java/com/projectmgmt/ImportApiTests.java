package com.projectmgmt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectmgmt.customer.CustomerRepository;
import com.projectmgmt.project.ProjectRepository;
import com.projectmgmt.security.AuthUser;
import com.projectmgmt.user.UserRepository;
import java.nio.charset.StandardCharsets;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ImportApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    CustomerRepository customerRepository;

    @Autowired
    ProjectRepository projectRepository;

    @Test
    void adminDownloadsCustomerTemplateWithHints() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();

        byte[] body = mockMvc.perform(get("/api/imports/CUSTOMER/template").with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        String text = new String(body, StandardCharsets.UTF_8);

        assertThat(text).contains("客户简称");
        assertThat(text.replace("\uFEFF", "")).startsWith("提示：必填");
        assertThat(text).contains("提示：必填");
        assertThat(text).contains("一般重要 / 重要 / 非常重要");
    }

    @Test
    void staffCannotDownloadTemplate() throws Exception {
        var staff = userRepository.findByUsername("liuxiaolin").orElseThrow();

        mockMvc.perform(get("/api/imports/CUSTOMER/template").with(user(new AuthUser(staff))))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingImportPathIsNotInternalError() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();

        mockMvc.perform(get("/api/imports/PROJECT/template/nope").with(user(new AuthUser(admin))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("接口不存在"));
    }

    @Test
    void excelFileIsRejected() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();

        mockMvc.perform(multipart("/api/imports/CUSTOMER")
                        .file(new MockMultipartFile(
                                "file",
                                "客户.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "not-csv".getBytes(StandardCharsets.UTF_8)
                        ))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("模板")));
    }

    @Test
    void importsCustomerAndProjectFromTemplateAndReportsRowErrors() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        String suffix = String.valueOf(System.currentTimeMillis());
        String customerCode = "I" + suffix;
        String badCode = "B" + suffix;
        String projectCode = "P" + suffix;

        String customerCsv = """
                提示：必填,提示：必填,提示：必填且唯一,提示：只能填 一般重要 / 重要 / 非常重要,提示：选填,提示：选填,提示：选填,提示：选填,提示：选填,提示：选填
                客户简称,公司名称,客户编码,等级,联系人姓名,联系人电话,联系人邮箱,账户名称,开户银行,银行账号
                导入客户,导入客户有限公司,%s,一般重要,张三,13800000000,,,
                错误客户,错误客户有限公司,%s,不存在的等级,,,,,,
                """.formatted(customerCode, badCode);

        mockMvc.perform(multipart("/api/imports/CUSTOMER")
                        .file(csv("客户导入模板.csv", customerCsv))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(1))
                .andExpect(jsonPath("$.data.failed").value(1))
                .andExpect(jsonPath("$.data.errors[0]").value(Matchers.containsString("等级")));

        assertThat(customerRepository.findByCode(customerCode)).isPresent();
        assertThat(customerRepository.findByCode(badCode)).isEmpty();

        String projectCsv = """
                提示：必填,提示：必填且唯一,提示：已有客户编码,提示：系统用户名,提示：已有区域,提示：选填
                项目名称,项目编码,客户编码,项目经理用户名,区域名称,项目概述
                导入项目,%s,%s,liuxiaolin,华东地区,概述
                """.formatted(projectCode, customerCode);

        mockMvc.perform(multipart("/api/imports/PROJECT")
                        .file(csv("项目导入模板.csv", projectCsv))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(1))
                .andExpect(jsonPath("$.data.failed").value(0));

        assertThat(projectRepository.findByCode(projectCode)).isPresent();
    }

    private static MockMultipartFile csv(String filename, String content) {
        return new MockMultipartFile("file", filename, "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }
}
