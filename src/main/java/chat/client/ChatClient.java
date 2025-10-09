package chat.client;

import chat.common.message.ClientMessage;
import chat.common.utils.Config;
import chat.common.utils.Connection;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class ChatClient implements AutoCloseable{
    private final static String QUIT_COMMAND = "/quit";
    private final static String CHAT_PRODUCER_THREAD_NAME = "chat-producer";
    private final static String CHAT_CONSUMER_THREAD_NAME = "chat-consumer";
    private final static int POOLING_INTERVAL_MS = 50;
    private final static int POOLING_INITIAL_DELAY_MS = 0;

    private final UserInfo userInfo;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final BlockingDeque<ClientMessage> queue = new LinkedBlockingDeque<>();

    private final ExecutorService producer = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, CHAT_PRODUCER_THREAD_NAME);
        thread.setDaemon(true);
        return thread;
    });
    private final ScheduledExecutorService consumer = Executors.newScheduledThreadPool(1, r -> {
        Thread thread = new Thread(r, CHAT_CONSUMER_THREAD_NAME);
        thread.setDaemon(true);
        return thread;
    });

    private volatile Connection connection;

    public ChatClient(UserInfo userInfo) {
        this.userInfo = userInfo;
    }

    public void start(Connection connection, InputStream consoleIn, OutputStream consoleOut) {
        this.connection = connection;

        final AtomicReference<BufferedReader> inRef = new AtomicReference<>();
        final AtomicReference<PrintWriter> outRef = new AtomicReference<>();

        inRef.set(connection.getReader());
        outRef.set(connection.getWriter());
        Supplier<BufferedReader> inSupplier = inRef::get;
        Supplier<PrintWriter> outSupplier = outRef::get;

        InetSocketAddress remote = (InetSocketAddress) this.connection.socket().getRemoteSocketAddress();
        Supplier<Boolean> reconnectSupplier = () -> reconnect(remote, inRef, outRef);

        PrintWriter consoleWriter = new PrintWriter(new OutputStreamWriter(consoleOut, Config.CHARSET),true);
        ChatSender sender = new ChatSender(queue, outSupplier, running, reconnectSupplier);
        ChatReceiver receiver = new ChatReceiver(inSupplier, consoleWriter, running);

        producer.submit(sender::run);
        consumer.scheduleWithFixedDelay(receiver::run, POOLING_INITIAL_DELAY_MS, POOLING_INTERVAL_MS, TimeUnit.MILLISECONDS);

        try (BufferedReader consoleReader = new BufferedReader(new InputStreamReader(consoleIn, Config.CHARSET))) {
            System.out.println("Welcome to BroadcastChat. Command /quit for exit.");
            while (running.get()) {
                String line = consoleReader.readLine();
                if (line == null) break;
                if (QUIT_COMMAND.equalsIgnoreCase(line.trim())) break;
                ClientMessage clientMessage = new ClientMessage(userInfo.username(), line, Instant.now().toEpochMilli());
                consoleWriter.println(clientMessage);
                queue.offerLast(clientMessage);
            }
        } catch (IOException e) {
            System.err.println("Error reading from terminal: " + e.getMessage());
        }
    }

    private boolean reconnect(InetSocketAddress remote, AtomicReference<BufferedReader> inRef, AtomicReference<PrintWriter> outRef) {
        System.out.println("No available servers. Retrying in 10 seconds.");
        try {
            Connection old = this.connection;
            if (old != null) {
                try { old.close(); } catch (Exception ignored) {}
            }
            Socket s = new Socket(remote.getHostString(), remote.getPort());
            s.setTcpNoDelay(true);
            Connection fresh = Connection.of(s);
            this.connection = fresh;
            inRef.set(fresh.getReader());
            outRef.set(fresh.getWriter());
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    @Override
    public void close() {
        if (!running.getAndSet(false)) return;
        producer.shutdownNow();
        consumer.shutdownNow();
        Connection c = this.connection;
        if (c != null) {
            try { c.close(); } catch (Exception ignored) {}
        }
        System.out.println("Client is stopped.");
    }
}