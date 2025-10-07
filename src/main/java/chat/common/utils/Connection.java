package chat.common.utils;

import java.io.*;
import java.net.Socket;
import java.util.Objects;

public final class Connection implements Closeable {
    private static final String NULL_ARG_FMT = "%s is null!";
    private static final String TO_STRING_FMT = "Connection(%s)";

    private final Socket socket;
    private final BufferedReader reader;
    private final PrintWriter writer;

    private Connection(Socket socket, BufferedReader reader, PrintWriter writer) {
        this.socket = Objects.requireNonNull(socket, String.format(NULL_ARG_FMT, "socket"));
        this.reader = Objects.requireNonNull(reader, String.format(NULL_ARG_FMT, "reader"));
        this.writer = Objects.requireNonNull(writer, String.format(NULL_ARG_FMT, "writer"));
    }

    public static Connection of(Socket socket) throws IOException {
        Objects.requireNonNull(socket, String.format(NULL_ARG_FMT, "socket"));
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), Config.CHARSET));
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), Config.CHARSET), true);
        return new Connection(socket, reader, writer);
    }

    public boolean isOpen() {
        return !socket.isClosed()
                && socket.isConnected()
                && !socket.isInputShutdown()
                && !socket.isOutputShutdown()
                && !writer.checkError();
    }

    public BufferedReader getReader() {
        return reader;
    }

    public PrintWriter getWriter() {
        return writer;
    }

    public Socket socket() {
        return socket;
    }

    public String readLine() throws IOException {
        return reader.readLine();
    }

    public void writeLine(String line) {
        writer.println(line);
    }

    @Override
    public String toString() {
        return String.format(TO_STRING_FMT, socket.getRemoteSocketAddress());
    }

    @Override
    public void close() {
        closePart(writer);
        closePart(reader);
        closePart(socket);
    }

    private static void closePart(Closeable c) {
        if (c == null) return;
        try {c.close();} catch (IOException ignored) {}
    }
}
