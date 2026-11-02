package com.projectmgmt.oa;

public record UpsertOaRequest(
        String applicantName,
        Long staffId,
        String gender,
        String mobile,
        String idNumber,
        Long departmentId,
        String position,
        String jobLevel,
        Long targetDepartmentId,
        String targetPosition,
        String targetLevel,
        String expectedDate,
        Long managerId,
        Long handoverUserId,
        Integer probationMonths,
        String resignType,
        String reason,
        Boolean isPublic,
        String loginUsername,
        String password,
        java.math.BigDecimal salary,
        java.util.List<UpsertOaSalaryLine> salaryLines,
        UpsertInterviewRequest interview,
        UpsertSealRequest seal,
        UpsertQualifyRequest qualify,
        UpsertTripRequest trip,
        UpsertLoanRequest loan,
        UpsertLeaveRequest leave
) {
}
