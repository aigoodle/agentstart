package io.aigoodle.knowledge.rerank;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the deliberately small {@code index: score} model response protocol. */
final class ModelRelevanceScoreParser {

    private static final Pattern SCORE_LINE = Pattern.compile("\\[?(\\d+)]?\\s*[:=\\-)]\\s*([0-9]*\\.?[0-9]+)");

    private ModelRelevanceScoreParser() {}

    static ModelRelevanceScores parse(String response, int candidateCount) {
        ModelRelevanceScores scores = new ModelRelevanceScores(candidateCount);
        if (response == null) {
            return scores;
        }

        Matcher scoreLines = SCORE_LINE.matcher(response);
        while (scoreLines.find()) {
            recordScore(scoreLines, scores);
        }
        return scores;
    }

    private static void recordScore(Matcher scoreLine, ModelRelevanceScores scores) {
        try {
            int candidateIndex = Integer.parseInt(scoreLine.group(1));
            double relevanceScore = Double.parseDouble(scoreLine.group(2));
            scores.record(candidateIndex, relevanceScore);
        } catch (NumberFormatException ignored) {
            // Ignore one malformed line while retaining every score parsed successfully.
        }
    }
}
