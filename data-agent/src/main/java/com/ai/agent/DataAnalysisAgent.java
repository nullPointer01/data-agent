package com.ai.agent;

import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;

/**
 * 数据分析 Agent 统一入口。
 *
 * @author data-agent
 */
public interface DataAnalysisAgent {

    /**
     * 分析用户请求并返回 Agent 响应。
     *
     * @param request 分析请求
     * @return 分析响应
     */
    AnalysisResponse analyze(AnalysisRequest request);
}
