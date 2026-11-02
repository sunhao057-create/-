package com.projectmgmt.region;

import com.projectmgmt.user.StaffView;

public record RegionView(Long id, String name, StaffView leader) {
    public static RegionView from(Region region) {
        return new RegionView(
                region.getId(),
                region.getName(),
                region.getLeader() == null ? null : StaffView.from(region.getLeader())
        );
    }
}
