package com.projectmgmt.portal;

import java.util.List;

public record PublicProjectView(
        Long id,
        String name,
        String code,
        Long customerId,
        String customerShortName,
        String companyName,
        String companyCode,
        String companyLevel,
        Long managerId,
        String managerName,
        Long regionId,
        String regionName,
        String regionLeaderName,
        String status,
        String overview,
        Integer progressPercent,
        List<PublicAttachmentView> attachments
) {}
