package io.aigoodle.web.dto.dify;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Request for explicitly naming a conversation or generating a name from its history. */
public record DifyConversationNameRequest(String name, @JsonProperty("auto_generate") Boolean autoGenerate) {

    public String requestedName() {
        if (name == null || name.isBlank()) {
            return null;
        }
        return name.strip();
    }

    public boolean requestsAutomaticName() {
        return Boolean.TRUE.equals(autoGenerate);
    }
}
