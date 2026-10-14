package io.aigoodle.channel.email;

import io.aigoodle.channel.api.*;
import io.aigoodle.channel.nativebot.*;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import jakarta.mail.search.FlagTerm;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class EmailChannel implements NativeChannel<EmailChannel.Config> {
    public record Config(
            String smtpHost,
            Integer smtpPort,
            String imapHost,
            Integer imapPort,
            String username,
            String password,
            String from,
            Boolean ssl,
            Boolean startTls,
            Integer pollIntervalSeconds,
            String accountId) {}

    public String id() {
        return "email";
    }

    public Class<Config> configType() {
        return Config.class;
    }

    public ChannelDescriptor descriptor() {
        return new ChannelDescriptor(
                id(),
                "Email",
                "SMTP/IMAP 双向邮件消息通道",
                "1",
                new ChannelCapabilities(Set.of(MessageType.TEXT), Set.of(MessageType.TEXT), false, true, false, false),
                ChannelAccountModel.tenant(new ChannelAccountModel.IdentityBridge(
                        true, "DIRECTORY", "邮件地址", "企业员工", "可按企业通讯录将发件人地址关联到员工。")),
                Map.of("icon", "Email"));
    }

    public Map<String, Object> credentialSchema() {
        return PlatformMessages.schema(
                "smtpHost",
                "string",
                "imapHost",
                "string",
                "username",
                "string",
                "password",
                "string",
                "from",
                "string");
    }

    public Map<String, Object> configurationSchema() {
        return PlatformMessages.optionalSchema(
                "smtpPort",
                "integer",
                "imapPort",
                "integer",
                "ssl",
                "boolean",
                "startTls",
                "boolean",
                "pollIntervalSeconds",
                "integer");
    }

    public ConnectionTestResult test(Config c) {
        Transport transport = null;
        Store store = null;
        try {
            transport = session(c).getTransport("smtp");
            transport.connect(c.smtpHost(), port(c), c.username(), c.password());
            store = imapSession(c).getStore(Boolean.TRUE.equals(c.ssl()) ? "imaps" : "imap");
            store.connect(c.imapHost(), imapPort(c), c.username(), c.password());
            return ConnectionTestResult.ok();
        } catch (Exception e) {
            return new ConnectionTestResult(false, "mail_connection_failed", e.getMessage(), Map.of());
        } finally {
            try {
                if (transport != null) transport.close();
            } catch (Exception ignored) {
            }
            try {
                if (store != null) store.close();
            } catch (Exception ignored) {
            }
        }
    }

    public SendResult send(Config c, OutboundMessage m) {
        try {
            MimeMessage mail = new MimeMessage(session(c));
            mail.setFrom(new InternetAddress(c.from()));
            mail.setRecipients(Message.RecipientType.TO, InternetAddress.parse(m.targetId()));
            mail.setSubject(String.valueOf(m.metadata().getOrDefault("subject", "Agent Start message")), "UTF-8");
            mail.setText(PlatformMessages.outboundText(m), "UTF-8");
            Transport.send(mail, c.username(), c.password());
            return SendResult.accepted(mail.getMessageID());
        } catch (Exception e) {
            throw new ChannelException("smtp_send_failed", e.getMessage(), true, e);
        }
    }

    public ChannelSession connect(Config c, InboundMessageSink sink) {
        AtomicBoolean running = new AtomicBoolean(true), connected = new AtomicBoolean();
        Thread worker = Thread.ofVirtual().name("email-imap-" + c.accountId()).start(() -> {
            while (running.get()) {
                try (Store store = imapSession(c).getStore(Boolean.TRUE.equals(c.ssl()) ? "imaps" : "imap")) {
                    store.connect(c.imapHost(), imapPort(c), c.username(), c.password());
                    connected.set(true);
                    try (Folder inbox = store.getFolder("INBOX")) {
                        inbox.open(Folder.READ_WRITE);
                        for (Message mail : inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false))) {
                            String messageId = mail instanceof MimeMessage mm ? mm.getMessageID() : null;
                            if (messageId == null)
                                messageId = "email:"
                                        + (mail.getSentDate() == null
                                                ? System.currentTimeMillis()
                                                : mail.getSentDate().getTime())
                                        + ":"
                                        + mail.getMessageNumber();
                            Address[] from = mail.getFrom();
                            if (from == null || from.length == 0) continue;
                            String sender = from[0] instanceof InternetAddress address
                                    ? address.getAddress()
                                    : from[0].toString();
                            String body = body(mail);
                            InboundReceipt receipt = sink.accept(PlatformMessages.inbound(
                                    id(),
                                    c.accountId(),
                                    messageId,
                                    sender,
                                    sender,
                                    sender,
                                    body,
                                    false,
                                    Map.of("subject", String.valueOf(mail.getSubject()))));
                            // Only acknowledge the mailbox after the durable inbound claim accepted
                            // the message. A busy/rejected host must see the same unread mail again.
                            if (receipt.accepted()) mail.setFlag(Flags.Flag.SEEN, true);
                        }
                    }
                } catch (Exception failure) {
                    connected.set(false);
                }
                try {
                    Thread.sleep(Math.max(5, c.pollIntervalSeconds() == null ? 30 : c.pollIntervalSeconds()) * 1000L);
                } catch (InterruptedException stop) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        return new ChannelSession() {
            public boolean connected() {
                return connected.get();
            }

            public void close() {
                running.set(false);
                worker.interrupt();
            }
        };
    }

    private Session session(Config c) {
        Properties p = new Properties();
        p.put("mail.smtp.host", c.smtpHost());
        p.put("mail.smtp.port", String.valueOf(port(c)));
        p.put("mail.smtp.auth", "true");
        p.put("mail.smtp.ssl.enable", String.valueOf(Boolean.TRUE.equals(c.ssl())));
        p.put(
                "mail.smtp.starttls.enable",
                String.valueOf(
                        c.startTls() == null ? !Boolean.TRUE.equals(c.ssl()) : Boolean.TRUE.equals(c.startTls())));
        return Session.getInstance(p);
    }

    private int port(Config c) {
        return c.smtpPort() == null ? (Boolean.TRUE.equals(c.ssl()) ? 465 : 587) : c.smtpPort();
    }

    private Session imapSession(Config c) {
        Properties p = new Properties();
        p.put("mail.store.protocol", Boolean.TRUE.equals(c.ssl()) ? "imaps" : "imap");
        return Session.getInstance(p);
    }

    private int imapPort(Config c) {
        return c.imapPort() == null ? (Boolean.TRUE.equals(c.ssl()) ? 993 : 143) : c.imapPort();
    }

    private static String body(Part part) throws Exception {
        if (part.isMimeType("text/plain")) return String.valueOf(part.getContent());
        if (part.isMimeType("text/html"))
            return String.valueOf(part.getContent())
                    .replaceAll("<[^>]+>", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            String html = "";
            for (int i = 0; i < multipart.getCount(); i++) {
                Part child = multipart.getBodyPart(i);
                if (Part.ATTACHMENT.equalsIgnoreCase(child.getDisposition())) continue;
                String value = body(child);
                if (child.isMimeType("text/plain") && !value.isBlank()) return value;
                if (!value.isBlank()) html = value;
            }
            return html;
        }
        return "";
    }
}
