package com.agent.core;

import com.agent.task.Task;
import com.agent.task.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class AgentManager {

    private static final Logger log = LoggerFactory.getLogger(AgentManager.class);

    private final Map<String, Agent> agents = new ConcurrentHashMap<>();
    private final MessageBus messageBus;

    public AgentManager(MessageBus messageBus) {
        this.messageBus = messageBus;
    }

    public Agent register(Agent agent) {
        agents.put(agent.getId(), agent);
        agent.initialize(messageBus);
        log.info("Registered agent: {}", agent);
        return agent;
    }

    public Agent unregister(String agentId) {
        Agent agent = agents.remove(agentId);
        if (agent != null) {
            agent.stop();
            log.info("Unregistered agent: {}", agent);
        }
        return agent;
    }

    public Agent startAgent(String agentId) {
        Agent agent = agents.get(agentId);
        if (agent != null) {
            agent.start();
        }
        return agent;
    }

    public Agent stopAgent(String agentId) {
        Agent agent = agents.get(agentId);
        if (agent != null) {
            agent.stop();
        }
        return agent;
    }

    public Agent pauseAgent(String agentId) {
        Agent agent = agents.get(agentId);
        if (agent != null) {
            agent.pause();
        }
        return agent;
    }

    public Optional<Agent> getAgent(String agentId) {
        return Optional.ofNullable(agents.get(agentId));
    }

    public List<Agent> getAllAgents() {
        return new ArrayList<>(agents.values());
    }

    public List<Agent> getAgentsByType(String type) {
        return agents.values().stream()
                .filter(a -> a.getType().equals(type))
                .collect(Collectors.toList());
    }

    public List<Agent> getAgentsByStatus(Agent.Status status) {
        return agents.values().stream()
                .filter(a -> a.getStatus() == status)
                .collect(Collectors.toList());
    }

    public List<Agent> getRunningAgents() {
        return getAgentsByStatus(Agent.Status.RUNNING);
    }

    public TaskResult executeOnAgent(String agentId, Task task) {
        Agent agent = agents.get(agentId);
        if (agent == null) {
            return TaskResult.failure("unknown", task.getId(), "Agent not found: " + agentId);
        }
        return agent.execute(task);
    }

    public TaskResult executeOnAnyAgent(String type, Task task) {
        List<Agent> running = agents.values().stream()
                .filter(a -> a.getType().equals(type) && a.getStatus() == Agent.Status.RUNNING)
                .collect(Collectors.toList());
        if (running.isEmpty()) {
            return TaskResult.failure("unknown", task.getId(), "No running agent of type: " + type);
        }
        Agent selected = running.get(new Random().nextInt(running.size()));
        return selected.execute(task);
    }

    public Map<String, Object> getSystemStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalAgents", agents.size());
        stats.put("runningAgents", getRunningAgents().size());
        Map<String, Long> byType = agents.values().stream()
                .collect(Collectors.groupingBy(Agent::getType, Collectors.counting()));
        stats.put("agentsByType", byType);
        Map<String, Long> byStatus = agents.values().stream()
                .collect(Collectors.groupingBy(a -> a.getStatus().name(), Collectors.counting()));
        stats.put("agentsByStatus", byStatus);
        return stats;
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down all agents...");
        agents.values().forEach(Agent::stop);
        agents.clear();
    }
}
