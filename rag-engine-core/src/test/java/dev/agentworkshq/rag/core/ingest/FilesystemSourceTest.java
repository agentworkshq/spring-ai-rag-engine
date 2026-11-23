package dev.agentworkshq.rag.core.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.agentworkshq.rag.core.constants.ErrorMessages;
import dev.agentworkshq.rag.core.exception.ServiceException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemSourceTest {

    @TempDir
    Path root;

    private List<String> scanIds(FilesystemSource source) {
        return source.scan().map(SourceItem::externalId).toList();
    }

    @Test
    void includeGlobsFilterFiles() throws IOException {
        Files.writeString(root.resolve("a.md"), "a");
        Files.writeString(root.resolve("b.txt"), "b");
        Files.createDirectories(root.resolve("docs"));
        Files.writeString(root.resolve("docs/c.md"), "c");

        assertThat(scanIds(new FilesystemSource("s", root, List.of("**/*.md"))))
                .containsExactly("a.md", "docs/c.md");
        assertThat(scanIds(new FilesystemSource("s", root, List.of("*.txt")))).containsExactly("b.txt");
        assertThat(scanIds(new FilesystemSource("s", root, List.of()))).containsExactly("a.md", "b.txt", "docs/c.md");
    }

    @Test
    void externalIdAlwaysUsesForwardSlashes() throws IOException {
        Files.createDirectories(root.resolve("a/b"));
        Files.writeString(root.resolve("a/b/c.md"), "c");

        assertThat(scanIds(new FilesystemSource("s", root, List.of()))).containsExactly("a/b/c.md");
    }

    @Test
    void itemExposesMetadataAndContent() throws IOException {
        Files.writeString(root.resolve("a.md"), "hello");

        SourceItem item = new FilesystemSource("s", root, List.of()).scan().findFirst().orElseThrow();

        assertThat(item.filename()).isEqualTo("a.md");
        assertThat(item.size()).isEqualTo(5);
        assertThat(item.lastModified()).isNotNull();
        try (var in = item.open()) {
            assertThat(new String(in.readAllBytes())).isEqualTo("hello");
        }
    }

    @Test
    void hiddenFilesAndDirectoriesAreSkipped() throws IOException {
        Files.writeString(root.resolve(".secret.md"), "x");
        Files.createDirectories(root.resolve(".git"));
        Files.writeString(root.resolve(".git/config.md"), "x");
        Files.writeString(root.resolve("visible.md"), "x");

        assertThat(scanIds(new FilesystemSource("s", root, List.of()))).containsExactly("visible.md");
    }

    @Test
    void symlinkPointingOutsideRootIsRejected(@TempDir Path outside) throws IOException {
        Path target = Files.writeString(outside.resolve("outside.md"), "secret");
        try {
            Files.createSymbolicLink(root.resolve("link.md"), target);
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            assumeTrue(false, "symlinks not supported here");
        }
        Files.writeString(root.resolve("inside.md"), "ok");

        assertThat(scanIds(new FilesystemSource("s", root, List.of()))).containsExactly("inside.md");
    }

    @Test
    void parentEscapePatternIsRejected() {
        assertThatThrownBy(() -> new FilesystemSource("s", root, List.of("../**/*.md")))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorMessage())
                .isEqualTo(ErrorMessages.RAG_E014);
    }

    @Test
    void missingRootFailsClearly() {
        Path missing = root.resolve("nope");

        assertThatThrownBy(() -> new FilesystemSource("s", missing, List.of()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("nope")
                .extracting(e -> ((ServiceException) e).getErrorMessage())
                .isEqualTo(ErrorMessages.RAG_E013);
    }
}
