package com.projectmgmt;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.projectmgmt.security.AuthUser;
import com.projectmgmt.user.UserRepository;
import java.util.Base64;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DeviceApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    com.projectmgmt.device.DeviceRepository deviceRepository;

    @Test
    void orgTreeVisibilityAndRepairComplete() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        String suffix = String.valueOf(System.currentTimeMillis());

        Integer hqId = JsonPath.read(mockMvc.perform(post("/api/org/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"设备总部%s","sortOrder":1}
                                """.formatted(suffix))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");
        Integer eastId = JsonPath.read(mockMvc.perform(post("/api/org/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"设备华东%s","parentId":%s,"sortOrder":1}
                                """.formatted(suffix, hqId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");
        Integer westId = JsonPath.read(mockMvc.perform(post("/api/org/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"设备华北%s","parentId":%s,"sortOrder":2}
                                """.formatted(suffix, hqId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");

        String hqUser = "devhq" + suffix;
        String eastUser = "deveast" + suffix;
        String westUser = "devwest" + suffix;
        createUser(admin, hqUser, "总部设备员", hqId);
        createUser(admin, eastUser, "华东设备员", eastId);
        createUser(admin, westUser, "华北设备员", westId);
        var hq = userRepository.findByUsername(hqUser).orElseThrow();
        var east = userRepository.findByUsername(eastUser).orElseThrow();
        var west = userRepository.findByUsername(westUser).orElseThrow();

        Integer eastDevice = JsonPath.read(mockMvc.perform(post("/api/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"华东电脑%s","location":"华东办公区","departmentId":%s}
                                """.formatted(suffix, eastId))
                        .with(user(new AuthUser(east))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.departmentId").value(eastId))
                .andExpect(jsonPath("$.data.status").value("IN_USE"))
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");
        Integer westDevice = JsonPath.read(mockMvc.perform(post("/api/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"华北电脑%s","location":"华北办公区","departmentId":%s}
                                """.formatted(suffix, westId))
                        .with(user(new AuthUser(west))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");

        mockMvc.perform(get("/api/devices").with(user(new AuthUser(east))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id==%s)]".formatted(eastDevice)).exists())
                .andExpect(jsonPath("$.data[?(@.id==%s)]".formatted(westDevice)).doesNotExist());
        mockMvc.perform(get("/api/devices/" + westDevice).with(user(new AuthUser(east))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/devices").with(user(new AuthUser(hq))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id==%s)]".formatted(eastDevice)).exists())
                .andExpect(jsonPath("$.data[?(@.id==%s)]".formatted(westDevice)).exists());
        mockMvc.perform(get("/api/devices").with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id==%s)]".formatted(eastDevice)).exists());

        Integer repairId = JsonPath.read(mockMvc.perform(post("/api/devices/repairs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deviceId":%s,"reason":"风扇异响","applyDate":"2026-09-01"}
                                """.formatted(eastDevice))
                        .with(user(new AuthUser(east))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applied").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");

        byte[] ticket = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        mockMvc.perform(multipart("/api/devices/repairs/" + repairId + "/files")
                        .file(new MockMultipartFile("files", "ticket.png", "image/png", ticket))
                        .with(user(new AuthUser(east))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.files[0].originalName").value("ticket.png"))
                .andExpect(jsonPath("$.data.files[0].url").value(Matchers.startsWith("/uploads/device-orders/")));

        mockMvc.perform(get("/api/devices/" + eastDevice).with(user(new AuthUser(east))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REPAIRING"));
        mockMvc.perform(get("/api/devices/repairs").with(user(new AuthUser(west))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id==%s)]".formatted(repairId)).doesNotExist());
        mockMvc.perform(get("/api/devices/repairs").with(user(new AuthUser(hq))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id==%s)]".formatted(repairId)).exists());

        complete(east, "DEVICE_REPAIR", repairId);

        mockMvc.perform(get("/api/devices/" + eastDevice).with(user(new AuthUser(east))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_USE"));
        mockMvc.perform(get("/api/devices/repairs/" + repairId).with(user(new AuthUser(east))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applied").value(true))
                .andExpect(jsonPath("$.data.workflowStatus").value("DONE"));

        mockMvc.perform(post("/api/devices/repairs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deviceId":%s,"reason":"重复维修","applyDate":"2026-09-01"}
                                """.formatted(westDevice))
                        .with(user(new AuthUser(east))))
                .andExpect(status().isForbidden());
    }

    @Test
    void photoQrcodeAndExcelExport() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        String suffix = String.valueOf(System.currentTimeMillis());
        Integer deptId = JsonPath.read(mockMvc.perform(post("/api/org/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"二维码部门%s","sortOrder":1}
                                """.formatted(suffix))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");
        Integer deviceId = JsonPath.read(mockMvc.perform(post("/api/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"扫码仪%s","location":"大厅","departmentId":%s}
                                """.formatted(suffix, deptId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");

        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        mockMvc.perform(multipart("/api/devices/" + deviceId + "/photo")
                        .file(new MockMultipartFile("file", "device.png", "image/png", png))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.photoUrl").value(Matchers.startsWith("/uploads/devices/")));

        byte[] qr = mockMvc.perform(get("/api/devices/" + deviceId + "/qrcode").with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", Matchers.containsString("image/png")))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        org.assertj.core.api.Assertions.assertThat(qr.length).isGreaterThan(100);
        org.assertj.core.api.Assertions.assertThat(qr[0]).isEqualTo((byte) 0x89);

        byte[] xlsx = mockMvc.perform(get("/api/devices/export").with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", Matchers.containsString("spreadsheetml")))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        org.assertj.core.api.Assertions.assertThat(xlsx.length).isGreaterThan(1000);
        org.assertj.core.api.Assertions.assertThat(xlsx[0]).isEqualTo((byte) 'P');
        org.assertj.core.api.Assertions.assertThat(xlsx[1]).isEqualTo((byte) 'K');

        byte[] selectedPost = mockMvc.perform(post("/api/devices/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[%s]}".formatted(deviceId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        org.assertj.core.api.Assertions.assertThat(selectedPost.length).isGreaterThan(1000);
        org.assertj.core.api.Assertions.assertThat(selectedPost[0]).isEqualTo((byte) 'P');

        mockMvc.perform(get("/api/devices").param("name", "扫码仪" + suffix).with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id==%s)]".formatted(deviceId)).exists());
        mockMvc.perform(get("/api/devices").param("location", "不存在的位置xyz").with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id==%s)]".formatted(deviceId)).doesNotExist());
    }

    @Test
    void exportEmbedsPhotoOncePerRowWhenUrlsRepeat() throws Exception {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        String suffix = String.valueOf(System.currentTimeMillis());
        Integer deptId = JsonPath.read(mockMvc.perform(post("/api/org/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"复用照片部门%s","sortOrder":1}
                                """.formatted(suffix))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.data.id");
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        String photoUrl = null;
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        for (int i = 0; i < 3; i++) {
            Integer id = JsonPath.read(mockMvc.perform(post("/api/devices")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"同图设备%s-%s","location":"库房","departmentId":%s}
                                    """.formatted(suffix, i, deptId))
                            .with(user(new AuthUser(admin))))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString(), "$.data.id");
            ids.add(id);
            if (i == 0) {
                photoUrl = JsonPath.read(mockMvc.perform(multipart("/api/devices/" + id + "/photo")
                                .file(new MockMultipartFile("file", "device.png", "image/png", png))
                                .with(user(new AuthUser(admin))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(), "$.data.photoUrl");
            } else {
                var device = deviceRepository.findById(id.longValue()).orElseThrow();
                device.setPhotoUrl(photoUrl);
                deviceRepository.save(device);
            }
        }
        byte[] xlsx = mockMvc.perform(post("/api/devices/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[%s,%s,%s]}".formatted(ids.get(0), ids.get(1), ids.get(2)))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        int media = countZipEntries(xlsx, name -> name.startsWith("xl/media/"));
        org.assertj.core.api.Assertions.assertThat(media).isGreaterThanOrEqualTo(6);
    }

    private static int countZipEntries(byte[] xlsx, java.util.function.Predicate<String> match) throws Exception {
        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("device-export", ".xlsx");
        java.nio.file.Files.write(tmp, xlsx);
        int n = 0;
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(tmp.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                if (match.test(entries.nextElement().getName())) {
                    n++;
                }
            }
        } finally {
            java.nio.file.Files.deleteIfExists(tmp);
        }
        return n;
    }

    private void createUser(com.projectmgmt.user.SysUser admin, String username, String name, Integer deptId)
            throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Staff123!","displayName":"%s","role":"USER","departmentId":%s,"deptLeader":false}
                                """.formatted(username, name, deptId))
                        .with(user(new AuthUser(admin))))
                .andExpect(status().isOk());
        var created = userRepository.findByUsername(username).orElseThrow();
        created.setMustChangePassword(false);
        userRepository.save(created);
    }

    private void complete(com.projectmgmt.user.SysUser actor, String module, Integer bizId) throws Exception {
        WorkflowTestSupport.complete(mockMvc, actor, module, bizId);
    }
}
