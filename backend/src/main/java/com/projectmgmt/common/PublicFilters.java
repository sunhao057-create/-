package com.projectmgmt.common;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class PublicFilters {

    private PublicFilters() {
    }

    public static <T> Specification<T> byFlag(String publicFilter) {
        if (!StringUtils.hasText(publicFilter) || "ALL".equalsIgnoreCase(publicFilter.trim())) {
            return (root, query, cb) -> cb.conjunction();
        }
        if ("YES".equalsIgnoreCase(publicFilter.trim())) {
            return (root, query, cb) -> cb.isTrue(root.get("isPublic"));
        }
        if ("NO".equalsIgnoreCase(publicFilter.trim())) {
            return (root, query, cb) -> cb.isFalse(root.get("isPublic"));
        }
        return (root, query, cb) -> cb.conjunction();
    }
}
