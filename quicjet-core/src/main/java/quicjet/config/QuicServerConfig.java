package quicjet.config;

import java.nio.file.Path;

public record QuicServerConfig(
    String host,
    int port,
    Path keyStorePath,
    String keyStorePassword,
    String keyManagerPassword
) {
    public QuicServerConfig {
        if (host == null || host.isBlank()) {
            host = "0.0.0.0";
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 0 and 65535");
        }
    }
}
