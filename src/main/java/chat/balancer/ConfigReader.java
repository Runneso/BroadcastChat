package chat.balancer;

import chat.common.message.ServerInfo;
import chat.common.utils.Json;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

public class ConfigReader {
    private final Path configPath;

    public ConfigReader(Path configPath) {
        this.configPath = configPath;
    }

    public Set<ServerInfo> run() throws IOException {
        return Json.fromFile(configPath.toString(), ServersConfig.class).servers();
    }
}

