package quicjet.transport;

public interface QuicConnection extends AutoCloseable {
    QuicStream openStream() throws Exception;
    boolean isConnected();

    @Override
    void close() throws Exception;
}
