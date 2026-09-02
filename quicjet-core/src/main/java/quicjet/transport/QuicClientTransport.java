package quicjet.transport;

public interface QuicClientTransport extends AutoCloseable {
    QuicConnection connect() throws Exception;
    boolean isRunning();

    @Override
    void close() throws Exception;
}
