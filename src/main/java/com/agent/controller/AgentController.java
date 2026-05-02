package com.agent.controller;

import com.agent.core.Agent;
import com.agent.core.AgentManager;
import com.agent.impl.DataProcessAgent;
import com.agent.impl.MonitorAgent;
import com.agent.impl.NotifyAgent;
import com.agent.impl.OrchestratorAgent;
import com.agent.task.Task;
import com.agent.task.TaskResult;
import com.agent.workflow.WorkflowEngine;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.agent.util.Maps;

import java.util.*;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentManager agentManager;
    private final WorkflowEngine workflowEngine;

    public AgentController(AgentManager agentManager, WorkflowEngine workflowEngine) {
        this.agentManager = agentManager;
        this.workflowEngine = workflowEngine;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registerAgent(@RequestBody Map<String, String> request) {
        String name = request.get("name");
        String type = request.get("type");

        if (name == null || type == null) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", "name and type are required"));
        }

        Agent agent;
        switch (type.toLowerCase()) {
            case "monitor":
                agent = new MonitorAgent(name);
                break;
            case "data-process":
                agent = new DataProcessAgent(name);
                break;
            case "notify":
                agent = new NotifyAgent(name);
                break;
            case "orchestrator":
                agent = new OrchestratorAgent(name, agentManager, workflowEngine);
                break;
            default:
                return ResponseEntity.badRequest().body(Collections.singletonMap("error", "Unknown agent type: " + type));
        }

        agentManager.register(agent);
        agent.start();

        return ResponseEntity.ok(Maps.of(
                Maps.entry("id", agent.getId()),
                Maps.entry("name", agent.getName()),
                Maps.entry("type", agent.getType()),
                Maps.entry("status", agent.getStatus().name())
        ));
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listAgents() {
        List<Map<String, Object>> agents = new ArrayList<>();
        for (Agent agent : agentManager.getAllAgents()) {
            agents.add(agentToMap(agent));
        }
        return ResponseEntity.ok(agents);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getAgent(@PathVariable String id) {
        return agentManager.getAgent(id)
                .map(agent -> ResponseEntity.ok(agentToMap(agent)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<Map<String, Object>> startAgent(@PathVariable String id) {
        Agent agent = agentManager.startAgent(id);
        if (agent == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Maps.of(Maps.entry("id", id), Maps.entry("status", agent.getStatus().name())));
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<Map<String, Object>> stopAgent(@PathVariable String id) {
        Agent agent = agentManager.stopAgent(id);
        if (agent == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Maps.of(Maps.entry("id", id), Maps.entry("status", agent.getStatus().name())));
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<Map<String, Object>> pauseAgent(@PathVariable String id) {
        Agent agent = agentManager.pauseAgent(id);
        if (agent == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Maps.of(Maps.entry("id", id), Maps.entry("status", agent.getStatus().name())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> unregisterAgent(@PathVariable String id) {
        Agent agent = agentManager.unregister(id);
        if (agent == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Collections.singletonMap("message", "Agent unregistered: " + id));
    }

    @PostMapping("/{id}/execute")
    public ResponseEntity<Map<String, Object>> executeTask(@PathVariable String id,
                                                            @RequestBody Map<String, Object> request) {
        String taskName = (String) request.getOrDefault("taskName", "api-task");
        String agentType = (String) request.getOrDefault("agentType", "generic");
        Object payload = request.get("payload");

        Task task = Task.of(taskName, agentType, payload);
        TaskResult result = agentManager.executeOnAgent(id, task);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("taskId", task.getId());
        response.put("success", result.isSuccess());
        response.put("status", result.getStatus().name());
        response.put("message", result.getMessage());
        if (result.getData() != null) response.put("data", result.getData());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(agentManager.getSystemStats());
    }

    private Map<String, Object> agentToMap(Agent agent) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", agent.getId());
        map.put("name", agent.getName());
        map.put("type", agent.getType());
        map.put("status", agent.getStatus().name());
        map.put("createdAt", agent.getCreatedAt().toString());
        map.put("lastActiveAt", agent.getLastActiveAt().toString());
        map.put("metadata", agent.getMetadata());
        return map;
    }
}
