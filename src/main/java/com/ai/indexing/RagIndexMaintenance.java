package com.ai.indexing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** 维护窗口必须重启所有实例生效；不在线切换状态，也不承诺在途请求瞬时排空。 */
@Component
public class RagIndexMaintenance {
    private final boolean enabled;
    public RagIndexMaintenance(@Value("${app.rag.index-maintenance:false}") boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled; }
    public void requireEnabled() {
        if (!enabled) throw new ResponseStatusException(HttpStatus.CONFLICT, "请先开启索引维护模式并排空业务请求");
    }
}
