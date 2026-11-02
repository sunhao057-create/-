package com.projectmgmt.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MojibakeRepairerTests {

    @Test
    void restoresDoubleEncodedChinese() {
        String garbled = new String(
                "苏州冠礼".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.nio.charset.StandardCharsets.ISO_8859_1
        );
        assertTrue(MojibakeRepairer.looksDoubleEncoded(garbled));
        assertEquals("苏州冠礼", MojibakeRepairer.decode(garbled));
    }

    @Test
    void leavesCorrectChineseUnchanged() {
        assertFalse(MojibakeRepairer.looksDoubleEncoded("苏州冠礼科技股份有限公司"));
        assertEquals("华东地区", MojibakeRepairer.decode("华东地区"));
        assertEquals("公开填报项目样例", MojibakeRepairer.decode("公开填报项目样例"));
    }
}
