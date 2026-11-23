package dev.agentworkshq.rag.web.upload;

import dev.agentworkshq.rag.core.constants.ErrorMessages;
import dev.agentworkshq.rag.core.exception.ServiceException;

final class FileNames {

    private static final int MAX_LENGTH = 200;

    private FileNames() {
    }

    static String sanitize(String raw) {
        if (raw == null) {
            throw new ServiceException(ErrorMessages.RAG_E027);
        }
        String[] segments = raw.split("[/\\\\]", -1);
        String name = segments[segments.length - 1].strip();
        if (name.isEmpty() || name.length() > MAX_LENGTH || name.startsWith(".") || name.contains("..")
                || name.chars().anyMatch(Character::isISOControl)) {
            throw new ServiceException(ErrorMessages.RAG_E027);
        }
        return name;
    }

    static String requireValid(String name) {
        if (!sanitize(name).equals(name)) {
            throw new ServiceException(ErrorMessages.RAG_E027);
        }
        return name;
    }
}
