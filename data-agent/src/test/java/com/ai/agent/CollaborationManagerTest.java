package com.ai.agent;

import com.ai.model.AgentProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.ai.agent.orchestrator.OrchestrationTask;
import com.ai.agent.specialist.SpecialistResult;
import com.ai.agent.specialist.SpecialistTask;

class CollaborationManagerTest {

    private final CollaborationManager collaborationManager = new CollaborationManager();

    @Test
    void manageSharedContextMergesTaskOutputContextInPlanOrder() {
        OrchestrationTask firstTask = task("t1", List.of());
        OrchestrationTask secondTask = task("t2", List.of("t1"));
        SpecialistResult firstResult = result("t1", Map.of("metric", 100));
        SpecialistResult secondResult = result("t2", Map.of("summary", "增长"));

        Map<String, Object> sharedContext = collaborationManager.manageSharedContext(
                List.of(firstTask, secondTask), List.of(secondResult, firstResult));

        assertEquals(100, sharedContext.get("metric"));
        assertEquals("增长", sharedContext.get("summary"));
        assertEquals(List.of("t1", "t2"), sharedContext.get(CollaborationManager.KEY_TASK_ORDER));
        Map<?, ?> collaborationSummary = (Map<?, ?>) sharedContext.get(CollaborationManager.KEY_COLLABORATION_SUMMARY);
        Map<?, ?> quality = (Map<?, ?>) sharedContext.get(CollaborationManager.KEY_QUALITY);
        Map<?, ?> dependencyGraph = (Map<?, ?>) sharedContext.get(CollaborationManager.KEY_DEPENDENCY_GRAPH);
        assertEquals(2, collaborationSummary.get("totalTasks"));
        assertEquals(2, collaborationSummary.get("successCount"));
        assertEquals("LOW", quality.get("riskLevel"));
        assertTrue(dependencyGraph.containsKey("t2"));
    }

    @Test
    void injectSharedContextOnlyCopiesDeclaredDependenciesAsStructuredContext() {
        AgentProfile profile = new AgentProfile();
        OrchestrationTask task = task("t2", List.of("t1"));

        SpecialistTask specialistTask = collaborationManager.injectSharedContext(
                task, Map.of("t1", "上游结果", "unused", "忽略"), profile);

        Map<?, ?> dependencies = (Map<?, ?>) specialistTask.sharedContext().get(CollaborationManager.KEY_DEPENDENCIES);
        assertEquals("上游结果", dependencies.get("t1"));
        assertFalse(specialistTask.sharedContext().containsKey("unused"));
    }

    @Test
    void injectSharedContextCopiesCollaborationSummaryAndDependencyGraph() {
        AgentProfile profile = new AgentProfile();
        OrchestrationTask firstTask = task("t1", List.of());
        SpecialistResult firstResult = result("t1", Map.of("metric", 100));
        Map<String, Object> sharedContext = collaborationManager.manageSharedContext(List.of(firstTask),
                List.of(firstResult));
        OrchestrationTask secondTask = task("t2", List.of("t1"));

        SpecialistTask specialistTask = collaborationManager.injectSharedContext(secondTask, sharedContext, profile);

        assertTrue(specialistTask.sharedContext().containsKey(CollaborationManager.KEY_COLLABORATION_SUMMARY));
        assertTrue(specialistTask.sharedContext().containsKey(CollaborationManager.KEY_DEPENDENCY_GRAPH));
    }

    @Test
    void manageSharedContextIndexesStructuredResultByTaskId() {
        OrchestrationTask task = task("t1", List.of());

        Map<String, Object> sharedContext = collaborationManager.manageSharedContext(
                List.of(task), List.of(new SpecialistResult("t1", "agent", true, "上游结果", "",
                        1L, Map.of("final_answer", "上游结果"), List.of())));

        Map<?, ?> taskContext = (Map<?, ?>) sharedContext.get("t1");
        assertEquals("上游结果", taskContext.get("result"));
        assertEquals("上游结果", sharedContext.get("final_answer"));
    }

    @Test
    void findBlockingDependenciesReturnsMissingAndFailedDependencies() {
        OrchestrationTask task = task("t3", List.of("t1", "t2"));
        SpecialistResult failedResult = new SpecialistResult("t1", "agent", false,
                "", "失败", 1L, Map.of(), List.of());

        List<String> blockingDependencies = collaborationManager.findBlockingDependencies(
                task, Map.of("t1", failedResult));

        assertEquals(List.of("t1", "t2"), blockingDependencies);
    }

    private OrchestrationTask task(String taskId, List<String> inputFrom) {
        return new OrchestrationTask(taskId, "处理 " + taskId, AgentType.REACT.name(),
                Map.of(), inputFrom, List.of(), List.of(), "输出结果");
    }

    private SpecialistResult result(String taskId, Map<String, Object> outputContext) {
        return new SpecialistResult(taskId, "agent", true, "ok", "", 1L, outputContext, List.of());
    }
}
