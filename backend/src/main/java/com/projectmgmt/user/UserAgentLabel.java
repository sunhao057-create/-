package com.projectmgmt.user;

import java.util.Locale;

public final class UserAgentLabel {

    private UserAgentLabel() {}

    public static String describe(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "未知设备";
        }
        String raw = userAgent.trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        String kind = kind(lower);
        String brand = brand(raw, lower, kind);
        if (brand == null || brand.isBlank()) {
            return kind;
        }
        return kind + " · " + brand;
    }

    private static String kind(String lower) {
        if (isTool(lower)) {
            return "程序";
        }
        if (contains(lower, "ipad", "tablet", "playbook", "silk/")) {
            return "平板";
        }
        if (contains(lower, "iphone", "ipod", "android", "mobile", "harmonyos", "okhttp", "micromessenger")) {
            if (contains(lower, "android") && !contains(lower, "mobile", "okhttp") && contains(lower, "linux")) {
                return "平板";
            }
            return "手机";
        }
        if (contains(lower, "windows", "macintosh", "mac os", "cros", "x11", "linux", "wow64")) {
            return "电脑";
        }
        return "电脑";
    }

    private static String brand(String raw, String lower, String kind) {
        if (contains(lower, "iphone", "ipad", "ipod", "macintosh", "mac os", "intel mac")) {
            return "苹果";
        }
        if (contains(lower, "huawei", "harmonyos", "hmos", "huawei")) {
            return "华为";
        }
        if (contains(lower, "honor")) {
            return "荣耀";
        }
        if (contains(lower, "xiaomi", "redmi", "miui", "mix ")) {
            return "小米";
        }
        if (contains(lower, "oppo", "heytap")) {
            return "OPPO";
        }
        if (contains(lower, "vivo")) {
            return "vivo";
        }
        if (contains(lower, "samsung", "sm-")) {
            return "三星";
        }
        if (contains(lower, "oneplus", "one plus")) {
            return "一加";
        }
        if (contains(lower, "realme")) {
            return "realme";
        }
        if (contains(lower, "meizu")) {
            return "魅族";
        }
        if (contains(lower, "pixel")) {
            return "Google";
        }
        if (contains(lower, "windows")) {
            return "Windows";
        }
        if (contains(lower, "cros", "chromebook")) {
            return "ChromeOS";
        }
        if (contains(lower, "android")) {
            return "安卓";
        }
        if (contains(lower, "linux") && "电脑".equals(kind)) {
            return "Linux";
        }
        if (lower.startsWith("curl/") || contains(lower, "curl/")) {
            return "curl";
        }
        if (contains(lower, "postman")) {
            return "Postman";
        }
        if (contains(lower, "python-requests", "python-urllib")) {
            return "Python";
        }
        if (raw.length() < 40 && !raw.contains("(")) {
            return raw;
        }
        return null;
    }

    private static boolean isTool(String lower) {
        return contains(
                lower,
                "curl/",
                "wget/",
                "postman",
                "python-requests",
                "python-urllib",
                "httpie",
                "insomnia",
                "apache-httpclient");
    }

    private static boolean contains(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
