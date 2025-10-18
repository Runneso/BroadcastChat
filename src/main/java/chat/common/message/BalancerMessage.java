package chat.common.message;

import chat.common.utils.Json;

import java.util.List;

public class BalancerMessage extends BaseMessage{
    private final List<ServerInfo> servers;

    public BalancerMessage(List<ServerInfo> servers) {
        this.servers = servers;
    }

    @Override
    public MessageType getType() {
        return MessageType.BALANCER_MESSAGE_TYPE ;
    }

    @Override
    public String toJsonString() {
        record Payload(String type, List<ServerInfo> servers) {}
        Payload payload = new Payload(getType().toString(), servers);
        return Json.toJson(payload);
    }


}
