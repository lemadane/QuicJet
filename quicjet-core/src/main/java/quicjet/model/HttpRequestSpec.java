package quicjet.model;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

public record HttpRequestSpec(
    String method,
    URI uri,
    HttpHeaderMap headers,
    byte[] body,
    InputStream bodyStream,
    Duration timeout
) {
    public HttpRequestSpec(
        String method,
        URI uri,
        HttpHeaderMap headers,
        byte[] body,
        Duration timeout
    ) {
        this(method, uri, headers, body, null, timeout);
    }

    public HttpRequestSpec(
        String method,
        URI uri,
        HttpHeaderMap headers,
        InputStream bodyStream,
        Duration timeout
    ) {
        this(method, uri, headers, new byte[0], bodyStream, timeout);
    }

    public HttpRequestSpec {
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("HTTP method cannot be null or blank");
        }
        if (uri == null) {
            throw new IllegalArgumentException("URI cannot be null");
        }
        if (headers == null) {
            headers = new HttpHeaderMap();
        }
        if (body == null) {
            body = new byte[0];
        }
    }

    public InputStream getInputStream() {
        if (bodyStream != null) {
            return bodyStream;
        }
        return new ByteArrayInputStream(body);
    }
}

