package chat.common.message;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ChatMessage.class, name = "CHAT_MESSAGE_TYPE"),
        @JsonSubTypes.Type(value = ClientMessage.class, name = "CLIENT_MESSAGE_TYPE"),
        @JsonSubTypes.Type(value = BalancerMessage.class, name = "BALANCER_MESSAGE_TYPE"),
        @JsonSubTypes.Type(value = HealthcheckMessage.class, name = "HEALTHCHECK_MESSAGE_TYPE")
})
public abstract class BaseMessage {
    public abstract MessageType getType();
    public abstract String toJsonString();
}
