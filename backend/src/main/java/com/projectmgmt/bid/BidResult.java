package com.projectmgmt.bid;

public enum BidResult {
    WON("中标"),
    LOST("未中标"),
    RETENDER("再次招标"),
    VOIDED("废标退回");

    private final String label;

    BidResult(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static BidResult parse(String raw, boolean allowVoided) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            BidResult value = BidResult.valueOf(raw.trim().toUpperCase());
            if (!allowVoided && value == VOIDED) {
                throw new IllegalArgumentException();
            }
            return value;
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("最终结果不正确");
        }
    }
}
