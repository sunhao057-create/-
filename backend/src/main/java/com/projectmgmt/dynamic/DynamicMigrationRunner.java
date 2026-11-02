package com.projectmgmt.dynamic;

import com.projectmgmt.workflow.WorkflowDefinition;
import com.projectmgmt.workflow.WorkflowGraph;
import com.projectmgmt.workflow.WorkflowRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Order(200)
@RequiredArgsConstructor
public class DynamicMigrationRunner implements ApplicationRunner {

    private final FormDefinitionRepository definitionRepository;
    private final WorkflowRepository workflowRepository;
    private final DynamicGraphConverter graphConverter;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<FormDefinition> defs = definitionRepository.findAllByEnabledTrueOrderByPackageCodeAscIdAsc();
        int created = 0;
        int synced = 0;
        for (FormDefinition def : defs) {
            String seedGraphJson = def.getGraphJson();
            boolean hasSeedGraph = seedGraphJson != null && !seedGraphJson.isBlank() && !"\"\"".equals(seedGraphJson.trim());
            var existing = workflowRepository.findByCode(def.getCode());
            if (existing.isPresent()) {
                // 已有流程定义但库内流程为空（种子新增了流程）时补齐，避免种子流程不生效
                WorkflowDefinition wf = existing.get();
                WorkflowGraph stored = wf.getGraph();
                boolean empty = stored == null || stored.getNodes() == null || stored.getNodes().isEmpty();
                if (hasSeedGraph && empty) {
                    wf.setGraph(graphConverter.convert(seedGraphJson));
                    workflowRepository.save(wf);
                    synced++;
                }
                continue;
            }
            WorkflowDefinition wf = new WorkflowDefinition();
            wf.setName(def.getTitle());
            wf.setCode(def.getCode());
            wf.setFormModule(def.getCode());
            wf.setPublished(true);
            wf.setGraph(graphConverter.convert(hasSeedGraph ? seedGraphJson : null));
            workflowRepository.save(wf);
            created++;
        }
        if (created > 0 || synced > 0) {
            log.info("动态流程迁移完成: 新增 {} 条流程定义, 同步 {} 条已有流程", created, synced);
        }
    }
}
