package io.aigoodle.observability.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLMOps configuration ({@code spring-agent.observability.*}). Pricing is per 1K tokens
 * in your chosen currency, keyed by model name; unknown models record tokens with zero
 * cost. Sensible defaults are seeded for common models.
 */
@ConfigurationProperties(prefix = "spring-agent.observability")
public class ObservabilityProperties {

    /** Master switch for LLM metering. */
    private boolean enabled = true;

    /** Currency label stored alongside costs (informational). */
    private String currency = "USD";

    /** model name -> price. */
    private Map<String, ModelPrice> pricing = defaultPricing();

    public static class ModelPrice {
        /** Price per 1,000 prompt (input) tokens. */
        private double inputPer1k;
        /** Price per 1,000 completion (output) tokens. */
        private double outputPer1k;

        public ModelPrice() {}

        public ModelPrice(double inputPer1k, double outputPer1k) {
            this.inputPer1k = inputPer1k;
            this.outputPer1k = outputPer1k;
        }

        public double getInputPer1k() {
            return inputPer1k;
        }

        public void setInputPer1k(double inputPer1k) {
            this.inputPer1k = inputPer1k;
        }

        public double getOutputPer1k() {
            return outputPer1k;
        }

        public void setOutputPer1k(double outputPer1k) {
            this.outputPer1k = outputPer1k;
        }
    }

    private static Map<String, ModelPrice> defaultPricing() {
        Map<String, ModelPrice> defaultPrices = new LinkedHashMap<>();
        defaultPrices.put("gpt-4o", new ModelPrice(0.0025, 0.01));
        defaultPrices.put("gpt-4o-mini", new ModelPrice(0.00015, 0.0006));
        defaultPrices.put("gpt-4.1", new ModelPrice(0.002, 0.008));
        defaultPrices.put("gpt-4.1-mini", new ModelPrice(0.0004, 0.0016));
        defaultPrices.put("deepseek-chat", new ModelPrice(0.00027, 0.0011));
        defaultPrices.put("text-embedding-3-small", new ModelPrice(0.00002, 0.0));
        return defaultPrices;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Map<String, ModelPrice> getPricing() {
        return pricing;
    }

    public void setPricing(Map<String, ModelPrice> pricing) {
        this.pricing = pricing == null ? new LinkedHashMap<>() : new LinkedHashMap<>(pricing);
    }
}
