package quicjet.transport;

import java.util.concurrent.CompletableFuture;

import quicjet.model.HttpRequestSpec;
import quicjet.model.HttpResponseSpec;

public interface Http3ClientTransport extends AutoCloseable {
    void start() throws Exception;
    void stop() throws Exception;
    boolean isRunning();

    HttpResponseSpec send(HttpRequestSpec request) throws Exception;
    CompletableFuture<HttpResponseSpec> sendAsync(HttpRequestSpec request);

    @Override
    default void close() throws Exception {
        stop();
    }
}
