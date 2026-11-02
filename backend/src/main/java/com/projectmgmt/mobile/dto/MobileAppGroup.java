package com.projectmgmt.mobile.dto;

import java.util.List;

public record MobileAppGroup(String key, String title, List<MobileAppItem> items) {
}
