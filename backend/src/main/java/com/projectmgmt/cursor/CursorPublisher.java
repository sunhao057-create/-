package com.projectmgmt.cursor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CursorPublisher {

    private static final Pattern BRANCH = Pattern.compile("^[A-Za-z0-9._/-]{1,200}$");

    private final CursorTaskRepository repository;
    private final String repoUrl;
    private final String startingRef;
    private final Path workDir;
    private final Path appJar;
    private final java.util.Set<Long> running = ConcurrentHashMap.newKeySet();

    public CursorPublisher(
            CursorTaskRepository repository,
            @Value("${app.cursor.repo-url:}") String repoUrl,
            @Value("${app.cursor.starting-ref:main}") String startingRef,
            @Value("${app.cursor.work-dir:/opt/project-mgmt/src}") String workDir,
            @Value("${app.cursor.app-jar:/opt/project-mgmt/app.jar}") String appJar
    ) {
        this.repository = repository;
        this.repoUrl = repoUrl == null ? "" : repoUrl.trim();
        this.startingRef = StringUtils.hasText(startingRef) ? startingRef.trim() : "main";
        this.workDir = Path.of(workDir);
        this.appJar = Path.of(appJar);
    }

    public boolean begin(Long id) {
        return running.add(id);
    }

    public void publish(Long id) {
        try {
            CursorTask task = repository.findById(id).orElse(null);
            if (task == null || !"PUBLISHING".equals(task.getStatus())) {
                return;
            }
            String branch = task.getBranch();
            if (!safeBranch(branch) || !safeBranch(startingRef)) {
                fail(task, "分支名不能用于自动发布");
                return;
            }
            if (!StringUtils.hasText(repoUrl) || !repoUrl.startsWith("https://")) {
                fail(task, "代码仓库地址需要是 https 地址");
                return;
            }
            prepareRepo(branch);
            build();
            installJar();
            task.setStatus("PUBLISHED");
            task.setErrorMessage(null);
            repository.save(task);
            restartLater();
        } catch (Exception ex) {
            CursorTask task = repository.findById(id).orElse(null);
            if (task != null) {
                fail(task, ex.getMessage() == null ? "自动发布失败" : ex.getMessage());
            }
        } finally {
            running.remove(id);
        }
    }

    private void prepareRepo(String branch) throws IOException, InterruptedException {
        Files.createDirectories(workDir.getParent() == null ? Path.of("/opt/project-mgmt") : workDir.getParent());
        if (!Files.exists(workDir.resolve(".git"))) {
            if (Files.exists(workDir)) {
                throw new IllegalStateException("发布目录不是空的，不能拉取代码");
            }
            run(workDir.getParent(), "git", "clone", "--branch", startingRef, repoUrl, workDir.getFileName().toString());
        }
        run(workDir, "git", "fetch", "origin", branch);
        run(workDir, "git", "checkout", startingRef);
        run(workDir, "git", "pull", "--ff-only", "origin", startingRef);
        run(workDir, "git", "-c", "user.email=publish@local", "-c", "user.name=Publish", "merge", "--no-edit", "origin/" + branch);
        run(workDir, "git", "push", "origin", startingRef);
    }

    private void build() throws IOException, InterruptedException {
        Path frontend = workDir.resolve("frontend");
        Path backend = workDir.resolve("backend");
        if (!Files.isDirectory(frontend) || !Files.isDirectory(backend)) {
            throw new IllegalStateException("仓库里没有 frontend 和 backend");
        }
        run(frontend, "npm", "ci");
        run(frontend, "npx", "vite", "build");
        Path assets = backend.resolve("src/main/resources/static/assets");
        Path distAssets = frontend.resolve("dist/assets");
        if (Files.isDirectory(assets)) {
            try (var files = Files.list(assets)) {
                files.filter(path -> path.getFileName().toString().startsWith("index-") && path.getFileName().toString().endsWith(".js"))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException ignored) {
                                // 旧包删不掉时，后面的复制仍会覆盖 index.html 指向的新文件。
                            }
                        });
            }
        }
        Files.createDirectories(assets);
        Files.copy(frontend.resolve("dist/index.html"), backend.resolve("src/main/resources/static/index.html"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        try (var files = Files.list(distAssets)) {
            files.forEach(path -> {
                try {
                    Files.copy(path, assets.resolve(path.getFileName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ex) {
                    throw new IllegalStateException("复制前端文件失败");
                }
            });
        }
        Path mvnw = backend.resolve("mvnw");
        if (!Files.isExecutable(mvnw)) {
            run(backend, "chmod", "+x", "mvnw");
        }
        run(backend, "./mvnw", "-DskipTests", "package");
    }

    private void installJar() throws IOException, InterruptedException {
        Path built = workDir.resolve("backend/target/backend-0.0.1-SNAPSHOT.jar");
        if (!Files.isRegularFile(built)) {
            throw new IllegalStateException("没有打出可发布的程序包");
        }
        Path updated = appJar.resolveSibling(appJar.getFileName().toString() + ".new");
        Files.copy(built, updated, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        if (Files.exists(appJar)) {
            Files.copy(appJar, appJar.resolveSibling(appJar.getFileName().toString() + ".bak"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(updated, appJar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private void restartLater() throws IOException {
        new ProcessBuilder("bash", "-c", "sleep 2; systemctl restart project-mgmt")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
    }

    private void run(Path dir, String... command) throws IOException, InterruptedException {
        List<String> args = new ArrayList<>(List.of(command));
        Process process = new ProcessBuilder(args).directory(dir.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        int code = process.waitFor();
        if (code != 0) {
            String tail = output.length() > 300 ? output.substring(output.length() - 300) : output;
            throw new IllegalStateException(args.get(0) + " 失败：" + tail.replace(repoUrl, "仓库").trim());
        }
    }

    private void fail(CursorTask task, String message) {
        task.setStatus("FAILED");
        task.setErrorMessage(message == null ? "自动发布失败" : (message.length() > 500 ? message.substring(0, 500) : message));
        repository.save(task);
    }

    private static boolean safeBranch(String branch) {
        return branch != null && BRANCH.matcher(branch).matches() && !branch.contains("..") && !branch.startsWith("-");
    }
}
