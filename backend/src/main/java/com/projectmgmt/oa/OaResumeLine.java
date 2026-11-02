package com.projectmgmt.oa;

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
@Table(name = "oa_resume_line")
public class OaResumeLine {

    public static final String EDUCATION = "EDUCATION";
    public static final String WORK = "WORK";
    public static final String FAMILY = "FAMILY";
    public static final String TRAINING = "TRAINING";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id")
    private OaRequest request;

    @Column(name = "line_kind", nullable = false, length = 20)
    private String lineKind;

    @Column(length = 120)
    private String title;

    @Column(length = 120)
    private String subtitle;

    @Column(length = 200)
    private String extra;

    @Column(name = "start_ym", length = 7)
    private String startYm;

    @Column(name = "end_ym", length = 7)
    private String endYm;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
