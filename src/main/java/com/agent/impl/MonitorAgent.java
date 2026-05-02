package com.agent.impl;

import com.agent.core.Agent;
import com.agent.message.Message;
import com.agent.task.Task;
import com.agent.task.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.*;

public class MonitorAgent extends Agent {

    private static final Logger log = LoggerFactory.getLogger(MonitorAgent.class);

    private final Map<String, Object> metrics = new LinkedHashMap<>();
    private final List<Map<String, Object>> alertHistory = new ArrayList<>();
    private final Map<String, Double> thresholds = new HashMap<>();
    private Timer monitorTimer;

    public MonitorAgent(String name) {
        super(name, "monitor");
        thresholds.put("cpuUsage", 80.0);
        thresholds.put("memoryUsage", 85.0);
    }

    @Override
    protected void doInit() {
        subscribe("monitor.commands");
        setMetadata("description", "System monitoring agent");
    }

    @Override
    protected void doStart() {
        monitorTimer = new Timer("MonitorAgent-Timer", true);
        monitorTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                collectMetrics();
            }
        }, 0, 10000);
        log.info("MonitorAgent started, collecting metrics every 10s");
    }

    @Override
    protected void doPause() {
        if (monitorTimer != null) {
            monitorTimer.cancel();
            monitorTimer = null;
        }
    }

    @Override
    protected void doStop() {
        if (monitorTimer != null) {
            monitorTimer.cancel();
            monitorTimer = null;
        }
    }

    @Override
    protected TaskResult doExecute(Task task) {
        String action = String.valueOf(task.getPayload());
        switch (action.toLowerCase()) {
            case "collect":
                collectMetrics();
                return TaskResult.success(getId(), task.getId(), new HashMap<>(metrics));
            case "get_metrics":
                return TaskResult.success(getId(), task.getId(), new HashMap<>(metrics));
            case "get_alerts":
                return TaskResult.success(getId(), task.getId(), new ArrayList<>(alertHistory));
            case "check_thresholds":
                return TaskResult.success(getId(), task.getId(), checkThresholds());
            default:
                return TaskResult.failure(getId(), task.getId(), "Unknown action: " + action);
        }
    }

    @Override
    protected void handleReceivedMessage(Message message) {
        if (message.getType() == Message.Type.COMMAND) {
            String command = String.valueOf(message.getPayload());
            if ("collect".equalsIgnoreCase(command)) {
                collectMetrics();
            } else if ("set_threshold".equalsIgnoreCase(command)) {
                String metric = message.getHeader("metric");
                String value = message.getHeader("value");
                if (metric != null && value != null) {
                    thresholds.put(metric, Double.parseDouble(value));
                }
            }
        }
    }

    private void collectMetrics() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();

        metrics.put("timestamp", System.currentTimeMillis());
        metrics.put("uptimeMs", runtime.getUptime());
        metrics.put("availableProcessors", os.getAvailableProcessors());
        metrics.put("systemLoadAverage", os.getSystemLoadAverage());
        metrics.put("heapUsed", memory.getHeapMemoryUsage().getUsed());
        metrics.put("heapMax", memory.getHeapMemoryUsage().getMax());
        metrics.put("nonHeapUsed", memory.getNonHeapMemoryUsage().getUsed());

        double heapUsedPercent = (double) memory.getHeapMemoryUsage().getUsed()
                / memory.getHeapMemoryUsage().getMax() * 100;
        metrics.put("heapUsedPercent", Math.round(heapUsedPercent * 100.0) / 100.0);

        checkThresholds();
    }

    private List<Map<String, Object>> checkThresholds() {
        List<Map<String, Object>> alerts = new ArrayList<>();
        Double heapThreshold = thresholds.get("memoryUsage");
        if (heapThreshold != null && metrics.containsKey("heapUsedPercent")) {
            double heapUsed = (double) metrics.get("heapUsedPercent");
            if (heapUsed > heapThreshold) {
                Map<String, Object> alert = new HashMap<>();
                alert.put("metric", "memoryUsage");
                alert.put("current", heapUsed);
                alert.put("threshold", heapThreshold);
                alert.put("severity", heapUsed > 95 ? "CRITICAL" : "WARNING");
                alert.put("timestamp", System.currentTimeMillis());
                alerts.add(alert);
                alertHistory.add(alert);

                if (messageBus != null) {
                    publish("monitor.alerts",
                            Message.alert(getId(), "High memory usage: " + heapUsed + "%")
                                    .setHeader("severity", (String) alert.get("severity")));
                }
            }
        }
        return alerts;
    }

    public Map<String, Object> getMetrics() {
        return Collections.unmodifiableMap(metrics);
    }

    public List<Map<String, Object>> getAlertHistory() {
        return Collections.unmodifiableList(alertHistory);
    }

    public void setThreshold(String metric, double value) {
        thresholds.put(metric, value);
    }
}
