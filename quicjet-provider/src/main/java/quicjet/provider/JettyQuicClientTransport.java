package quicjet.provider;

import quicjet.config.QuicClientConfig;
import quicjet.exception.QuicException;
import quicjet.transport.QuicClientTransport;
import quicjet.transport.QuicConnection;
import quicjet.transport.QuicStream;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class JettyQuicClientTransport implements QuicClientTransport {
    private final QuicClientConfig config;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public JettyQuicClientTransport(QuicClientConfig config) {
        this.config = config;
    }

    public synchronized void start() throws Exception {
        running.set(true);
    }

    public synchronized void stop() throws Exception {
        running.set(false);
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public QuicConnection connect() throws Exception {
        if (!isRunning()) {
            start();
        }

        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout((int) config.connectTimeout().toMillis());
        InetAddress address = InetAddress.getByName(config.host());

        return new JettyUdpQuicConnection(socket, address, config.port());
    }

    @Override
    public void close() throws Exception {
        stop();
    }

    private static class JettyUdpQuicConnection implements QuicConnection {
        private final DatagramSocket socket;
        private final InetAddress address;
        private final int port;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final AtomicLong streamIdCounter = new AtomicLong(0);

        public JettyUdpQuicConnection(DatagramSocket socket, InetAddress address, int port) {
            this.socket = socket;
            this.address = address;
            this.port = port;
        }

        @Override
        public QuicStream openStream() throws Exception {
            if (closed.get()) throw new QuicException("Connection is closed");
            return new JettyUdpQuicStream(streamIdCounter.getAndIncrement(), socket, address, port);
        }

        @Override
        public boolean isConnected() {
            return !closed.get() && !socket.isClosed();
        }

        @Override
        public void close() throws Exception {
            if (closed.compareAndSet(false, true)) {
                socket.close();
            }
        }
    }

    private static class JettyUdpQuicStream implements QuicStream {
        private final long id;
        private final DatagramSocket socket;
        private final InetAddress address;
        private final int port;
        private boolean closed = false;

        public JettyUdpQuicStream(long id, DatagramSocket socket, InetAddress address, int port) {
            this.id = id;
            this.socket = socket;
            this.address = address;
            this.port = port;
        }

        @Override
        public long streamId() {
            return id;
        }

        @Override
        public boolean isBidirectional() {
            return true;
        }

        @Override
        public void write(byte[] data) throws Exception {
            if (closed) throw new QuicException("Stream is closed");
            DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
            socket.send(packet);
        }

        @Override
        public byte[] read() throws Exception {
            if (closed) throw new QuicException("Stream is closed");
            byte[] buf = new byte[65535];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            socket.receive(packet);

            byte[] data = new byte[packet.getLength()];
            System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());
            return data;
        }

        @Override
        public InputStream inputStream() {
            return new InputStream() {
                private java.io.ByteArrayInputStream currentChunk = new java.io.ByteArrayInputStream(new byte[0]);

                @Override
                public synchronized int read() {
                    try {
                        if (currentChunk.available() == 0) {
                            byte[] b = JettyUdpQuicStream.this.read();
                            if (b == null || b.length == 0) return -1;
                            currentChunk = new java.io.ByteArrayInputStream(b);
                        }
                        return currentChunk.read();
                    } catch (Exception e) {
                        return -1;
                    }
                }

                @Override
                public synchronized int read(byte[] b, int off, int len) {
                    try {
                        if (currentChunk.available() == 0) {
                            byte[] packetData = JettyUdpQuicStream.this.read();
                            if (packetData == null || packetData.length == 0) return -1;
                            currentChunk = new java.io.ByteArrayInputStream(packetData);
                        }
                        return currentChunk.read(b, off, len);
                    } catch (Exception e) {
                        return -1;
                    }
                }
            };
        }


        @Override
        public OutputStream outputStream() {
            return new OutputStream() {
                @Override
                public void write(int b) throws java.io.IOException {
                    try {
                        JettyUdpQuicStream.this.write(new byte[]{(byte) b});
                    } catch (Exception e) {
                        throw new QuicException("Failed to write to stream", e);
                    }
                }

                @Override
                public void write(byte[] b, int off, int len) throws java.io.IOException {
                    try {
                        byte[] data = new byte[len];
                        System.arraycopy(b, off, data, 0, len);
                        JettyUdpQuicStream.this.write(data);
                    } catch (Exception e) {
                        throw new QuicException("Failed to write to stream", e);
                    }
                }
            };
        }

        @Override
        public void close() throws Exception {
            this.closed = true;
        }
    }
}
