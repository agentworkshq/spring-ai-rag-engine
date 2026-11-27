package dev.agentworkshq.rag.core.exception;

import static org.assertj.core.api.Assertions.assertThat;

import dev.agentworkshq.rag.core.constants.ErrorMessages;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ClientMessageTest {

    @Test
    void noServerErrorGivesClientsAnArgument() {
        Arrays.stream(ErrorMessages.values()).filter(e -> e.getHttpStatus() >= 500).forEach(code -> {
            ServiceException e = new ServiceException(code, "/srv/private/path", "private-argument");

            assertThat(e.getClientMessage()).as(code.name())
                    .isEqualTo(code.getPublicMessage())
                    .doesNotContain("%", "private");
        });
    }

    @Test
    void aServerErrorKeepsItsDetailForTheLogs() {
        assertThat(new ServiceException(ErrorMessages.RAG_E013, "/srv/docs").getMessage()).contains("/srv/docs");
    }

    @Test
    void echoedValuesAreCleaned() {
        ServiceException e = new ServiceException(ErrorMessages.RAG_E016, "a\r\nb\u001B[31m", "c\u202Ed\u2028e\u200F");

        assertThat(e.getMessage()).isEqualTo("Ingestion is already running for source ab[31m and tenant cde");
        assertThat(e.getClientMessage()).isEqualTo(e.getMessage());
    }

    @Test
    void echoedValuesAreCut() {
        ResourceNotFoundException e = new ResourceNotFoundException(ErrorMessages.RAG_E018, "n".repeat(5000));

        assertThat(e.getMessage()).isEqualTo("Source " + "n".repeat(64) + " not found");
    }

    @Test
    void cuttingNeverSplitsACharacter() {
        String name = "n".repeat(63) + "\uD83D\uDE00" + "tail";

        assertThat(new ResourceNotFoundException(ErrorMessages.RAG_E018, name).getMessage())
                .isEqualTo("Source " + "n".repeat(63) + " not found");
    }

    @Test
    void numbersAreStillFormattedAsNumbers() {
        assertThat(new ServiceException(ErrorMessages.RAG_E009, "a.txt", 11L, 10L).getMessage())
                .isEqualTo("File a.txt is 11 bytes, exceeding the maximum of 10 bytes");
    }
}
