package com.projectmgmt.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.springframework.util.StringUtils;

public final class BizParse {

    private BizParse() {
    }

    public static String required(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw ApiException.badRequest(message);
        }
        return value.trim();
    }

    public static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public static BigDecimal amount(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw ApiException.badRequest("请填写金额");
        }
        try {
            BigDecimal value = new BigDecimal(raw.trim()).setScale(2, RoundingMode.HALF_UP);
            if (value.compareTo(BigDecimal.ZERO) <= 0) {
                throw ApiException.badRequest("金额必须大于 0");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw ApiException.badRequest("金额格式不正确");
        }
    }

    public static BigDecimal amountOrZero(String raw) {
        if (!StringUtils.hasText(raw)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        try {
            BigDecimal value = new BigDecimal(raw.trim()).setScale(2, RoundingMode.HALF_UP);
            if (value.compareTo(BigDecimal.ZERO) < 0) {
                throw ApiException.badRequest("金额不能为负数");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw ApiException.badRequest("金额格式不正确");
        }
    }

    public static BigDecimal qty(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw ApiException.badRequest("请填写数量");
        }
        try {
            BigDecimal value = new BigDecimal(raw.trim()).setScale(3, RoundingMode.HALF_UP);
            if (value.compareTo(BigDecimal.ZERO) <= 0) {
                throw ApiException.badRequest("数量必须大于 0");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw ApiException.badRequest("数量格式不正确");
        }
    }

    public static LocalDate date(String raw, String message) {
        if (!StringUtils.hasText(raw)) {
            throw ApiException.badRequest(message);
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            throw ApiException.badRequest(message);
        }
    }

    public static String unit(String raw) {
        return StringUtils.hasText(raw) ? raw.trim() : "件";
    }

    public static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
