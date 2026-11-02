package com.projectmgmt.role;

import org.springframework.data.jpa.domain.Specification;

public final class DataScopeQuery {

    private DataScopeQuery() {}

    public static <T> Specification<T> createdBy(Long userId) {
        if (userId == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> cb.equal(root.get("createdBy").get("id"), userId);
    }
}
