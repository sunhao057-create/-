package com.projectmgmt.oa;

import java.util.List;

public record UpsertInterviewRequest(
        String birthDate,
        String ethnicity,
        String maritalStatus,
        String email,
        String hukouAddress,
        String currentAddress,
        String school,
        String major,
        String highestEducation,
        String educationNote,
        List<UpsertOaResumeLine> educationLines,
        List<UpsertOaResumeLine> workLines,
        List<UpsertOaResumeLine> familyLines,
        List<UpsertOaResumeLine> trainingLines
) {
}
