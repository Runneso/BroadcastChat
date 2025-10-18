package chat.balancer;

import chat.common.message.ServerInfo;

import java.util.Set;

public record ServersConfig(Set<ServerInfo> servers) {}

