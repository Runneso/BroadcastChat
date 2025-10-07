package chat.client;

import chat.common.message.BaseMessage;
import chat.common.message.ChatMessage;
import chat.common.utils.Json;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class ChatReceiver {
    private final Supplier<BufferedReader> stream;
    private final PrintWriter consoleOut;
    private final AtomicBoolean running;

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
                    consoleOut.println(chatMessage);
                }
            }
        } catch (Exception error) {
            System.err.println("Error reading message: " + error.getMessage());
        }
    }
}
