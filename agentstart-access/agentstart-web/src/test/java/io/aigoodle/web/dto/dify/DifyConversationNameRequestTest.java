package io.aigoodle.web.dto.dify;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class DifyConversationNameRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void normalizesAnExplicitConversationName() {
        DifyConversationNameRequest request = new DifyConversationNameRequest("  Quarterly review  ", false);

        assertThat(request.requestedName()).isEqualTo("Quarterly review");
        assertThat(request.requestsAutomaticName()).isFalse();
    }

    @Test
    void treatsBlankNamesAsAbsentAndPreservesAutomaticNamingIntent() {
        DifyConversationNameRequest request = new DifyConversationNameRequest("  ", true);

        assertThat(request.requestedName()).isNull();
        assertThat(request.requestsAutomaticName()).isTrue();
    }

    @Test
    void bindsTheDifySnakeCaseAutomaticNamingField() throws Exception {
        DifyConversationNameRequest request =
                objectMapper.readValue("{\"auto_generate\":true}", DifyConversationNameRequest.class);

        assertThat(request.requestsAutomaticName()).isTrue();
    }
}
