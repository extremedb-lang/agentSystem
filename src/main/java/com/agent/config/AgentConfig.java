package com.agent.config;

import com.agent.core.AgentManager;
import com.agent.core.MessageBus;
import com.agent.core.TaskScheduler;
import com.agent.impl.*;
import com.agent.workflow.WorkflowEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

@Configuration
public class AgentConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentConfig.class);

    private final AgentManager agentManager;
    private final MessageBus messageBus;
    private final TaskScheduler taskScheduler;
    private final WorkflowEngine workflowEngine;

    public AgentConfig(AgentManager agentManager, MessageBus messageBus,
                       TaskScheduler taskScheduler, WorkflowEngine workflowEngine) {
        this.agentManager = agentManager;
        this.messageBus = messageBus;
        this.taskScheduler = taskScheduler;
        this.workflowEngine = workflowEngine;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("========================================");
        log.info(" Multi-Agent Operations System Started ");
        log.info("========================================");

        MonitorAgent monitor = new MonitorAgent("System Monitor");
        DataProcessAgent dataProcess = new DataProcessAgent("Data Processor");
        NotifyAgent notify = new NotifyAgent("Notification Service");
        OrchestratorAgent orchestrator = new OrchestratorAgent("Main Orchestrator", agentManager, workflowEngine);

        agentManager.register(monitor);
        agentManager.register(dataProcess);
        agentManager.register(notify);
        agentManager.register(orchestrator);

        monitor.start();
        dataProcess.start();
        notify.start();
        orchestrator.start();

        messageBus.createTopic("monitor.alerts");
        messageBus.createTopic("workflow.events");
        messageBus.createTopic("data.commands");
        messageBus.createTopic("data.pipeline");
        messageBus.createTopic("notify.commands");
        messageBus.createTopic("orchestrator.commands");

        log.info("Default agents initialized: Monitor, DataProcess, Notify, Orchestrator");
        log.info("API available at http://localhost:8080/api/");
        log.info("  - GET  /api/agents          - List all agents");
        log.info("  - POST /api/agents/register  - Register new agent");
        log.info("  - POST /api/tasks/submit     - Submit task");
        log.info("  - POST /api/workflows/create - Create workflow");
        log.info("========================================");
    }
}
