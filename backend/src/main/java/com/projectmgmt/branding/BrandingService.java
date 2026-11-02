package com.projectmgmt.branding;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.profile.FileStorageService;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class BrandingService {

    public static final long DEFAULT_ID = 1L;
    public static final String DEFAULT_NAME = "项目管理";
    public static final String DEFAULT_THEME_ID = "qingflow";
    private static final Set<String> THEME_IDS = Set.of(
            "qingflow",
            "minimal",
            "business",
            "oled",
            "glass",
            "soft",
            "aurora",
            "brutal",
            "accessible",
            "teal",
            "paper",
            "cyber",
            "vibrant"
    );

    private final BrandingRepository brandingRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public SysBranding ensureDefault() {
        SysBranding branding = brandingRepository.findById(DEFAULT_ID).orElseGet(() -> {
            SysBranding created = new SysBranding();
            created.setId(DEFAULT_ID);
            created.setName(DEFAULT_NAME);
            created.setThemeId(DEFAULT_THEME_ID);
            return brandingRepository.save(created);
        });
        if (branding.getThemeId() == null || branding.getThemeId().isBlank()) {
            branding.setThemeId(DEFAULT_THEME_ID);
        }
        return branding;
    }

    @Transactional
    public BrandingView get() {
        return BrandingView.from(ensureDefault());
    }

    @Transactional
    public BrandingView updateName(String name) {
        SysBranding branding = ensureDefault();
        branding.setName(name.trim());
        return BrandingView.from(branding);
    }

    @Transactional
    public BrandingView updateLogo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("请选择图标图片");
        }
        SysBranding branding = ensureDefault();
        branding.setLogoUrl(fileStorageService.saveLogo(file));
        return BrandingView.from(branding);
    }

    @Transactional
    public BrandingView updateTheme(String themeId) {
        String id = themeId == null ? "" : themeId.trim();
        if (!THEME_IDS.contains(id)) {
            throw ApiException.badRequest("不支持的主题风格");
        }
        SysBranding branding = ensureDefault();
        branding.setThemeId(id);
        return BrandingView.from(branding);
    }
}
