package com.agent.impl;

import com.agent.core.Agent;
import com.agent.core.AgentManager;
import com.agent.message.Message;
import com.agent.task.Task;
import com.agent.task.TaskResult;
import com.agent.workflow.Workflow;
import com.agent.workflow.WorkflowEngine;
import com.agent.workflow.WorkflowStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.agent.util.Maps;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OrchestratorAgent extends Agent {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorAgent.class);

    private final AgentManager agentManager;
    private final WorkflowEngine workflowEngine;
    private final Map<String, String> executionHistory = new ConcurrentHashMap<>();

    public OrchestratorAgent(String name, AgentManager agentManager, WorkflowEngine workflowEngine) {
        super(name, "orchestrator");
        this.agentManager = agentManager;
        this.workflowEngine = workflowEngine;
    }

    @Override
    protected void doInit() {
        subscribe("orchestrator.commands");
        subscribe("workflow.events");
        subscribe("monitor.alerts");
        setMetadata("description", "Orchestration agent for coordinating multi-agent workflows");
    }

    @Override
    protected void doStart() {
        registerDefaultWorkflows();
        log.info("OrchestratorAgent started with {} default workflows", 2);
    }

    @Override
    protected void doPause() {}

    @Override
    protected void doStop() {}

    @Override
    protected TaskResult doExecute(Task task) {
        Map<String, Object> config = (Map<String, Object>) task.getPayload();
        String action = (String) config.getOrDefault("action", "unknown");

        switch (action.toLowerCase()) {
            case "create_workflow":
                return createWorkflow(config, task);
            case "execute_workflow":
                return executeWorkflow(config, task);
            case "execute_adhoc":
                return executeAdhocWorkflow(config, task);
            case "list_workflows":
                return TaskResult.success(getId(), task.getId(), workflowEngine.getEngineStats());
            case "list_agents":
                return TaskResult.success(getId(), task.getId(), agentManager.getSystemStats());
            default:
                return TaskResult.failure(getId(), task.getId(), "Unknown action: " + action);
        }
    }

    @Override
    protected void handleReceivedMessage(Message message) {
        if (message.getType() == Message.Type.ALERT) {
            log.info("Orchestrator received alert: {}", message.getPayload());
            handleAlert(message);
        }
    }

    private TaskResult createWorkflow(Map<String, Object> config, Task task) {
        String id = (String) config.get("id");
        String name = (String) config.get("name");
        List<Map<String, Object>> stepConfigs = (List<Map<String, Object>>) config.get("steps");

        if (id == null || name == null || stepConfigs == null) {
            return TaskResult.failure(getId(), task.getId(), "Missing required fields: id, name, steps");
        }

        Workflow workflow = Workflow.of(id, name);
        for (int i = 0; i < stepConfigs.size(); i++) {
            Map<String, Object> sc = stepConfigs.get(i);
            String stepType = (String) sc.getOrDefault("type", "agent_task");
            String stepId = (String) sc.getOrDefault("id", "step-" + (i + 1));
            String stepName = (String) sc.getOrDefault("name", "Step " + (i + 1));
            String agentType = (String) sc.get("agentType");
            Object stepConfig = sc.get("config");

            WorkflowStep step;
            switch (stepType) {
                case "agent_task":
                    step = WorkflowStep.agentTask(stepId, stepName, agentType, stepConfig);
                    break;
                case "delay":
                    step = WorkflowStep.delay(stepId, stepName, ((Number) sc.getOrDefault("delayMs", 1000)).longValue());
                    break;
                case "condition":
                    step = WorkflowStep.condition(stepId, stepName, stepConfig);
                    break;
                default:
                    step = WorkflowStep.agentTask(stepId, stepName, agentType, stepConfig);
            }
            workflow.addStep(step);
        }

        workflowEngine.registerWorkflow(workflow);
        return TaskResult.success(getId(), task.getId(), workflow.getExecutionSummary());
    }

    @SuppressWarnings("unchecked")
    private TaskResult executeWorkflow(Map<String, Object> config, Task task) {
        String workflowId = (String) config.get("workflowId");
        Map<String, Object> variables = (Map<String, Object>) config.getOrDefault("variables", new HashMap<>());

        if (workflowId == null) {
            return TaskResult.failure(getId(), task.getId(), "Missing workflowId");
        }

        try {
            String executionId = workflowEngine.execute(workflowId, variables);
            executionHistory.put(workflowId, executionId);
            return TaskResult.success(getId(), task.getId(), Maps.of(
                    Maps.entry("workflowId", workflowId),
                    Maps.entry("executionId", executionId),
                    Maps.entry("status", "started")
            ));
        } catch (Exception e) {
            return TaskResult.failure(getId(), task.getId(), e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private TaskResult executeAdhocWorkflow(Map<String, Object> config, Task task) {
        String name = (String) config.getOrDefault("name", "adhoc-" + System.currentTimeMillis());
        List<Map<String, Object>> steps = (List<Map<String, Object>>) config.get("steps");

        if (steps == null || steps.isEmpty()) {
            return TaskResult.failure(getId(), task.getId(), "No steps defined");
        }

        String workflowId = "adhoc-" + UUID.randomUUID().toString().substring(0, 6);
        Map<String, Object> createConfig = new HashMap<>();
        createConfig.put("id", workflowId);
        createConfig.put("name", name);
        createConfig.put("steps", steps);

        TaskResult createResult = createWorkflow(createConfig, task);
        if (!createResult.isSuccess()) return createResult;

        return executeWorkflow(Collections.singletonMap("workflowId", workflowId), task);
    }

    private void handleAlert(Message message) {
        String severity = message.getHeader("severity");
        if ("CRITICAL".equals(severity)) {
            log.warn("Critical alert received, triggering incident response workflow");
        }
    }

    private void registerDefaultWorkflows() {
        Workflow healthCheck = Workflow.of("health-check", "System Health Check")
                .withDescription("Collects system metrics and sends alerts if thresholds are exceeded")
                .addStep(WorkflowStep.agentTask("collect-metrics", "Collect System Metrics", "monitor", "collect"))
                .addStep(WorkflowStep.agentTask("check-alerts", "Check Alert Thresholds", "monitor", "check_thresholds"));

        workflowEngine.registerWorkflow(healthCheck);

        Workflow dataPipeline = Workflow.of("data-pipeline", "Data Processing Pipeline")
                .withDescription("Validates, transforms, and aggregates data")
                .addStep(WorkflowStep.agentTask("validate", "Validate Input Data", "data-process",
                        Collections.singletonMap("operation", "validate")))
                .addStep(WorkflowStep.agentTask("transform", "Transform Data", "data-process",
                        Collections.singletonMap("operation", "transform")))
                .addStep(WorkflowStep.agentTask("aggregate", "Aggregate Results", "data-process",
                        Collections.singletonMap("operation", "aggregate")));

        workflowEngine.registerWorkflow(dataPipeline);
    }

    public Map<String, String> getExecutionHistory() {
        return Collections.unmodifiableMap(executionHistory);
    }
}
