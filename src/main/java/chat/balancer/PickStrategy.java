package chat.balancer;

import java.net.InetSocketAddress;

public interface PickStrategy {
    InetSocketAddress pick();
}

