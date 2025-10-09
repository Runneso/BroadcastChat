package chat.server;

public class Main {
    public static void main(String[] args) throws Exception {
        String serverId = args[0], host = args[1];
        int port = Integer.parseInt(args[2]);
        try (ChatServer server = new ChatServer(serverId, host, port)) {
            server.start();
        }
    }
}

