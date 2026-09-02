package quicjet.client;

import quicjet.model.HttpHeaderMap;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public record HttpResponse<T>(
    int statusCode,
    HttpHeaderMap headers,
    T body
) {
    @FunctionalInterface
    public interface BodyHandler<T> {
        T apply(int statusCode, HttpHeaderMap headers, byte[] bodyBytes) throws Exception;
    }

    public static class BodyHandlers {
        public static BodyHandler<String> ofString() {
            return (status, headers, bytes) -> new String(bytes, StandardCharsets.UTF_8);
        }

        public static BodyHandler<byte[]> ofByteArray() {
            return (status, headers, bytes) -> bytes;
        }

        public static BodyHandler<InputStream> ofInputStream() {
            return (status, headers, bytes) -> new ByteArrayInputStream(bytes);
        }

        public static BodyHandler<Path> ofFile(Path destination) {
            return (status, headers, bytes) -> {
                Files.write(destination, bytes);
                return destination;
            };
        }

        public static BodyHandler<Void> discarding() {
            return (status, headers, bytes) -> null;
        }
    }
}
