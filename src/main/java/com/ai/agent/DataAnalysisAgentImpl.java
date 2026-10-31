package com.ai.agent;

import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import org.springframework.stereotype.Component;

/** 内部同步/异步分析适配器；产品聊天使用持久后台提交接口。 */
@Component
public class DataAnalysisAgentImpl implements DataAnalysisAgent {
    private final AgentRequestPreparationService preparation;
    private final AgentRuntimeService runtime;

    public DataAnalysisAgentImpl(AgentRequestPreparationService preparation, AgentRuntimeService runtime) {
        this.preparation = preparation;
        this.runtime = runtime;
    }

    @Override public AnalysisResponse analyze(AnalysisRequest request) {
        return runtime.execute(preparation.prepare(request));
    }

}
