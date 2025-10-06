package chat.common.message;

import chat.common.utils.Json;

import java.time.Instant;

public class HealthcheckMessage extends BaseMessage {
    private final String serverId;
    private final long timestamp;

    public HealthcheckMessage(String serverId, long timestamp) {
        this.serverId = serverId;
        this.timestamp = timestamp;
    }

    @Override
    public MessageType getType() {
        return MessageType.HEALTHCHECK_MESSAGE_TYPE;
    }

    @Override
    public String toJsonString() {
        record Payload(String type, String serverId, long timestamp) {
        }
        Payload payload = new Payload(getType().toString(), serverId, timestamp);
        return Json.toJson(payload);
    }
}
