package quicjet.server;

import quicjet.client.HttpRequest;
import quicjet.client.QuicHttpClient;
import quicjet.client.QuicClient;
import quicjet.transport.QuicConnection;
import quicjet.transport.QuicStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

public class Http3ClientServerE2ETest {
    private static Http3Server server;
    private static QuicHttpClient client;
    private static int serverPort;
    private static Path tempKeystore;

    @BeforeAll
    public static void setupAll() throws Exception {
        tempKeystore = Files.createTempFile("test-keystore", ".p12");
        try (InputStream is = Http3ClientServerE2ETest.class.getResourceAsStream("/test-keystore.p12")) {
            assertNotNull(is, "test-keystore.p12 must exist in test resources");
            Files.copy(is, tempKeystore, StandardCopyOption.REPLACE_EXISTING);
        }

        server = Http3Server.builder()
                .port(0)
                .keyStore(tempKeystore, "password")
                .virtualThreads(true)
                .route("GET", "/api/hello", req -> 
                    Http3Response.status(200)
                            .header("X-Custom-Header", "Java21")
                            .text("Hello E2E HTTP/3!")
                            .build()
                )
                .route("POST", "/api/echo", req -> 
                    Http3Response.status(201)
                            .text("Echoed: " + req.bodyAsString())
                            .build()
                )
                .defaultHandler(req -> Http3Response.status(404).text("Not Found").build())
                .build();

        server.start();
        serverPort = server.port();

        client = QuicHttpClient.builder()
                .connectTimeout(Duration.ofSeconds(3))
                .trustAllCertificates(true)
                .followRedirects(true)
                .build();
    }

    @AfterAll
    public static void tearDownAll() throws Exception {
        if (client != null) {
            try { client.close(); } catch (Exception ignored) {}
        }
        if (server != null) {
            try { server.stop(); } catch (Exception ignored) {}
        }
        if (tempKeystore != null) {
            Files.deleteIfExists(tempKeystore);
        }
    }

    @Test
    public void testServerIsRunningAndConfigured() {
        assertTrue(server.isRunning(), "Http3Server should be running");
        assertTrue(serverPort > 0, "Server port should be assigned dynamically");
    }

    @Test
    public void testHttpRequestResponseModeling() {
        HttpRequest req = HttpRequest.post("https://localhost:" + serverPort + "/api/echo", "{\"test\":true}");
        assertEquals("POST", req.method());
        assertEquals("{\"test\":true}", new String(req.body(), StandardCharsets.UTF_8));

        Http3Request serverReq = Http3Request.fromSpec(
                new quicjet.model.HttpRequestSpec(
                        req.method(),
                        req.uri(),
                        req.headers(),
                        req.body(),
                        req.timeout()
                )
        );
        assertEquals("POST", serverReq.method());
        assertEquals("{\"test\":true}", serverReq.bodyAsString());

        Http3Response res = Http3Response.status(200)
                .header("X-App", "Test")
                .text("Response Body")
                .build();

        assertEquals(200, res.statusCode());
        assertEquals("Response Body", new String(res.body(), StandardCharsets.UTF_8));
        assertEquals("Test", res.headers().firstValue("X-App").orElse(null));
    }

    @Test
    public void testRawQuicE2EStream() throws Exception {
        try (QuicServer quicServer = QuicServer.builder()
                .port(0)
                .keyStore(tempKeystore, "password")
                .build()) {

            quicServer.start();
            assertTrue(quicServer.isRunning());
            int quicPort = quicServer.port();
            assertTrue(quicPort > 0);

            try (QuicClient quicClient = QuicClient.builder()
                    .host("127.0.0.1")
                    .port(quicPort)
                    .trustAllCertificates(true)
                    .build()) {

                try (QuicConnection connection = quicClient.connect()) {
                    assertTrue(connection.isConnected());
                    try (QuicStream stream = connection.openStream()) {
                        assertTrue(stream.isBidirectional());
                        byte[] payload = "E2E QUIC Stream Test Data".getBytes(StandardCharsets.UTF_8);
                        stream.write(payload);
                        assertNotNull(stream.inputStream());
                    }
                }
            }
        }
    }
}
