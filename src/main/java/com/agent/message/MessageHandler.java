package com.agent.message;

@FunctionalInterface
public interface MessageHandler {
    void handle(Message message);
}
