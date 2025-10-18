package chat.client;

import chat.common.message.ClientMessage;

import java.io.PrintWriter;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class ChatSender {
    private static final int ERROR_SLEEP_MS = 200;
    private static final int RETRY_ALL_DOWN_MS = 10_000;

    private final BlockingDeque<ClientMessage> queue;
    private final Supplier<PrintWriter> stream;
    private final AtomicBoolean running;
    private final Supplier<Boolean> reconnectSupplier;

    public ChatSender(BlockingDeque<ClientMessage> deque,
                      Supplier<PrintWriter> stream,
                      AtomicBoolean running,
                      Supplier<Boolean> reconnectSupplier) {
        this.queue = deque;
        this.stream = stream;
        this.running = running;
        this.reconnectSupplier = reconnectSupplier;
    }

    public void run() {
        try {
            while (running.get()) {
                ClientMessage message = queue.take();
                try {
                    PrintWriter out = stream.get();
                    if (out == null) {
                        if (!reconnectSupplier.get()) {
                            queue.offerFirst(message);
                            TimeUnit.MILLISECONDS.sleep(RETRY_ALL_DOWN_MS);
                            continue;
                        }
                        out = stream.get();
                        if (out == null) {
                            queue.offerFirst(message);
                            TimeUnit.MILLISECONDS.sleep(ERROR_SLEEP_MS);
                            continue;
                        }
                    }
                    out.println(message.toJsonString());
                    if (out.checkError()){
                        queue.offerFirst(message);
                        if (!reconnectSupplier.get()) {
                            TimeUnit.MILLISECONDS.sleep(RETRY_ALL_DOWN_MS);
                        } else {
                            TimeUnit.MILLISECONDS.sleep(ERROR_SLEEP_MS);
                        }
                    }

                } catch (Exception ignored) {
                    queue.offerFirst(message);
                    TimeUnit.MILLISECONDS.sleep(ERROR_SLEEP_MS);
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
