package com.projectmgmt.warehouse;

public record UpsertWarehouseRequest(
        String name,
        String address,
        String keeper,
        String remark,
        Boolean enabled
) {
}
