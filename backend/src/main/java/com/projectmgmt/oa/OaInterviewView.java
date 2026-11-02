package com.projectmgmt.oa;

import java.time.LocalDate;
import java.util.List;

public record OaInterviewView(
        LocalDate birthDate,
        String ethnicity,
        String maritalStatus,
        String maritalStatusLabel,
        String email,
        String hukouAddress,
        String currentAddress,
        String school,
        String major,
        String highestEducation,
        String educationNote,
        List<OaResumeLineView> educationLines,
        List<OaResumeLineView> workLines,
        List<OaResumeLineView> familyLines,
        List<OaResumeLineView> trainingLines
) {
}
