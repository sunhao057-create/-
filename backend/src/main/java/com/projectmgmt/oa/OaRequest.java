package com.projectmgmt.oa;

import com.projectmgmt.org.OrgDepartment;
import com.projectmgmt.user.SysUser;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "oa_request")
public class OaRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OaKind kind;

    @Column(name = "applicant_name", nullable = false, length = 80)
    private String applicantName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private SysUser staff;

    @Column(length = 16)
    private String gender;

    @Column(length = 20)
    private String mobile;

    @Column(name = "id_number", length = 32)
    private String idNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private OrgDepartment department;

    @Column(length = 80)
    private String position;

    @Column(name = "job_level", length = 40)
    private String jobLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_department_id")
    private OrgDepartment targetDepartment;

    @Column(name = "target_position", length = 80)
    private String targetPosition;

    @Column(name = "target_level", length = 40)
    private String targetLevel;

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private SysUser manager;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handover_user_id")
    private SysUser handover;

    @Column(name = "probation_months")
    private Integer probationMonths;

    @Column(name = "resign_type", length = 20)
    private String resignType;

    @Column(nullable = false, length = 2000)
    private String reason;

    @Column(name = "extra_json", columnDefinition = "text")
    private String extraJson;

    @Column(name = "link_request_id")
    private Long linkRequestId;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    @Column(name = "submit_source", nullable = false, length = 16)
    private String submitSource = "ADMIN";

    @Column(name = "login_username", length = 64)
    private String loginUsername;

    @Column(name = "password_hash", length = 120)
    private String passwordHash;

    @Column(nullable = false)
    private boolean applied;

    @Column(precision = 18, scale = 2)
    private java.math.BigDecimal salary;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(length = 40)
    private String ethnicity;

    @Column(name = "marital_status", length = 20)
    private String maritalStatus;

    @Column(length = 120)
    private String email;

    @Column(name = "hukou_address", length = 200)
    private String hukouAddress;

    @Column(name = "current_address", length = 200)
    private String currentAddress;

    @Column(length = 120)
    private String school;

    @Column(length = 80)
    private String major;

    @Column(name = "highest_education", length = 40)
    private String highestEducation;

    @Column(name = "education_note", length = 80)
    private String educationNote;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc, id asc")
    private List<OaSalaryLine> salaryLines = new ArrayList<>();

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc, id asc")
    private List<OaResumeLine> resumeLines = new ArrayList<>();

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private List<OaRequestFile> files = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private SysUser createdBy;

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
