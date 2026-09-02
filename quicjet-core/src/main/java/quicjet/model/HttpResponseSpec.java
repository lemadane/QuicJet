package quicjet.model;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public record HttpResponseSpec(
    int statusCode,
    HttpHeaderMap headers,
    byte[] body,
    InputStream bodyStream
) {
    public HttpResponseSpec(int statusCode, HttpHeaderMap headers, byte[] body) {
        this(statusCode, headers, body, null);
    }

    public HttpResponseSpec(int statusCode, HttpHeaderMap headers, InputStream bodyStream) {
        this(statusCode, headers, new byte[0], bodyStream);
    }

    public HttpResponseSpec {
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

