package com.ai.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理短生命周期的模型调用上下文。
 *
 * @author data-agent
 */
@Service
public class McpContextManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(McpContextManager.class);
    private static final long CONTEXT_TIMEOUT_MS = 30L * 60L * 1000L;
    private static final long CLEANUP_INTERVAL_MS = 5L * 60L * 1000L;
    private static final String CONTEXT_EXPIRED_LOG = "Context expired: {}";

    private final Map<String, McpContext> contexts = new ConcurrentHashMap<>();

    public String createContext(String skillId, String modelType) {
        String contextId = UUID.randomUUID().toString();
        McpContext context = new McpContext(contextId, skillId, modelType);
        contexts.put(contextId, context);
        LOGGER.debug("Context created: {} for skill: {}", contextId, skillId);
        return contextId;
    }

    public McpContext getContext(String contextId) {
        return contexts.get(contextId);
    }

    public void updateContext(String contextId, String key, Object value) {
        McpContext context = contexts.get(contextId);
        if (context != null) {
            context.getMetadata().put(key, value);
        }
    }

    public void addConversationTurn(String contextId, String role, String content) {
        McpContext context = contexts.get(contextId);
        if (context != null) {
            context.addHistory(role, content);
            context.setLastAccessTime(new Date());
        }
    }

    public void destroyContext(String contextId) {
        contexts.remove(contextId);
        LOGGER.debug("Context destroyed: {}", contextId);
    }

    public Map<String, McpContext> getContexts() {
        return Map.copyOf(contexts);
    }

    @Scheduled(fixedRate = CLEANUP_INTERVAL_MS)
    public void cleanupExpiredContexts() {
        long now = System.currentTimeMillis();
        contexts.entrySet().removeIf(entry -> {
            long lastAccess = entry.getValue().getLastAccessTime().getTime();
            boolean expired = (now - lastAccess) > CONTEXT_TIMEOUT_MS;
            if (expired) {
                LOGGER.info(CONTEXT_EXPIRED_LOG, entry.getKey());
            }
            return expired;
        });
    }

}
