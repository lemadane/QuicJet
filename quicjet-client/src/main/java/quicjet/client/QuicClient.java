package quicjet.client;

import quicjet.config.QuicClientConfig;
import quicjet.transport.QuicClientTransport;
import quicjet.transport.QuicConnection;
import quicjet.spi.QuicProvider;
import quicjet.spi.QuicProviderLoader;

import java.time.Duration;

public class QuicClient implements AutoCloseable {
    private final QuicClientTransport transport;

    private QuicClient(Builder builder) {
        QuicProvider provider = builder.providerName != null ?
                QuicProviderLoader.get(builder.providerName) :
                QuicProviderLoader.load();

        QuicClientConfig config = new QuicClientConfig(
                builder.host,
                builder.port,
                builder.connectTimeout,
                builder.idleTimeout,
                builder.trustAllCertificates
        );

        this.transport = provider.createQuicClient(config);
    }

    public QuicConnection connect() throws Exception {
        return transport.connect();
    }

    @Override
    public void close() throws Exception {
        transport.close();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String host = "127.0.0.1";
        private int port = 4433;
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration idleTimeout = Duration.ofSeconds(30);
        private boolean trustAllCertificates = false;
        private String providerName;

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder idleTimeout(Duration idleTimeout) {
            this.idleTimeout = idleTimeout;
            return this;
        }

        public Builder trustAllCertificates(boolean trustAllCertificates) {
            this.trustAllCertificates = trustAllCertificates;
            return this;
        }

        public Builder provider(String providerName) {
            this.providerName = providerName;
            return this;
        }

        public QuicClient build() {
            return new QuicClient(this);
        }
    }
}
