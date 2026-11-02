package com.projectmgmt.formview;

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
@Table(name = "form_data_view")
public class FormView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String module;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(length = 200)
    private String remark;

    @Column(name = "pin_top", nullable = false)
    private boolean pinTop;

    @Column(nullable = false, length = 16)
    private String visibility = "ALL";

    @Column(name = "data_range", nullable = false, length = 16)
    private String dataRange = "ALL";

    @Column(name = "row_height", nullable = false, length = 16)
    private String rowHeight = "MEDIUM";

    @Column(name = "sort_field", length = 64)
    private String sortField;

    @Column(name = "sort_dir", length = 8)
    private String sortDir = "DESC";

    @Column(name = "search_enabled", nullable = false)
    private boolean searchEnabled = true;

    @Column(name = "query_enabled", nullable = false)
    private boolean queryEnabled = true;

    @Column(name = "fields_json", columnDefinition = "TEXT")
    private String fieldsJson;

    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson;

    @Column(name = "created_by_id")
    private Long createdById;

    @Column(name = "created_by_name", length = 80)
    private String createdByName;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
