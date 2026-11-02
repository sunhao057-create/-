package com.projectmgmt.oa;

import com.projectmgmt.user.UserStatus;
import java.time.LocalDate;

public record StaffPortalView(
        Long id,
        String displayName,
        String gender,
        String genderLabel,
        String idNumber,
        LocalDate birthDate,
        String mobile,
        String position,
        String jobLevel,
        Long departmentId,
        String departmentName,
        LocalDate hireDate,
        UserStatus status,
        String statusLabel
) {
}
