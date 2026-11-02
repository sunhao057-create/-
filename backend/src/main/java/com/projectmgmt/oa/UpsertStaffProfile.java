package com.projectmgmt.oa;

public record UpsertStaffProfile(
        String displayName,
        String gender,
        String idNumber,
        String birthDate,
        String mobile,
        String position,
        String hireDate
) {
}
