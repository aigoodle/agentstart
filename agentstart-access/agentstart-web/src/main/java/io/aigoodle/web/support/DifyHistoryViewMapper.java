package io.aigoodle.web.support;

import io.aigoodle.agent.entity.AppConversationEntity;
import io.aigoodle.agent.service.AppConversationService;
import io.aigoodle.memory.MemoryItem;
import io.aigoodle.memory.MemoryRole;
import io.aigoodle.web.dto.dify.DifyConversationVO;
import io.aigoodle.web.dto.dify.DifyMessageVO;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnClass(AppConversationService.class)
public final class DifyHistoryViewMapper {
    public DifyConversationVO toConversation(AppConversationEntity conversation) {
        DifyConversationVO view = new DifyConversationVO();
        view.setId(conversation.getId());
        view.setName(conversation.getName());
        view.setInputs(Collections.emptyMap());
        view.setStatus(conversation.getStatus() == null ? "normal" : conversation.getStatus());
        view.setIntroduction(conversation.getIntroduction() == null ? "" : conversation.getIntroduction());
        view.setCreatedAt(epochSecond(conversation.getCreatedAt()));
        view.setUpdatedAt(epochSecond(
                conversation.getUpdatedAt() == null ? conversation.getCreatedAt() : conversation.getUpdatedAt()));
        return view;
    }

    public List<DifyMessageVO> toMessages(String conversationId, List<MemoryItem> rows) {
        List<DifyMessageVO> messages = new ArrayList<>();
        MemoryItem pendingQuestion = null;
        for (MemoryItem row : rows) {
            if (row.role() == MemoryRole.USER) {
                if (pendingQuestion != null) messages.add(toMessage(conversationId, pendingQuestion, null));
                pendingQuestion = row;
            } else if (row.role() == MemoryRole.ASSISTANT) {
                messages.add(toMessage(conversationId, pendingQuestion, row));
                pendingQuestion = null;
            }
        }
        if (pendingQuestion != null) messages.add(toMessage(conversationId, pendingQuestion, null));
        return messages;
    }

    private DifyMessageVO toMessage(String conversationId, MemoryItem question, MemoryItem answer) {
        MemoryItem identity = answer != null ? answer : question;
        DifyMessageVO view = new DifyMessageVO();
        view.setId(identity.id());
        view.setConversationId(conversationId);
        view.setInputs(Collections.emptyMap());
        view.setQuery(question == null ? "" : question.content());
        view.setAnswer(answer == null ? "" : answer.content());
        view.setMessageFiles(Collections.emptyList());
        view.setFeedback(null);
        view.setRetrieverResources(Collections.emptyList());
        view.setCreatedAt(
                identity.createdAt() == null ? 0L : identity.createdAt().getEpochSecond());
        return view;
    }

    private static long epochSecond(LocalDateTime time) {
        return time == null ? 0L : time.toEpochSecond(ZoneOffset.UTC);
    }
}
