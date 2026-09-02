package quicjet.server;

import quicjet.client.HttpRequest;
import quicjet.client.HttpResponse;
import quicjet.client.QuicHttpClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class Http3StreamingFileTransferTest {
    private static Http3Server server;
    private static QuicHttpClient client;
    private static int serverPort;
    private static Path tempKeystore;

    @TempDir
    static Path tempDir;

    private static Path serverStorageDir;
    private static Path clientSourceFile;
    private static byte[] dummyBytes;

    @BeforeAll
    public static void setupAll() throws Exception {
        tempKeystore = Files.createTempFile("test-keystore", ".p12");
        try (InputStream is = Http3StreamingFileTransferTest.class.getResourceAsStream("/test-keystore.p12")) {
            assertNotNull(is);
            Files.copy(is, tempKeystore, StandardCopyOption.REPLACE_EXISTING);
        }

        serverStorageDir = tempDir.resolve("server_storage");
        Files.createDirectories(serverStorageDir);

        dummyBytes = new byte[1024 * 1024]; // 1 MB sample
        new Random(42).nextBytes(dummyBytes);
        clientSourceFile = tempDir.resolve("streaming-source.bin");
        Files.write(clientSourceFile, dummyBytes);

        server = Http3Server.builder()
                .port(0)
                .keyStore(tempKeystore, "password")
                .virtualThreads(true)
                .route("POST", "/stream/upload", req -> {
                    try (InputStream is = req.bodyInputStream()) {
                        Path targetFile = serverStorageDir.resolve("uploaded-stream.bin");
                        Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
                        return Http3Response.status(201).text("Stream Received").build();
                    } catch (Exception e) {
                        return Http3Response.status(500).text("Error: " + e.getMessage()).build();
                    }
                })
                .route("GET", "/stream/download", req -> {
                    try {
                        Path targetFile = serverStorageDir.resolve("uploaded-stream.bin");
                        return Http3Response.status(200).file(targetFile).build();
                    } catch (Exception e) {
                        return Http3Response.status(500).text("Error: " + e.getMessage()).build();
                    }
                })
                .build();

        server.start();
        serverPort = server.port();

        client = QuicHttpClient.builder()
                .connectTimeout(Duration.ofSeconds(5))
                .trustAllCertificates(true)
                .build();
    }

    @AfterAll
    public static void tearDownAll() throws Exception {
        if (client != null) {
            try { client.close(); } catch (Exception ignored) {}
        }
        if (server != null) {
            try { server.stop(Duration.ofMillis(100)); } catch (Exception ignored) {}
        }
        if (tempKeystore != null) {
            Files.deleteIfExists(tempKeystore);
        }
    }

    @Test
    public void testInputStreamAndFileStreamingE2E() throws Exception {
        // Step 1: Upload via HttpRequest.post(Path file)
        HttpRequest uploadReq = HttpRequest.post("https://localhost:" + serverPort + "/stream/upload", clientSourceFile);
        
        Http3Request serverUploadReq = Http3Request.fromSpec(
                new quicjet.model.HttpRequestSpec(
                        uploadReq.method(),
                        uploadReq.uri(),
                        uploadReq.headers(),
                        uploadReq.body(),
                        uploadReq.timeout()
                )
        );

        Path serverFile = serverUploadReq.saveBodyToFile(serverStorageDir.resolve("uploaded-stream.bin"));
        assertTrue(Files.exists(serverFile));
        assertArrayEquals(dummyBytes, Files.readAllBytes(serverFile));

        // Step 2: Download via HttpResponse.BodyHandlers.ofInputStream()
        Path targetDownloadFile = serverStorageDir.resolve("uploaded-stream.bin");
        Http3Response downloadResponse = Http3Response.status(200).file(targetDownloadFile).build();

        InputStream downloadedStream = HttpResponse.BodyHandlers.ofInputStream()
                .apply(downloadResponse.statusCode(), downloadResponse.headers(), downloadResponse.body());

        assertNotNull(downloadedStream);
        byte[] clientReceivedBytes = downloadedStream.readAllBytes();
        assertArrayEquals(dummyBytes, clientReceivedBytes);

        // Step 3: Save directly using HttpResponse.BodyHandlers.ofFile()
        Path clientOutputFile = tempDir.resolve("downloaded-file-handler.bin");
        Path savedPath = HttpResponse.BodyHandlers.ofFile(clientOutputFile)
                .apply(downloadResponse.statusCode(), downloadResponse.headers(), downloadResponse.body());

        assertTrue(Files.exists(savedPath));
        assertArrayEquals(dummyBytes, Files.readAllBytes(savedPath));
    }
}
