package quicjet.config;

import java.nio.file.Path;

public record Http3ServerConfig(
    String host,
    int port,
    Path keyStorePath,
    String keyStorePassword,
    String keyManagerPassword,
    Path trustStorePath,
    String trustStorePassword,
    boolean virtualThreadsEnabled,
    long maxRequestBodySize
) {
    public Http3ServerConfig(
        String host,
        int port,
        Path keyStorePath,
        String keyStorePassword,
        String keyManagerPassword,
        boolean virtualThreadsEnabled
    ) {
        this(host, port, keyStorePath, keyStorePassword, keyManagerPassword, null, null, virtualThreadsEnabled, 10 * 1024 * 1024L);
    }

    public Http3ServerConfig(
        String host,
        int port,
        Path keyStorePath,
        String keyStorePassword,
        String keyManagerPassword,
        boolean virtualThreadsEnabled,
        long maxRequestBodySize
    ) {
        this(host, port, keyStorePath, keyStorePassword, keyManagerPassword, null, null, virtualThreadsEnabled, maxRequestBodySize);
    }

    public Http3ServerConfig {
        if (host == null || host.isBlank()) {
            host = "0.0.0.0";
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 0 and 65535");
        }
        if (maxRequestBodySize <= 0) {
            maxRequestBodySize = 10 * 1024 * 1024L;
        }
    }
}

