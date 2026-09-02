package quicjet.transport;

public interface Http3ServerTransport extends AutoCloseable {
    void registerRoute(String method, String path, Http3RequestHandler handler);
    void setDefaultHandler(Http3RequestHandler handler);
    void start() throws Exception;
    void stop() throws Exception;
    boolean isRunning();
    int port();

    @Override
    default void close() throws Exception {
        stop();
    }
}
