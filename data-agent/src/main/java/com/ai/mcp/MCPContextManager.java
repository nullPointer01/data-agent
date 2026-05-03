package com.ai.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MCPContextManager {

    private static final Logger log = LoggerFactory.getLogger(MCPContextManager.class);
    private static final long CONTEXT_TIMEOUT_MS = 30 * 60 * 1000;

    private final Map<String, MCPContext> contexts = new ConcurrentHashMap<>();

    public String createContext(String skillId, String modelType) {
        String contextId = UUID.randomUUID().toString();
        MCPContext context = new MCPContext(contextId, skillId, modelType);
        contexts.put(contextId, context);
        log.debug("Context created: {} for skill: {}", contextId, skillId);
        return contextId;
    }

    public MCPContext getContext(String contextId) {
        return contexts.get(contextId);
    }

    public void updateContext(String contextId, String key, Object value) {
        MCPContext context = contexts.get(contextId);
        if (context != null) context.getMetadata().put(key, value);
    }

    public void destroyContext(String contextId) {
        contexts.remove(contextId);
        log.debug("Context destroyed: {}", contextId);
    }

    public Map<String, MCPContext> getContexts() {
        return contexts;
    }

    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void cleanupExpiredContexts() {
        long now = System.currentTimeMillis();
        contexts.entrySet().removeIf(entry -> {
            long lastAccess = entry.getValue().getLastAccessTime().getTime();
            boolean expired = (now - lastAccess) > CONTEXT_TIMEOUT_MS;
            if (expired) log.info("Context expired: {}", entry.getKey());
            return expired;
        });
    }

    public int getActiveContextCount() {
        return contexts.size();
    }
}
