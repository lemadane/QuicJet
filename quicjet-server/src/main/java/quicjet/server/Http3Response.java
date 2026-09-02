package quicjet.server;

import quicjet.model.HttpHeaderMap;
import quicjet.model.HttpResponseSpec;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public record Http3Response(
    int statusCode,
    HttpHeaderMap headers,
    byte[] body,
    InputStream bodyStream,
    Path filePath
) {
    public Http3Response(int statusCode, HttpHeaderMap headers, byte[] body) {
        this(statusCode, headers, body, null, null);
    }

    public Http3Response {
        if (headers == null) {
            headers = new HttpHeaderMap();
        }
        if (body == null) {
            body = new byte[0];
        }
    }

    @Override
    public byte[] body() {
        if (filePath != null && Files.exists(filePath)) {
            try {
                return Files.readAllBytes(filePath);
            } catch (Exception e) {
                return new byte[0];
            }
        }
        if ((body == null || body.length == 0) && bodyStream != null) {
            try {
                return bodyStream.readAllBytes();
            } catch (Exception e) {
                return new byte[0];
            }
        }
        return body;
    }

    public HttpResponseSpec toSpec() {
        if (filePath != null && Files.exists(filePath)) {
            try {
                return new HttpResponseSpec(statusCode, headers, Files.newInputStream(filePath));
            } catch (Exception e) {
                // fallback
            }
        }
        if (bodyStream != null) {
            return new HttpResponseSpec(statusCode, headers, bodyStream);
        }
        return new HttpResponseSpec(statusCode, headers, body);
    }

    public static Builder status(int statusCode) {
        return new Builder(statusCode);
    }

    public static Http3Response ok(String text) {
        return status(200).text(text).build();
    }

    public static Http3Response ok() {
        return status(200).build();
    }

    public static class Builder {
        private final int statusCode;
        private final HttpHeaderMap headers = new HttpHeaderMap();
        private byte[] body = new byte[0];
        private InputStream bodyStream;
        private Path filePath;

        public Builder(int statusCode) {
            this.statusCode = statusCode;
        }

        public Builder header(String name, String value) {
            this.headers.add(name, value);
            return this;
        }

        public Builder text(String text) {
            this.headers.set("Content-Type", "text/plain; charset=utf-8");
            this.body = text != null ? text.getBytes(StandardCharsets.UTF_8) : new byte[0];
            this.bodyStream = null;
            this.filePath = null;
            return this;
        }

        public Builder json(String json) {
            this.headers.set("Content-Type", "application/json; charset=utf-8");
            this.body = json != null ? json.getBytes(StandardCharsets.UTF_8) : new byte[0];
            this.bodyStream = null;
            this.filePath = null;
            return this;
        }

        public Builder body(byte[] body) {
            this.body = body != null ? body : new byte[0];
            this.bodyStream = null;
            this.filePath = null;
            return this;
        }

        public Builder stream(InputStream inputStream) {
            this.bodyStream = inputStream;
            this.filePath = null;
            return this;
        }

        public Builder file(Path filePath) throws Exception {
            this.headers.set("Content-Type", "application/octet-stream");
            if (filePath != null) {
                this.headers.set("Content-Disposition", "attachment; filename=\"" + filePath.getFileName() + "\"");
                this.filePath = filePath;
            }
            return this;
        }

        public Http3Response build() {
            return new Http3Response(statusCode, headers, body, bodyStream, filePath);
        }
    }
}


