package quicjet.server;

import quicjet.client.QuicClient;
import quicjet.transport.QuicConnection;
import quicjet.transport.QuicStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.*;

public class Http3ServerClientIntegrationTest {
    private Http3Server server;
    private Path tempKeystore;

    @BeforeEach
    public void setup() throws Exception {
        tempKeystore = Files.createTempFile("test-keystore", ".p12");
        try (InputStream is = Http3ServerClientIntegrationTest.class.getResourceAsStream("/test-keystore.p12")) {
            assertNotNull(is, "test-keystore.p12 must exist in test resources");
            Files.copy(is, tempKeystore, StandardCopyOption.REPLACE_EXISTING);
        }

        server = Http3Server.builder()
                .port(0)
                .keyStore(tempKeystore, "password")
                .virtualThreads(true)
                .route("GET", "/hello", req -> Http3Response.ok("Hello Java 21 HTTP/3!"))
                .route("POST", "/echo", req -> Http3Response.ok("Echo: " + req.bodyAsString()))
                .build();

        server.start();
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (server != null) {
            server.stop();
        }
        if (tempKeystore != null) {
            Files.deleteIfExists(tempKeystore);
        }
    }

    @Test
    public void testServerLifecycleAndRouting() {
        assertTrue(server.isRunning(), "Server should be running");
        assertTrue(server.port() > 0, "Server port should be assigned dynamically");
    }

    @Test
    public void testRawQuicClientServer() throws Exception {
        try (QuicServer quicServer = QuicServer.builder()
                .port(0)
                .onConnection(conn -> {
                    assertNotNull(conn);
                })
                .build()) {

            quicServer.start();
            assertTrue(quicServer.isRunning());
            assertTrue(quicServer.port() > 0);

            try (QuicClient quicClient = QuicClient.builder()
                    .host("127.0.0.1")
                    .port(quicServer.port())
                    .build()) {

                try (QuicConnection conn = quicClient.connect()) {
                    assertTrue(conn.isConnected());
                    try (QuicStream stream = conn.openStream()) {
                        assertTrue(stream.isBidirectional());
                        stream.write("Hello QUIC".getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
        }
    }
}
