package dev.agentworkshq.rag.web.other;

import dev.agentworkshq.rag.core.constants.ErrorMessages;
import dev.agentworkshq.rag.core.exception.ResourceNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OutsideController {

    @GetMapping("/outside/not-found")
    public String notFound() {
        throw new ResourceNotFoundException(ErrorMessages.RAG_E001);
    }
}
