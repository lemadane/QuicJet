package quicjet.client;

import quicjet.model.HttpHeaderMap;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public class HttpRequest {
    private final String method;
    private final URI uri;
    private final HttpHeaderMap headers;
    private final byte[] body;
    private final Duration timeout;

    private HttpRequest(Builder builder) {
        this.method = builder.method;
        this.uri = builder.uri;
        this.headers = builder.headers;
        this.body = builder.body;
        this.timeout = builder.timeout;
    }

    public String method() {
        return method;
    }

    public URI uri() {
        return uri;
    }

    public HttpHeaderMap headers() {
        return headers;
    }

    public byte[] body() {
        return body;
    }

    public Duration timeout() {
        return timeout;
    }

    public static Builder newBuilder(URI uri) {
        return new Builder(uri);
    }

    public static Builder newBuilder(String uriString) {
        return new Builder(URI.create(uriString));
    }

    public static HttpRequest get(String uriString) {
        return newBuilder(uriString).GET().build();
    }

    public static HttpRequest post(String uriString, String body) {
        return newBuilder(uriString).POST(body).build();
    }

    public static HttpRequest post(String uriString, byte[] body) {
        return newBuilder(uriString).POST(body).build();
    }

    public static HttpRequest post(String uriString, Path file) throws Exception {
        return newBuilder(uriString).POST(file).build();
    }


    public static class Builder {
        private final URI uri;
        private String method = "GET";
        private final HttpHeaderMap headers = new HttpHeaderMap();
        private byte[] body = new byte[0];
        private Duration timeout;

        public Builder(URI uri) {
            if (uri == null) {
                throw new IllegalArgumentException("URI cannot be null");
            }
            this.uri = uri;
        }

        public Builder header(String name, String value) {
            this.headers.add(name, value);
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder GET() {
            this.method = "GET";
            this.body = new byte[0];
            return this;
        }

        public Builder POST(String body) {
            this.method = "POST";
            this.body = body != null ? body.getBytes(StandardCharsets.UTF_8) : new byte[0];
            return this;
        }

        public Builder POST(byte[] body) {
            this.method = "POST";
            this.body = body != null ? body : new byte[0];
            return this;
        }

        public Builder POST(Path file) throws Exception {
            this.method = "POST";
            this.body = file != null ? Files.readAllBytes(file) : new byte[0];
            return this;
        }

        public Builder POST(InputStream inputStream) throws Exception {
            this.method = "POST";
            this.body = inputStream != null ? inputStream.readAllBytes() : new byte[0];
            return this;
        }

        public Builder method(String method, byte[] body) {
            this.method = method;
            this.body = body != null ? body : new byte[0];
            return this;
        }

        public HttpRequest build() {
            return new HttpRequest(this);
        }
    }
}
