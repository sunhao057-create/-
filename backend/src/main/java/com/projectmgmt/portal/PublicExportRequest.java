package com.projectmgmt.portal;

import java.util.List;

public record PublicExportRequest(String kind, java.util.List<Long> ids, String keyword) {}
