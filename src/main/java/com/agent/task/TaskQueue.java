package com.agent.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class TaskQueue {

    private static final Logger log = LoggerFactory.getLogger(TaskQueue.class);

    private final PriorityBlockingQueue<Task> queue;
    private final Map<String, Task> taskIndex = new HashMap<>();
    private final AtomicInteger totalEnqueued = new AtomicInteger(0);
    private final AtomicInteger totalDequeued = new AtomicInteger(0);

    public TaskQueue() {
        this(1000);
    }

    public TaskQueue(int capacity) {
        this.queue = new PriorityBlockingQueue<>(capacity, this::compareTasks);
    }

    public void enqueue(Task task) {
        queue.offer(task);
        synchronized (taskIndex) {
            taskIndex.put(task.getId(), task);
        }
        totalEnqueued.incrementAndGet();
        log.debug("Enqueued task: {}", task);
    }

    public Task dequeue() throws InterruptedException {
        Task task = queue.take();
        synchronized (taskIndex) {
            taskIndex.remove(task.getId());
        }
        totalDequeued.incrementAndGet();
        return task;
    }

    public Task poll(long timeoutMs) throws InterruptedException {
        Task task = queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
        if (task != null) {
            synchronized (taskIndex) {
                taskIndex.remove(task.getId());
            }
            totalDequeued.incrementAndGet();
        }
        return task;
    }

    public Optional<Task> getTask(String taskId) {
        synchronized (taskIndex) {
            return Optional.ofNullable(taskIndex.get(taskId));
        }
    }

    public boolean cancel(String taskId) {
        synchronized (taskIndex) {
            Task task = taskIndex.remove(taskId);
            if (task != null) {
                queue.remove(task);
                task.markCancelled();
                return true;
            }
        }
        return false;
    }

    public int size() { return queue.size(); }
    public boolean isEmpty() { return queue.isEmpty(); }
    public int getTotalEnqueued() { return totalEnqueued.get(); }
    public int getTotalDequeued() { return totalDequeued.get(); }

    public List<Task> snapshot() {
        return new ArrayList<>(queue);
    }

    private int compareTasks(Task a, Task b) {
        int priorityCompare = b.getPriority().compareTo(a.getPriority());
        if (priorityCompare != 0) return priorityCompare;
        return a.getCreatedAt().compareTo(b.getCreatedAt());
    }
}
