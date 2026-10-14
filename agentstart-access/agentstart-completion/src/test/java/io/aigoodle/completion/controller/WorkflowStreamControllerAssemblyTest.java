package io.aigoodle.completion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.aigoodle.workflow.service.WorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;

class WorkflowStreamControllerAssemblyTest {

    @Test
    void registersInReactiveApplication() {
        new ReactiveWebApplicationContextRunner()
                .withBean(WorkflowService.class, () -> mock(WorkflowService.class))
                .withUserConfiguration(WorkflowStreamController.class)
                .run(context -> assertThat(context).hasSingleBean(WorkflowStreamController.class));
    }

    @Test
    void skipsNonReactiveApplication() {
        new ApplicationContextRunner()
                .withBean(WorkflowService.class, () -> mock(WorkflowService.class))
                .withUserConfiguration(WorkflowStreamController.class)
                .run(context -> assertThat(context).doesNotHaveBean(WorkflowStreamController.class));
    }
}
