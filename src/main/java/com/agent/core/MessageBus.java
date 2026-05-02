package com.agent.core;

import com.agent.message.Message;
import com.agent.message.MessageHandler;
import com.agent.message.Topic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class MessageBus {

    private static final Logger log = LoggerFactory.getLogger(MessageBus.class);

    private final Map<String, Set<Agent>> subscriptions = new ConcurrentHashMap<>();
    private final Map<String, Topic> topics = new ConcurrentHashMap<>();
    private final List<MessageHandler> globalHandlers = new CopyOnWriteArrayList<>();
    private final BlockingQueue<Message> deadLetterQueue = new LinkedBlockingQueue<>(1000);
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final AtomicLong messageCount = new AtomicLong(0);

    public Topic createTopic(String name) {
        Topic topic = new Topic(name);
        topics.put(name, topic);
        log.info("Created topic: {}", name);
        return topic;
    }

    public void subscribe(String topicName, Agent agent) {
        subscriptions.computeIfAbsent(topicName, k -> ConcurrentHashMap.newKeySet()).add(agent);
        topics.computeIfAbsent(topicName, k -> new Topic(topicName));
        log.debug("Agent [{}] subscribed to topic [{}]", agent.getName(), topicName);
    }

    public void unsubscribe(String topicName, Agent agent) {
        Set<Agent> subs = subscriptions.get(topicName);
        if (subs != null) {
            subs.remove(agent);
        }
    }

    public void addGlobalHandler(MessageHandler handler) {
        globalHandlers.add(handler);
    }

    public void publish(String topicName, Message message) {
        message.setHeader("topic", topicName);
        messageCount.incrementAndGet();

        for (MessageHandler handler : globalHandlers) {
            try {
                handler.handle(message);
            } catch (Exception e) {
                log.error("Global handler error", e);
            }
        }

        Set<Agent> subscribers = subscriptions.get(topicName);
        if (subscribers != null && !subscribers.isEmpty()) {
            for (Agent agent : subscribers) {
                executor.submit(() -> {
                    try {
                        agent.onMessage(message);
                    } catch (Exception e) {
                        log.error("Error delivering message to agent [{}]", agent.getName(), e);
                        offerToDeadLetter(message, agent.getId(), e);
                    }
                });
            }
        } else {
            log.debug("No subscribers for topic [{}], message dropped", topicName);
        }
    }

    public void publishAsync(String topicName, Message message) {
        executor.submit(() -> publish(topicName, message));
    }

    public int getSubscriberCount(String topicName) {
        Set<Agent> subs = subscriptions.get(topicName);
        return subs == null ? 0 : subs.size();
    }

    public Set<String> getTopics() {
        return Collections.unmodifiableSet(topics.keySet());
    }

    public long getMessageCount() {
        return messageCount.get();
    }

    public List<Message> getDeadLetters() {
        return new ArrayList<>(deadLetterQueue);
    }

    private void offerToDeadLetter(Message message, String agentId, Exception error) {
        message.setHeader("deadLetterReason", error.getMessage());
        message.setHeader("targetAgent", agentId);
        if (!deadLetterQueue.offer(message)) {
            log.warn("Dead letter queue full, discarding message");
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        log.info("MessageBus shut down. Total messages processed: {}", messageCount.get());
    }
}
