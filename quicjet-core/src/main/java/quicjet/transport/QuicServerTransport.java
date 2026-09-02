package quicjet.transport;

import java.util.function.Consumer;

public interface QuicServerTransport extends AutoCloseable {
    void onNewConnection(Consumer<QuicConnection> connectionConsumer);
    void start() throws Exception;
    void stop() throws Exception;
    boolean isRunning();
    int port();

    @Override
    default void close() throws Exception {
        stop();
    }
}
