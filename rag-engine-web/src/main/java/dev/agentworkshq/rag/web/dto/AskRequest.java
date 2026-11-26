package dev.agentworkshq.rag.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AskRequest(
        @NotBlank String question,
        @Size(max = 20) List<@NotBlank String> sourceNames,
        @Min(1) @Max(50) Integer topK) {

    @Override
    public String toString() {
        return "AskRequest[questionLength=" + (question == null ? 0 : question.length()) + ", sourceNames="
                + (sourceNames == null ? 0 : sourceNames.size()) + ", topK=" + topK + "]";
    }
}
