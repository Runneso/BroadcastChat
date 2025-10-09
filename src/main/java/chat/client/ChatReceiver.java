package chat.client;

import chat.common.message.BaseMessage;
import chat.common.message.ChatMessage;
import chat.common.utils.Json;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class ChatReceiver {
    private static final int RECENT_CAPACITY = 256;

    private final Supplier<BufferedReader> stream;
    private final PrintWriter consoleOut;
    private final AtomicBoolean running;

    private final ArrayDeque<String> recentOrder = new ArrayDeque<>(RECENT_CAPACITY);
    private final Set<String> recentSet = new HashSet<>(RECENT_CAPACITY);

    public ChatReceiver(Supplier<BufferedReader> stream, PrintWriter consoleOut, AtomicBoolean running) {
        this.stream = stream;
        this.consoleOut = consoleOut;
        this.running = running;
    }

    public void run() {
        if (!running.get()) return;

        BufferedReader in = stream.get();
        if (in == null) return;

        try {
            while (in.ready()) {
                String line = in.readLine();
                if (line == null) {
                    return;
                }
                BaseMessage message = Json.fromJson(line, BaseMessage.class);
                if (message instanceof ChatMessage chatMessage){
                    String id = chatMessage.getMessageId().toString();
                    if (recentSet.add(id)) {
                        recentOrder.addLast(id);
                        if (recentOrder.size() > RECENT_CAPACITY) {
                            String old = recentOrder.removeFirst();
                            recentSet.remove(old);
                        }
                        consoleOut.println(chatMessage);
                    }
                }
            }
        } catch (Exception error) {
            System.err.println("Error reading message: " + error.getMessage());
        }
    }
}
