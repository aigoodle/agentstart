package io.aigoodle.web.common;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.common.exception.PlatformException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class GlobalExceptionHandlerTest {
    @Test
    void concurrentRunClaimIsAConflictWithAStableMachineCode() {
        var response = new GlobalExceptionHandler()
                .handleAgent(new PlatformException("run_concurrent_update", "another operator resumed this run", null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("run_concurrent_update");
        assertThat(response.getBody().getMessage()).contains("another operator");
    }
}
