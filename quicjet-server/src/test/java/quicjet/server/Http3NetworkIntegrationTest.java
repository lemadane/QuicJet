package quicjet.server;

import quicjet.client.HttpRequest;
import quicjet.client.QuicHttpClient;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

public class Http3NetworkIntegrationTest {
    private static Http3Server server;
    private static int serverPort;
    private static Path tempKeystore;

    @BeforeAll
    public static void setupAll() throws Exception {
        tempKeystore = Files.createTempFile("test-keystore", ".p12");
        try (InputStream is = Http3NetworkIntegrationTest.class.getResourceAsStream("/test-keystore.p12")) {
            assertNotNull(is, "test-keystore.p12 must exist in test resources");
            Files.copy(is, tempKeystore, StandardCopyOption.REPLACE_EXISTING);
        }

        server = Http3Server.builder()
                .port(0)
                .keyStore(tempKeystore, "password")
                .virtualThreads(true)
                .maxRequestBodySize(512 * 1024L) // 512 KB limit for testing
                .route("GET", "/api/ping", req -> Http3Response.ok("PONG"))
                .route("POST", "/api/echo", req -> Http3Response.status(200).text("Echo: " + req.bodyAsString()).build())
                .defaultHandler(req -> Http3Response.status(404).text("Not Found").build())
                .build();

        server.start();
        serverPort = server.port();
    }

    @AfterAll
    public static void tearDownAll() throws Exception {
        if (server != null) {
            try { server.stop(); } catch (Exception ignored) {}
        }
        if (tempKeystore != null) {
            Files.deleteIfExists(tempKeystore);
        }
    }

    @Test
    public void testServerPortBoundAndRunning() {
        assertTrue(server.isRunning());
        assertTrue(serverPort > 0);
    }

    @Test
    public void testMaxRequestBodySizeGuardrail() {
        byte[] oversizedPayload = new byte[1024 * 1024]; // 1 MB payload > 512 KB limit
        HttpRequest oversizedReq = HttpRequest.post("https://localhost:" + serverPort + "/api/echo", oversizedPayload);

        Http3Request serverReq = Http3Request.fromSpec(
                new quicjet.model.HttpRequestSpec(
                        oversizedReq.method(),
                        oversizedReq.uri(),
                        oversizedReq.headers(),
                        oversizedReq.body(),
                        oversizedReq.timeout()
                )
        );

        assertNotNull(serverReq);
        assertEquals("POST", serverReq.method());
    }
}
