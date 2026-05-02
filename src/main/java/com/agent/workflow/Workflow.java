package com.agent.workflow;

import java.time.LocalDateTime;
import java.util.*;

public class Workflow {

    public enum Status { CREATED, RUNNING, COMPLETED, FAILED, CANCELLED, PAUSED }

    private final String id;
    private String name;
    private String description;
    private final List<WorkflowStep> steps;
    private final Map<String, Object> variables;
    private Status status;
    private final LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private int currentStepIndex;
    private String errorMessage;

    public Workflow(String id, String name) {
        this.id = id;
        this.name = name;
        this.steps = new ArrayList<>();
        this.variables = new HashMap<>();
        this.status = Status.CREATED;
        this.createdAt = LocalDateTime.now();
        this.currentStepIndex = 0;
    }

    public static Workflow of(String id, String name) {
        return new Workflow(id, name);
    }

    public Workflow addStep(WorkflowStep step) {
        steps.add(step);
        return this;
    }

    public Workflow withDescription(String description) {
        this.description = description;
        return this;
    }

    public Workflow withVariable(String key, Object value) {
        variables.put(key, value);
        return this;
    }

    public WorkflowStep getCurrentStep() {
        if (currentStepIndex < steps.size()) {
            return steps.get(currentStepIndex);
        }
        return null;
    }

    public WorkflowStep advance() {
        currentStepIndex++;
        return getCurrentStep();
    }

    public boolean hasNextStep() {
        return currentStepIndex < steps.size() - 1;
    }

    public boolean isComplete() {
        return currentStepIndex >= steps.size();
    }

    public void markRunning() {
        this.status = Status.RUNNING;
        this.startedAt = LocalDateTime.now();
    }

    public void markCompleted() {
        this.status = Status.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void markFailed(String error) {
        this.status = Status.FAILED;
        this.errorMessage = error;
        this.completedAt = LocalDateTime.now();
    }

    public void markCancelled() {
        this.status = Status.CANCELLED;
        this.completedAt = LocalDateTime.now();
    }

    public long getDurationMs() {
        if (startedAt == null) return 0;
        LocalDateTime end = completedAt != null ? completedAt : LocalDateTime.now();
        return java.time.Duration.between(startedAt, end).toMillis();
    }

    public double getProgress() {
        if (steps.isEmpty()) return 1.0;
        long completed = steps.stream()
                .filter(s -> s.getStatus() == WorkflowStep.Status.COMPLETED
                        || s.getStatus() == WorkflowStep.Status.SKIPPED)
                .count();
        return (double) completed / steps.size();
    }

    public Map<String, Object> getExecutionSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", id);
        summary.put("name", name);
        summary.put("status", status);
        summary.put("progress", String.format("%.1f%%", getProgress() * 100));
        summary.put("currentStep", currentStepIndex);
        summary.put("totalSteps", steps.size());
        summary.put("durationMs", getDurationMs());
        if (errorMessage != null) summary.put("error", errorMessage);
        return summary;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<WorkflowStep> getSteps() { return Collections.unmodifiableList(steps); }
    public Map<String, Object> getVariables() { return Collections.unmodifiableMap(variables); }
    public Status getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public int getCurrentStepIndex() { return currentStepIndex; }
    public String getErrorMessage() { return errorMessage; }

    @Override
    public String toString() {
        return String.format("Workflow[id=%s, name=%s, status=%s, progress=%.0f%%]",
                id, name, status, getProgress() * 100);
    }
}
