package quicjet.config;

import java.time.Duration;

public record QuicClientConfig(
    String host,
    int port,
    Duration connectTimeout,
    Duration idleTimeout,
    boolean trustAllCertificates
) {
    public QuicClientConfig {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Host must not be empty");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(10);
        }
        if (idleTimeout == null) {
            idleTimeout = Duration.ofSeconds(30);
        }
    }
}
