package com.projectmgmt.bid;

import com.projectmgmt.customer.Customer;
import com.projectmgmt.project.Project;
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
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "pm_bid")
public class Bid {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_lead_id")
    private SysUser salesLead;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(name = "opportunity_name", length = 200)
    private String opportunityName;

    @Column(name = "bid_agent", length = 200)
    private String bidAgent;

    @Column(name = "tender_name", nullable = false, length = 200)
    private String tenderName;

    @Column(name = "tender_code", nullable = false, length = 80)
    private String tenderCode;

    @Column(name = "download_deadline", nullable = false)
    private LocalDateTime downloadDeadline;

    @Column(name = "bid_time", nullable = false)
    private LocalDateTime bidTime;

    @Column(length = 2000)
    private String description;

    @Column(name = "announcement_url", length = 500)
    private String announcementUrl;

    @Column(name = "signed_up", nullable = false)
    private boolean signedUp;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private BidResult result;

    @Column(name = "win_amount", precision = 18, scale = 2)
    private BigDecimal winAmount;

    @Column(name = "win_unit", length = 200)
    private String winUnit;

    @Column(name = "win_unit_name", length = 200)
    private String winUnitName;

    @Column(name = "round2_name", length = 200)
    private String round2Name;

    @Column(name = "round2_download_deadline")
    private LocalDateTime round2DownloadDeadline;

    @Column(name = "round2_bid_time")
    private LocalDateTime round2BidTime;

    @Column(name = "round2_announcement_url", length = 500)
    private String round2AnnouncementUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "round2_result", length = 16)
    private BidResult round2Result;

    @Column(name = "round2_win_amount", precision = 18, scale = 2)
    private BigDecimal round2WinAmount;

    @Column(name = "round2_win_unit", length = 200)
    private String round2WinUnit;

    @Column(name = "round2_win_unit_name", length = 200)
    private String round2WinUnitName;

    @Column(name = "final_contract_name", length = 200)
    private String finalContractName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private SysUser createdBy;

    @OneToMany(mappedBy = "bid", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc, id asc")
    private List<BidSignup> signups = new ArrayList<>();

    @OneToMany(mappedBy = "bid", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BidFile> files = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
