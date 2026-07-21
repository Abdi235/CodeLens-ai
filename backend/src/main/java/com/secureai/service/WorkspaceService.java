package com.secureai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Service
public class WorkspaceService {

    private final Path workspaceRoot;

    public WorkspaceService(@Value("${secureai.workspace-dir:./workspace}") String workspaceDir) {
        this.workspaceRoot = Path.of(workspaceDir).toAbsolutePath().normalize();
    }

    public Path ensureRoot() throws IOException {
        Files.createDirectories(workspaceRoot);
        return workspaceRoot;
    }

    public Path prepareProjectWorkspace(Long projectId, Long scanId) throws IOException {
        Path dir = ensureRoot().resolve("project-" + projectId).resolve("scan-" + scanId);
        if (Files.exists(dir)) {
            deleteRecursive(dir);
        }
        Files.createDirectories(dir);
        return dir;
    }

    public Path cloneRepository(String repositoryUrl, Path destination) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "git", "clone", "--depth", "1", repositoryUrl, destination.toString()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        boolean finished = process.waitFor(3, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("git clone timed out");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("git clone failed: " + output);
        }
        return destination;
    }

    public Path resolveLocalOrSample(String repositoryUrl, Path destination) throws IOException {
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            return copySamples(destination);
        }
        Path local = Path.of(repositoryUrl);
        if (Files.exists(local)) {
            copyDirectory(local.toAbsolutePath().normalize(), destination);
            return destination;
        }
        if (repositoryUrl.startsWith("samples/") || repositoryUrl.startsWith("./samples")) {
            Path samples = Path.of(repositoryUrl).toAbsolutePath().normalize();
            if (Files.exists(samples)) {
                copyDirectory(samples, destination);
                return destination;
            }
        }
        throw new IllegalArgumentException("Unsupported repository reference: " + repositoryUrl);
    }

    public Path copySamples(Path destination) throws IOException {
        List<Path> candidates = List.of(
                Path.of("samples").toAbsolutePath().normalize(),
                Path.of("..", "samples").toAbsolutePath().normalize(),
                Path.of("/app/samples")
        );
        Path samples = candidates.stream().filter(Files::exists).findFirst().orElse(null);
        if (samples == null) {
            throw new IllegalStateException("Sample vulnerable projects not found. Set repository URL or place samples/ at repo root.");
        }
        copyDirectory(samples, destination);
        return destination;
    }

    public void deleteRecursive(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            });
        }
    }

    private void copyDirectory(Path source, Path destination) throws IOException {
        try (Stream<Path> walk = Files.walk(source)) {
            walk.forEach(path -> {
                try {
                    Path relative = source.relativize(path);
                    Path target = destination.resolve(relative.toString());
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(path, target);
                    }
                } catch (IOException e) {
                    throw new IllegalStateException("Failed copying " + path + ": " + e.getMessage(), e);
                }
            });
        }
    }
}
