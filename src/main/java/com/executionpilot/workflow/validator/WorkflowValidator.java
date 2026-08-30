package com.executionpilot.workflow.validator;

import com.executionpilot.workflow.domain.Workflow;

import java.util.List;

public interface WorkflowValidator {

    List<ValidationMessage> validate(Workflow workflow, ValidationContext context);
}
