package dev.agentworkshq.rag.core.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.agentworkshq.rag.core.constants.ErrorMessages;
import dev.agentworkshq.rag.core.exception.ServiceException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ContentHasherTest {

    private static SourceItem item(long declaredSize, SourceItem.Opener opener) {
        return new SourceItem("big.txt", "big.txt", "text/plain", Instant.now(), declaredSize, opener);
    }

    private static void assertTooLarge(SourceItem item, long maxFileSize) {
        assertThatThrownBy(() -> ContentHasher.fileHash(item, maxFileSize))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getErrorMessage())
                        .isEqualTo(ErrorMessages.RAG_E009));
    }

    @Test
    void anItemLargerThanTheLimitIsNeverOpened() {
        AtomicInteger opened = new AtomicInteger();

        assertTooLarge(item(11, () -> {
            opened.incrementAndGet();
            return new ByteArrayInputStream(new byte[11]);
        }), 10);

        assertThat(opened).hasValue(0);
    }

    @Test
    void anItemThatStreamsMoreThanItReportsIsStopped() {
        AtomicInteger read = new AtomicInteger();
        InputStream endless = new InputStream() {
            @Override
            public int read() {
                read.incrementAndGet();
                return 'x';
            }

            @Override
            public int read(byte[] buffer, int offset, int length) {
                read.addAndGet(length);
                return length;
            }
        };

        assertTooLarge(item(1, () -> endless), 10_000);

        assertThat(read.get()).isLessThanOrEqualTo(10_000 + 8192);
    }

    @Test
    void anItemOfExactlyTheLimitIsHashed() {
        SourceItem item = item(3, () -> new ByteArrayInputStream(new byte[] {'a', 'b', 'c'}));

        assertThat(ContentHasher.fileHash(item, 3))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
