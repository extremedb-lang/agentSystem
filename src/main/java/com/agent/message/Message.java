package com.agent.message;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Message {

    public enum Type { TASK, EVENT, COMMAND, QUERY, RESPONSE, ALERT, HEARTBEAT, CUSTOM }

    private final String id;
    private final Type type;
    private final String sourceAgentId;
    private final Object payload;
    private final LocalDateTime timestamp;
    private final Map<String, String> headers;

    public Message(Type type, String sourceAgentId, Object payload) {
        this.id = UUID.randomUUID().toString().substring(0, 12);
        this.type = type;
        this.sourceAgentId = sourceAgentId;
        this.payload = payload;
        this.timestamp = LocalDateTime.now();
        this.headers = new HashMap<>();
    }

    public static Message task(String sourceAgentId, Object payload) {
        return new Message(Type.TASK, sourceAgentId, payload);
    }

    public static Message event(String sourceAgentId, Object payload) {
        return new Message(Type.EVENT, sourceAgentId, payload);
    }

    public static Message command(String sourceAgentId, Object payload) {
        return new Message(Type.COMMAND, sourceAgentId, payload);
    }

    public static Message query(String sourceAgentId, Object payload) {
        return new Message(Type.QUERY, sourceAgentId, payload);
    }

    public static Message alert(String sourceAgentId, Object payload) {
        return new Message(Type.ALERT, sourceAgentId, payload);
    }

    public static Message heartbeat(String sourceAgentId) {
        return new Message(Type.HEARTBEAT, sourceAgentId, "ping");
    }

    public Message setHeader(String key, String value) {
        headers.put(key, value);
        return this;
    }

    public String getId() { return id; }
    public Type getType() { return type; }
    public String getSourceAgentId() { return sourceAgentId; }
    public Object getPayload() { return payload; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public Map<String, String> getHeaders() { return Collections.unmodifiableMap(headers); }
    public String getHeader(String key) { return headers.get(key); }

    @Override
    public String toString() {
        return String.format("Message[id=%s, type=%s, from=%s, payload=%s]", id, type, sourceAgentId, payload);
    }
}
