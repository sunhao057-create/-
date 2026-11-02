package com.projectmgmt.device;

import com.projectmgmt.customer.Customer;
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
@Table(name = "eq_work_order")
public class DeviceWorkOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DeviceWorkOrderKind kind;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id")
    private Device device;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "intake_json", columnDefinition = "TEXT")
    private String intakeJson;

    @Column(name = "apply_date", nullable = false)
    private LocalDate applyDate;

    @Column(name = "target_location", length = 200)
    private String targetLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_department_id")
    private OrgDepartment targetDepartment;

    /** 委托客户，维修费用向这家客户结算。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_customer_id")
    private Customer clientCustomer;

    /** 实际上门维修的客户。为空时与委托客户相同。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_customer_id")
    private Customer serviceCustomer;

    @Column(nullable = false)
    private boolean applied;

    @Enumerated(EnumType.STRING)
    @Column(name = "snapshot_status", nullable = false, length = 16)
    private DeviceStatus snapshotStatus;

    @Column(name = "snapshot_location", length = 200)
    private String snapshotLocation;

    @Column(name = "snapshot_department_id")
    private Long snapshotDepartmentId;

    @Column(name = "snapshot_enabled", nullable = false)
    private boolean snapshotEnabled = true;

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private List<DeviceWorkOrderFile> files = new ArrayList<>();

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
