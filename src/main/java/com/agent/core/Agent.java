package com.agent.core;

import com.agent.message.Message;
import com.agent.task.Task;
import com.agent.task.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class Agent {

    private static final Logger log = LoggerFactory.getLogger(Agent.class);

    public enum Status { CREATED, INITIALIZED, RUNNING, PAUSED, STOPPED, ERROR }

    private final String id;
    private final String name;
    private final String type;
    private volatile Status status;
    private final LocalDateTime createdAt;
    private LocalDateTime lastActiveAt;
    private final Map<String, Object> metadata;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    protected MessageBus messageBus;

    protected Agent(String name, String type) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.name = name;
        this.type = type;
        this.status = Status.CREATED;
        this.createdAt = LocalDateTime.now();
        this.lastActiveAt = createdAt;
        this.metadata = new HashMap<>();
    }

    public final void initialize(MessageBus messageBus) {
        if (initialized.compareAndSet(false, true)) {
            this.messageBus = messageBus;
            doInit();
            this.status = Status.INITIALIZED;
            log.info("Agent [{}] initialized", name);
        }
    }

    public final void start() {
        if (status == Status.INITIALIZED || status == Status.PAUSED || status == Status.STOPPED) {
            doStart();
            this.status = Status.RUNNING;
            this.lastActiveAt = LocalDateTime.now();
            log.info("Agent [{}] started", name);
        }
    }

    public final void pause() {
        if (status == Status.RUNNING) {
            doPause();
            this.status = Status.PAUSED;
            log.info("Agent [{}] paused", name);
        }
    }

    public final void stop() {
        if (status == Status.RUNNING || status == Status.PAUSED) {
            doStop();
            this.status = Status.STOPPED;
            log.info("Agent [{}] stopped", name);
        }
    }

    public final TaskResult execute(Task task) {
        if (status != Status.RUNNING) {
            return TaskResult.failure(id, task.getId(), "Agent is not running, current status: " + status);
        }
        try {
            lastActiveAt = LocalDateTime.now();
            TaskResult result = doExecute(task);
            log.debug("Agent [{}] executed task [{}], success: {}", name, task.getId(), result.isSuccess());
            return result;
        } catch (Exception e) {
            log.error("Agent [{}] failed to execute task [{}]", name, task.getId(), e);
            return TaskResult.failure(id, task.getId(), e.getMessage());
        }
    }

    public void onMessage(Message message) {
        lastActiveAt = LocalDateTime.now();
        handleReceivedMessage(message);
    }

    public void subscribe(String topic) {
        if (messageBus != null) {
            messageBus.subscribe(topic, this);
        }
    }

    public void publish(String topic, Message message) {
        if (messageBus != null) {
            messageBus.publish(topic, message);
        }
    }

    protected abstract void doInit();
    protected abstract void doStart();
    protected abstract void doPause();
    protected abstract void doStop();
    protected abstract TaskResult doExecute(Task task);
    protected abstract void handleReceivedMessage(Message message);

    public String getId() { return id; }
    public String getName() { return name; }
    public String getType() { return type; }
    public Status getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastActiveAt() { return lastActiveAt; }
    public Map<String, Object> getMetadata() { return Collections.unmodifiableMap(metadata); }

    protected void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    @Override
    public String toString() {
        return String.format("Agent[id=%s, name=%s, type=%s, status=%s]", id, name, type, status);
    }
}
