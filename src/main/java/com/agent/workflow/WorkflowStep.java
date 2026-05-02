package com.agent.workflow;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class WorkflowStep {

    public enum Type { AGENT_TASK, PARALLEL_GROUP, CONDITION, DELAY, CUSTOM }
    public enum Status { PENDING, RUNNING, COMPLETED, FAILED, SKIPPED }

    private final String id;
    private final String name;
    private final Type type;
    private final String agentType;
    private final Object config;
    private Status status;
    private Object result;
    private String errorMessage;
    private final Map<String, Object> context;

    private WorkflowStep(String id, String name, Type type, String agentType, Object config) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.agentType = agentType;
        this.config = config;
        this.status = Status.PENDING;
        this.context = new HashMap<>();
    }

    public static WorkflowStep agentTask(String id, String name, String agentType, Object config) {
        return new WorkflowStep(id, name, Type.AGENT_TASK, agentType, config);
    }

    public static WorkflowStep parallelGroup(String id, String name) {
        return new WorkflowStep(id, name, Type.PARALLEL_GROUP, null, null);
    }

    public static WorkflowStep condition(String id, String name, Object conditionConfig) {
        return new WorkflowStep(id, name, Type.CONDITION, null, conditionConfig);
    }

    public static WorkflowStep delay(String id, String name, long delayMs) {
        return new WorkflowStep(id, name, Type.DELAY, null, delayMs);
    }

    public WorkflowStep withContext(String key, Object value) {
        this.context.put(key, value);
        return this;
    }

    public void markRunning() { this.status = Status.RUNNING; }
    public void markCompleted(Object result) { this.status = Status.COMPLETED; this.result = result; }
    public void markFailed(String error) { this.status = Status.FAILED; this.errorMessage = error; }
    public void markSkipped() { this.status = Status.SKIPPED; }

    public String getId() { return id; }
    public String getName() { return name; }
    public Type getType() { return type; }
    public String getAgentType() { return agentType; }
    public Object getConfig() { return config; }
    public Status getStatus() { return status; }
    public Object getResult() { return result; }
    public String getErrorMessage() { return errorMessage; }
    public Map<String, Object> getContext() { return Collections.unmodifiableMap(context); }

    @Override
    public String toString() {
        return String.format("WorkflowStep[id=%s, name=%s, type=%s, status=%s]", id, name, type, status);
    }
}
