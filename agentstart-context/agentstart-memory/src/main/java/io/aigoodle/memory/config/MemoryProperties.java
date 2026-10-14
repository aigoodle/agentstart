package io.aigoodle.memory.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("spring-agent.memory")
public class MemoryProperties {
    private int workingCapacity = 32;
    private Duration shortTermTtl = Duration.ofDays(7);
    private double longTermThreshold = 0.8;
    private double recencyWeight = 0.35;
    private double relevanceWeight = 0.45;
    private double importanceWeight = 0.20;
    private double accessWeight = 0.10;
    private boolean extractionEnabled = true;

    public int getWorkingCapacity() {
        return workingCapacity;
    }

    public void setWorkingCapacity(int value) {
        workingCapacity = value;
    }

    public Duration getShortTermTtl() {
        return shortTermTtl;
    }

    public void setShortTermTtl(Duration value) {
        shortTermTtl = value;
    }

    public double getLongTermThreshold() {
        return longTermThreshold;
    }

    public void setLongTermThreshold(double value) {
        longTermThreshold = value;
    }

    public double getRecencyWeight() {
        return recencyWeight;
    }

    public void setRecencyWeight(double value) {
        recencyWeight = value;
    }

    public double getRelevanceWeight() {
        return relevanceWeight;
    }

    public void setRelevanceWeight(double value) {
        relevanceWeight = value;
    }

    public double getImportanceWeight() {
        return importanceWeight;
    }

    public void setImportanceWeight(double value) {
        importanceWeight = value;
    }

    public double getAccessWeight() {
        return accessWeight;
    }

    public void setAccessWeight(double value) {
        accessWeight = value;
    }

    public boolean isExtractionEnabled() {
        return extractionEnabled;
    }

    public void setExtractionEnabled(boolean value) {
        extractionEnabled = value;
    }
}
