package io.aigoodle.channel.nativebot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aigoodle.channel.*;
import io.aigoodle.channel.api.InboundMessage;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/channel-events/native")
public final class NativeChannelCallbackController {
    private final NativeChannelRuntimeProvider runtime;
    private final ChannelInboundDispatcher dispatcher;
    private final ChannelEventLogService events;
    private final ObjectMapper json;

    public NativeChannelCallbackController(
            NativeChannelRuntimeProvider runtime,
            ChannelInboundDispatcher dispatcher,
            ChannelEventLogService events,
            ObjectMapper json) {
        this.runtime = runtime;
        this.dispatcher = dispatcher;
        this.events = events;
        this.json = json;
    }

    @GetMapping("/{channelId}/{accountId}")
    public Object verify(
            @PathVariable String channelId,
            @PathVariable String accountId,
            @RequestHeader Map<String, String> headers,
            @RequestParam Map<String, String> params) {
        Map<String, Object> payload = new LinkedHashMap<>(params);
        Object challenge = runtime.challenge(channelId, accountId, headers, payload);
        if (challenge == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported callback verification");
        return challenge;
    }

    @PostMapping(value = "/{channelId}/{accountId}", consumes = "application/json")
    public Object receiveJson(
            @PathVariable String channelId,
            @PathVariable String accountId,
            @RequestHeader Map<String, String> headers,
            @RequestBody byte[] body) {
        try {
            Map<String, Object> payload = json.readValue(body, new TypeReference<>() {});
            Map<String, String> trusted = new LinkedHashMap<>(headers);
            trusted.put("x-agentstart-raw-body", Base64.getEncoder().encodeToString(body));
            return receive(channelId, accountId, trusted, payload);
        } catch (java.io.IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid JSON callback", e);
        }
    }

    Object receive(String channelId, String accountId, Map<String, String> headers, Map<String, Object> payload) {
        Object challenge = runtime.challenge(channelId, accountId, headers, payload);
        if (challenge != null) return challenge;
        InboundMessage m = runtime.parse(channelId, accountId, headers, payload);
        Map<String, Object> meta = new LinkedHashMap<>(m.metadata());
        meta.put("replyTargetId", m.replyTargetId());
        ChannelInboundEvent event = new ChannelInboundEvent(
                "native",
                channelId,
                accountId,
                m.messageId(),
                m.senderId(),
                m.conversationId(),
                m.text(),
                m.contents().isEmpty() ? "TEXT" : m.contents().getFirst().type().name(),
                List.of(),
                Map.of("conversationType", m.conversationType().name()),
                m.timestamp(),
                m.conversationType() == io.aigoodle.channel.api.ConversationType.GROUP,
                meta);
        ChannelEventLogService.InboundClaim claim = events.claimInbound(event, "native-callback:" + UUID.randomUUID());
        if (claim.priorResult() != null) return callbackAck(channelId, claim.priorResult());
        if (claim.busy())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "message is being processed");
        long started = System.nanoTime();
        try (ChannelEventLogService.InboundLease ignored = events.keepAlive(claim)) {
            ChannelInboundResult result = dispatcher.dispatch(event);
            events.completeInbound(claim, event, result, (System.nanoTime() - started) / 1_000_000, null);
            return callbackAck(channelId, result);
        } catch (RuntimeException e) {
            events.completeInbound(
                    claim, event, ChannelInboundResult.unhandled(), (System.nanoTime() - started) / 1_000_000, e);
            throw e;
        }
    }

    @PostMapping(
            value = "/{channelId}/{accountId}",
            consumes = {"application/xml", "text/xml"})
    public Object receiveXml(
            @PathVariable String channelId,
            @PathVariable String accountId,
            @RequestHeader Map<String, String> headers,
            @RequestBody String body,
            @RequestParam Map<String, String> params) {
        Map<String, Object> payload = parseXml(body);
        payload.putAll(params);
        return receive(channelId, accountId, headers, payload);
    }

    private static Map<String, Object> parseXml(String value) {
        try {
            var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var doc = factory.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(value)));
            Map<String, Object> result = new LinkedHashMap<>();
            var nodes = doc.getDocumentElement().getChildNodes();
            for (int i = 0; i < nodes.getLength(); i++)
                if (nodes.item(i).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE)
                    result.put(nodes.item(i).getNodeName(), nodes.item(i).getTextContent());
            return result;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid XML callback", e);
        }
    }

    private static ChannelInboundResult acknowledge(ChannelInboundResult r) {
        Map<String, Object> m = new LinkedHashMap<>(r.metadata());
        m.put("replyQueued", r.reply() != null && !r.reply().isBlank());
        return new ChannelInboundResult(r.handled(), null, r.code(), m);
    }

    private static Object callbackAck(String channelId, ChannelInboundResult result) {
        if ("qqbot".equals(channelId)) return Map.of("op", 12, "d", 0);
        if ("wecom".equals(channelId)) return "success";
        return acknowledge(result);
    }
}
