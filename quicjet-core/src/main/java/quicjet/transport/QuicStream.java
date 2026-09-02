package quicjet.transport;

import java.io.InputStream;
import java.io.OutputStream;

public interface QuicStream extends AutoCloseable {
    long streamId();
    boolean isBidirectional();

    void write(byte[] data) throws Exception;
    byte[] read() throws Exception;

    InputStream inputStream();
    OutputStream outputStream();

    @Override
    void close() throws Exception;
}
