package com.projectmgmt.customeropen;

import com.projectmgmt.user.SysUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "customer_account_open")
public class CustomerAccountOpen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "company_name", nullable = false, length = 80)
    private String companyName;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Column(nullable = false, length = 32)
    private String username;

    @Column(length = 30)
    private String phone;

    @Column(name = "account_type", length = 16)
    private String accountType;

    @Column(name = "role_code", length = 32)
    private String roleCode;

    @Column(name = "initial_password", length = 64)
    private String initialPassword;

    @Column(name = "user_id")
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private SysUser createdBy;

    @Column(nullable = false)
    private boolean settled;
}
