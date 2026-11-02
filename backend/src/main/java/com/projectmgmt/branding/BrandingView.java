package com.projectmgmt.branding;

public record BrandingView(String name, String logoUrl, String themeId) {
    public static BrandingView from(SysBranding branding) {
        String theme = branding.getThemeId();
        if (theme == null || theme.isBlank()) {
            theme = BrandingService.DEFAULT_THEME_ID;
        }
        return new BrandingView(branding.getName(), branding.getLogoUrl(), theme);
    }
}
