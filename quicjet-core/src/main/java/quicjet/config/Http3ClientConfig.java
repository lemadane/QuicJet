package quicjet.config;

import java.nio.file.Path;
import java.time.Duration;

public record Http3ClientConfig(
    Duration connectTimeout,
    Duration idleTimeout,
    boolean followRedirects,
    boolean trustAllCertificates,
    int maxStreamsPerConnection,
    Path trustStorePath,
    String trustStorePassword
) {
    public Http3ClientConfig(
        Duration connectTimeout,
        Duration idleTimeout,
        boolean followRedirects,
        boolean trustAllCertificates,
        int maxStreamsPerConnection
    ) {
        this(connectTimeout, idleTimeout, followRedirects, trustAllCertificates, maxStreamsPerConnection, null, null);
    }

    public Http3ClientConfig {
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(10);
        }
        if (idleTimeout == null) {
            idleTimeout = Duration.ofSeconds(30);
        }
        if (maxStreamsPerConnection <= 0) {
            maxStreamsPerConnection = 100;
        }
    }
}
