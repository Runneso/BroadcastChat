package chat.client;

import chat.common.utils.Connection;

import java.io.IOException;
import java.net.Socket;

public class Main {
    public static void main(String[] args) throws IOException {
        ChatClient c = new ChatClient(new UserInfo("213"));

        try(Socket socket = new Socket("0.0.0.0",5000)){
            Connection cc = Connection.of(socket);
            c.start(cc, System.in, System.out);
        }
    }
}
