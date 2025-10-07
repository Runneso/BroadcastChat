package chat.client;

import chat.common.utils.Connection;

import java.io.IOException;
import java.net.Socket;

public class Main {
    public static void main(String[] args) throws IOException {
        String host = args[0], username = args[2];
        int port = Integer.parseInt(args[1]);
        ChatClient c = new ChatClient(new UserInfo(username));

        try(Socket socket = new Socket(host,port)){
            Connection cc = Connection.of(socket);
            c.start(cc, System.in, System.out);
        }
    }
}
