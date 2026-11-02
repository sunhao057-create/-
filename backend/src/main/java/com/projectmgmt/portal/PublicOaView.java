package com.projectmgmt.portal;

import com.projectmgmt.oa.OaInterviewView;
import com.projectmgmt.oa.OaLeaveView;
import com.projectmgmt.oa.OaLoanView;
import com.projectmgmt.oa.OaQualifyView;
import com.projectmgmt.oa.OaSealView;
import com.projectmgmt.oa.OaTripView;
import com.projectmgmt.project.AttachmentView;
import java.time.LocalDate;
import java.util.List;

public record PublicOaView(
        Long id,
        String code,
        String kind,
        String kindLabel,
        String applicantName,
        String gender,
        String genderLabel,
        String mobile,
        String departmentName,
        String position,
        String jobLevel,
        String targetDepartmentName,
        String targetPosition,
        String targetLevel,
        LocalDate expectedDate,
        String managerName,
        String handoverName,
        Integer probationMonths,
        String resignType,
        String resignTypeLabel,
        String reason,
        String loginUsername,
        boolean applied,
        OaInterviewView interview,
        OaSealView seal,
        OaQualifyView qualify,
        OaTripView trip,
        OaLoanView loan,
        OaLeaveView leave,
        List<AttachmentView> files
) {
}
