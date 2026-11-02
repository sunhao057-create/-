package com.projectmgmt.profile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.projectmgmt.common.ApiException;

@Service
public class FileStorageService {

    private static final Set<String> ALLOWED = Set.of("jpg", "jpeg", "png", "webp");

    private final Path root;

    public FileStorageService(@Value("${app.upload-dir}") String uploadDir) throws IOException {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(this.root.resolve("avatars"));
        Files.createDirectories(this.root.resolve("logos"));
    }

    public String saveLogo(MultipartFile file) {
        return saveImage(file, "logos", "logo-", "图标", 2 * 1024 * 1024L);
    }

    public String saveAvatar(Long userId, MultipartFile file) {
        return saveImage(file, "avatars", userId + "-", "头像", 2 * 1024 * 1024L);
    }

    public String saveDevicePhoto(MultipartFile file) {
        return saveImage(file, "devices", "eq-", "设备图片", 8 * 1024 * 1024L);
    }

    public String savePortalImage(MultipartFile file) {
        return saveImage(file, "portal", "banner-", "图片", 8 * 1024 * 1024L);
    }

    public Path resolveUpload(String url) {
        if (url == null || !url.startsWith("/uploads/")) {
            return null;
        }
        Path target = root.resolve(url.substring("/uploads/".length())).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(target)) {
            return null;
        }
        return target;
    }

    private String saveImage(MultipartFile file, String folder, String prefix, String label, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("请选择" + label);
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = original.contains(".")
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
                : "";
        if (!ALLOWED.contains(ext)) {
            throw ApiException.badRequest(label + "仅支持 jpg / png / webp");
        }
        if (file.getSize() > maxBytes) {
            throw ApiException.badRequest(label + "不能超过 " + (maxBytes / (1024 * 1024)) + "MB");
        }
        String filename = prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + "." + ext;
        try {
            Path dir = root.resolve(folder);
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            file.transferTo(target);
        } catch (IOException e) {
            throw new IllegalStateException("保存" + label + "失败", e);
        }
        return "/uploads/" + folder + "/" + filename;
    }

    public StoredFile saveProjectFile(MultipartFile file) {
        return saveBizFile(file, "projects");
    }

    public StoredFile savePaymentFile(MultipartFile file) {
        return saveBizFile(file, "payments");
    }

    public StoredFile saveExpenseFile(MultipartFile file) {
        return saveBizFile(file, "expenses");
    }

    public StoredFile saveDeviceWorkOrderFile(MultipartFile file) {
        return saveBizFile(file, "device-orders");
    }

    public StoredFile saveOaFile(MultipartFile file) {
        return saveBizFile(file, "oa");
    }

    public StoredFile saveTechFile(MultipartFile file) {
        return saveBizFile(file, "tech");
    }

    public StoredFile saveDepositFile(MultipartFile file) {
        return saveBizFile(file, "deposits");
    }

    public StoredFile saveBidFile(MultipartFile file) {
        return saveBizFile(file, "bids");
    }

    private StoredFile saveBizFile(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("请选择要上传的文件");
        }
        String original = file.getOriginalFilename() == null ? "未命名" : file.getOriginalFilename();
        String ext = original.contains(".")
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
                : "";
        Set<String> allowed = Set.of(
                "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
                "png", "jpg", "jpeg", "gif", "bmp", "tif", "tiff", "webp", "heic",
                "zip", "rar", "7z", "txt", "csv"
        );
        if (!allowed.contains(ext)) {
            throw ApiException.badRequest("附件仅支持文档、图片或压缩包");
        }
        if (file.getSize() > 20 * 1024 * 1024L) {
            throw ApiException.badRequest("单个附件不能超过 20MB");
        }
        String filename = UUID.randomUUID().toString().replace("-", "") + (ext.isBlank() ? "" : "." + ext);
        try {
            Path dir = root.resolve(folder);
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            file.transferTo(target);
            return new StoredFile(original, "/uploads/" + folder + "/" + filename, file.getSize(), file.getContentType());
        } catch (IOException e) {
            throw new IllegalStateException("保存附件失败", e);
        }
    }

    public record StoredFile(String originalName, String url, long size, String contentType) {
    }
}
