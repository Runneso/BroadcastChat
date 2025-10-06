package chat.common.message;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ChatMessage.class, name = "CHAT_MESSAGE_TYPE")
})
public abstract class BaseMessage {
    public abstract MessageType getType();
    public abstract String toJsonString();
}
