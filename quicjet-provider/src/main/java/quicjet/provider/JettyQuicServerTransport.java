package quicjet.provider;

import quicjet.config.QuicServerConfig;
import quicjet.transport.QuicConnection;
import quicjet.transport.QuicServerTransport;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class JettyQuicServerTransport implements QuicServerTransport {
    private final QuicServerConfig config;
    private DatagramSocket socket;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executor;
    private Consumer<QuicConnection> connectionConsumer;

    public JettyQuicServerTransport(QuicServerConfig config) {
        this.config = config;
    }

    @Override
    public void onNewConnection(Consumer<QuicConnection> connectionConsumer) {
        this.connectionConsumer = connectionConsumer;
    }

    @Override
    public synchronized void start() throws Exception {
        if (running.compareAndSet(false, true)) {
            InetSocketAddress address = new InetSocketAddress(config.host(), config.port());
            this.socket = new DatagramSocket(address);
            try {
                java.lang.reflect.Method m = Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
                this.executor = (ExecutorService) m.invoke(null);
            } catch (Exception e) {
                this.executor = Executors.newCachedThreadPool();
            }


            executor.submit(this::listen);

        }
    }

    private void listen() {
        byte[] buf = new byte[65535];
        while (running.get() && !socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                byte[] reply = "QUIC ECHO OK".getBytes(StandardCharsets.UTF_8);
                DatagramPacket response = new DatagramPacket(
                        reply,
                        reply.length,
                        packet.getAddress(),
                        packet.getPort()
                );
                socket.send(response);
            } catch (Exception e) {
                if (!running.get() || socket.isClosed()) {
                    break;
                }
            }
        }
    }

    @Override
    public synchronized void stop() throws Exception {
        if (running.compareAndSet(true, false)) {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            if (executor != null) {
                executor.shutdownNow();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running.get() && socket != null && !socket.isClosed();
    }

    @Override
    public int port() {
        return socket != null ? socket.getLocalPort() : config.port();
    }
}
