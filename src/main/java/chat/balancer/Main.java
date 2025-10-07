package chat.balancer;

import java.io.IOException;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(args[0]);
        Path configPath = Path.of(args[1]);
        try(Balancer balancer = new Balancer(configPath, port)){
            balancer.start();
        }
    }
}
