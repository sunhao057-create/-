package com.projectmgmt.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.projectmgmt.common.ApiException;
import org.junit.jupiter.api.Test;

class SubmitRateLimiterTests {

    @Test
    void eleventhCallInWindowIsRejected() {
        SubmitRateLimiter limiter = new SubmitRateLimiter();
        for (int i = 0; i < 10; i++) {
            limiter.check("10.0.0.1", "CUSTOMER");
        }
        ApiException ex = assertThrows(ApiException.class, () -> limiter.check("10.0.0.1", "CUSTOMER"));
        assertEquals(429, ex.getStatus());
        assertEquals("提交过于频繁，请稍后再试", ex.getMessage());
    }
}
