package com.agent.impl;

import com.agent.core.Agent;
import com.agent.message.Message;
import com.agent.task.Task;
import com.agent.task.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class DataProcessAgent extends Agent {

    private static final Logger log = LoggerFactory.getLogger(DataProcessAgent.class);

    private final Map<String, Object> processingStats = new LinkedHashMap<>();

    public DataProcessAgent(String name) {
        super(name, "data-process");
    }

    @Override
    protected void doInit() {
        subscribe("data.commands");
        subscribe("data.pipeline");
        setMetadata("description", "Data processing agent");
    }

    @Override
    protected void doStart() {
        processingStats.put("totalProcessed", 0L);
        processingStats.put("totalFailed", 0L);
        processingStats.put("lastProcessedAt", null);
    }

    @Override
    protected void doPause() {}

    @Override
    protected void doStop() {}

    @Override
    protected TaskResult doExecute(Task task) {
        long startTime = System.currentTimeMillis();
        Map<String, Object> config = (Map<String, Object>) task.getPayload();
        String operation = (String) config.getOrDefault("operation", "unknown");

        try {
            Object result;
            switch (operation.toLowerCase()) {
                case "filter":
                    result = filterData(config);
                    break;
                case "transform":
                    result = transformData(config);
                    break;
                case "aggregate":
                    result = aggregateData(config);
                    break;
                case "sort":
                    result = sortData(config);
                    break;
                case "deduplicate":
                    result = deduplicateData(config);
                    break;
                case "validate":
                    result = validateData(config);
                    break;
                default:
                    return TaskResult.failure(getId(), task.getId(), "Unknown operation: " + operation);
            }

            long duration = System.currentTimeMillis() - startTime;
            incrementStat("totalProcessed");
            processingStats.put("lastProcessedAt", System.currentTimeMillis());

            return TaskResult.success(getId(), task.getId(), result, duration);
        } catch (Exception e) {
            incrementStat("totalFailed");
            return TaskResult.failure(getId(), task.getId(), "Processing error: " + e.getMessage());
        }
    }

    @Override
    protected void handleReceivedMessage(Message message) {
        if (message.getType() == Message.Type.TASK) {
            log.info("DataProcessAgent received data processing request");
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> filterData(Map<String, Object> config) {
        List<Object> data = (List<Object>) config.get("data");
        String field = (String) config.get("field");
        String operator = (String) config.get("operator");
        Object value = config.get("value");

        if (data == null) return Collections.emptyList();

        return data.stream()
                .filter(item -> {
                    if (item instanceof Map && field != null) {
                        Object itemValue = ((Map<String, Object>) item).get(field);
                        return evaluateFilter(itemValue, operator, value);
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    private boolean evaluateFilter(Object actual, String operator, Object expected) {
        if (actual == null || operator == null) return true;
        String a = String.valueOf(actual);
        String e = String.valueOf(expected);
        switch (operator) {
            case "equals": return a.equals(e);
            case "not_equals": return !a.equals(e);
            case "contains": return a.contains(e);
            case "gt": return Double.parseDouble(a) > Double.parseDouble(e);
            case "lt": return Double.parseDouble(a) < Double.parseDouble(e);
            case "gte": return Double.parseDouble(a) >= Double.parseDouble(e);
            case "lte": return Double.parseDouble(a) <= Double.parseDouble(e);
            default: return true;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> transformData(Map<String, Object> config) {
        List<Object> data = (List<Object>) config.get("data");
        String targetField = (String) config.get("field");
        String transformType = (String) config.get("transformType");

        if (data == null) return Collections.emptyList();

        return data.stream().map(item -> {
            if (item instanceof Map && targetField != null) {
                Map<String, Object> map = new HashMap<>((Map<String, Object>) item);
                Object value = map.get(targetField);
                if (value != null) {
                    switch (transformType != null ? transformType : "uppercase") {
                        case "uppercase": map.put(targetField, String.valueOf(value).toUpperCase()); break;
                        case "lowercase": map.put(targetField, String.valueOf(value).toLowerCase()); break;
                        case "trim": map.put(targetField, String.valueOf(value).trim()); break;
                        case "to_int": map.put(targetField, Integer.parseInt(String.valueOf(value))); break;
                        case "to_double": map.put(targetField, Double.parseDouble(String.valueOf(value))); break;
                    }
                }
                return map;
            }
            return item;
        }).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> aggregateData(Map<String, Object> config) {
        List<Object> data = (List<Object>) config.get("data");
        String field = (String) config.get("field");
        String function = (String) config.get("function");

        if (data == null || field == null) return Collections.emptyMap();

        List<Double> values = data.stream()
                .filter(item -> item instanceof Map)
                .map(item -> ((Map<String, Object>) item).get(field))
                .filter(Objects::nonNull)
                .map(v -> Double.parseDouble(String.valueOf(v)))
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", values.size());

        if (!values.isEmpty()) {
            double sum = values.stream().mapToDouble(Double::doubleValue).sum();
            result.put("sum", sum);
            result.put("avg", sum / values.size());
            result.put("min", values.stream().mapToDouble(Double::doubleValue).min().orElse(0));
            result.put("max", values.stream().mapToDouble(Double::doubleValue).max().orElse(0));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Object> sortData(Map<String, Object> config) {
        List<Object> data = (List<Object>) config.get("data");
        String field = (String) config.get("field");
        boolean ascending = (boolean) config.getOrDefault("ascending", true);

        if (data == null) return Collections.emptyList();

        List<Object> sorted = new ArrayList<>(data);
        sorted.sort((a, b) -> {
            if (a instanceof Map && b instanceof Map && field != null) {
                Object va = ((Map<String, Object>) a).get(field);
                Object vb = ((Map<String, Object>) b).get(field);
                if (va instanceof Comparable && vb instanceof Comparable) {
                    int cmp = ((Comparable) va).compareTo(vb);
                    return ascending ? cmp : -cmp;
                }
            }
            return 0;
        });
        return sorted;
    }

    @SuppressWarnings("unchecked")
    private List<Object> deduplicateData(Map<String, Object> config) {
        List<Object> data = (List<Object>) config.get("data");
        String field = (String) config.get("field");

        if (data == null) return Collections.emptyList();

        Set<Object> seen = new HashSet<>();
        return data.stream().filter(item -> {
            Object key = item;
            if (item instanceof Map && field != null) {
                key = ((Map<String, Object>) item).get(field);
            }
            return seen.add(key);
        }).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> validateData(Map<String, Object> config) {
        List<Object> data = (List<Object>) config.get("data");
        List<Map<String, Object>> rules = (List<Map<String, Object>>) config.get("rules");

        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        int validCount = 0;

        if (data != null && rules != null) {
            for (int i = 0; i < data.size(); i++) {
                Object item = data.get(i);
                boolean valid = true;
                for (Map<String, Object> rule : rules) {
                    if (item instanceof Map) {
                        String field = (String) rule.get("field");
                        boolean required = (boolean) rule.getOrDefault("required", false);
                        Object value = ((Map<String, Object>) item).get(field);
                        if (required && value == null) {
                            errors.add(Map.of("index", i, "field", field, "error", "Required field missing"));
                            valid = false;
                        }
                    }
                }
                if (valid) validCount++;
            }
        }

        result.put("total", data != null ? data.size() : 0);
        result.put("valid", validCount);
        result.put("invalid", errors.size());
        result.put("errors", errors);
        return result;
    }

    private void incrementStat(String key) {
        Object val = processingStats.get(key);
        if (val instanceof Long) {
            processingStats.put(key, (Long) val + 1);
        }
    }

    public Map<String, Object> getStats() {
        return Collections.unmodifiableMap(processingStats);
    }
}
