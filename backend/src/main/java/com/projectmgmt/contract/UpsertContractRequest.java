package com.projectmgmt.contract;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpsertContractRequest(
        @NotBlank(message = "请填写合同名称")
        String name,
        @NotNull(message = "请选择客户")
        Long customerId,
        @NotNull(message = "请填写合同金额")
        BigDecimal amount,
        LocalDate signDate,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        String remark,
        Boolean isPublic
) {
}
