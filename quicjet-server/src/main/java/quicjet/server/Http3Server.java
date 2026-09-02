package quicjet.server;

import quicjet.config.Http3ServerConfig;
import quicjet.transport.Http3ServerTransport;
import quicjet.spi.QuicProvider;
import quicjet.spi.QuicProviderLoader;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class Http3Server implements AutoCloseable {
    private final Http3ServerTransport transport;

    private Http3Server(Builder builder) {
        QuicProvider provider = builder.providerName != null ?
                QuicProviderLoader.get(builder.providerName) :
                QuicProviderLoader.load();

        Http3ServerConfig config = new Http3ServerConfig(
                builder.host,
                builder.port,
                builder.keyStorePath,
                builder.keyStorePassword,
                builder.keyManagerPassword,
                builder.trustStorePath,
                builder.trustStorePassword,
                builder.virtualThreadsEnabled,
                builder.maxRequestBodySize
        );


        this.transport = provider.createHttp3Server(config);

        builder.routes.forEach((key, handler) -> {
            String[] parts = key.split(":", 2);
            transport.registerRoute(parts[0], parts[1], spec -> {
                try {
                    Http3Request req = Http3Request.fromSpec(spec);
                    Http3Response res = handler.handle(req);
                    return res.toSpec();
                } catch (Exception e) {
                    return Http3Response.status(500).text("Internal Server Error").build().toSpec();
                }
            });
        });

        if (builder.defaultHandler != null) {
            transport.setDefaultHandler(spec -> {
                try {
                    Http3Request req = Http3Request.fromSpec(spec);
                    Http3Response res = builder.defaultHandler.handle(req);
                    return res.toSpec();
                } catch (Exception e) {
                    return Http3Response.status(500).text("Internal Server Error").build().toSpec();
                }
            });
        }
    }

    public void start() throws Exception {
        transport.start();
    }

    public void stop() throws Exception {
        transport.stop();
    }

    public void stop(Duration drainTimeout) throws Exception {
        if (drainTimeout != null && !drainTimeout.isZero()) {
            Thread.sleep(Math.min(drainTimeout.toMillis(), 1000));
        }
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
        private int port = 8443;
        private Path keyStorePath;
        private String keyStorePassword;
        private String keyManagerPassword;
        private Path trustStorePath;
        private String trustStorePassword;
        private boolean virtualThreadsEnabled = true;
        private long maxRequestBodySize = 10 * 1024 * 1024L; // 10 MB default
        private String providerName;
        private final Map<String, Http3Handler> routes = new HashMap<>();
        private Http3Handler defaultHandler;

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

        public Builder keyStore(Path keyStorePath, String keyStorePassword, String keyManagerPassword) {
            this.keyStorePath = keyStorePath;
            this.keyStorePassword = keyStorePassword;
            this.keyManagerPassword = keyManagerPassword;
            return this;
        }

        public Builder trustStore(Path trustStorePath, String trustStorePassword) {
            this.trustStorePath = trustStorePath;
            this.trustStorePassword = trustStorePassword;
            return this;
        }

        public Builder virtualThreads(boolean virtualThreadsEnabled) {
            this.virtualThreadsEnabled = virtualThreadsEnabled;
            return this;
        }

        public Builder maxRequestBodySize(long maxRequestBodySize) {
            this.maxRequestBodySize = maxRequestBodySize;
            return this;
        }


        public Builder provider(String providerName) {
            this.providerName = providerName;
            return this;
        }

        public Builder route(String path, Http3Handler handler) {
            return route("GET", path, handler);
        }

        public Builder route(String method, String path, Http3Handler handler) {
            routes.put(method.toUpperCase() + ":" + path, handler);
            return this;
        }

        public Builder defaultHandler(Http3Handler handler) {
            this.defaultHandler = handler;
            return this;
        }

        public Http3Server build() {
            return new Http3Server(this);
        }
    }
}
