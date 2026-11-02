package com.projectmgmt.user.dto;

import com.projectmgmt.user.SysLoginLog;
import com.projectmgmt.user.UserAgentLabel;
import java.time.LocalDateTime;

public record LoginLogView(
        Long id,
        Long userId,
        String username,
        boolean success,
        String failReason,
        String ip,
        String userAgent,
        String deviceLabel,
        LocalDateTime createdAt
) {
    public static LoginLogView from(SysLoginLog log) {
        return new LoginLogView(
                log.getId(),
                log.getUserId(),
                log.getUsername(),
                log.isSuccess(),
                log.getFailReason(),
                log.getIp(),
                log.getUserAgent(),
                UserAgentLabel.describe(log.getUserAgent()),
                log.getCreatedAt()
        );
    }
}
