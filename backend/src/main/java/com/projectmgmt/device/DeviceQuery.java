package com.projectmgmt.device;

public record DeviceQuery(
        String keyword,
        Long departmentId,
        Boolean enabled,
        String code,
        String name,
        String model,
        String location
) {
    public static DeviceQuery keywordOnly(String keyword) {
        return new DeviceQuery(keyword, null, null, null, null, null, null);
    }
}
