package com.projectmgmt.tech;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public final class TechFlowCatalog {

    private TechFlowCatalog() {
    }

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final Map<TechKind, List<Step>> CACHE = new ConcurrentHashMap<>();

    public record Step(String key, String name, String type, String assigneeField) {
    }

    public static List<Step> of(TechKind kind) {
        return CACHE.computeIfAbsent(kind, TechFlowCatalog::load);
    }

    private static List<Step> load(TechKind kind) {
        try (InputStream in = new ClassPathResource("tech/flows.json").getInputStream()) {
            JsonNode root = MAPPER.readTree(in);
            JsonNode stepsNode = root.path(kind.name());
            List<Step> steps = new ArrayList<>();
            if (stepsNode.isArray()) {
                for (JsonNode s : stepsNode) {
                    steps.add(new Step(
                            s.path("key").asText(),
                            s.path("name").asText(),
                            s.path("type").asText(""),
                            s.has("assigneeField") ? s.path("assigneeField").asText() : null));
                }
            }
            return List.copyOf(steps);
        } catch (IOException e) {
            throw new IllegalStateException("加载 tech flows.json 失败: " + e.getMessage(), e);
        }
    }
}
