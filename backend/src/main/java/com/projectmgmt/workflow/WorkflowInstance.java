package com.projectmgmt.workflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "wf_instance",
        uniqueConstraints = @UniqueConstraint(columnNames = {"form_module", "biz_id"})
)
public class WorkflowInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "definition_id")
    private WorkflowDefinition definition;

    @Column(name = "form_module", nullable = false, length = 64)
    private String formModule;

    @Column(name = "biz_id", nullable = false)
    private Long bizId;

    @Column(name = "current_node_key", nullable = false, length = 64)
    private String currentNodeKey;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 200)
    private String title;

    @Column(name = "starter_id")
    private Long starterId;

    @Column(name = "starter_name", length = 50)
    private String starterName;

    @Column(name = "last_return_reason", length = 500)
    private String lastReturnReason;

    @Column(name = "last_return_from_key", length = 64)
    private String lastReturnFromKey;

    @Column(name = "last_return_from_name", length = 80)
    private String lastReturnFromName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "visited_keys")
    private List<String> visitedKeys = new ArrayList<>();

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (visitedKeys == null) {
            visitedKeys = new ArrayList<>();
        }
    }
}
