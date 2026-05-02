package com.agent.core;

import com.agent.task.Task;
import com.agent.task.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class TaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(TaskScheduler.class);

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final ExecutorService workerPool = Executors.newFixedThreadPool(8);
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final Map<String, TaskResult> taskResults = new ConcurrentHashMap<>();
    private final Map<String, TaskExecution> taskHistory = new ConcurrentHashMap<>();
    private final AtomicLong taskCounter = new AtomicLong(0);
    private final AgentManager agentManager;

    public TaskScheduler(AgentManager agentManager) {
        this.agentManager = agentManager;
    }

    public TaskResult submitImmediate(Task task) {
        String taskId = task.getId();
        log.info("Submitting immediate task: {}", taskId);

        TaskResult result = agentManager.executeOnAnyAgent(task.getAgentType(), task);
        taskResults.put(taskId, result);
        taskHistory.put(taskId, new TaskExecution(task, result));
        taskCounter.incrementAndGet();
        return result;
    }

    public CompletableFuture<TaskResult> submitAsync(Task task) {
        return CompletableFuture.supplyAsync(() -> submitImmediate(task), workerPool);
    }

    public String submitScheduled(Task task, long delayMs) {
        String taskId = task.getId();
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            TaskResult result = submitImmediate(task);
            log.info("Scheduled task [{}] completed: {}", taskId, result.isSuccess());
        }, delayMs, TimeUnit.MILLISECONDS);
        scheduledTasks.put(taskId, future);
        return taskId;
    }

    public String submitRecurring(Task task, long initialDelayMs, long periodMs) {
        String taskId = task.getId();
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                TaskResult result = submitImmediate(task);
                log.debug("Recurring task [{}] completed: {}", taskId, result.isSuccess());
            } catch (Exception e) {
                log.error("Recurring task [{}] failed", taskId, e);
            }
        }, initialDelayMs, periodMs, TimeUnit.MILLISECONDS);
        scheduledTasks.put(taskId, future);
        return taskId;
    }

    public boolean cancelScheduled(String taskId) {
        ScheduledFuture<?> future = scheduledTasks.remove(taskId);
        if (future != null) {
            boolean cancelled = future.cancel(false);
            log.info("Cancelled scheduled task [{}]: {}", taskId, cancelled);
            return cancelled;
        }
        return false;
    }

    public Optional<TaskResult> getTaskResult(String taskId) {
        return Optional.ofNullable(taskResults.get(taskId));
    }

    public List<TaskExecution> getTaskHistory() {
        return new ArrayList<>(taskHistory.values());
    }

    public Map<String, Object> getSchedulerStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalTasksExecuted", taskCounter.get());
        stats.put("scheduledTasks", scheduledTasks.size());
        stats.put("pendingResults", taskResults.size());
        stats.put("historySize", taskHistory.size());
        stats.put("workerPoolActive", ((ThreadPoolExecutor) workerPool).getActiveCount());
        stats.put("schedulerPoolActive", ((ThreadPoolExecutor) scheduler).getActiveCount());
        return stats;
    }

    public static class TaskExecution {
        private final Task task;
        private final TaskResult result;
        private final long executedAt;

        public TaskExecution(Task task, TaskResult result) {
            this.task = task;
            this.result = result;
            this.executedAt = System.currentTimeMillis();
        }

        public Task getTask() { return task; }
        public TaskResult getResult() { return result; }
        public long getExecutedAt() { return executedAt; }
    }

    @PreDestroy
    public void shutdown() {
        scheduledTasks.values().forEach(f -> f.cancel(false));
        scheduler.shutdown();
        workerPool.shutdown();
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
            workerPool.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            workerPool.shutdownNow();
        }
        log.info("TaskScheduler shut down. Total tasks executed: {}", taskCounter.get());
    }
}
