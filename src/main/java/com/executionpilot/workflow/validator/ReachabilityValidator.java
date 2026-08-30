package com.executionpilot.workflow.validator;

import com.executionpilot.workflow.domain.Workflow;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class ReachabilityValidator implements WorkflowValidator {

    @Override
    public List<ValidationMessage> validate(Workflow workflow, ValidationContext context) {
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();

        queue.add(workflow.getStartStateId());
        visited.add(workflow.getStartStateId());

        while (!queue.isEmpty()) {
            String current = queue.remove();
            for (String neighbor : context.neighbors(current)) {
                if (visited.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }

        Set<String> unreachable = new TreeSet<>(workflow.getStates().keySet());
        unreachable.removeAll(visited);
        if (!unreachable.isEmpty()) {
            return List.of(new ValidationMessage(
                    ValidationErrorCode.UNREACHABLE_STATES,
                    "Unreachable states found from start state '" + workflow.getStartStateId() + "': " + unreachable,
                    true
            ));
        }

        return List.of();
    }
}
