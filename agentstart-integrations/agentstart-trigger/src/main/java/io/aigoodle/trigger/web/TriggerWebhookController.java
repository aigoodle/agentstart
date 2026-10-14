package io.aigoodle.trigger.web;

import io.aigoodle.trigger.entity.TriggerEntity;
import io.aigoodle.trigger.service.TriggerInvocationRequest;
import io.aigoodle.trigger.service.TriggerService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound webhook endpoint: {@code POST /triggers/webhook/{path}} resolves the matching
 * enabled webhook trigger, verifies an optional token and fires it asynchronously,
 * returning the invocation id.
 */
@RestController
public class TriggerWebhookController {

    private final TriggerService triggerService;

    public TriggerWebhookController(TriggerService triggerService) {
        this.triggerService = triggerService;
    }

    @PostMapping("/triggers/webhook/{path}")
    public ResponseEntity<Map<String, String>> invokeWebhook(
            @PathVariable String path,
            @RequestBody(required = false) Map<String, Object> payload,
            @RequestParam(required = false) String token,
            @RequestHeader(value = "X-Trigger-Token", required = false) String headerToken) {
        TriggerEntity trigger = triggerService.findWebhook(path).orElse(null);
        if (trigger == null) {
            return ResponseEntity.status(404).body(Map.of("error", "no webhook trigger for path '" + path + "'"));
        }

        Object configuredToken = triggerService.config(trigger).get("token");
        if (configuredToken != null && !String.valueOf(configuredToken).isBlank()) {
            String providedToken = token != null ? token : headerToken;
            if (!String.valueOf(configuredToken).equals(providedToken)) {
                return ResponseEntity.status(401).body(Map.of("error", "invalid token"));
            }
        }

        TriggerInvocationRequest invocationRequest =
                TriggerInvocationRequest.webhook(trigger.getId(), payload == null ? Map.of() : payload);
        String invocationId = triggerService.fireAsynchronously(trigger.getTenantId(), invocationRequest);
        return ResponseEntity.accepted().body(Map.of("invocationId", invocationId, "triggerId", trigger.getId()));
    }
}
