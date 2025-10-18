package chat.common.message;

import chat.common.utils.Json;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

public class ChatMessage extends BaseMessage {
    private static final String MESSAGE_FORMAT = "[%s] %s: %s";

    private final UUID messageId;
    private final String serverId;
    private final String username;
    private final String content;
    private final long timestamp;

    @JsonCreator
    public ChatMessage(
            @JsonProperty("messageId") UUID messageId,
            @JsonProperty("serverId") String serverId,
            @JsonProperty("username") String username,
            @JsonProperty("content") String content,
            @JsonProperty("timestamp") long timestamp
    ) {
        this.messageId = messageId;
        this.serverId = serverId;
        this.username = username;
        this.content = content;
        this.timestamp = timestamp;
    }

    @Override
    public MessageType getType() {
        return MessageType.CHAT_MESSAGE_TYPE;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public String getUsername() {
        return username;
    }

    public String getContent() {
        return content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toJsonString() {
        record Payload(String type, String messageId, String serverId, String username, String content,
                       long timestamp) {
        }
        Payload payload = new Payload(getType().toString(), messageId.toString(), serverId, username, content, timestamp);
        return Json.toJson(payload);
    }

    @Override
    public String toString() {
        Instant instant = Instant.ofEpochMilli(getTimestamp());
        LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        return String.format(MESSAGE_FORMAT, dateTime, getUsername(), getContent());
    }


}
