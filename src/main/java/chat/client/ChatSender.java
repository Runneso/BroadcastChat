package chat.client;

import chat.common.message.ClientMessage;

import java.io.PrintWriter;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class ChatSender {
    private static final int ERROR_SLEEP_MS = 200;

    private final BlockingDeque<ClientMessage> queue;
    private final Supplier<PrintWriter> stream;
    private final AtomicBoolean running;

    public ChatSender(BlockingDeque<ClientMessage> deque, Supplier<PrintWriter> stream, AtomicBoolean running) {
        this.queue = deque;
        this.stream = stream;
        this.running = running;
    }

    public void run() {
        try {
            while (running.get()) {
                ClientMessage message = queue.take();
                try {
                    PrintWriter out = stream.get();
                    if (out == null) {
                        queue.offerFirst(message);
                        TimeUnit.MILLISECONDS.sleep(ERROR_SLEEP_MS);
                        continue;
                    }

                    out.println(message.toJsonString());
                    if (out.checkError()){
                        throw new RuntimeException("Writing to stream error!");
                    }

                } catch (Exception error) {
                    System.err.println("Error in sending message: " + error.getMessage());
                    queue.offerFirst(message);
                    TimeUnit.MILLISECONDS.sleep(ERROR_SLEEP_MS);
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
