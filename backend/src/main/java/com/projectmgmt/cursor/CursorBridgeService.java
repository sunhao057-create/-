package com.projectmgmt.cursor;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.security.CurrentUser;
import com.projectmgmt.user.SysUser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class CursorBridgeService {

    private static final String API = "https://api.cursor.com";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final CursorTaskRepository repository;
    private final CurrentUser currentUser;
    private final CursorPublisher publisher;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final String apiKey;
    private final String repoUrl;
    private final String startingRef;

    public CursorBridgeService(
            CursorTaskRepository repository,
            CurrentUser currentUser,
            CursorPublisher publisher,
            @Value("${app.cursor.api-key:}") String apiKey,
            @Value("${app.cursor.repo-url:}") String repoUrl,
            @Value("${app.cursor.starting-ref:main}") String startingRef
    ) {
        this.repository = repository;
        this.currentUser = currentUser;
        this.publisher = publisher;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.repoUrl = repoUrl == null ? "" : repoUrl.trim();
        this.startingRef = StringUtils.hasText(startingRef) ? startingRef.trim() : "main";
    }

    @Transactional(readOnly = true)
    public CursorStatusView status() {
        requireAdmin();
        boolean configured = StringUtils.hasText(apiKey) && StringUtils.hasText(repoUrl);
        String hint = configured
                ? "提交后由孙浩改代码，改完自动发布到线上。"
                : "代码仓库还没接上，暂时不能改系统。";
        return new CursorStatusView(configured, configured ? repoUrl : "", hint);
    }

    @Transactional
    public List<CursorTaskView> list() {
        requireAdmin();
        List<CursorTask> rows = repository.findAllByOrderByIdDesc();
        for (CursorTask row : rows) {
            if ("RUNNING".equals(row.getStatus())) {
                refresh(row);
            }
        }
        return rows.stream().map(CursorTaskView::from).toList();
    }

    @Transactional
    public CursorTaskView create(String prompt) {
        requireAdmin();
        ensureConfigured();
        String text = prompt.trim();
        SysUser me = currentUser.entity();
        CursorTask task = new CursorTask();
        task.setPrompt(text);
        task.setStatus("RUNNING");
        task.setCreatedBy(me.getId());
        task.setCreatedByName(me.getDisplayName());
        task = repository.save(task);
        try {
            JsonNode created = post("/v1/agents", createBody(text));
            JsonNode agent = created.path("agent");
            JsonNode run = created.path("run");
            task.setAgentId(textOf(agent, "id"));
            task.setRunId(textOf(run, "id"));
            task.setAgentUrl(textOf(agent, "url"));
            if (!StringUtils.hasText(task.getRunId())) {
                task.setRunId(textOf(agent, "latestRunId"));
            }
        } catch (ApiException ex) {
            task.setStatus("FAILED");
            task.setErrorMessage(trim(ex.getMessage()));
        }
        return CursorTaskView.from(repository.save(task));
    }

    @Transactional
    public CursorTaskView follow(Long id, String prompt) {
        requireAdmin();
        ensureConfigured();
        CursorTask task = require(id);
        if (!StringUtils.hasText(task.getAgentId())) {
            throw ApiException.badRequest("这条需求还没有交出去");
        }
        try {
            JsonNode run = post("/v1/agents/" + task.getAgentId() + "/runs", Map.of("prompt", Map.of("text", prompt.trim())));
            if (!StringUtils.hasText(textOf(run, "id")) && run.has("run")) {
                run = run.get("run");
            }
            String runId = textOf(run, "id");
            if (StringUtils.hasText(runId)) {
                task.setRunId(runId);
            }
            task.setStatus("RUNNING");
            task.setErrorMessage(null);
        } catch (ApiException ex) {
            task.setErrorMessage(trim(ex.getMessage()));
        }
        return CursorTaskView.from(repository.save(task));
    }

    private void refresh(CursorTask task) {
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(task.getAgentId()) || !StringUtils.hasText(task.getRunId())) {
            return;
        }
        try {
            JsonNode run = get("/v1/agents/" + task.getAgentId() + "/runs/" + task.getRunId());
            String status = textOf(run, "status");
            if ("FINISHED".equals(status)) {
                task.setResultText(textOf(run, "result"));
                task.setErrorMessage(null);
                rememberBranch(task, run);
                if (!StringUtils.hasText(task.getBranch())) {
                    task.setStatus("FAILED");
                    task.setErrorMessage("没有产生可发布的分支");
                } else if (publisher.begin(task.getId())) {
                    task.setStatus("PUBLISHING");
                    Long id = task.getId();
                    afterCommit(() -> publisher.publish(id));
                }
            } else if ("ERROR".equals(status) || "CANCELLED".equals(status) || "EXPIRED".equals(status)) {
                task.setStatus("FAILED");
                task.setResultText(textOf(run, "result"));
                task.setErrorMessage(StringUtils.hasText(textOf(run, "result")) ? trim(textOf(run, "result")) : "这次修改没有完成");
            }
            rememberBranch(task, run);
        } catch (ApiException ex) {
            task.setErrorMessage(trim(ex.getMessage()));
        }
    }

    private Map<String, Object> createBody(String prompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", prompt.length() > 40 ? prompt.substring(0, 40) : prompt);
        body.put("prompt", Map.of("text", """
                你正在修改项目管理系统的代码。只实现下面这条需求，不要自己部署，不要改无关功能。
                改完用中文说明改了哪些文件、怎么验证。系统会在你完成后自动发布。

                需求：
                %s
                """.formatted(prompt)));
        body.put("repos", List.of(Map.of("url", repoUrl, "startingRef", startingRef)));
        body.put("autoCreatePR", true);
        body.put("skipReviewerRequest", true);
        body.put("workOnCurrentBranch", false);
        return body;
    }

    private JsonNode post(String path, Object body) {
        return send("POST", path, body);
    }

    private JsonNode get(String path) {
        return send("GET", path, null);
    }

    private JsonNode send(String method, String path, Object body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(API + path))
                    .timeout(Duration.ofSeconds(40))
                    .header("Authorization", basic())
                    .header("Accept", "application/json");
            if (body == null) {
                builder.GET();
            } else {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
            }
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw ApiException.badRequest(errorText(response.body(), response.statusCode()));
            }
            if (!StringUtils.hasText(response.body())) {
                return JSON.createObjectNode();
            }
            return JSON.readTree(response.body());
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ApiException.badRequest("连接失败");
        }
    }

    private String errorText(String body, int status) {
        try {
            JsonNode node = JSON.readTree(body);
            String message = textOf(node, "message");
            if (!StringUtils.hasText(message) && node.path("error").isObject()) {
                message = textOf(node.path("error"), "message");
            }
            if (StringUtils.hasText(message)) {
                return trim(message);
            }
        } catch (Exception ignored) {
            // 非 JSON 错误页只返回状态码，避免把密钥或整页 HTML 带回前端。
        }
        return "请求失败 " + status;
    }

    private String basic() {
        return "Basic " + Base64.getEncoder().encodeToString((apiKey + ":").getBytes(StandardCharsets.UTF_8));
    }

    private void ensureConfigured() {
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(repoUrl)) {
            throw ApiException.badRequest("代码仓库还没接上，暂时不能改系统");
        }
    }

    private CursorTask require(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("需求不存在"));
    }

    private void requireAdmin() {
        SysUser user = currentUser.entity();
        if (user == null || !PermissionService.ADMIN_CODE.equals(user.getRole())) {
            throw ApiException.forbidden("只有系统管理员可以使用");
        }
    }

    private static void rememberBranch(CursorTask task, JsonNode run) {
        JsonNode branches = run.path("git").path("branches");
        if (branches.isArray() && !branches.isEmpty()) {
            JsonNode first = branches.get(0);
            task.setBranch(textOf(first, "branch"));
            task.setPrUrl(textOf(first, "prUrl"));
        }
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    new Thread(action, "cursor-publish").start();
                }
            });
        } else {
            new Thread(action, "cursor-publish").start();
        }
    }

    private static String textOf(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asString();
        return StringUtils.hasText(text) ? text : null;
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.length() > 500 ? text.substring(0, 500) : text;
    }
}
