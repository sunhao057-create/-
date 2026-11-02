package com.projectmgmt.org;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public record AssignMemberRequest(
        Long userId,
        List<Long> userIds,
        Boolean deptLeader
) {
    public List<Long> ids() {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (userIds != null) {
            for (Long id : userIds) {
                if (id != null && id > 0) {
                    ids.add(id);
                }
            }
        }
        if (userId != null && userId > 0) {
            ids.add(userId);
        }
        return new ArrayList<>(ids);
    }
}
