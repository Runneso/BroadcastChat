package chat.common.message;

import chat.common.utils.Json;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class ClientMessage extends BaseMessage{
    private static final String MESSAGE_FORMAT = "[%s] %s: %s";

    private final String username;
    private final String content;
    private final long timestamp;

    public ClientMessage(String username, String content, long timestamp) {
        this.username = username;
        this.content = content;
        this.timestamp = timestamp;
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
    public MessageType getType() {
        return MessageType.CLIENT_MESSAGE_TYPE;
    }

    @Override
    public String toJsonString() {
        record Payload(String type, String username, String content, long timestamp) {}
        Payload payload = new Payload(getType().toString(), username, content, timestamp);
        return Json.toJson(payload);
    }

    @Override
    public String toString() {
        Instant instant = Instant.ofEpochMilli(getTimestamp());
        LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        return String.format(MESSAGE_FORMAT, dateTime, getUsername(), getContent());
    }
}
