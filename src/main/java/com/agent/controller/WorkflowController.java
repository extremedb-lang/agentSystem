package com.agent.controller;

import com.agent.workflow.Workflow;
import com.agent.workflow.WorkflowEngine;
import com.agent.workflow.WorkflowStep;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.agent.util.Maps;

import java.util.*;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowEngine workflowEngine;

    public WorkflowController(WorkflowEngine workflowEngine) {
        this.workflowEngine = workflowEngine;
    }

    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createWorkflow(@RequestBody Map<String, Object> request) {
        String id = (String) request.get("id");
        String name = (String) request.get("name");
        String description = (String) request.get("description");
        List<Map<String, Object>> stepConfigs = (List<Map<String, Object>>) request.get("steps");

        if (id == null || name == null || stepConfigs == null) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", "id, name, and steps are required"));
        }

        Workflow workflow = Workflow.of(id, name);
        if (description != null) workflow.withDescription(description);

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
                    long delayMs = ((Number) sc.getOrDefault("delayMs", 1000)).longValue();
                    step = WorkflowStep.delay(stepId, stepName, delayMs);
                    break;
                case "condition":
                    step = WorkflowStep.condition(stepId, stepName, stepConfig);
                    break;
                default:
                    return ResponseEntity.badRequest().body(Collections.singletonMap("error", "Unknown step type: " + stepType));
            }
            workflow.addStep(step);
        }

        workflowEngine.registerWorkflow(workflow);
        return ResponseEntity.ok(workflow.getExecutionSummary());
    }

    @PostMapping("/{workflowId}/execute")
    public ResponseEntity<Map<String, Object>> executeWorkflow(
            @PathVariable String workflowId,
            @RequestBody(required = false) Map<String, Object> request) {
        try {
            Map<String, Object> variables = new HashMap<>();
            if (request != null && request.containsKey("variables")) {
                variables = (Map<String, Object>) request.get("variables");
            }
            String executionId = workflowEngine.execute(workflowId, variables);
            return ResponseEntity.ok(Maps.of(
                    Maps.entry("workflowId", workflowId),
                    Maps.entry("executionId", executionId),
                    Maps.entry("status", "started")
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/executions")
    public ResponseEntity<List<Map<String, Object>>> listExecutions() {
        List<Map<String, Object>> executions = new ArrayList<>();
        for (WorkflowEngine.WorkflowExecution exec : workflowEngine.getAllExecutions()) {
            executions.add(executionToMap(exec));
        }
        return ResponseEntity.ok(executions);
    }

    @GetMapping("/executions/{executionId}")
    public ResponseEntity<Map<String, Object>> getExecution(@PathVariable String executionId) {
        return workflowEngine.getExecution(executionId)
                .map(exec -> ResponseEntity.ok(executionToMap(exec)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(workflowEngine.getEngineStats());
    }

    private Map<String, Object> executionToMap(WorkflowEngine.WorkflowExecution exec) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("executionId", exec.getId());
        map.put("workflowId", exec.getWorkflow().getId());
        map.put("workflowName", exec.getWorkflow().getName());
        map.put("status", exec.getWorkflow().getStatus().name());
        map.put("progress", String.format("%.1f%%", exec.getWorkflow().getProgress() * 100));
        map.put("currentStep", exec.getWorkflow().getCurrentStepIndex());
        map.put("totalSteps", exec.getWorkflow().getSteps().size());
        map.put("durationMs", exec.getWorkflow().getDurationMs());
        if (exec.getErrorMessage() != null) map.put("error", exec.getErrorMessage());
        return map;
    }
}
