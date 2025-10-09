package chat.server;

import chat.common.message.ServerInfo;
import chat.common.utils.Connection;

import java.io.IOException;
import java.net.Socket;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class PeerManager implements AutoCloseable {
    private final Set<Connection> inbound = ConcurrentHashMap.newKeySet();
    private final Map<String, Connection> outboundByServerId = new ConcurrentHashMap<>();

    public PeerManager() {}

    public void registerInbound(Connection c) {
        inbound.add(c);
    }

    public void unregisterInbound(Connection c) {
        inbound.remove(c);
    }

    public void updateOutbound(String selfId, Set<ServerInfo> desired) {
        Set<String> desiredIds = desired.stream().map(ServerInfo::serverId).collect(java.util.stream.Collectors.toSet());
        for (String sid : outboundByServerId.keySet()) {
            if (!desiredIds.contains(sid)) closeQuietly(outboundByServerId.remove(sid));
        }
        for (ServerInfo si : desired) {
            outboundByServerId.compute(si.serverId(), (_, existing) -> {
                if (existing != null && existing.isOpen()) return existing;
                return connect(si);
            });
        }
        outboundByServerId.entrySet().removeIf(e -> e.getValue() == null);
    }

    public void sendToPeers(String line) {
        for (Map.Entry<String, Connection> e : outboundByServerId.entrySet()) {
            Connection c = e.getValue();
            if (c == null) continue;
            try {
                if (!c.isOpen()) throw new IOException("closed");
                c.writeLine(line);
                if (!c.isOpen()) throw new IOException("error");
            } catch (Exception ex) {
                closeQuietly(c);
                outboundByServerId.remove(e.getKey());
            }
        }
    }

    private static Connection connect(ServerInfo si) {
        try {
            String host = normalizeHost(si.host());
            Socket s = new Socket(host, si.port());
            s.setTcpNoDelay(true);
            return Connection.of(s);
        } catch (IOException e) {
            return null;
        }
    }

    private static String normalizeHost(String host) {
        if (host == null) return null;
        String h = host.trim();
        if ("0.0.0.0".equals(h) || "::".equals(h) || "::0".equals(h)) return "127.0.0.1";
        return h;
    }

    private static void closeQuietly(AutoCloseable c) {
        if (c == null) return;
        try { c.close(); } catch (Exception ignored) {}
    }

    @Override
    public void close() {
        for (Connection c : inbound){
            closeQuietly(c);
        }
        inbound.clear();
        for (Connection c : outboundByServerId.values()) {
            closeQuietly(c);
        }
        outboundByServerId.clear();
    }
}
