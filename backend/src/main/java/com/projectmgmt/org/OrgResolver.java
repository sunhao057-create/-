package com.projectmgmt.org;

import com.projectmgmt.user.SysUser;
import com.projectmgmt.user.UserRepository;
import com.projectmgmt.user.UserStatus;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class OrgResolver {

    private static final int MAX_DEPTH = 20;

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<List<SysUser>> chain(SysUser starter, Integer maxLevels) {
        if (starter == null || starter.getDepartment() == null) {
            return List.of();
        }
        int limit = (maxLevels == null || maxLevels <= 0) ? MAX_DEPTH : Math.min(maxLevels, MAX_DEPTH);
        List<List<SysUser>> levels = new ArrayList<>();
        OrgDepartment dept = starter.getDepartment();
        boolean skipOwnDept = starter.isDeptLeader();
        int depth = 0;
        Set<Long> seenDepts = new LinkedHashSet<>();
        while (dept != null && depth < MAX_DEPTH && seenDepts.add(dept.getId())) {
            if (!(skipOwnDept && dept.getId().equals(starter.getDepartment().getId()))) {
                List<SysUser> leaders = leadersOf(dept.getId(), starter.getId());
                if (!leaders.isEmpty()) {
                    levels.add(leaders);
                    if (levels.size() >= limit) {
                        break;
                    }
                }
            }
            dept = dept.getParent();
            depth++;
        }
        return levels;
    }

    public List<SysUser> flatten(List<List<SysUser>> levels) {
        List<SysUser> users = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (List<SysUser> level : levels) {
            for (SysUser user : level) {
                if (seen.add(user.getId())) {
                    users.add(user);
                }
            }
        }
        return users;
    }

    public List<SysUser> currentLevel(List<List<SysUser>> levels, Set<Long> alreadyApproved) {
        for (List<SysUser> level : levels) {
            boolean done = level.stream().anyMatch(user -> alreadyApproved.contains(user.getId()));
            if (!done) {
                return level;
            }
        }
        return List.of();
    }

    private List<SysUser> leadersOf(Long departmentId, Long excludeUserId) {
        return userRepository.findByDepartment_IdAndDeptLeaderTrueAndStatus(departmentId, UserStatus.ENABLED).stream()
                .filter(user -> excludeUserId == null || !excludeUserId.equals(user.getId()))
                .toList();
    }
}
