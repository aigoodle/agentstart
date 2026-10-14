package io.aigoodle.knowledge.chunk.template;

import io.aigoodle.knowledge.chunk.Chunk;
import io.aigoodle.knowledge.chunk.Chunker;
import io.aigoodle.knowledge.config.ProcessRule;
import io.aigoodle.knowledge.enums.ChunkingTemplate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects question/answer pairs (English and Chinese markers) and emits one chunk
 * per pair, with the question kept in metadata so it can be weighted at query time.
 * Falls back to {@link NaiveChunker} when no Q/A markers are present.
 */
public class QaChunker implements Chunker {

    private static final Pattern QUESTION_MARKER = Pattern.compile(
            "^\\s*(?:Q\\d*|Question|问|问题)\\s*[:：]\\s*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern ANSWER_MARKER =
            Pattern.compile("^\\s*(?:A|Answer|答|答案)\\s*[:：]\\s*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final NaiveChunker fallback = new NaiveChunker();

    @Override
    public ChunkingTemplate template() {
        return ChunkingTemplate.QA;
    }

    @Override
    public List<Chunk> chunk(String text, ProcessRule rule, Map<String, Object> baseMetadata) {
        String[] lines = text.split("\n");
        List<Chunk> chunks = new ArrayList<>();
        StringBuilder question = new StringBuilder();
        StringBuilder answer = new StringBuilder();
        boolean inAnswer = false;
        int nextPosition = 0;
        boolean questionMarkerFound = false;

        for (String line : lines) {
            Matcher questionMarker = QUESTION_MARKER.matcher(line);
            Matcher answerMarker = ANSWER_MARKER.matcher(line);
            if (questionMarker.find()) {
                nextPosition = appendQuestionAnswerPair(chunks, question, answer, baseMetadata, nextPosition);
                question.setLength(0);
                answer.setLength(0);
                question.append(line.substring(questionMarker.end()).strip());
                inAnswer = false;
                questionMarkerFound = true;
            } else if (answerMarker.find()) {
                answer.append(line.substring(answerMarker.end()).strip());
                inAnswer = true;
            } else if (!line.isBlank()) {
                StringBuilder activePart = inAnswer ? answer : question;
                if (activePart.length() > 0) {
                    activePart.append(' ');
                }
                activePart.append(line.strip());
            }
        }
        appendQuestionAnswerPair(chunks, question, answer, baseMetadata, nextPosition);

        if (!questionMarkerFound) {
            return fallback.chunk(text, rule, baseMetadata);
        }
        return chunks;
    }

    private int appendQuestionAnswerPair(
            List<Chunk> chunks,
            StringBuilder questionBuffer,
            StringBuilder answerBuffer,
            Map<String, Object> baseMetadata,
            int position) {
        String question = questionBuffer.toString().strip();
        String answer = answerBuffer.toString().strip();
        if (question.isEmpty() && answer.isEmpty()) {
            return position;
        }
        Map<String, Object> metadata = new HashMap<>(baseMetadata);
        if (!question.isEmpty()) {
            metadata.put("question", question);
        }
        chunks.add(new Chunk(formatPair(question, answer), position, metadata));
        return position + 1;
    }

    private static String formatPair(String question, String answer) {
        if (question.isEmpty()) {
            return answer;
        }
        return answer.isEmpty() ? question : "Q: " + question + "\nA: " + answer;
    }
}
