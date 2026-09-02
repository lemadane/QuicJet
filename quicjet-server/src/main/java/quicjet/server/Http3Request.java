package quicjet.server;

import quicjet.model.HttpHeaderMap;
import quicjet.model.HttpRequestSpec;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public record Http3Request(
    String method,
    URI uri,
    HttpHeaderMap headers,
    byte[] body
) {
    public static Http3Request fromSpec(HttpRequestSpec spec) {
        return new Http3Request(spec.method(), spec.uri(), spec.headers(), spec.body());
    }

    public String bodyAsString() {
        return body != null ? new String(body, StandardCharsets.UTF_8) : "";
    }

    public InputStream bodyInputStream() {
        return new ByteArrayInputStream(body != null ? body : new byte[0]);
    }

    public Path saveBodyToFile(Path destination) throws Exception {
        byte[] content = body != null ? body : new byte[0];
        Files.write(destination, content);
        return destination;
    }
}
