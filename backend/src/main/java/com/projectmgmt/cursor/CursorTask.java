package com.projectmgmt.cursor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "cursor_task")
public class CursorTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 4000)
    private String prompt;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "agent_id", length = 80)
    private String agentId;

    @Column(name = "run_id", length = 80)
    private String runId;

    @Column(name = "agent_url", length = 300)
    private String agentUrl;

    @Column(name = "pr_url", length = 300)
    private String prUrl;

    @Column(length = 200)
    private String branch;

    @Column(name = "result_text", columnDefinition = "TEXT")
    private String resultText;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_by_name", length = 50)
    private String createdByName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
