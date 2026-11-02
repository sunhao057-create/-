package com.projectmgmt.device;

final class DeviceLabels {

    private DeviceLabels() {
    }

    static String status(DeviceStatus status) {
        if (status == null) {
            return "在用";
        }
        return switch (status) {
            case IN_USE -> "在用";
            case REPAIRING -> "维修中";
            case MOVING -> "搬迁中";
            case SCRAPPED -> "已报废";
        };
    }

    static String kind(DeviceWorkOrderKind kind) {
        return switch (kind) {
            case REPAIR -> "维修";
            case MOVE -> "搬迁";
            case SCRAP -> "报废";
        };
    }
}
