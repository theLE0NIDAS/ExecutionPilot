package com.executionpilot.workflow.validator;

import com.executionpilot.workflow.domain.TransitionDefinition;
import com.executionpilot.workflow.domain.Workflow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ValidationContext {

    private final Map<String, List<String>> adjacency = new HashMap<>();
    private List<String> topologicalOrder = List.of();

    private ValidationContext() {
    }

    public static ValidationContext from(Workflow workflow) {
        ValidationContext context = new ValidationContext();
        for (String stateId : workflow.getStates().keySet()) {
            context.adjacency.put(stateId, new ArrayList<>());
        }
        for (TransitionDefinition transition : workflow.getTransitions()) {
            List<String> edges = context.adjacency.get(transition.getFromStateId());
            if (edges != null && context.adjacency.containsKey(transition.getToStateId())) {
                edges.add(transition.getToStateId());
            }
        }
        return context;
    }

    public Map<String, List<String>> adjacency() {
        Map<String, List<String>> copy = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : adjacency.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return copy;
    }

    public void setTopologicalOrder(List<String> topologicalOrder) {
        this.topologicalOrder = topologicalOrder == null ? List.of() : List.copyOf(topologicalOrder);
    }

    public List<String> getTopologicalOrder() {
        return topologicalOrder;
    }

    public Set<String> distinctEdges() {
        Set<String> edges = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> entry : adjacency.entrySet()) {
            for (String to : entry.getValue()) {
                edges.add(entry.getKey() + "->" + to);
            }
        }
        return edges;
    }

    public Collection<String> neighbors(String stateId) {
        List<String> neighbors = adjacency.get(stateId);
        return neighbors == null ? List.of() : List.copyOf(neighbors);
    }
}
