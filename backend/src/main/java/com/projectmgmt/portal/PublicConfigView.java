package com.projectmgmt.portal;

import java.util.List;

public record PublicConfigView(
        PublicModuleFlags project,
        PublicModuleFlags customer,
        PublicModuleFlags supplier,
        PublicModuleFlags payment,
        List<PublicModuleConfig> modules
) {}
