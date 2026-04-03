package io.agentscope.examples.monolithchat.agentscope.notebook;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PlanNotebook {
    private final Map<String, Plan> planMap = new ConcurrentHashMap<>();
    private final Map<String, List<ExecutionRecord>> executionMap = new ConcurrentHashMap<>();

    public void savePlan(String sessionId, Plan plan) {
        planMap.put(sessionId, plan);
    }

    public void recordExecution(String sessionId, ExecutionRecord record) {
        executionMap.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(record);
    }

    public Plan getPlan(String sessionId) {
        return planMap.get(sessionId);
    }

    public List<ExecutionRecord> getExecution(String sessionId) {
        return executionMap.getOrDefault(sessionId, List.of());
    }
}
