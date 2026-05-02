package com.agent.controller;

import com.agent.core.AgentManager;
import com.agent.core.MessageBus;
import com.agent.core.TaskScheduler;
import com.agent.workflow.WorkflowEngine;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final AgentManager agentManager;
    private final MessageBus messageBus;
    private final TaskScheduler taskScheduler;
    private final WorkflowEngine workflowEngine;

    public HealthController(AgentManager agentManager, MessageBus messageBus,
                            TaskScheduler taskScheduler, WorkflowEngine workflowEngine) {
        this.agentManager = agentManager;
        this.messageBus = messageBus;
        this.taskScheduler = taskScheduler;
        this.workflowEngine = workflowEngine;
    }

    @GetMapping
    public Map<String, Object> health() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());

        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();

        Map<String, Object> system = new LinkedHashMap<>();
        system.put("uptimeMs", runtime.getUptime());
        system.put("heapUsedMB", memory.getHeapMemoryUsage().getUsed() / (1024 * 1024));
        system.put("heapMaxMB", memory.getHeapMemoryUsage().getMax() / (1024 * 1024));
        health.put("system", system);

        health.put("agents", agentManager.getSystemStats());
        health.put("messages", Map.of(
                "totalProcessed", messageBus.getMessageCount(),
                "topics", messageBus.getTopics().size(),
                "deadLetters", messageBus.getDeadLetters().size()
        ));
        health.put("tasks", taskScheduler.getSchedulerStats());
        health.put("workflows", workflowEngine.getEngineStats());

        return health;
    }
}
