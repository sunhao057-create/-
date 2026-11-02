package com.projectmgmt.project;

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
public class ProjectProgressService {

    private final ProjectRepository projectRepository;
    private final ProjectProgressRepository progressRepository;
    private final PermissionService permissionService;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public List<ProjectProgressView> list(Long projectId) {
        Project project = requireProject(projectId);
        permissionService.requireOwnRecord(FormModule.PROJECT, project.getCreatedBy());
        return progressRepository.findByProjectOrderByIdDesc(project).stream()
                .map(item -> {
                    item.getFiles().size();
                    return ProjectProgressView.from(item);
                })
                .toList();
    }

    @Transactional
    public ProjectProgressView create(
            Long projectId,
            String content,
            Integer percent,
            List<MultipartFile> files,
            SysUser actor
    ) {
        Project project = requireProject(projectId);
        permissionService.requireOwnRecord(FormModule.PROJECT, project.getCreatedBy());
        return save(project, content, percent, files, actor, "ADMIN", actor == null ? "系统" : actor.getDisplayName());
    }

    @Transactional(readOnly = true)
    public List<ProjectProgressView> listPublic(Long projectId) {
        Project project = requirePublicProject(projectId);
        return progressRepository.findByProjectOrderByIdDesc(project).stream()
                .map(item -> {
                    item.getFiles().size();
                    return ProjectProgressView.from(item);
                })
                .toList();
    }

    @Transactional
    public ProjectProgressView createPublic(Long projectId, String content, Integer percent, List<MultipartFile> files) {
        Project project = requirePublicProject(projectId);
        return save(project, content, percent, files, null, "PUBLIC", "公开访客");
    }

    @Transactional
    public void deleteForProject(Project project) {
        progressRepository.deleteAll(progressRepository.findByProjectOrderByIdDesc(project));
    }

    private ProjectProgressView save(
            Project project,
            String content,
            Integer percent,
            List<MultipartFile> files,
            SysUser actor,
            String source,
            String creatorName
    ) {
        if (!StringUtils.hasText(content)) {
            throw ApiException.badRequest("请填写项目进展情况");
        }
        String trimmed = content.trim();
        if (trimmed.length() > 2000) {
            throw ApiException.badRequest("进展情况不能超过 2000 字");
        }
        if (percent == null || percent < 0 || percent > 100) {
            throw ApiException.badRequest("完成百分比必须是 0 到 100 的整数");
        }
        ProjectProgress progress = new ProjectProgress();
        progress.setProject(project);
        progress.setContent(trimmed);
        progress.setPercent(percent);
        progress.setSubmitSource(source);
        progress.setCreatedBy(actor);
        progress.setCreatorName(creatorName);
        if (files != null) {
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                FileStorageService.StoredFile stored = fileStorageService.saveProjectFile(file);
                ProjectProgressFile attachment = new ProjectProgressFile();
                attachment.setProgress(progress);
                attachment.setOriginalName(stored.originalName());
                attachment.setUrl(stored.url());
                attachment.setSize(stored.size());
                attachment.setContentType(stored.contentType());
                progress.getFiles().add(attachment);
            }
        }
        ProjectProgress saved = progressRepository.save(progress);
        project.setOverview(trimmed);
        project.setProgressPercent(percent);
        saved.getFiles().size();
        return ProjectProgressView.from(saved);
    }

    private Project requireProject(Long projectId) {
        return projectRepository.findById(projectId).orElseThrow(() -> ApiException.notFound("项目不存在"));
    }

    private Project requirePublicProject(Long projectId) {
        return projectRepository.findById(projectId)
                .filter(Project::isPublic)
                .orElseThrow(() -> ApiException.notFound("内容不存在"));
    }
}
