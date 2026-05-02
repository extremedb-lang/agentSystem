package com.agent.workflow;

import com.agent.core.AgentManager;
import com.agent.core.MessageBus;
import com.agent.message.Message;
import com.agent.task.Task;
import com.agent.task.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

    private final Map<String, Workflow> workflows = new ConcurrentHashMap<>();
    private final Map<String, WorkflowExecution> executions = new ConcurrentHashMap<>();
    private final AgentManager agentManager;
    private final MessageBus messageBus;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final AtomicInteger executionCounter = new AtomicInteger(0);

    public WorkflowEngine(AgentManager agentManager, MessageBus messageBus) {
        this.agentManager = agentManager;
        this.messageBus = messageBus;
    }

    public Workflow registerWorkflow(Workflow workflow) {
        workflows.put(workflow.getId(), workflow);
        log.info("Registered workflow: {}", workflow);
        return workflow;
    }

    public String execute(String workflowId) {
        return execute(workflowId, new HashMap<>());
    }

    public String execute(String workflowId, Map<String, Object> variables) {
        Workflow template = workflows.get(workflowId);
        if (template == null) {
            throw new IllegalArgumentException("Workflow not found: " + workflowId);
        }

        String executionId = "exec-" + executionCounter.incrementAndGet();
        WorkflowExecution execution = new WorkflowExecution(executionId, template, variables);
        executions.put(executionId, execution);

        executor.submit(() -> runWorkflow(execution));
        log.info("Started workflow execution: {} for workflow: {}", executionId, workflowId);
        return executionId;
    }

    public Optional<WorkflowExecution> getExecution(String executionId) {
        return Optional.ofNullable(executions.get(executionId));
    }

    public List<WorkflowExecution> getAllExecutions() {
        return new ArrayList<>(executions.values());
    }

    public Map<String, Object> getEngineStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("registeredWorkflows", workflows.size());
        stats.put("totalExecutions", executionCounter.get());
        stats.put("activeExecutions", executions.values().stream()
                .filter(e -> e.getWorkflow().getStatus() == Workflow.Status.RUNNING)
                .count());
        return stats;
    }

    private void runWorkflow(WorkflowExecution execution) {
        Workflow workflow = execution.getWorkflow();
        workflow.markRunning();

        messageBus.publish("workflow.events",
                Message.event("workflow-engine", "Workflow started: " + workflow.getName())
                        .setHeader("workflowId", workflow.getId())
                        .setHeader("executionId", execution.getId()));

        try {
            while (workflow.getCurrentStep() != null) {
                WorkflowStep step = workflow.getCurrentStep();
                step.markRunning();

                log.info("Executing step [{}] in workflow [{}]", step.getName(), workflow.getName());

                TaskResult result = executeStep(step, execution);

                if (result.isSuccess()) {
                    step.markCompleted(result.getData());
                } else {
                    step.markFailed(result.getMessage());
                    workflow.markFailed("Step [" + step.getName() + "] failed: " + result.getMessage());

                    messageBus.publish("workflow.events",
                            Message.alert("workflow-engine", "Workflow failed: " + workflow.getName())
                                    .setHeader("workflowId", workflow.getId())
                                    .setHeader("stepId", step.getId())
                                    .setHeader("error", result.getMessage()));
                    return;
                }

                workflow.advance();
            }

            workflow.markCompleted();
            execution.markCompleted();

            messageBus.publish("workflow.events",
                    Message.event("workflow-engine", "Workflow completed: " + workflow.getName())
                            .setHeader("workflowId", workflow.getId())
                            .setHeader("executionId", execution.getId()));

            log.info("Workflow [{}] completed successfully", workflow.getName());

        } catch (Exception e) {
            workflow.markFailed(e.getMessage());
            execution.markFailed(e.getMessage());
            log.error("Workflow [{}] execution failed", workflow.getName(), e);
        }
    }

    private TaskResult executeStep(WorkflowStep step, WorkflowExecution execution) {
        switch (step.getType()) {
            case AGENT_TASK:
                Task task = Task.of(step.getName(), step.getAgentType(), step.getConfig());
                execution.getVariables().forEach(task::withContext);
                return agentManager.executeOnAnyAgent(step.getAgentType(), task);

            case PARALLEL_GROUP:
                return executeParallelGroup(step, execution);

            case CONDITION:
                return evaluateCondition(step, execution);

            case DELAY:
                return executeDelay(step);

            default:
                return TaskResult.failure("engine", step.getId(), "Unknown step type: " + step.getType());
        }
    }

    private TaskResult executeParallelGroup(WorkflowStep step, WorkflowExecution execution) {
        List<CompletableFuture<TaskResult>> futures = new ArrayList<>();
        execution.getVariables().forEach((key, value) -> {
            if (value instanceof Task) {
                Task task = (Task) value;
                futures.add(CompletableFuture.supplyAsync(
                        () -> agentManager.executeOnAnyAgent(task.getAgentType(), task), executor));
            }
        });

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);
            boolean allSuccess = futures.stream()
                    .allMatch(f -> f.join().isSuccess());
            return allSuccess
                    ? TaskResult.success("engine", step.getId(), "All parallel tasks completed")
                    : TaskResult.failure("engine", step.getId(), "Some parallel tasks failed");
        } catch (Exception e) {
            return TaskResult.failure("engine", step.getId(), "Parallel execution error: " + e.getMessage());
        }
    }

    private TaskResult evaluateCondition(WorkflowStep step, WorkflowExecution execution) {
        Map<String, Object> config = (Map<String, Object>) step.getConfig();
        String variable = (String) config.get("variable");
        String operator = (String) config.get("operator");
        Object expected = config.get("value");
        Object actual = execution.getVariables().get(variable);

        boolean result = evaluate(actual, operator, expected);
        log.debug("Condition [{} {} {}] evaluated to: {} (actual: {})", variable, operator, expected, result, actual);
        return result
                ? TaskResult.success("engine", step.getId(), true)
                : TaskResult.failure("engine", step.getId(), "Condition not met");
    }

    private boolean evaluate(Object actual, String operator, Object expected) {
        if (actual == null) return "equals".equals(operator) && expected == null;
        String actualStr = String.valueOf(actual);
        String expectedStr = String.valueOf(expected);
        switch (operator) {
            case "equals": return actualStr.equals(expectedStr);
            case "not_equals": return !actualStr.equals(expectedStr);
            case "contains": return actualStr.contains(expectedStr);
            case "greater_than": return Double.parseDouble(actualStr) > Double.parseDouble(expectedStr);
            case "less_than": return Double.parseDouble(actualStr) < Double.parseDouble(expectedStr);
            default: return false;
        }
    }

    private TaskResult executeDelay(WorkflowStep step) {
        try {
            long delayMs = ((Number) step.getConfig()).longValue();
            Thread.sleep(delayMs);
            return TaskResult.success("engine", step.getId(), "Delayed " + delayMs + "ms");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return TaskResult.failure("engine", step.getId(), "Delay interrupted");
        }
    }

    public static class WorkflowExecution {
        private final String id;
        private final Workflow workflow;
        private final Map<String, Object> variables;
        private final LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private String errorMessage;

        public WorkflowExecution(String id, Workflow workflow, Map<String, Object> variables) {
            this.id = id;
            this.workflow = workflow;
            this.variables = new HashMap<>(variables);
            this.startedAt = java.time.LocalDateTime.now();
        }

        public void markCompleted() { this.completedAt = java.time.LocalDateTime.now(); }
        public void markFailed(String error) { this.errorMessage = error; this.completedAt = java.time.LocalDateTime.now(); }

        public String getId() { return id; }
        public Workflow getWorkflow() { return workflow; }
        public Map<String, Object> getVariables() { return variables; }
        public LocalDateTime getStartedAt() { return startedAt; }
        public LocalDateTime getCompletedAt() { return completedAt; }
        public String getErrorMessage() { return errorMessage; }
    }
}
