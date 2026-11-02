package com.projectmgmt.org;

public record OrgDepartmentView(
        Long id,
        String name,
        Long parentId,
        String parentName,
        int sortOrder,
        int memberCount,
        int leaderCount
) {
}
