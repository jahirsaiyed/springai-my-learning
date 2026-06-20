package com.example.memory.procedural;

import java.util.List;
import java.util.Map;

/**
 * Represents a parsed YAML workflow definition.
 * Workflows consist of sequential steps, each with a name, action, and conditions.
 */
public record WorkflowDefinition(
    String name,
    String description,
    List<WorkflowStep> steps
) {

    public record WorkflowStep(
        String name,
        String action,
        String description,
        Map<String, String> parameters,
        String requiresConfirmation,
        String onSuccess,
        String onFailure
    ) {}
}
