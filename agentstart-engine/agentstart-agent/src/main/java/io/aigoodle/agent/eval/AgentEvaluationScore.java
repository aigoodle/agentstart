package io.aigoodle.agent.eval;

/** Normalized evaluator outcome; score is clamped to [0,1]. */
public record AgentEvaluationScore(String evaluator, double score, String message) {
    public AgentEvaluationScore {
        evaluator = evaluator == null || evaluator.isBlank() ? "unnamed" : evaluator;
        score = Math.max(0, Math.min(1, score));
        message = message == null ? "" : message;
    }

    public boolean passed(double threshold) {
        return score >= threshold;
    }
}
