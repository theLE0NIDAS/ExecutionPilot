package com.executionpilot.workflow.validator;

import com.executionpilot.workflow.domain.Workflow;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CycleDetectionValidator implements WorkflowValidator {

    private enum Color {
        WHITE,
        GRAY,
        BLACK
    }

    @Override
    public List<ValidationMessage> validate(Workflow workflow, ValidationContext context) {
        Map<String, Color> colors = new HashMap<>();
        for (String stateId : workflow.getStates().keySet()) {
            colors.put(stateId, Color.WHITE);
        }

        for (String stateId : workflow.getStates().keySet()) {
            if (colors.get(stateId) == Color.WHITE && hasCycle(stateId, context, colors)) {
                return List.of(new ValidationMessage(
                        ValidationErrorCode.CYCLE_DETECTED,
                        "Cycle detected in workflow graph. Workflow must be a DAG.",
                        true
                ));
            }
        }

        return List.of();
    }

    private boolean hasCycle(String rootStateId, ValidationContext context, Map<String, Color> colors) {
        Deque<String> stack = new ArrayDeque<>();
        Deque<Integer> edgeIndexStack = new ArrayDeque<>();

        stack.push(rootStateId);
        edgeIndexStack.push(0);
        colors.put(rootStateId, Color.GRAY);

        while (!stack.isEmpty()) {
            String current = stack.peek();
            List<String> neighbors = new ArrayList<>(context.neighbors(current));
            int index = edgeIndexStack.pop();

            if (index >= neighbors.size()) {
                colors.put(current, Color.BLACK);
                stack.pop();
                continue;
            }

            edgeIndexStack.push(index + 1);
            String neighbor = neighbors.get(index);
            Color neighborColor = colors.get(neighbor);

            if (neighborColor == Color.GRAY) {
                return true;
            }

            if (neighborColor == Color.WHITE) {
                colors.put(neighbor, Color.GRAY);
                stack.push(neighbor);
                edgeIndexStack.push(0);
            }
        }

        return false;
    }
}
