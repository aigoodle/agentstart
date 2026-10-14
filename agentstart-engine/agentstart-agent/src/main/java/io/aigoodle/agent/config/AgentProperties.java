package io.aigoodle.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent runtime configuration ({@code spring-agent.agent.*}).
 */
@ConfigurationProperties(prefix = "spring-agent.agent")
public class AgentProperties {
    private int maxContextCharacters = 32_000;
    private double longTermContextShare = 0.25;
    private int contextSummaryCharacters = 1_000;

    public int getMaxContextCharacters() {
        return maxContextCharacters;
    }

    public void setMaxContextCharacters(int value) {
        maxContextCharacters = value;
    }

    public double getLongTermContextShare() {
        return longTermContextShare;
    }

    public void setLongTermContextShare(double value) {
        longTermContextShare = value;
    }

    public int getContextSummaryCharacters() {
        return contextSummaryCharacters;
    }

    public void setContextSummaryCharacters(int value) {
        contextSummaryCharacters = value;
    }
}
