package com.ai.agent;

import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;

public interface DataAnalysisAgent {
    AnalysisResponse analyze(AnalysisRequest request);
}
