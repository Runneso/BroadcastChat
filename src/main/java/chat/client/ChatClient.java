package chat.client;

import chat.common.message.ClientMessage;
import chat.common.utils.Config;
import chat.common.utils.Connection;

import java.io.*;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class ChatClient {
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

        PrintWriter consoleWriter = new PrintWriter(new OutputStreamWriter(consoleOut, Config.CHARSET),true);
        ChatSender sender = new ChatSender(queue, outSupplier, running);
        ChatReceiver receiver = new ChatReceiver(inSupplier, consoleWriter, running);

        producer.submit(sender::run);
        consumer.scheduleWithFixedDelay(receiver::run, POOLING_INITIAL_DELAY_MS, POOLING_INTERVAL_MS, TimeUnit.MILLISECONDS);

        try (BufferedReader consoleReader = new BufferedReader(new InputStreamReader(consoleIn, Config.CHARSET))) {
            System.out.println("Welcome to BroadcastChat. Command /quit for exit.");
            while (running.get()) {
                String line = consoleReader.readLine();
                if (QUIT_COMMAND.equalsIgnoreCase(line.trim())) break;
                ClientMessage clientMessage = new ClientMessage(userInfo.username(), line, Instant.now().toEpochMilli());
                consoleWriter.println(clientMessage);
                queue.offerLast(clientMessage);
            }
        } catch (IOException e) {
            System.err.println("Error reading from terminal: " + e.getMessage());
        } finally {
            shutdown();
        }
    }

    public void shutdown() {
        if (!running.getAndSet(false)) return;
        producer.shutdownNow();
        consumer.shutdownNow();
        System.out.println("Client is stopped.");
    }
}