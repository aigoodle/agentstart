package io.aigoodle.channel.wecom;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WeComGatewaySessionTest {

    @Test
    void buildsCallbackBoundStreamingReplyFrame() {
        Map<String, Object> frame = WeComGatewaySession.streamFrame("callback-request-1", "stream-1", "完整回复", true);

        assertThat(frame).containsEntry("cmd", "aibot_respond_msg");
        assertThat(map(frame.get("headers"))).containsEntry("req_id", "callback-request-1");
        Map<String, Object> body = map(frame.get("body"));
        assertThat(body).containsEntry("msgtype", "stream");
        assertThat(map(body.get("stream")))
                .containsEntry("id", "stream-1")
                .containsEntry("content", "完整回复")
                .containsEntry("finish", true);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
