package com.agent.task;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TaskResult {

    public enum Status { SUCCESS, FAILURE, TIMEOUT, CANCELLED }

    private final String agentId;
    private final String taskId;
    private final Status status;
    private final Object data;
    private final String message;
    private final LocalDateTime completedAt;
    private final long durationMs;
    private final Map<String, Object> metadata;

    private TaskResult(String agentId, String taskId, Status status, Object data, String message, long durationMs) {
        this.agentId = agentId;
        this.taskId = taskId;
        this.status = status;
        this.data = data;
        this.message = message;
        this.completedAt = LocalDateTime.now();
        this.durationMs = durationMs;
        this.metadata = new HashMap<>();
    }

    public static TaskResult success(String agentId, String taskId, Object data) {
        return new TaskResult(agentId, taskId, Status.SUCCESS, data, "OK", 0);
    }

    public static TaskResult success(String agentId, String taskId, Object data, long durationMs) {
        return new TaskResult(agentId, taskId, Status.SUCCESS, data, "OK", durationMs);
    }

    public static TaskResult failure(String agentId, String taskId, String message) {
        return new TaskResult(agentId, taskId, Status.FAILURE, null, message, 0);
    }

    public static TaskResult timeout(String agentId, String taskId) {
        return new TaskResult(agentId, taskId, Status.TIMEOUT, null, "Task timed out", 0);
    }

    public static TaskResult cancelled(String agentId, String taskId) {
        return new TaskResult(agentId, taskId, Status.CANCELLED, null, "Task cancelled", 0);
    }

    public TaskResult withMetadata(String key, Object value) {
        metadata.put(key, value);
        return this;
    }

    public boolean isSuccess() { return status == Status.SUCCESS; }
    public String getAgentId() { return agentId; }
    public String getTaskId() { return taskId; }
    public Status getStatus() { return status; }
    public Object getData() { return data; }
    public String getMessage() { return message; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public long getDurationMs() { return durationMs; }
    public Map<String, Object> getMetadata() { return Collections.unmodifiableMap(metadata); }

    @Override
    public String toString() {
        return String.format("TaskResult[task=%s, agent=%s, status=%s, message=%s]",
                taskId, agentId, status, message);
    }
}
