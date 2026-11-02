package com.projectmgmt.dynamic;

import com.projectmgmt.tech.TechCatalog;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.Iterator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class DynamicFormSeedRunner implements ApplicationRunner {

    private static final String FORMS_RESOURCE = "dynamic-seed/forms.json";

    private final FormDefinitionRepository definitionRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        int created = load(FORMS_RESOURCE);
        int retired = retireLegacyTechForms();
        if (created > 0 || retired > 0) {
            log.info("动态表单种子导入完成: 新增 {} 张表单, 停用 {} 张已迁出的技术服务表", created, retired);
        }
    }

    private int retireLegacyTechForms() {
        int retired = 0;
        for (FormDefinition def : definitionRepository.findAll()) {
            if (TechCatalog.isLegacyAppKey(def.getCode()) && def.isEnabled()) {
                def.setEnabled(false);
                definitionRepository.save(def);
                retired++;
            }
        }
        return retired;
    }

    private int load(String location) throws Exception {
        ClassPathResource resource = new ClassPathResource(location);
        if (!resource.exists()) {
            return 0;
        }
        JsonNode root = objectMapper.readTree(resource.getInputStream());
        JsonNode items = root.path("items");
        if (!items.isArray()) {
            return 0;
        }
        int created = 0;
        Iterator<JsonNode> it = items.iterator();
        while (it.hasNext()) {
            JsonNode item = it.next();
            String code = item.path("code").asText();
            String title = item.path("title").asText();
            String packageCode = item.path("packageCode").asText("CRM");
            String designJson = item.path("design").toString();
            JsonNode graph = item.path("graph");
            // 种子未配置流程时保持库内已有的流程定义
            String graphJson = graph.isMissingNode() || graph.isNull() ? null : graph.toString();
            var existing = definitionRepository.findByCode(code);
            if (existing.isPresent()) {
                // 定义已存在：设计变更（新增/调整字段）时同步，避免种子改动无法生效
                FormDefinition def = existing.get();
                boolean designChanged = !designJson.equals(def.getDesignJson());
                boolean graphChanged = graphJson != null && !graphJson.equals(def.getGraphJson());
                if (designChanged || graphChanged) {
                    def.setTitle(title);
                    def.setPackageCode(packageCode);
                    def.setDesignJson(designJson);
                    if (graphChanged) {
                        def.setGraphJson(graphJson);
                    }
                    definitionRepository.save(def);
                }
                continue;
            }
            FormDefinition def = new FormDefinition();
            def.setCode(code);
            def.setTitle(title);
            def.setPackageCode(packageCode);
            def.setDesignJson(designJson);
            def.setGraphJson(graphJson == null ? "" : graphJson);
            def.setEnabled(true);
            definitionRepository.save(def);
            created++;
        }
        return created;
    }
}
