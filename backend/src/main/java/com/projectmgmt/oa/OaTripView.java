package com.projectmgmt.oa;

import java.math.BigDecimal;
import java.util.List;

public record OaTripView(
        List<Long> companionIds,
        List<String> companionNames,
        String fromCity,
        String toCity,
        String travelMode,
        String travelModeLabel,
        String plateNo,
        BigDecimal mileage,
        String returnPlanDate,
        String reviewTime,
        String actualReturnDate,
        BigDecimal durationHours,
        String remark
) {
}
