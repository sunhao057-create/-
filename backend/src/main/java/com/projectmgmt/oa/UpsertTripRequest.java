package com.projectmgmt.oa;

import java.math.BigDecimal;
import java.util.List;

public record UpsertTripRequest(
        List<Long> companionIds,
        String fromCity,
        String toCity,
        String travelMode,
        String plateNo,
        BigDecimal mileage,
        String returnPlanDate,
        String reviewTime,
        String actualReturnDate,
        BigDecimal durationHours,
        String remark
) {
}
