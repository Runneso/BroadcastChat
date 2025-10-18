package chat.balancer;

import chat.common.exceptions.NoALiveServers;
import chat.common.message.BalancerMessage;
import chat.common.message.ServerInfo;
import chat.common.utils.Connection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class Balancer implements AutoCloseable {
    private static final int CLIENT_BACKLOG = 128;
    private static final int CONNECT_TIMEOUT_MS = 1000;
    private static final int HEALTHCHECK_PERIOD_SEC = 5;
    private static final int RELOAD_PERIOD_SEC = 30;
    private static final int PIPE_BUFFER_SIZE = 8192;

    private final Path configPath;
    private final ServerSocket server;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "balancer-scheduler");
        t.setDaemon(true);
        return t;
    });

    private final AtomicReference<List<InetSocketAddress>> aliveServers = new AtomicReference<>(List.of());
    private final PickStrategy strategy = new RoundRobinStrategy(aliveServers);

    private final Set<InetSocketAddress> desiredServers = ConcurrentHashMap.newKeySet();
    private final Map<InetSocketAddress, ServerInfo> infoByAddr = new ConcurrentHashMap<>();

    private final Map<InetSocketAddress, CopyOnWriteArraySet<Socket>> upstreamsByServer = new ConcurrentHashMap<>();
    private final Map<Socket, Socket> clientToUpstream = new ConcurrentHashMap<>();
    private final Map<Socket, Socket> upstreamToClient = new ConcurrentHashMap<>();

    public Balancer(Path configPath, int port) throws IOException {
        this.configPath = configPath;
        this.server = new ServerSocket();
        this.server.setReuseAddress(true);
        this.server.bind(new InetSocketAddress(port), CLIENT_BACKLOG);
    }

    public void start() {
        reloadConfig();
        runHealthcheck();
        sendServerListUpdate();

        scheduler.scheduleAtFixedRate(this::runHealthcheckSafe, 0, HEALTHCHECK_PERIOD_SEC, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::reloadConfigSafe, RELOAD_PERIOD_SEC, RELOAD_PERIOD_SEC, TimeUnit.SECONDS);

        System.out.println("Balancer is listening on " + server.getLocalSocketAddress());
        while (running.get()) {
            try {
                Socket client = server.accept();
                client.setTcpNoDelay(true);
                handleClient(client);
            } catch (IOException e) {
                if (running.get()) {
                    System.err.println("Accept error: " + e.getMessage());
                }
            }
        }
    }

    private void handleClient(Socket client) {
        try{
            InetSocketAddress target = strategy.pick();
            CompletableFuture.runAsync(() -> proxy(client, target));
        }catch (NoALiveServers e){
            System.err.println("No alive servers for new client " + client.getRemoteSocketAddress());
            closeQuietly(client);
        }
    }

    private void proxy(Socket client, InetSocketAddress serverAddr) {
        Socket upstream = null;
        try {
            upstream = new Socket();
            upstream.setTcpNoDelay(true);
            upstream.connect(serverAddr, CONNECT_TIMEOUT_MS);

            upstreamsByServer.computeIfAbsent(serverAddr, _ -> new CopyOnWriteArraySet<>()).add(upstream);
            clientToUpstream.put(client, upstream);
            upstreamToClient.put(upstream, client);

            Socket finalUpstream = upstream;
            Thread t1 = new Thread(() -> pipe(client, finalUpstream));
            Thread t2 = new Thread(() -> pipe(finalUpstream, client));
            t1.setDaemon(true);
            t2.setDaemon(true);
            t1.start();
            t2.start();

            t1.join();
            t2.join();
        } catch (Exception e) {
            System.err.println("Proxy error to " + serverAddr + ": " + e.getMessage());
        } finally {
            if (upstream != null) {
                CopyOnWriteArraySet<Socket> set = upstreamsByServer.get(serverAddr);
                if (set != null) set.remove(upstream);
                Socket peer = upstreamToClient.remove(upstream);
                if (peer != null) clientToUpstream.remove(peer);
            }
            closeQuietly(upstream);
            closeQuietly(client);
        }
    }

    private static void pipe(Socket inSock, Socket outSock) {
        try (InputStream in = inSock.getInputStream(); OutputStream out = outSock.getOutputStream()) {
            byte[] buf = new byte[PIPE_BUFFER_SIZE];
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
                out.flush();
            }
        } catch (IOException ignored) {
        }
    }

    private void runHealthcheckSafe() {
        try {
            runHealthcheck();
            sendServerListUpdate();
        } catch (Exception e) {
            System.err.println("Healthcheck error: " + e.getMessage());
        }
    }

    private void reloadConfigSafe() {
        try {
            reloadConfig();
            sendServerListUpdate();
        } catch (Exception e) {
            System.err.println("Config reload error: " + e.getMessage());
        }
    }

    private void runHealthcheck() {
        List<InetSocketAddress> alive = new ArrayList<>();
        for (InetSocketAddress addr : new HashSet<>(desiredServers)) {
            if (isAlive(addr)) alive.add(addr);
        }
        aliveServers.set(List.copyOf(alive));
    }

    private boolean isAlive(InetSocketAddress addr) {
        try (Socket s = new Socket()) {
            s.connect(addr, CONNECT_TIMEOUT_MS);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private void reloadConfig() {
        try {
            ConfigReader reader = new ConfigReader(configPath);
            Set<ServerInfo> servers = reader.run();
            Set<InetSocketAddress> next = new HashSet<>();
            Map<InetSocketAddress, ServerInfo> map = new HashMap<>();
            for (ServerInfo si : servers) {
                String dialHost = normalizeHost(si.host());
                InetSocketAddress a = new InetSocketAddress(dialHost, si.port());
                next.add(a);
                map.put(a, si);
            }

            Set<InetSocketAddress> prevDesired = new HashSet<>(desiredServers);
            Set<InetSocketAddress> removed = new HashSet<>(prevDesired);
            removed.removeAll(next);
            Set<InetSocketAddress> added = new HashSet<>(next);
            added.removeAll(prevDesired);

            desiredServers.clear();
            desiredServers.addAll(next);
            infoByAddr.clear();
            infoByAddr.putAll(map);

            List<InetSocketAddress> currentAlive = aliveServers.get();
            List<InetSocketAddress> updatedAlive = new ArrayList<>();
            for (InetSocketAddress a : currentAlive) {
                if (next.contains(a)) updatedAlive.add(a);
            }
            for (InetSocketAddress a : added) {
                if (isAlive(a)) updatedAlive.add(a);
            }
            aliveServers.set(List.copyOf(updatedAlive));

            for (InetSocketAddress addr : removed) {
                CopyOnWriteArraySet<Socket> set = upstreamsByServer.remove(addr);
                if (set == null) continue;
                for (Socket up : set) {
                    Socket peer = upstreamToClient.remove(up);
                    if (peer != null) clientToUpstream.remove(peer);
                    closeQuietly(up);
                    closeQuietly(peer);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read config: " + e.getMessage(), e);
        }
    }

    private void sendServerListUpdate() {
        List<InetSocketAddress> alive = aliveServers.get();
        if (alive.isEmpty()) return;
        List<ServerInfo> list = alive.stream()
                .map(infoByAddr::get)
                .filter(s -> s != null)
                .map(s -> new ServerInfo(s.serverId(), normalizeHost(s.host()), s.port()))
                .collect(Collectors.toList());
        if (list.isEmpty()) return;
        BalancerMessage bm = new BalancerMessage(list);
        String json = bm.toJsonString();
        for (InetSocketAddress addr : new HashSet<>(desiredServers)) {
            try (Socket s = new Socket()) {
                s.setTcpNoDelay(true);
                s.connect(addr, CONNECT_TIMEOUT_MS);
                try (Connection c = Connection.of(s)) {
                    c.writeLine(json);
                }
            } catch (IOException ignored) {
            }
        }
    }

    private static String normalizeHost(String host) {
        if (host == null) return null;
        String h = host.trim();
        if ("0.0.0.0".equals(h) || "::".equals(h) || "::0".equals(h)) return "127.0.0.1";
        return h;
    }

    private static void closeQuietly(Socket s) {
        if (s == null) return;
        try { s.close(); } catch (IOException ignored) {}
    }

    @Override
    public void close() {
        if (!running.getAndSet(false)) return;
        try { server.close(); } catch (IOException ignored) {}
        scheduler.shutdownNow();
        for (CopyOnWriteArraySet<Socket> set : upstreamsByServer.values()) {
            for (Socket up : set) {
                Socket peer = upstreamToClient.remove(up);
                if (peer != null) clientToUpstream.remove(peer);
                closeQuietly(up);
                closeQuietly(peer);
            }
        }
        upstreamsByServer.clear();
        System.out.println("Balancer is stopped.");
    }
}
