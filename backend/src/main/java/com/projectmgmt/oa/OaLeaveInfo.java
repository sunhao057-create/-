package com.projectmgmt.oa;

import java.math.BigDecimal;

public record OaLeaveInfo(
        BigDecimal holidayDays,
        Boolean includeCompensatory,
        BigDecimal compensatoryDays,
        String month,
        String leaveType,
        String kinship,
        String endDate,
        BigDecimal leaveDays,
        String actualEndDate,
        BigDecimal actualDays
) {
}
