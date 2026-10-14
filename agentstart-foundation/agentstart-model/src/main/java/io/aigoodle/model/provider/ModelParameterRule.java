package io.aigoodle.model.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Declarative description of a single tunable model parameter (Dify-parity).
 * <p>
 * A rule tells the UI how to render an input for one parameter — a slider for
 * {@code temperature}, a number field for {@code maxTokens}, a text field for
 * {@code endpointId}. The runtime side is separate: whatever the user saves
 * lands in {@link io.aigoodle.model.entity.ModelEntity#getEncryptedConfig()}
 * and is fed into {@link ModelEndpoint#getProperties()} on resolve, where the
 * provider (e.g. {@code OpenAiCompatibleModelProvider}) picks the values back
 * out by name.
 * <p>
 * Rules are provided by the {@link ModelProvider} — either the provider-wide
 * default per {@link io.aigoodle.model.enums.ModelType} or, when a
 * specific {@link PredefinedModel} needs to override, on the predefined model
 * itself.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelParameterRule {

    public enum Type {
        /** Continuous numeric, drives a slider. */
        FLOAT,
        /** Discrete numeric, drives a number field. */
        INT,
        /** Text, drives an input. */
        STRING,
        /** Toggle. */
        BOOLEAN
    }

    /** Property key persisted in {@link ModelEntity#getEncryptedConfig()}. */
    private String name;

    /** Human-friendly label for UIs. */
    private String label;

    private Type type;

    /** Numeric lower bound (inclusive), null for unbounded. */
    private Double min;

    /** Numeric upper bound (inclusive), null for unbounded. */
    private Double max;

    /** Slider step, null defaults to 1 for INT and 0.1 for FLOAT. */
    private Double step;

    /** Decimal places to round to when rendering a FLOAT value. Null = 2. */
    private Integer precision;

    private Object defaultValue;

    /** Placeholder text for input fields. */
    private String placeholder;

    /** One-liner shown under the field to explain the parameter. */
    private String help;

    /** Whether the field must be provided. Defaults to false. */
    private boolean required;

    // ------------------------------------------------------- convenience builders

    public static ModelParameterRule floatRule(
            String name, String label, double min, double max, double defaultValue, String help) {
        return ModelParameterRule.builder()
                .name(name)
                .label(label)
                .type(Type.FLOAT)
                .min(min)
                .max(max)
                .step(0.1)
                .precision(2)
                .defaultValue(defaultValue)
                .help(help)
                .build();
    }

    public static ModelParameterRule intRule(
            String name, String label, int min, int max, int defaultValue, String help) {
        return ModelParameterRule.builder()
                .name(name)
                .label(label)
                .type(Type.INT)
                .min((double) min)
                .max((double) max)
                .step(1.0)
                .defaultValue(defaultValue)
                .help(help)
                .build();
    }
}
