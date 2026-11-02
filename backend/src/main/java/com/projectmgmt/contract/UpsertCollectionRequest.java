package com.projectmgmt.contract;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpsertCollectionRequest(
        Long projectId,
        Long accountId,
        @NotNull(message = "请填写回款金额")
        BigDecimal amount,
        @NotNull(message = "请选择回款日期")
        LocalDate receivedDate,
        String method,
        String remark
) {
}
