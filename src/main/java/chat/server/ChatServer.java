package chat.server;

import chat.common.dedupstore.DeduplicationStore;
import chat.common.message.*;
import chat.common.utils.Config;
import chat.common.utils.Connection;
import chat.common.utils.Json;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class ChatServer implements AutoCloseable {
    private static final int CLIENT_BACKLOG = 256;
    private static final int TTL_MINUTES = 5;

    private final String serverId;
    private final String host;
    private final int port;

    private final ServerSocket serverSocket;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private final Map<Connection, Boolean> clientConnections = new ConcurrentHashMap<>();

    private final DeduplicationStore dedup = new DeduplicationStore(TTL_MINUTES, TimeUnit.MINUTES);
    private final ExecutorService acceptPool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "server-accept");
        t.setDaemon(true);
        return t;
    });
    private final ExecutorService ioPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "server-io");
        t.setDaemon(true);
        return t;
    });

    private final PeerManager peerManager;

    public ChatServer(String serverId, String host, int port) throws IOException {
        this.serverId = Objects.requireNonNull(serverId);
        this.host = Objects.requireNonNull(host);
        this.port = port;
        this.serverSocket = new ServerSocket();
        this.serverSocket.setReuseAddress(true);
        this.serverSocket.bind(new InetSocketAddress(host, port), CLIENT_BACKLOG);
        this.peerManager = new PeerManager();
    }

    public void start() {
        acceptPool.submit(this::acceptLoop);
        System.out.println("Server " + serverId + " is listening on " + host + ":" + port);
        Object lock = new Object();
        synchronized (lock) {
            try { lock.wait(); } catch (InterruptedException ignored) {}
        }
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket s = serverSocket.accept();
                s.setTcpNoDelay(true);
                Connection c = Connection.of(s);
                ioPool.submit(() -> handleConnection(c));
            } catch (IOException e) {
                if (running.get()) {
                    System.err.println("Accept error: " + e.getMessage());
                }
            }
        }
    }

    private void handleConnection(Connection conn) {
        try {
            String first = conn.readLine();
            if (first == null) {
                closeQuietly(conn);
                return;
            }
            MessageEnvelope env;
            try { env = JsonEnvelope.parse(first); }
            catch (Exception ex) { env = new MessageEnvelope(""); }

            String t = env.type();
            if ("CLIENT_MESSAGE_TYPE".equals(t) || "CLIENT_MESSAGE".equals(t)) {
                handleClientStream(conn, first);
            } else if ("CHAT_MESSAGE_TYPE".equals(t) || "CHAT_MESSAGE".equals(t)) {
                handlePeerStream(conn, first);
            } else if ("BALANCER_MESSAGE_TYPE".equals(t) || "SERVER_LIST_UPDATE".equals(t)) {
                handleBalancerLine(first);
                closeQuietly(conn);
            } else {
                try {
                    ServerListPayload sl = chat.common.utils.Json.fromJson(first, ServerListPayload.class);
                    if (sl != null && sl.servers != null) {
                        handleBalancerLine(first);
                        closeQuietly(conn);
                        return;
                    }
                } catch (Exception ignored) {}
                handleClientStream(conn, first);
            }
        } catch (IOException e) {
            closeQuietly(conn);
        }
    }

    private void handleClientStream(Connection conn, String alreadyRead) {
        clientConnections.put(conn, Boolean.TRUE);
        try {
            if (alreadyRead != null) processClientLine(alreadyRead);
            String line;
            while ((line = conn.readLine()) != null) {
                processClientLine(line);
            }
        } catch (Exception ignored) {
        } finally {
            clientConnections.remove(conn);
            closeQuietly(conn);
        }
    }

    private void processClientLine(String line) {
        try {
            MessageEnvelope env = JsonEnvelope.parse(line);
            String t = env.type();
            if (!"CLIENT_MESSAGE_TYPE".equals(t) && !"CLIENT_MESSAGE".equals(t)) return;
            ClientPayload p = Json.fromJson(line, ClientPayload.class);
            if (p == null || p.username == null || p.content == null) return;
            String key = p.username + "|" + p.timestamp + "|" + p.content;
            UUID id = UUID.nameUUIDFromBytes(key.getBytes(Config.CHARSET));
            ChatMessage chat = new ChatMessage(id, serverId, p.username, p.content, p.timestamp);
            dedup.isDuplicate(chat.getMessageId().toString());
            broadcastToLocal(chat);
            peerManager.sendToPeers(chat.toJsonString());
        } catch (Exception e) {
            System.err.println("Client line error: " + e.getMessage());
        }
    }

    private void handlePeerStream(Connection conn, String alreadyRead) {
        peerManager.registerInbound(conn);
        try {
            if (alreadyRead != null) handleInboundPeerLine(alreadyRead);
            String line;
            while ((line = conn.readLine()) != null) {
                handleInboundPeerLine(line);
            }
        } catch (Exception ignored) {
        } finally {
            onPeerClosed(conn);
            closeQuietly(conn);
        }
    }

    private void handleInboundPeerLine(String line) {
        try {
            MessageEnvelope env = JsonEnvelope.parse(line);
            String t = env.type();
            if ("CHAT_MESSAGE_TYPE".equals(t) || "CHAT_MESSAGE".equals(t)) {
                BaseMessage base = chat.common.utils.Json.fromJson(line, BaseMessage.class);
                if (base instanceof ChatMessage msg) {
                    if (!dedup.isDuplicate(msg.getMessageId().toString())) {
                        broadcastToLocal(msg);
                    }
                }
            } else if ("BALANCER_MESSAGE_TYPE".equals(t) || "SERVER_LIST_UPDATE".equals(t)) {
                handleBalancerLine(line);
            }
        } catch (Exception e) {
            System.err.println("Peer line error: " + e.getMessage());
        }
    }

    private void handleBalancerLine(String line) {
        try {
            ServerListPayload payload = chat.common.utils.Json.fromJson(line, ServerListPayload.class);
            if (payload == null || payload.servers == null) return;
            List<ServerInfo> all = payload.servers;
            Set<ServerInfo> desired = all.stream()
                    .filter(si -> !serverId.equals(si.serverId()))
                    .filter(si -> !(host.equals(si.host()) && port == si.port()))
                    .collect(Collectors.toSet());
            peerManager.updateOutbound(serverId, desired);
        } catch (Exception e) {
            System.err.println("Balancer message error: " + e.getMessage());
        }
    }

    private void broadcastToLocal(ChatMessage msg) {
        String json = msg.toJsonString();
        for (Connection c : clientConnections.keySet()) {
            try {
                c.writeLine(json);
            } catch (Exception ignored) {
            }
        }
    }

    private void onPeerClosed(Connection c) {
        peerManager.unregisterInbound(c);
    }

    private static void closeQuietly(AutoCloseable c) {
        if (c == null) return;
        try { c.close(); } catch (Exception ignored) {}
    }

    @Override
    public void close() {
        if (!running.getAndSet(false)) return;
        closeQuietly(serverSocket);
        acceptPool.shutdownNow();
        ioPool.shutdownNow();
        for (Connection c : clientConnections.keySet()) closeQuietly(c);
        clientConnections.clear();
        peerManager.close();
        dedup.shutdown();
        System.out.println("Server is stopped.");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MessageEnvelope(String type) {}

    private static final class JsonEnvelope {
        static MessageEnvelope parse(String line) {
            return chat.common.utils.Json.fromJson(line, MessageEnvelope.class);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class ServerListPayload {
        public String type;
        public List<ServerInfo> servers;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class ClientPayload {
        public String type;
        public String username;
        public String content;
        public long timestamp;
    }
}
