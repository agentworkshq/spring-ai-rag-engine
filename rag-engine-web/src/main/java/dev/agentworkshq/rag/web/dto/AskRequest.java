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
}
