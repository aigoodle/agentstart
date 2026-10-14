package io.aigoodle.channel.nativebot;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aigoodle.channel.*;
import io.aigoodle.channel.api.*;
import io.aigoodle.channel.api.ChannelException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bridges the lightweight channel SPI into the existing durable channel runtime. */
public final class NativeChannelRuntimeProvider implements ChannelRuntimeProvider {
    public static final String PROVIDER = "native";
    private static final Logger log = LoggerFactory.getLogger(NativeChannelRuntimeProvider.class);

    private record Account(
            String name, boolean enabled, NativeChannel<Object> channel, Object config, Instant updatedAt) {}

    private final Map<String, NativeChannel<?>> channels;
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final Map<String, ChannelSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper json;
    private final NativeAccountStore store;
    private final InboundMessageSink sink;
    private final Map<String, ChannelManifestCatalog.Manifest> manifests;

    public NativeChannelRuntimeProvider(List<NativeChannel<?>> values, ObjectMapper json) {
        this(values, json, null, null);
    }

    public NativeChannelRuntimeProvider(
            List<NativeChannel<?>> values, ObjectMapper json, NativeAccountStore store, InboundMessageSink sink) {
        Map<String, NativeChannel<?>> m = new LinkedHashMap<>();
        for (var c : values)
            if (m.putIfAbsent(c.id(), c) != null)
                throw new IllegalArgumentException("Duplicate native channel: " + c.id());
        channels = Map.copyOf(m);
        this.json = json;
        this.store = store;
        this.sink = sink;
        this.manifests = ChannelManifestCatalog.load();
    }

    public String type() {
        return PROVIDER;
    }

    public List<ChannelDefinition> discoverChannels() {
        return channels.values().stream()
                .map(c -> {
                    ChannelManifestCatalog.Manifest manifest = manifests.get(c.id());
                    Map<String, Object> credentials =
                            manifest == null || manifest.credentialSchema().isEmpty()
                                    ? c.credentialSchema()
                                    : manifest.credentialSchema();
                    Map<String, Object> configuration =
                            manifest == null || manifest.configurationSchema().isEmpty()
                                    ? c.configurationSchema()
                                    : manifest.configurationSchema();
                    return new ChannelDefinition(
                            PROVIDER,
                            c.id(),
                            value(
                                    manifest == null ? null : manifest.name(),
                                    c.descriptor().name()),
                            value(
                                    manifest == null ? null : manifest.description(),
                                    c.descriptor().description()),
                            value(
                                    manifest == null ? null : manifest.version(),
                                    c.descriptor().version()),
                            true,
                            true,
                            "UP",
                            write(credentials),
                            write(configuration),
                            manifest == null ? Map.of() : manifest.uiSchema(),
                            capabilities(c.descriptor().capabilities()),
                            catalogMetadata(c.descriptor(), manifest));
                })
                .toList();
    }

    private static Map<String, Object> catalogMetadata(
            ChannelDescriptor descriptor, ChannelManifestCatalog.Manifest manifest) {
        Map<String, Object> metadata = new LinkedHashMap<>(descriptor.metadata());
        if (manifest != null) {
            metadata.putAll(manifest.metadata());
            metadata.put("manifestVersion", manifest.schemaVersion());
            metadata.put("formSource", "classpath-yaml");
        }
        // The typed SPI is authoritative; do not require every frontend to infer
        // ownership semantics from channel ids or platform names.
        metadata.put(
                "accountModel",
                manifest == null || manifest.accountModel().isEmpty()
                        ? descriptor.accountModel().toMetadata()
                        : manifest.accountModel());
        return Map.copyOf(metadata);
    }

    private static String value(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    public List<ChannelAccount> accounts(String channelId) {
        return accounts.entrySet().stream()
                .filter(e -> e.getKey().startsWith(channelId + ":"))
                .map(e -> view(channelId, e.getKey().substring(channelId.length() + 1), e.getValue()))
                .toList();
    }

    /** Restores persisted streaming accounts and starts their sessions after application startup. */
    public void restoreEnabledAccounts() {
        if (store == null) return;
        for (NativeAccountStore.SavedAccount saved : store.findEnabled()) {
            String accountKey = key(saved.channelId(), saved.accountId());
            if (accounts.containsKey(accountKey)) continue;
            try {
                Account account = restore(saved.channelId(), saved.accountId(), saved.account());
                accounts.putIfAbsent(accountKey, account);
            } catch (RuntimeException e) {
                log.error("Failed to restore native channel account {}/{}", saved.channelId(), saved.accountId(), e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public ChannelAccount saveAccount(SaveChannelAccountRequest r) {
        NativeChannel<Object> c = (NativeChannel<Object>) require(r.channelId());
        Map<String, Object> raw = new LinkedHashMap<>(r.configuration());
        raw.put("accountId", r.accountId());
        validateRequired(c.credentialSchema(), raw);
        validateRequired(c.configurationSchema(), raw);
        Object cfg = json.convertValue(raw, c.configType());
        Account a = new Account(r.name(), r.enabled(), c, cfg, Instant.now());
        if (r.enabled()) {
            ConnectionTestResult t = c.test(cfg);
            if (!t.success()) throw new ChannelException(t.code(), t.message(), false);
        }
        accounts.put(key(r.channelId(), r.accountId()), a);
        startSession(r.channelId(), r.accountId(), a);
        return view(r.channelId(), r.accountId(), a);
    }

    public ChannelAccount testAccount(String channelId, String accountId) {
        Account a = requireAccount(channelId, accountId);
        ConnectionTestResult t = a.channel.test(a.config);
        ChannelSession session = sessions.get(key(channelId, accountId));
        boolean connected = session != null && session.connected();
        Map<String, Object> metadata = new LinkedHashMap<>(t.metadata());
        metadata.put("transport", transport(a, session));
        metadata.put("credentialsValid", t.success());
        return new ChannelAccount(
                PROVIDER,
                channelId,
                accountId,
                a.name,
                a.enabled,
                true,
                a.enabled,
                connected,
                connected ? Instant.now() : null,
                t.success() ? null : t.message(),
                Map.copyOf(metadata));
    }

    public void deleteAccount(String channelId, String accountId) {
        accounts.remove(key(channelId, accountId));
        ChannelSession session = sessions.remove(key(channelId, accountId));
        if (session != null) session.close();
    }

    public ChannelSendResult sendWithResult(ChannelOutboundMessage old) {
        Account a = requireAccount(old.channelId(), old.accountId());
        List<MessageContent> content = new ArrayList<>();
        if (old.content() != null && !old.content().isBlank()) content.add(MessageContent.text(old.content()));
        old.attachments()
                .forEach(x -> content.add(new MessageContent(
                        MessageType.valueOf(x.type()), null, x.url(), x.name(), x.mimeType(), x.size(), x.metadata())));
        Map<String, Object> metadata = new LinkedHashMap<>(old.metadata());
        metadata.putIfAbsent("conversationType", old.contentPayload().getOrDefault("conversationType", "C2C"));
        String replyTo = metadata.get("replyToPlatformMessageId") == null
                ? null
                : String.valueOf(metadata.get("replyToPlatformMessageId"));
        SendResult r = a.channel.send(
                a.config,
                new OutboundMessage(
                        String.valueOf(old.metadata().get("idempotencyKey")),
                        old.conversationId(),
                        old.targetId(),
                        replyTo,
                        content,
                        metadata));
        return new ChannelSendResult(r.platformMessageId(), r.metadata());
    }

    public void send(ChannelOutboundMessage m) {
        sendWithResult(m);
    }

    public InboundMessage parse(String channelId, String accountId, Map<String, String> h, Map<String, Object> p) {
        Account a = requireAccount(channelId, accountId);
        return a.channel.parse(a.config, h, p);
    }

    public Object challenge(String channelId, String accountId, Map<String, String> h, Map<String, Object> p) {
        Account a = requireAccount(channelId, accountId);
        return a.channel.challenge(a.config, h, p);
    }

    private NativeChannel<?> require(String id) {
        NativeChannel<?> c = channels.get(id);
        if (c == null) throw new IllegalArgumentException("Unknown native channel: " + id);
        return c;
    }

    private Account requireAccount(String c, String a) {
        Account v = accounts.get(key(c, a));
        if (v == null && store != null)
            v = store.find(c, a).map(saved -> restore(c, a, saved)).orElse(null);
        if (v == null) throw new IllegalArgumentException("Unknown native channel account: " + c + "/" + a);
        accounts.putIfAbsent(key(c, a), v);
        return v;
    }

    @SuppressWarnings("unchecked")
    private Account restore(String channel, String accountId, NativeAccountStore.Saved saved) {
        NativeChannel<Object> impl = (NativeChannel<Object>) require(channel);
        Object config = json.convertValue(saved.configuration(), impl.configType());
        Account account = new Account(saved.name(), saved.enabled(), impl, config, Instant.now());
        startSession(channel, accountId, account);
        return account;
    }

    private void startSession(String channel, String accountId, Account account) {
        ChannelSession old = sessions.remove(key(channel, accountId));
        if (old != null) old.close();
        if (account.enabled
                && sink != null
                && account.channel.descriptor().capabilities().streaming()) {
            ChannelSession session = account.channel.connect(account.config, sink);
            if (session != null) sessions.put(key(channel, accountId), session);
        }
    }

    private static String key(String c, String a) {
        return c + ":" + a;
    }

    private ChannelAccount view(String c, String id, Account a) {
        ChannelSession session = sessions.get(key(c, id));
        boolean connected = session != null && session.connected();
        return new ChannelAccount(
                PROVIDER,
                c,
                id,
                a.name,
                a.enabled,
                true,
                a.enabled,
                connected,
                connected ? a.updatedAt : null,
                null,
                Map.of("transport", transport(a, session), "credentialsValid", a.enabled));
    }

    private static String transport(Account account, ChannelSession session) {
        ChannelCapabilities capabilities = account.channel.descriptor().capabilities();
        if (capabilities.streaming() && capabilities.webhook() && session == null) return "webhook";
        Object value = account.channel.descriptor().metadata().get("transport");
        return value == null ? (capabilities.streaming() ? "stream" : "webhook") : String.valueOf(value);
    }

    private String write(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static void validateRequired(Map<String, Object> schema, Map<String, Object> values) {
        Object required = schema == null ? null : schema.get("required");
        if (!(required instanceof Collection<?> fields)) return;
        List<String> missing = fields.stream()
                .map(String::valueOf)
                .filter(field -> {
                    Object value = values.get(field);
                    return value == null || (value instanceof String text && text.isBlank());
                })
                .toList();
        if (!missing.isEmpty()) {
            throw new ChannelException(
                    "invalid_configuration", "Missing required fields: " + String.join(", ", missing), false);
        }
    }

    private static Map<String, Object> capabilities(ChannelCapabilities c) {
        return Map.of(
                "inbound",
                c.inbound().stream().map(Enum::name).toList(),
                "outbound",
                c.outbound().stream().map(Enum::name).toList(),
                "webhook",
                c.webhook(),
                "streaming",
                c.streaming(),
                "groupChat",
                c.groupChat(),
                "deliveryReceipts",
                c.deliveryReceipts());
    }
}
