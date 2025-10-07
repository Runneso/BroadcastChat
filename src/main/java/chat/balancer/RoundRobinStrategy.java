package chat.balancer;

import chat.common.exceptions.NoALiveServers;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class RoundRobinStrategy implements PickStrategy {
    private final AtomicInteger cursor = new AtomicInteger(0);
    private final AtomicReference<List<InetSocketAddress>> ref;

    public RoundRobinStrategy(AtomicReference<List<InetSocketAddress>> ref) {
        this.ref = ref;
    }

    @Override
    public InetSocketAddress pick() {
        List<InetSocketAddress> list = ref.get();
        if (list.isEmpty()){
            throw new NoALiveServers("No alive servers available!");
        }
        int index = cursor.getAndIncrement();
        return list.get(index % list.size());
    }
}

