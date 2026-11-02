package com.projectmgmt.org;

public record OrgMemberView(
        Long id,
        String username,
        String displayName,
        boolean deptLeader,
        String status
) {
}
