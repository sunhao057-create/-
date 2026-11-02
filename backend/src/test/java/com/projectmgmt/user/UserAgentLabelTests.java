package com.projectmgmt.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserAgentLabelTests {

    @Test
    void windowsEdgeIsPcWindows() {
        String ua =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36 Edg/132.0.0.0";
        assertThat(UserAgentLabel.describe(ua)).isEqualTo("电脑 · Windows");
    }

    @Test
    void curlIsProgram() {
        assertThat(UserAgentLabel.describe("curl/8.2.1.0")).isEqualTo("程序 · curl");
    }

    @Test
    void iphoneIsMobileApple() {
        String ua =
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1";
        assertThat(UserAgentLabel.describe(ua)).isEqualTo("手机 · 苹果");
    }

    @Test
    void huaweiAndroidIsMobileHuawei() {
        String ua =
                "Mozilla/5.0 (Linux; Android 12; HUAWEI NOH-AN00) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/99.0.4844.88 Mobile Safari/537.36";
        assertThat(UserAgentLabel.describe(ua)).isEqualTo("手机 · 华为");
    }
}
