package com.projectmgmt.workflow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WorkflowGraph {
    private List<WorkflowNode> nodes = new ArrayList<>();
    private List<WorkflowEdge> edges = new ArrayList<>();

    @Getter
    @Setter
    public static class WorkflowNode {
        private String key;
        private String name;
        private String type;
        private int x;
        private int y;
        private Map<String, String> fields = new LinkedHashMap<>();
        /** USER | FORM_FIELD | STARTER | ORG_SUPERVISOR | ORG_CHAIN */
        private String assigneeType;
        private List<Long> assigneeUserIds = new ArrayList<>();
        private String assigneeField;
        /** For ORG_CHAIN: empty/0 means up to root. */
        private Integer assigneeLevels;
        private Integer timeoutValue;
        /** HOUR | DAY */
        private String timeoutUnit;
        /** 超时提醒人（用户 id），由流程配置自行指定。 */
        private List<Long> remindees = new ArrayList<>();
        /** 超时后再过多久升级提醒；为空表示不升级。 */
        private Integer escalateAfterValue;
        /** HOUR | DAY */
        private String escalateAfterUnit;
        /** 升级提醒人（用户 id）。 */
        private List<Long> escalateRemindees = new ArrayList<>();
        /** For ROBOT node: target form definition code. */
        private String targetCode;
        /** For ROBOT node: robot action name. */
        private String robotName;
    }

    @Getter
    @Setter
    @com.fasterxml.jackson.annotation.JsonAutoDetect(
            fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY,
            getterVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE,
            setterVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE,
            isGetterVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE
    )
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class WorkflowEdge {
        private String from;
        private String to;
        private String name;
        @com.fasterxml.jackson.annotation.JsonProperty("isDefault")
        @com.fasterxml.jackson.annotation.JsonAlias("default")
        private boolean isDefault;
        private List<WorkflowCondition> conditions = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class WorkflowCondition {
        private String field;
        /** EQ | NE | CONTAINS | GT | GTE | LT | LTE | EMPTY | NOT_EMPTY */
        private String op;
        private String value;
        /** Same group is AND; different groups are OR. */
        private Integer group;
    }
}
