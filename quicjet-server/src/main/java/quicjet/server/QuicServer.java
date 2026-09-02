package quicjet.server;

import quicjet.config.QuicServerConfig;
import quicjet.transport.QuicConnection;
import quicjet.transport.QuicServerTransport;
import quicjet.spi.QuicProvider;
import quicjet.spi.QuicProviderLoader;

import java.nio.file.Path;
import java.util.function.Consumer;

public class QuicServer implements AutoCloseable {
    private final QuicServerTransport transport;

    private QuicServer(Builder builder) {
        QuicProvider provider = builder.providerName != null ?
                QuicProviderLoader.get(builder.providerName) :
                QuicProviderLoader.load();

        QuicServerConfig config = new QuicServerConfig(
                builder.host,
                builder.port,
                builder.keyStorePath,
                builder.keyStorePassword,
                builder.keyManagerPassword
        );

        this.transport = provider.createQuicServer(config);
        if (builder.connectionConsumer != null) {
            this.transport.onNewConnection(builder.connectionConsumer);
        }
    }

    public void start() throws Exception {
        transport.start();
    }

    public void stop() throws Exception {
        transport.stop();
    }

    public boolean isRunning() {
        return transport.isRunning();
    }

    public int port() {
        return transport.port();
    }

    @Override
    public void close() throws Exception {
        stop();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String host = "0.0.0.0";
        private int port = 4433;
        private Path keyStorePath;
        private String keyStorePassword;
        private String keyManagerPassword;
        private String providerName;
        private Consumer<QuicConnection> connectionConsumer;

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder keyStore(Path keyStorePath, String keyStorePassword) {
            this.keyStorePath = keyStorePath;
            this.keyStorePassword = keyStorePassword;
            return this;
        }

        public Builder provider(String providerName) {
            this.providerName = providerName;
            return this;
        }

        public Builder onConnection(Consumer<QuicConnection> connectionConsumer) {
            this.connectionConsumer = connectionConsumer;
            return this;
        }

        public QuicServer build() {
            return new QuicServer(this);
        }
    }
}
