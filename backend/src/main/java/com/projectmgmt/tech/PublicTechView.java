package com.projectmgmt.tech;

import java.util.List;
import java.util.Map;

public record PublicTechView(
        Long id,
        String code,
        String title,
        String kind,
        String kindLabel,
        Map<String, Object> extra,
        List<Map<String, Object>> files,
        String createdAt) {

    public static PublicTechView from(TechRecordView view) {
        return new PublicTechView(
                view.id(),
                view.code(),
                view.title(),
                view.kind(),
                view.kindLabel(),
                view.extra(),
                view.files(),
                view.createdAt());
    }
}
