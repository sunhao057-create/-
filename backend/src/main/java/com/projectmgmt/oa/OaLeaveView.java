package com.projectmgmt.oa;

import java.math.BigDecimal;

public record OaLeaveView(
        BigDecimal holidayDays,
        Boolean includeCompensatory,
        BigDecimal compensatoryDays,
        String month,
        String leaveType,
        String leaveTypeLabel,
        String kinship,
        String kinshipLabel,
        String endDate,
        BigDecimal leaveDays,
        String actualEndDate,
        BigDecimal actualDays
) {
}
