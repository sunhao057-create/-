package com.projectmgmt.portal;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.profile.FileStorageService;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.user.SysUser;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class PortalPostService {

    private final PortalPostRepository repository;
    private final PermissionService permissionService;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public List<PortalPostView> list(String kind) {
        FormModule module = moduleOf(kind);
        permissionService.require(module, "VIEW");
        return repository.findByKindOrderBySortOrderAscIdDesc(module.name()).stream()
                .map(PortalPostView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PortalPostView get(String kind, Long id) {
        FormModule module = moduleOf(kind);
        permissionService.require(module, "VIEW");
        PortalPost post = require(id);
        if (!module.name().equals(post.getKind())) {
            throw ApiException.notFound("内容不存在");
        }
        return PortalPostView.from(post);
    }

    @Transactional(readOnly = true)
    public List<PortalPostView> published(String kind) {
        FormModule module = moduleOf(kind);
        return repository.findByKindAndEnabledTrueOrderBySortOrderAscIdDesc(module.name()).stream()
                .map(PortalPostView::from)
                .toList();
    }

    @Transactional
    public PortalPostView create(PortalPostView request, SysUser creator) {
        FormModule module = moduleOf(request == null ? null : request.kind());
        permissionService.require(module, "CREATE");
        PortalPost post = new PortalPost();
        post.setKind(module.name());
        post.setCreatedBy(creator);
        apply(post, request);
        return PortalPostView.from(repository.save(post));
    }

    @Transactional
    public PortalPostView update(Long id, PortalPostView request) {
        PortalPost post = require(id);
        permissionService.require(moduleOf(post.getKind()), "EDIT");
        apply(post, request);
        return PortalPostView.from(repository.save(post));
    }

    @Transactional
    public void delete(Long id) {
        PortalPost post = require(id);
        permissionService.require(moduleOf(post.getKind()), "DELETE");
        repository.delete(post);
    }

    public String upload(MultipartFile file, String kind) {
        permissionService.require(moduleOf(kind), "CREATE");
        return fileStorageService.savePortalImage(file);
    }

    private PortalPost require(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("内容不存在"));
    }

    private void apply(PortalPost post, PortalPostView request) {
        String title = request == null ? "" : request.title();
        if (!StringUtils.hasText(title)) {
            throw ApiException.badRequest("请填写标题");
        }
        post.setTitle(title.trim());
        post.setCategory(trim(request == null ? null : request.category()));
        post.setSummary(trim(request == null ? null : request.summary()));
        post.setBody(request == null ? null : request.body());
        post.setCoverUrl(trim(request == null ? null : request.coverUrl()));
        post.setSortOrder(request == null ? 0 : request.sortOrder());
        post.setEnabled(request == null || request.enabled());
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    static FormModule moduleOf(String kind) {
        if (!StringUtils.hasText(kind)) {
            throw ApiException.badRequest("请选择内容类型");
        }
        FormModule module = FormModule.fromNameOrNull(kind.trim());
        if (module == null || !module.isMiniContent()) {
            throw ApiException.badRequest("未知的小程序内容");
        }
        return module;
    }
}
