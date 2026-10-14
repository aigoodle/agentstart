package io.aigoodle.web.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.aigoodle.agent.entity.AppConversationEntity;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.memory.MemoryItem;
import io.aigoodle.memory.MemoryRole;
import io.aigoodle.memory.MemoryTier;
import io.aigoodle.web.dto.dify.DifyConversationVO;
import io.aigoodle.web.dto.dify.DifyMessageVO;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DifyHistoryViewMapperTest {

    private final DifyHistoryViewMapper mapper = new DifyHistoryViewMapper();

    @Test
    void combinesUserAndAssistantRowsIntoOneMessage() {
        MemoryItem question = message("question-1", MemoryRole.USER, "How are you?", 1);
        MemoryItem answer = message("answer-1", MemoryRole.ASSISTANT, "Great", 2);

        List<DifyMessageVO> messages = mapper.toMessages("conversation-1", List.of(question, answer));

        assertThat(messages).singleElement().satisfies(message -> {
            assertThat(message.getId()).isEqualTo("answer-1");
            assertThat(message.getConversationId()).isEqualTo("conversation-1");
            assertThat(message.getQuery()).isEqualTo("How are you?");
            assertThat(message.getAnswer()).isEqualTo("Great");
        });
    }

    @Test
    void preservesUnpairedQuestionsAndAnswers() {
        MemoryItem firstQuestion = message("question-1", MemoryRole.USER, "First", 1);
        MemoryItem secondQuestion = message("question-2", MemoryRole.USER, "Second", 2);
        MemoryItem answer = message("answer-2", MemoryRole.ASSISTANT, "Reply", 3);
        MemoryItem orphanAnswer = message("answer-3", MemoryRole.ASSISTANT, "Opening", 4);

        List<DifyMessageVO> messages =
                mapper.toMessages("conversation-1", List.of(firstQuestion, secondQuestion, answer, orphanAnswer));

        assertThat(messages).extracting(DifyMessageVO::getId).containsExactly("question-1", "answer-2", "answer-3");
        assertThat(messages.get(0).getAnswer()).isEmpty();
        assertThat(messages.get(2).getQuery()).isEmpty();
    }

    @Test
    void mapsConversationDefaultsAndTimestamps() {
        AppConversationEntity conversation = new AppConversationEntity();
        conversation.setId("conversation-1");
        conversation.setName("Readable title");
        conversation.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));

        DifyConversationVO view = mapper.toConversation(conversation);

        assertThat(view.getStatus()).isEqualTo("normal");
        assertThat(view.getIntroduction()).isEmpty();
        assertThat(view.getUpdatedAt()).isEqualTo(view.getCreatedAt());
    }

    @Test
    void resolvesAppIdByDocumentedPrecedence() {
        assertThat(DifyAppIdResolver.resolve("query-app", "header-app", "Bearer token-app"))
                .isEqualTo("query-app");
        assertThat(DifyAppIdResolver.resolve(null, " header-app ", "Bearer token-app"))
                .isEqualTo("header-app");
        assertThat(DifyAppIdResolver.resolve(null, null, "Bearer token-app")).isEqualTo("token-app");
        assertThatThrownBy(() -> DifyAppIdResolver.resolve(null, " ", null)).isInstanceOf(PlatformException.class);
    }

    private static MemoryItem message(String id, MemoryRole role, String content, long sequence) {
        return new MemoryItem(
                id,
                "default",
                "app-1",
                "conversation-1",
                MemoryTier.SHORT_TERM,
                role,
                content,
                .5,
                LocalDateTime.of(2026, 1, 1, 0, Math.toIntExact(sequence)).toInstant(ZoneOffset.UTC),
                null,
                0,
                Map.of());
    }
}
