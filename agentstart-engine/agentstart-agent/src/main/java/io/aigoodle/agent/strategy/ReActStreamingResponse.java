package io.aigoodle.agent.strategy;

import org.springframework.ai.chat.client.ChatClient;

/**
 * Executes one ReAct model request and exposes only user-facing answer tokens.
 * Internal Thought/Action protocol messages remain buffered because they belong
 * to the agent trace rather than the chat response.
 */
final class ReActStreamingResponse {

    private static final int CLASSIFICATION_LIMIT = 160;
    private static final String FINAL_ANSWER_MARKER = "Final Answer:";
    private static final String ACTION_MARKER = "Action:";
    private static final String THOUGHT_MARKER = "Thought:";
    private static final String[] PROTOCOL_MARKERS = {THOUGHT_MARKER, ACTION_MARKER, FINAL_ANSWER_MARKER};

    private ReActStreamingResponse() {}

    static String generate(AgentRunContext context, ChatClient.ChatClientRequestSpec request, boolean hideThought) {
        if (!context.isTokenStreamingEnabled()) {
            String content = request.call().content();
            return content == null ? "" : content;
        }

        StreamingState state = new StreamingState();
        request.stream()
                .content()
                .doOnNext(delta -> accept(delta, state, context, hideThought))
                .blockLast();

        flushUnclassifiedAnswer(state, context, hideThought);
        return state.content.toString();
    }

    static String stripThoughtPreamble(String content) {
        if (content == null) {
            return "";
        }
        int answerStart = findAnswerStartAfterThought(content, 0);
        return answerStart == 0 ? content : content.substring(answerStart);
    }

    private static void accept(String delta, StreamingState state, AgentRunContext context, boolean hideThought) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        state.content.append(delta);
        if (state.mode == ResponseMode.INTERNAL) {
            return;
        }
        if (state.mode == ResponseMode.UNKNOWN) {
            state.mode = classify(state, hideThought);
        }
        if (state.mode == ResponseMode.ANSWER) {
            emitNewContent(state, context);
        }
    }

    private static ResponseMode classify(StreamingState state, boolean hideThought) {
        String content = state.content.toString();
        int finalAnswerIndex = indexOfIgnoreCase(content, FINAL_ANSWER_MARKER);
        int actionIndex = indexOfIgnoreCase(content, ACTION_MARKER);

        if (finalAnswerIndex >= 0 && (actionIndex < 0 || finalAnswerIndex < actionIndex)) {
            state.emittedCharacters = skipWhitespace(content, finalAnswerIndex + FINAL_ANSWER_MARKER.length());
            return ResponseMode.ANSWER;
        }
        if (actionIndex >= 0) {
            return ResponseMode.INTERNAL;
        }
        if (!couldBeProtocolMarker(content)) {
            return ResponseMode.ANSWER;
        }
        if (content.length() >= CLASSIFICATION_LIMIT) {
            if (hideThought) {
                state.emittedCharacters = findAnswerStartAfterThought(content, state.emittedCharacters);
            }
            return ResponseMode.ANSWER;
        }
        return ResponseMode.UNKNOWN;
    }

    private static void emitNewContent(StreamingState state, AgentRunContext context) {
        int contentLength = state.content.length();
        if (state.emittedCharacters >= contentLength) {
            return;
        }
        context.publishToken(state.content.substring(state.emittedCharacters, contentLength));
        state.emittedCharacters = contentLength;
    }

    private static void flushUnclassifiedAnswer(StreamingState state, AgentRunContext context, boolean hideThought) {
        String content = state.content.toString();
        if (state.mode != ResponseMode.UNKNOWN || content.isBlank()) {
            return;
        }
        int answerStart = state.emittedCharacters;
        if (hideThought) {
            answerStart = findAnswerStartAfterThought(content, answerStart);
        }
        if (answerStart < content.length() && !containsAction(content)) {
            context.publishToken(content.substring(answerStart));
        }
    }

    private static boolean couldBeProtocolMarker(String content) {
        int firstContentCharacter = skipWhitespace(content, 0);
        if (firstContentCharacter >= content.length()) {
            return true;
        }
        String leadingText = content.substring(firstContentCharacter);
        for (String marker : PROTOCOL_MARKERS) {
            int comparableLength = Math.min(leadingText.length(), marker.length());
            if (leadingText.regionMatches(true, 0, marker, 0, comparableLength)) {
                return true;
            }
        }
        return false;
    }

    private static int findAnswerStartAfterThought(String content, int offset) {
        int thoughtStart = skipWhitespace(content, offset);
        if (!content.regionMatches(true, thoughtStart, THOUGHT_MARKER, 0, THOUGHT_MARKER.length())) {
            return offset;
        }

        int afterMarker = thoughtStart + THOUGHT_MARKER.length();
        int blankLine = content.indexOf("\n\n", afterMarker);
        if (blankLine >= 0) {
            return skipWhitespace(content, blankLine + 2);
        }
        int nextLine = content.indexOf('\n', afterMarker);
        if (nextLine >= 0) {
            return skipWhitespace(content, nextLine + 1);
        }
        return skipWhitespace(content, afterMarker);
    }

    private static int skipWhitespace(String content, int start) {
        int index = start;
        while (index < content.length() && Character.isWhitespace(content.charAt(index))) {
            index++;
        }
        return index;
    }

    private static boolean containsAction(String content) {
        return indexOfIgnoreCase(content, ACTION_MARKER) >= 0;
    }

    private static int indexOfIgnoreCase(String text, String searchText) {
        if (searchText.isEmpty() || text.length() < searchText.length()) {
            return -1;
        }
        int lastStart = text.length() - searchText.length();
        for (int index = 0; index <= lastStart; index++) {
            if (text.regionMatches(true, index, searchText, 0, searchText.length())) {
                return index;
            }
        }
        return -1;
    }

    private enum ResponseMode {
        UNKNOWN,
        ANSWER,
        INTERNAL
    }

    private static final class StreamingState {

        private final StringBuilder content = new StringBuilder();
        private ResponseMode mode = ResponseMode.UNKNOWN;
        private int emittedCharacters;
    }
}
