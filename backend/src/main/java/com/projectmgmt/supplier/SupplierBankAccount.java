package com.projectmgmt.supplier;

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
@Table(name = "crm_supplier_bank")
public class SupplierBankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(name = "account_name", nullable = false, length = 80)
    private String accountName;

    @Column(name = "bank_name", nullable = false, length = 80)
    private String bankName;

    @Column(name = "account_no", nullable = false, length = 40)
    private String accountNo;

    @Column(length = 80)
    private String branch;

    @Column(name = "primary_account", nullable = false)
    private boolean primaryAccount;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
