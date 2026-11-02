package com.projectmgmt.dynamic;

import com.projectmgmt.user.SysUser;
import com.projectmgmt.user.UserRepository;
import com.projectmgmt.workflow.WorkflowGraph;
import com.projectmgmt.workflow.WorkflowGraph.WorkflowCondition;
import com.projectmgmt.workflow.WorkflowGraph.WorkflowEdge;
import com.projectmgmt.workflow.WorkflowGraph.WorkflowNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class DynamicGraphConverter {

    private final UserRepository userRepository;

    public WorkflowGraph convert(String graphJson) {
        if (graphJson == null || graphJson.isBlank()) {
            return new WorkflowGraph();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = new JsonMapper().readValue(graphJson, Map.class);
            return convert(raw);
        } catch (Exception e) {
            return new WorkflowGraph();
        }
    }

    @SuppressWarnings("unchecked")
    public WorkflowGraph convert(Map<String, Object> graph) {
        WorkflowGraph result = new WorkflowGraph();
        if (graph == null) {
            return result;
        }
        Object nodesObj = graph.get("nodes");
        if (!(nodesObj instanceof List<?> nodes)) {
            return result;
        }
        Map<String, WorkflowNode> nodeMap = new LinkedHashMap<>();
        for (Object item : nodes) {
            if (!(item instanceof Map<?, ?> rawNode)) {
                continue;
            }
            WorkflowNode node = toNode(rawNode);
            if (node.getKey() == null) {
                continue;
            }
            nodeMap.put(node.getKey(), node);
            result.getNodes().add(node);
        }
        for (Object item : nodes) {
            if (!(item instanceof Map<?, ?> rawNode)) {
                continue;
            }
            String key = str(rawNode.get("key"));
            if (key == null) {
                continue;
            }
            Object nextObj = rawNode.get("next");
            if (nextObj != null) {
                String next = String.valueOf(nextObj);
                if (nodeMap.containsKey(next)) {
                    WorkflowEdge edge = new WorkflowEdge();
                    edge.setFrom(key);
                    edge.setTo(next);
                    edge.setDefault(true);
                    result.getEdges().add(edge);
                }
            }
            Object branchesObj = rawNode.get("branches");
            if (branchesObj instanceof List<?> branches) {
                for (Object b : branches) {
                    if (!(b instanceof Map<?, ?> branch)) {
                        continue;
                    }
                    String to = str(branch.get("to"));
                    if (to == null || !nodeMap.containsKey(to)) {
                        continue;
                    }
                    WorkflowEdge edge = new WorkflowEdge();
                    edge.setFrom(key);
                    edge.setTo(to);
                    Object isDefault = branch.get("default");
                    edge.setDefault(Boolean.TRUE.equals(isDefault) || "true".equalsIgnoreCase(String.valueOf(isDefault)));
                    Object condsObj = branch.get("conditions");
                    if (condsObj instanceof List<?> conds) {
                        List<WorkflowCondition> conditions = new ArrayList<>();
                        int group = 0;
                        for (Object c : conds) {
                            if (!(c instanceof Map<?, ?> cond)) {
                                continue;
                            }
                            WorkflowCondition wc = new WorkflowCondition();
                            wc.setField(str(cond.get("field")));
                            wc.setOp(str(cond.get("op")));
                            wc.setValue(str(cond.get("value")));
                            wc.setGroup(group++);
                            conditions.add(wc);
                        }
                        edge.setConditions(conditions);
                    }
                    result.getEdges().add(edge);
                }
            }
        }
        return result;
    }

    private WorkflowNode toNode(Map<?, ?> raw) {
        WorkflowNode node = new WorkflowNode();
        node.setKey(str(raw.get("key")));
        node.setName(str(raw.get("name")));
        String type = str(raw.get("type"));
        node.setType(type == null ? null : type.toUpperCase(Locale.ROOT));
        node.setTargetCode(str(raw.get("targetCode")));
        node.setRobotName(str(raw.get("robotName")));

        // 审批节点超时时限：与固定模块流程定义保持一致
        Object timeoutValue = raw.get("timeoutValue");
        if (timeoutValue instanceof Number number) {
            node.setTimeoutValue(number.intValue());
        } else if (timeoutValue != null) {
            try {
                node.setTimeoutValue(Integer.parseInt(String.valueOf(timeoutValue).trim()));
            } catch (NumberFormatException ignored) {
                // 非法数值按未配置处理
            }
        }
        node.setTimeoutUnit(str(raw.get("timeoutUnit")));

        Object fieldAuth = raw.get("fieldAuth");
        if (fieldAuth instanceof Map<?, ?> auth) {
            Map<String, String> fields = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : auth.entrySet()) {
                fields.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            }
            node.setFields(fields);
        }

        Object assignees = raw.get("assignees");
        if (assignees instanceof List<?> list) {
            List<String> fieldAssignees = new ArrayList<>();
            List<Long> userIds = new ArrayList<>();
            for (Object a : list) {
                if (a == null) {
                    continue;
                }
                String s = String.valueOf(a);
                if (s.startsWith("field:")) {
                    fieldAssignees.add(s.substring("field:".length()));
                } else {
                    userRepository.findByUsername(s).map(SysUser::getId).ifPresent(userIds::add);
                }
            }
            if (!fieldAssignees.isEmpty()) {
                node.setAssigneeType("FORM_FIELD");
                node.setAssigneeField(fieldAssignees.get(0));
            } else if (!userIds.isEmpty()) {
                node.setAssigneeType("USER");
                node.setAssigneeUserIds(userIds);
            }
        }
        return node;
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
