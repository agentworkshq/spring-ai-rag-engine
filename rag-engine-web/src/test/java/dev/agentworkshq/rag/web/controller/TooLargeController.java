package dev.agentworkshq.rag.web.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestController
public class TooLargeController implements RagControllers {

    @PostMapping("/too-large")
    void tooLarge() {
        throw new MaxUploadSizeExceededException(10);
    }
}
