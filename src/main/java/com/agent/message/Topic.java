package com.agent.message;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Topic {

    private final String name;
    private final LocalDateTime createdAt;
    private final List<Message> recentMessages = new ArrayList<>();
    private int maxRecentMessages = 100;

    public Topic(String name) {
        this.name = name;
        this.createdAt = LocalDateTime.now();
    }

    public void addMessage(Message message) {
        synchronized (recentMessages) {
            recentMessages.add(message);
            if (recentMessages.size() > maxRecentMessages) {
                recentMessages.remove(0);
            }
        }
    }

    public List<Message> getRecentMessages() {
        synchronized (recentMessages) {
            return new ArrayList<>(recentMessages);
        }
    }

    public void setMaxRecentMessages(int max) {
        this.maxRecentMessages = max;
    }

    public String getName() { return name; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public int getMessageCount() { synchronized (recentMessages) { return recentMessages.size(); } }

    @Override
    public String toString() {
        return String.format("Topic[name=%s, messages=%d]", name, getMessageCount());
    }
}
