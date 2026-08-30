package com.executionpilot.workflow.validator;

import com.executionpilot.workflow.domain.TransitionDefinition;
import com.executionpilot.workflow.domain.Workflow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public final class KahnTopologicalOrderValidator implements WorkflowValidator {

    @Override
    public List<ValidationMessage> validate(Workflow workflow, ValidationContext context) {
        Map<String, Integer> indegree = new HashMap<>();
        for (String stateId : workflow.getStates().keySet()) {
            indegree.put(stateId, 0);
        }

        for (TransitionDefinition transition : workflow.getTransitions()) {
            if (indegree.containsKey(transition.getToStateId()) && indegree.containsKey(transition.getFromStateId())) {
                indegree.put(transition.getToStateId(), indegree.get(transition.getToStateId()) + 1);
            }
        }

        PriorityQueue<String> queue = new PriorityQueue<>();
        for (Map.Entry<String, Integer> entry : indegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<String> topologicalOrder = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            topologicalOrder.add(current);

            for (String neighbor : context.neighbors(current)) {
                int nextDegree = indegree.get(neighbor) - 1;
                indegree.put(neighbor, nextDegree);
                if (nextDegree == 0) {
                    queue.add(neighbor);
                }
            }
        }

        if (topologicalOrder.size() != workflow.getStates().size()) {
            return List.of(new ValidationMessage(
                    ValidationErrorCode.TOPOLOGICAL_SORT_FAILED,
                    "Topological sort failed because graph still contains cyclic dependencies.",
                    true
            ));
        }

        context.setTopologicalOrder(topologicalOrder);
        return List.of();
    }
}
