package io.aigoodle.channel.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring-agent.channel")
public class ChannelProperties {
    /**
     * Must stay identical to the historical {@code spring-agent.connector.encryption-secret} default
     * so channel connection ciphertexts written before the connector/channel split still decrypt.
     */
    private String encryptionSecret = "change-me-connector-secret";

    private Duration catalogCacheTtl = Duration.ofMinutes(10);
    private boolean outboxEnabled = true;
    private Duration outboxPollInterval = Duration.ofSeconds(1);
    private Duration outboxLeaseDuration = Duration.ofSeconds(30);
    private Duration inboundLeaseDuration = Duration.ofMinutes(2);
    private int outboxBatchSize = 20;
    private int outboxMaxAttempts = 8;
    private int outboxMaxSendsPerSecond = 10;
    private boolean connectionReconcileEnabled = true;
    private Duration connectionReconcileInterval = Duration.ofSeconds(10);
    private Duration connectionReconcileLeaseDuration = Duration.ofSeconds(30);
    private int connectionReconcileBatchSize = 20;
    private boolean connectionHealthEnabled = true;
    private Duration connectionHealthPollInterval = Duration.ofSeconds(30);
    private Duration connectionHealthRefreshInterval = Duration.ofMinutes(2);
    private Duration connectionHealthLeaseDuration = Duration.ofSeconds(30);
    private int connectionHealthBatchSize = 20;
    private boolean slaReminderEnabled = true;
    private Duration slaReminderPollInterval = Duration.ofSeconds(30);
    private Duration slaReminderLeadTime = Duration.ofMinutes(5);
    private Duration slaReminderLeaseDuration = Duration.ofMinutes(1);
    private int slaReminderBatchSize = 100;
    private boolean backfillEnabled = false;

    public String getEncryptionSecret() {
        return encryptionSecret;
    }

    public void setEncryptionSecret(String encryptionSecret) {
        this.encryptionSecret = encryptionSecret;
    }

    public Duration getCatalogCacheTtl() {
        return catalogCacheTtl;
    }

    public void setCatalogCacheTtl(Duration value) {
        this.catalogCacheTtl = value;
    }

    public boolean isOutboxEnabled() {
        return outboxEnabled;
    }

    public void setOutboxEnabled(boolean value) {
        this.outboxEnabled = value;
    }

    public Duration getOutboxPollInterval() {
        return outboxPollInterval;
    }

    public void setOutboxPollInterval(Duration value) {
        this.outboxPollInterval = value;
    }

    public Duration getOutboxLeaseDuration() {
        return outboxLeaseDuration;
    }

    public void setOutboxLeaseDuration(Duration value) {
        this.outboxLeaseDuration = value;
    }

    public Duration getInboundLeaseDuration() {
        return inboundLeaseDuration;
    }

    public void setInboundLeaseDuration(Duration value) {
        this.inboundLeaseDuration = value;
    }

    public int getOutboxBatchSize() {
        return outboxBatchSize;
    }

    public void setOutboxBatchSize(int value) {
        this.outboxBatchSize = value;
    }

    public int getOutboxMaxAttempts() {
        return outboxMaxAttempts;
    }

    public void setOutboxMaxAttempts(int value) {
        this.outboxMaxAttempts = value;
    }

    public int getOutboxMaxSendsPerSecond() {
        return outboxMaxSendsPerSecond;
    }

    public void setOutboxMaxSendsPerSecond(int value) {
        this.outboxMaxSendsPerSecond = value;
    }

    public boolean isConnectionReconcileEnabled() {
        return connectionReconcileEnabled;
    }

    public void setConnectionReconcileEnabled(boolean value) {
        this.connectionReconcileEnabled = value;
    }

    public Duration getConnectionReconcileInterval() {
        return connectionReconcileInterval;
    }

    public void setConnectionReconcileInterval(Duration value) {
        this.connectionReconcileInterval = value;
    }

    public Duration getConnectionReconcileLeaseDuration() {
        return connectionReconcileLeaseDuration;
    }

    public void setConnectionReconcileLeaseDuration(Duration value) {
        this.connectionReconcileLeaseDuration = value;
    }

    public int getConnectionReconcileBatchSize() {
        return connectionReconcileBatchSize;
    }

    public void setConnectionReconcileBatchSize(int value) {
        this.connectionReconcileBatchSize = value;
    }

    public boolean isConnectionHealthEnabled() {
        return connectionHealthEnabled;
    }

    public void setConnectionHealthEnabled(boolean value) {
        this.connectionHealthEnabled = value;
    }

    public Duration getConnectionHealthPollInterval() {
        return connectionHealthPollInterval;
    }

    public void setConnectionHealthPollInterval(Duration value) {
        this.connectionHealthPollInterval = value;
    }

    public Duration getConnectionHealthRefreshInterval() {
        return connectionHealthRefreshInterval;
    }

    public void setConnectionHealthRefreshInterval(Duration value) {
        this.connectionHealthRefreshInterval = value;
    }

    public Duration getConnectionHealthLeaseDuration() {
        return connectionHealthLeaseDuration;
    }

    public void setConnectionHealthLeaseDuration(Duration value) {
        this.connectionHealthLeaseDuration = value;
    }

    public int getConnectionHealthBatchSize() {
        return connectionHealthBatchSize;
    }

    public void setConnectionHealthBatchSize(int value) {
        this.connectionHealthBatchSize = value;
    }

    public boolean isSlaReminderEnabled() {
        return slaReminderEnabled;
    }

    public void setSlaReminderEnabled(boolean value) {
        this.slaReminderEnabled = value;
    }

    public Duration getSlaReminderPollInterval() {
        return slaReminderPollInterval;
    }

    public void setSlaReminderPollInterval(Duration value) {
        this.slaReminderPollInterval = value;
    }

    public Duration getSlaReminderLeadTime() {
        return slaReminderLeadTime;
    }

    public void setSlaReminderLeadTime(Duration value) {
        this.slaReminderLeadTime = value;
    }

    public Duration getSlaReminderLeaseDuration() {
        return slaReminderLeaseDuration;
    }

    public void setSlaReminderLeaseDuration(Duration value) {
        this.slaReminderLeaseDuration = value;
    }

    public int getSlaReminderBatchSize() {
        return slaReminderBatchSize;
    }

    public void setSlaReminderBatchSize(int value) {
        this.slaReminderBatchSize = value;
    }

    public boolean isBackfillEnabled() {
        return backfillEnabled;
    }

    public void setBackfillEnabled(boolean value) {
        this.backfillEnabled = value;
    }
}
