package dev.agentworkshq.rag.web.tenant;

import dev.agentworkshq.rag.core.constants.ErrorMessages;
import dev.agentworkshq.rag.core.exception.UnauthorizedException;
import dev.agentworkshq.rag.core.text.Texts;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TenantContext {

    private static final Logger log = LoggerFactory.getLogger(TenantContext.class);

    private TenantContext() {
    }

    public static String require(TenantResolver resolver, HttpServletRequest request) {
        String tenantId = resolver.resolve(request);
        if (!Texts.isPlain(tenantId, Texts.MAX_ID_LENGTH)) {
            log.warn("Invalid tenant id from {}", resolver.getClass().getName());
            throw new UnauthorizedException(ErrorMessages.RAG_E024);
        }
        return tenantId;
    }
}
