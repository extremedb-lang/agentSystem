package com.agent.controller;

import com.agent.core.TaskScheduler;
import com.agent.task.Task;
import com.agent.task.TaskQueue;
import com.agent.task.TaskResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskScheduler taskScheduler;
    private final TaskQueue taskQueue;

    public TaskController(TaskScheduler taskScheduler, TaskQueue taskQueue) {
        this.taskScheduler = taskScheduler;
        this.taskQueue = taskQueue;
    }

    @PostMapping("/submit")
    public ResponseEntity<Map<String, Object>> submitTask(@RequestBody Map<String, Object> request) {
        String name = (String) request.getOrDefault("name", "api-task");
        String agentType = (String) request.getOrDefault("agentType", "monitor");
        Object payload = request.get("payload");
        String priority = (String) request.getOrDefault("priority", "NORMAL");

        Task task = Task.of(name, agentType, payload);
        task.withPriority(Task.Priority.valueOf(priority.toUpperCase()));

        TaskResult result = taskScheduler.submitImmediate(task);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("taskId", task.getId());
        response.put("success", result.isSuccess());
        response.put("status", result.getStatus().name());
        response.put("message", result.getMessage());
        if (result.getData() != null) response.put("data", result.getData());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/submit-async")
    public ResponseEntity<Map<String, Object>> submitAsyncTask(@RequestBody Map<String, Object> request) {
        String name = (String) request.getOrDefault("name", "api-task");
        String agentType = (String) request.getOrDefault("agentType", "monitor");
        Object payload = request.get("payload");

        Task task = Task.of(name, agentType, payload);
        CompletableFuture<TaskResult> future = taskScheduler.submitAsync(task);

        return ResponseEntity.ok(Map.of(
                "taskId", task.getId(),
                "status", "submitted",
                "message", "Task submitted asynchronously"
        ));
    }

    @PostMapping("/schedule")
    public ResponseEntity<Map<String, Object>> scheduleTask(@RequestBody Map<String, Object> request) {
        String name = (String) request.getOrDefault("name", "scheduled-task");
        String agentType = (String) request.getOrDefault("agentType", "monitor");
        Object payload = request.get("payload");
        long delayMs = ((Number) request.getOrDefault("delayMs", 5000)).longValue();

        Task task = Task.of(name, agentType, payload);
        String taskId = taskScheduler.submitScheduled(task, delayMs);

        return ResponseEntity.ok(Map.of(
                "taskId", taskId,
                "status", "scheduled",
                "delayMs", delayMs,
                "message", "Task scheduled"
        ));
    }

    @PostMapping("/schedule-recurring")
    public ResponseEntity<Map<String, Object>> scheduleRecurring(@RequestBody Map<String, Object> request) {
        String name = (String) request.getOrDefault("name", "recurring-task");
        String agentType = (String) request.getOrDefault("agentType", "monitor");
        Object payload = request.get("payload");
        long initialDelayMs = ((Number) request.getOrDefault("initialDelayMs", 1000)).longValue();
        long periodMs = ((Number) request.getOrDefault("periodMs", 60000)).longValue();

        Task task = Task.of(name, agentType, payload);
        String taskId = taskScheduler.submitRecurring(task, initialDelayMs, periodMs);

        return ResponseEntity.ok(Map.of(
                "taskId", taskId,
                "status", "recurring",
                "initialDelayMs", initialDelayMs,
                "periodMs", periodMs,
                "message", "Recurring task scheduled"
        ));
    }

    @DeleteMapping("/cancel/{taskId}")
    public ResponseEntity<Map<String, Object>> cancelTask(@PathVariable String taskId) {
        boolean cancelled = taskScheduler.cancelScheduled(taskId);
        if (!cancelled) {
            cancelled = taskQueue.cancel(taskId);
        }
        return ResponseEntity.ok(Map.of(
                "taskId", taskId,
                "cancelled", cancelled
        ));
    }

    @GetMapping("/result/{taskId}")
    public ResponseEntity<Map<String, Object>> getResult(@PathVariable String taskId) {
        return taskScheduler.getTaskResult(taskId)
                .map(result -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("taskId", result.getTaskId());
                    map.put("success", result.isSuccess());
                    map.put("status", result.getStatus().name());
                    map.put("message", result.getMessage());
                    map.put("durationMs", result.getDurationMs());
                    if (result.getData() != null) map.put("data", result.getData());
                    return ResponseEntity.ok(map);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/history")
    public ResponseEntity<List<Map<String, Object>>> getHistory() {
        List<Map<String, Object>> history = new ArrayList<>();
        for (TaskScheduler.TaskExecution exec : taskScheduler.getTaskHistory()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("taskId", exec.getTask().getId());
            entry.put("name", exec.getTask().getName());
            entry.put("agentType", exec.getTask().getAgentType());
            entry.put("success", exec.getResult().isSuccess());
            entry.put("status", exec.getResult().getStatus().name());
            entry.put("executedAt", exec.getExecutedAt());
            history.add(entry);
        }
        return ResponseEntity.ok(history);
    }

    @GetMapping("/queue")
    public ResponseEntity<Map<String, Object>> getQueueStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("size", taskQueue.size());
        status.put("totalEnqueued", taskQueue.getTotalEnqueued());
        status.put("totalDequeued", taskQueue.getTotalDequeued());
        return ResponseEntity.ok(status);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(taskScheduler.getSchedulerStats());
    }
}
