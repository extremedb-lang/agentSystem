package com.agent.impl;

import com.agent.core.Agent;
import com.agent.message.Message;
import com.agent.task.Task;
import com.agent.task.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class NotifyAgent extends Agent {

    private static final Logger log = LoggerFactory.getLogger(NotifyAgent.class);

    public enum Channel { LOG, WEBHOOK, EMAIL, SMS, SLACK }

    private final List<NotificationRecord> notificationHistory = new CopyOnWriteArrayList<>();
    private final Map<String, String> channelConfig = new HashMap<>();
    private final List<Channel> enabledChannels = new ArrayList<>();

    public NotifyAgent(String name) {
        super(name, "notify");
    }

    @Override
    protected void doInit() {
        subscribe("notify.commands");
        subscribe("monitor.alerts");
        subscribe("workflow.events");
        enabledChannels.add(Channel.LOG);
        setMetadata("description", "Notification agent supporting multiple channels");
    }

    @Override
    protected void doStart() {
        log.info("NotifyAgent started with channels: {}", enabledChannels);
    }

    @Override
    protected void doPause() {}

    @Override
    protected void doStop() {}

    @Override
    protected TaskResult doExecute(Task task) {
        Map<String, Object> config = (Map<String, Object>) task.getPayload();
        String channel = (String) config.getOrDefault("channel", "LOG");
        String title = (String) config.getOrDefault("title", "Notification");
        String content = String.valueOf(config.getOrDefault("content", ""));
        String severity = (String) config.getOrDefault("severity", "INFO");

        try {
            Channel ch = Channel.valueOf(channel.toUpperCase());
            NotificationRecord record = sendNotification(ch, title, content, severity);
            return TaskResult.success(getId(), task.getId(), record);
        } catch (IllegalArgumentException e) {
            return TaskResult.failure(getId(), task.getId(), "Unknown channel: " + channel);
        } catch (Exception e) {
            return TaskResult.failure(getId(), task.getId(), "Send failed: " + e.getMessage());
        }
    }

    @Override
    protected void handleReceivedMessage(Message message) {
        String severity = message.getHeader("severity");
        if (severity == null) severity = "INFO";

        String title = "Message from " + message.getSourceAgentId();
        String content = String.valueOf(message.getPayload());

        for (Channel channel : enabledChannels) {
            sendNotification(channel, title, content, severity);
        }
    }

    private NotificationRecord sendNotification(Channel channel, String title, String content, String severity) {
        NotificationRecord record = new NotificationRecord(
                UUID.randomUUID().toString().substring(0, 8),
                channel.name(),
                title,
                content,
                severity,
                LocalDateTime.now()
        );

        switch (channel) {
            case LOG:
                logNotification(severity, title, content);
                break;
            case WEBHOOK:
                sendWebhook(title, content);
                break;
            case EMAIL:
                sendEmail(title, content);
                break;
            case SMS:
                sendSms(content);
                break;
            case SLACK:
                sendSlack(title, content, severity);
                break;
        }

        notificationHistory.add(record);
        if (notificationHistory.size() > 1000) {
            notificationHistory.remove(0);
        }

        return record;
    }

    private void logNotification(String severity, String title, String content) {
        String msg = String.format("[%s] %s: %s", severity, title, content);
        switch (severity.toUpperCase()) {
            case "CRITICAL":
            case "ERROR": log.error(msg); break;
            case "WARNING": log.warn(msg); break;
            default: log.info(msg); break;
        }
    }

    private void sendWebhook(String title, String content) {
        String url = channelConfig.getOrDefault("webhook.url", "http://localhost:9999/webhook");
        log.info("Webhook [{}]: {} - {}", url, title, content);
    }

    private void sendEmail(String title, String content) {
        String to = channelConfig.getOrDefault("email.to", "admin@example.com");
        log.info("Email [{}]: {} - {}", to, title, content);
    }

    private void sendSms(String content) {
        String phone = channelConfig.getOrDefault("sms.phone", "+1234567890");
        log.info("SMS [{}]: {}", phone, content);
    }

    private void sendSlack(String title, String content, String severity) {
        String webhook = channelConfig.getOrDefault("slack.webhook", "#alerts");
        log.info("Slack [{}]: [{}] {} - {}", webhook, severity, title, content);
    }

    public void configureChannel(String key, String value) {
        channelConfig.put(key, value);
    }

    public void enableChannel(Channel channel) {
        if (!enabledChannels.contains(channel)) {
            enabledChannels.add(channel);
        }
    }

    public void disableChannel(Channel channel) {
        enabledChannels.remove(channel);
    }

    public List<NotificationRecord> getHistory() {
        return Collections.unmodifiableList(notificationHistory);
    }

    public List<NotificationRecord> getHistoryByChannel(String channel) {
        return notificationHistory.stream()
                .filter(r -> r.channel.equals(channel))
                .collect(java.util.stream.Collectors.toList());
    }

    public static class NotificationRecord {
        public final String id;
        public final String channel;
        public final String title;
        public final String content;
        public final String severity;
        public final LocalDateTime sentAt;

        public NotificationRecord(String id, String channel, String title, String content,
                                  String severity, LocalDateTime sentAt) {
            this.id = id;
            this.channel = channel;
            this.title = title;
            this.content = content;
            this.severity = severity;
            this.sentAt = sentAt;
        }

        @Override
        public String toString() {
            return String.format("[%s][%s] %s: %s (%s)",
                    sentAt.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                    channel, severity, title, content);
        }
    }
}
