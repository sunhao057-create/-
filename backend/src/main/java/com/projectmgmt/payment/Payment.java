package com.projectmgmt.payment;

import com.projectmgmt.finance.BankAccount;
import com.projectmgmt.project.Project;
import com.projectmgmt.supplier.Supplier;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "pm_payment")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 500)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private BankAccount account;

    @Column(name = "bank_account_id", nullable = false)
    private Long bankAccountId;

    @Column(name = "payee_name", nullable = false, length = 80)
    private String payeeName;

    @Column(name = "bank_name", nullable = false, length = 80)
    private String bankName;

    @Column(name = "account_no", nullable = false, length = 40)
    private String accountNo;

    @Column(length = 80)
    private String branch;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_method", nullable = false, length = 16)
    private PayMethod payMethod;

    @Column(name = "expected_pay_date", nullable = false)
    private LocalDate expectedPayDate;

    @Column(name = "budget_deducted", nullable = false)
    private boolean budgetDeducted;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    @Column(name = "submit_source", nullable = false, length = 16)
    private String submitSource = "ADMIN";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private SysUser createdBy;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentFile> files = new ArrayList<>();

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
