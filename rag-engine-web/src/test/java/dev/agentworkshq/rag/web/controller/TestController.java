package dev.agentworkshq.rag.web.controller;

import dev.agentworkshq.rag.core.constants.ErrorMessages;
import dev.agentworkshq.rag.core.exception.ResourceNotFoundException;
import dev.agentworkshq.rag.web.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestController {

    record Body(@NotBlank String name, @Email String email) {
    }

    @GetMapping("/ok")
    ApiResponse<String> ok() {
        return ApiResponse.success("done", null);
    }

    @GetMapping("/not-found")
    ApiResponse<Void> notFound() {
        throw new ResourceNotFoundException(ErrorMessages.RAG_E002, 42);
    }

    @GetMapping("/boom")
    ApiResponse<Void> boom() {
        throw new IllegalStateException("secret internal detail");
    }

    @PostMapping("/valid")
    ApiResponse<String> valid(@Valid @RequestBody Body body) {
        return ApiResponse.success("valid", body.name());
    }
}
