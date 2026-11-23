package dev.agentworkshq.rag.core.ingest;

import dev.agentworkshq.rag.core.constants.ErrorMessages;
import dev.agentworkshq.rag.core.exception.ServiceException;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class FilesystemSource implements RagSource {

    private final String name;
    private final Path root;
    private final List<PathMatcher> matchers;

    public FilesystemSource(String name, Path root, List<String> includes) {
        this.name = name;
        this.root = realRoot(root);
        this.matchers = matchers(includes);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Stream<SourceItem> scan() {
        List<SourceItem> items = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (!dir.equals(root) && isHidden(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    toItem(file).ifPresent(items::add);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw failure(ErrorMessages.RAG_E015, e, root);
        }
        items.sort(Comparator.comparing(SourceItem::externalId));
        return items.stream();
    }

    private Optional<SourceItem> toItem(Path file) throws IOException {
        if (isHidden(file)) {
            return Optional.empty();
        }
        Path real = file.toRealPath();
        if (!real.startsWith(root) || !Files.isRegularFile(real) || isHidden(real)) {
            return Optional.empty();
        }
        Path relative = root.relativize(real);
        if (!matches(relative)) {
            return Optional.empty();
        }
        String contentType = Files.probeContentType(real);
        return Optional.of(new SourceItem(
                relative.toString().replace('\\', '/'),
                real.getFileName().toString(),
                contentType != null ? contentType : "application/octet-stream",
                Instant.ofEpochMilli(Files.getLastModifiedTime(real).toMillis()),
                Files.size(real),
                () -> Files.newInputStream(real)));
    }

    private boolean matches(Path relative) {
        return matchers.isEmpty() || matchers.stream().anyMatch(m -> m.matches(relative));
    }

    private static boolean isHidden(Path path) throws IOException {
        Path fileName = path.getFileName();
        return fileName != null && (fileName.toString().startsWith(".") || Files.isHidden(path));
    }

    private static Path realRoot(Path root) {
        if (root == null || !Files.isDirectory(root)) {
            throw new ServiceException(ErrorMessages.RAG_E013, root);
        }
        try {
            return root.toRealPath();
        } catch (IOException e) {
            throw failure(ErrorMessages.RAG_E013, e, root);
        }
    }

    private static List<PathMatcher> matchers(List<String> includes) {
        List<PathMatcher> result = new ArrayList<>();
        if (includes == null) {
            return result;
        }
        for (String pattern : includes) {
            for (String segment : pattern.split("[/\\\\]")) {
                if (segment.equals("..")) {
                    throw new ServiceException(ErrorMessages.RAG_E014, pattern);
                }
            }
            result.add(FileSystems.getDefault().getPathMatcher("glob:" + pattern));
            // glob "**/x" does not match a file directly in the root
            if (pattern.startsWith("**/")) {
                result.add(FileSystems.getDefault().getPathMatcher("glob:" + pattern.substring(3)));
            }
        }
        return result;
    }

    private static ServiceException failure(ErrorMessages message, IOException cause, Object arg) {
        ServiceException ex = new ServiceException(message, arg);
        ex.initCause(cause);
        return ex;
    }
}
