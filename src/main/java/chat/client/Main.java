package chat.client;

import chat.common.utils.Connection;

import java.io.IOException;
import java.net.Socket;

public class Main {
    public static void main(String[] args) throws IOException {
        String host = args[0], username = args[2];
        int port = Integer.parseInt(args[1]);

        try (ChatClient client = new ChatClient(new UserInfo(username))) {
            try (Socket socket = new Socket(host, port)) {
                Connection connection = Connection.of(socket);
                client.start(connection, System.in, System.out);
            }
        }
    }
}
