package com.projectmgmt.portal;

import java.util.List;

public record PublicLookupsView(
        List<PublicCustomerView> customers,
        List<PublicStaffOption> staff,
        List<PublicRegionOption> regions
) {}
