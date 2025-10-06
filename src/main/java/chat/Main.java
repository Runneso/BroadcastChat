package chat;


import chat.common.message.BalancerMessage;
import chat.common.message.ClientMessage;
import chat.common.message.HealthcheckMessage;
import chat.common.utils.Connection;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.time.Instant;

public class Main {
    public static void main(String[] args) {
        try(Socket socket = new Socket("0.0.0.0",5000)){
            Connection c = Connection.of(socket);
            c.writeLine(new HealthcheckMessage("server1", Instant.now().toEpochMilli()).toJsonString());
            String a = c.readLine();
            System.out.println(a);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
