package com.agent.task;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Task {

    public enum Priority { LOW, NORMAL, HIGH, CRITICAL }
    public enum Status { PENDING, RUNNING, COMPLETED, FAILED, CANCELLED }

    private final String id;
    private String name;
    private final String agentType;
    private final Object payload;
    private Priority priority;
    private volatile Status status;
    private final LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private final Map<String, Object> context;
    private int maxRetries;
    private int retryCount;
    private long timeoutMs;

    public Task(String name, String agentType, Object payload) {
        this.id = UUID.randomUUID().toString().substring(0, 10);
        this.name = name;
        this.agentType = agentType;
        this.payload = payload;
        this.priority = Priority.NORMAL;
        this.status = Status.PENDING;
        this.createdAt = LocalDateTime.now();
        this.context = new HashMap<>();
        this.maxRetries = 3;
        this.retryCount = 0;
        this.timeoutMs = 30000;
    }

    public static Task of(String name, String agentType, Object payload) {
        return new Task(name, agentType, payload);
    }

    public Task withPriority(Priority priority) {
        this.priority = priority;
        return this;
    }

    public Task withMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
        return this;
    }

    public Task withTimeout(long timeoutMs) {
        this.timeoutMs = timeoutMs;
        return this;
    }

    public Task withContext(String key, Object value) {
        this.context.put(key, value);
        return this;
    }

    public void markRunning() {
        this.status = Status.RUNNING;
        this.startedAt = LocalDateTime.now();
    }

    public void markCompleted() {
        this.status = Status.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = Status.FAILED;
        this.completedAt = LocalDateTime.now();
    }

    public void markCancelled() {
        this.status = Status.CANCELLED;
        this.completedAt = LocalDateTime.now();
    }

    public boolean canRetry() {
        return retryCount < maxRetries;
    }

    public void incrementRetry() {
        retryCount++;
    }

    public long getDurationMs() {
        if (startedAt == null) return 0;
        LocalDateTime end = completedAt != null ? completedAt : LocalDateTime.now();
        return java.time.Duration.between(startedAt, end).toMillis();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getAgentType() { return agentType; }
    public Object getPayload() { return payload; }
    public Priority getPriority() { return priority; }
    public Status getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public Map<String, Object> getContext() { return Collections.unmodifiableMap(context); }
    public int getMaxRetries() { return maxRetries; }
    public int getRetryCount() { return retryCount; }
    public long getTimeoutMs() { return timeoutMs; }

    @Override
    public String toString() {
        return String.format("Task[id=%s, name=%s, type=%s, priority=%s, status=%s]",
                id, name, agentType, priority, status);
    }
}
