package quicjet.client;

import quicjet.config.Http3ClientConfig;
import quicjet.model.HttpRequestSpec;
import quicjet.model.HttpResponseSpec;
import quicjet.transport.Http3ClientTransport;
import quicjet.spi.QuicProvider;
import quicjet.spi.QuicProviderLoader;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class QuicHttpClient implements AutoCloseable {
    private final Http3ClientTransport transport;

    private QuicHttpClient(Builder builder) {
        QuicProvider provider = builder.providerName != null ?
                QuicProviderLoader.get(builder.providerName) :
                QuicProviderLoader.load();

        Http3ClientConfig config = new Http3ClientConfig(
                builder.connectTimeout,
                builder.idleTimeout,
                builder.followRedirects,
                builder.trustAllCertificates,
                builder.maxStreamsPerConnection,
                builder.trustStorePath,
                builder.trustStorePassword
        );

        this.transport = provider.createHttp3Client(config);
    }

    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler) throws Exception {
        HttpRequestSpec spec = new HttpRequestSpec(
                request.method(),
                request.uri(),
                request.headers(),
                request.body(),
                request.timeout()
        );

        HttpResponseSpec responseSpec = transport.send(spec);
        T parsedBody = bodyHandler.apply(responseSpec.statusCode(), responseSpec.headers(), responseSpec.body());
        return new HttpResponse<>(responseSpec.statusCode(), responseSpec.headers(), parsedBody);
    }

    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler) {
        HttpRequestSpec spec = new HttpRequestSpec(
                request.method(),
                request.uri(),
                request.headers(),
                request.body(),
                request.timeout()
        );

        return transport.sendAsync(spec).thenApply(responseSpec -> {
            try {
                T parsedBody = bodyHandler.apply(responseSpec.statusCode(), responseSpec.headers(), responseSpec.body());
                return new HttpResponse<>(responseSpec.statusCode(), responseSpec.headers(), parsedBody);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public HttpResponse<String> get(String uriString) throws Exception {
        return send(HttpRequest.get(uriString), HttpResponse.BodyHandlers.ofString());
    }

    @Override
    public void close() throws Exception {
        transport.close();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static QuicHttpClient create() {
        return builder().build();
    }

    public static class Builder {
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration idleTimeout = Duration.ofSeconds(30);
        private boolean followRedirects = true;
        private boolean trustAllCertificates = false;
        private int maxStreamsPerConnection = 100;
        private Path trustStorePath;
        private String trustStorePassword;
        private String providerName;

        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder idleTimeout(Duration idleTimeout) {
            this.idleTimeout = idleTimeout;
            return this;
        }

        public Builder followRedirects(boolean followRedirects) {
            this.followRedirects = followRedirects;
            return this;
        }

        public Builder trustAllCertificates(boolean trustAllCertificates) {
            this.trustAllCertificates = trustAllCertificates;
            return this;
        }

        public Builder trustStore(Path trustStorePath, String trustStorePassword) {
            this.trustStorePath = trustStorePath;
            this.trustStorePassword = trustStorePassword;
            return this;
        }

        public Builder maxStreamsPerConnection(int maxStreamsPerConnection) {
            this.maxStreamsPerConnection = maxStreamsPerConnection;
            return this;
        }

        public Builder provider(String providerName) {
            this.providerName = providerName;
            return this;
        }

        public QuicHttpClient build() {
            return new QuicHttpClient(this);
        }
    }
}
