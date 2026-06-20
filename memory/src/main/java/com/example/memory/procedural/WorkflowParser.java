package com.example.memory.procedural;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parses YAML workflow definitions into WorkflowDefinition objects.
 */
@Component
public class WorkflowParser {

    private final Yaml yaml = new Yaml();

    @SuppressWarnings("unchecked")
    public WorkflowDefinition parse(String yamlContent) {
        Map<String, Object> root = yaml.load(yamlContent);

        String name = (String) root.getOrDefault("name", "unnamed");
        String description = (String) root.getOrDefault("description", "");

        List<Map<String, Object>> stepMaps =
            (List<Map<String, Object>>) root.getOrDefault("steps", List.of());

        List<WorkflowDefinition.WorkflowStep> steps = new ArrayList<>();
        for (Map<String, Object> stepMap : stepMaps) {
            Map<String, String> params = stepMap.containsKey("parameters")
                ? toStringMap((Map<String, Object>) stepMap.get("parameters"))
                : Map.of();

            steps.add(new WorkflowDefinition.WorkflowStep(
                (String) stepMap.getOrDefault("name", ""),
                (String) stepMap.getOrDefault("action", ""),
                (String) stepMap.getOrDefault("description", ""),
                params,
                (String) stepMap.getOrDefault("requires_confirmation", null),
                (String) stepMap.getOrDefault("on_success", null),
                (String) stepMap.getOrDefault("on_failure", null)
            ));
        }

        return new WorkflowDefinition(name, description, steps);
    }

    public String serialize(WorkflowDefinition definition) {
        return yaml.dump(Map.of(
            "name", definition.name(),
            "description", definition.description(),
            "steps", definition.steps().stream()
                .map(step -> {
                    Map<String, Object> stepMap = new java.util.LinkedHashMap<>();
                    stepMap.put("name", step.name());
                    stepMap.put("action", step.action());
                    if (!step.description().isEmpty()) stepMap.put("description", step.description());
                    if (!step.parameters().isEmpty()) stepMap.put("parameters", step.parameters());
                    if (step.requiresConfirmation() != null) stepMap.put("requires_confirmation", step.requiresConfirmation());
                    if (step.onSuccess() != null) stepMap.put("on_success", step.onSuccess());
                    if (step.onFailure() != null) stepMap.put("on_failure", step.onFailure());
                    return stepMap;
                })
                .toList()
        ));
    }

    private Map<String, String> toStringMap(Map<String, Object> map) {
        var result = new java.util.LinkedHashMap<String, String>();
        map.forEach((k, v) -> result.put(k, String.valueOf(v)));
        return result;
    }
}
