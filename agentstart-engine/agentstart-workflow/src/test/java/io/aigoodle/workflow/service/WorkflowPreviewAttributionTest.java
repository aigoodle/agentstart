package io.aigoodle.workflow.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aigoodle.agent.service.AppPermissionService;
import io.aigoodle.workflow.engine.WorkflowEngine;
import io.aigoodle.workflow.engine.WorkflowRunOptions;
import io.aigoodle.workflow.engine.WorkflowRunResult;
import io.aigoodle.workflow.mapper.WorkflowMapper;
import io.aigoodle.workflow.mapper.WorkflowRunMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorkflowPreviewAttributionTest {
    @Test
    void checksWriteAccessAndPassesTrustedAppIdToEngine() throws Exception {
        var engine = mock(WorkflowEngine.class);
        var permissions = mock(AppPermissionService.class);
        var service = new WorkflowService(mock(WorkflowMapper.class), mock(WorkflowRunMapper.class), engine);
        service.setAppPermissions(permissions);
        var result = new WorkflowRunResult();
        result.setRunId("preview");
        result.succeed(Map.of());
        when(engine.run(any(), anyMap(), isNull(), isNull(), isNull(), eq("default"), any(WorkflowRunOptions.class)))
                .thenReturn(result);
        var graph = new ObjectMapper().readTree("{\"nodes\":[],\"edges\":[]}");
        assertSame(
                result, service.runGraphForTenant(graph, Map.of("appId", "untrusted"), null, "default", "app-a", null));
        verify(permissions).requireLinkedApp("default", "app-a", true);
        var options = ArgumentCaptor.forClass(WorkflowRunOptions.class);
        verify(engine).run(any(), anyMap(), isNull(), isNull(), isNull(), eq("default"), options.capture());
        assertEquals("app-a", options.getValue().appId());
    }

    @Test
    void deniedPreviewDoesNotInvokeEngine() throws Exception {
        var engine = mock(WorkflowEngine.class);
        var permissions = mock(AppPermissionService.class);
        var service = new WorkflowService(mock(WorkflowMapper.class), mock(WorkflowRunMapper.class), engine);
        service.setAppPermissions(permissions);
        doThrow(new SecurityException("Denied")).when(permissions).requireLinkedApp("default", "hidden", true);
        assertThrows(
                SecurityException.class,
                () -> service.runGraphForTenant(
                        new ObjectMapper().readTree("{}"), Map.of(), null, "default", "hidden", null));
        verifyNoInteractions(engine);
    }
}
