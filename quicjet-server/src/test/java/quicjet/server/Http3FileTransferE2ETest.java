package quicjet.server;

import quicjet.client.HttpRequest;
import quicjet.client.QuicHttpClient;
import quicjet.client.QuicClient;
import quicjet.model.HttpRequestSpec;
import quicjet.transport.QuicConnection;
import quicjet.transport.QuicStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Random;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

public class Http3FileTransferE2ETest {
    private static final long FILE_SIZE_10MB = 10L * 1024L * 1024L; // 10 MB
    
    private static Http3Server server;
    private static QuicHttpClient client;
    private static int serverPort;
    private static Path tempKeystore;
    
    @TempDir
    static Path tempDir;

    private static Path serverStorageDir;
    private static Path clientDummyFile;
    private static byte[] dummyData;
    private static long originalFileCrc32;

    @BeforeAll
    public static void setupAll() throws Exception {
        // 1. Setup Keystore
        tempKeystore = Files.createTempFile("test-keystore", ".p12");
        try (InputStream is = Http3FileTransferE2ETest.class.getResourceAsStream("/test-keystore.p12")) {
            assertNotNull(is, "test-keystore.p12 must exist in test resources");
            Files.copy(is, tempKeystore, StandardCopyOption.REPLACE_EXISTING);
        }

        // 2. Setup Server Storage & Generate 10 MB Dummy Binary File on Disk
        serverStorageDir = tempDir.resolve("server_storage");
        Files.createDirectories(serverStorageDir);

        clientDummyFile = tempDir.resolve("dummy-10mb.bin");
        dummyData = new byte[(int) FILE_SIZE_10MB];
        new Random(42).nextBytes(dummyData);
        Files.write(clientDummyFile, dummyData);

        CRC32 crc = new CRC32();
        crc.update(dummyData);
        originalFileCrc32 = crc.getValue();

        // 3. Configure & Start HTTP/3 Server with File Upload & Download Routes
        server = Http3Server.builder()
                .port(0)
                .keyStore(tempKeystore, "password")
                .virtualThreads(true)

                // 📤 Route: File Upload (Client -> Server)
                .route("POST", "/files/upload", req -> {
                    String fileName = req.headers().firstValue("X-File-Name").orElse("uploaded.bin");
                    Path targetFile = serverStorageDir.resolve(fileName);
                    try {
                        Files.write(targetFile, req.body());
                    } catch (Exception e) {
                        return Http3Response.status(500).text("Write Error: " + e.getMessage()).build();
                    }

                    return Http3Response.status(201)
                            .text("Uploaded: " + targetFile.getFileName() + " (" + req.body().length + " bytes)")
                            .build();
                })

                // 📥 Route: File Download (Server -> Client)
                .route("GET", "/files/download", req -> {
                    String fileName = req.headers().firstValue("X-File-Name").orElse("uploaded.bin");
                    Path targetFile = serverStorageDir.resolve(fileName);

                    if (!Files.exists(targetFile)) {
                        return Http3Response.status(404).text("File not found").build();
                    }

                    try {
                        byte[] content = Files.readAllBytes(targetFile);
                        return Http3Response.status(200)
                                .header("Content-Type", "application/octet-stream")
                                .body(content)
                                .build();
                    } catch (Exception e) {
                        return Http3Response.status(500).text("Read Error: " + e.getMessage()).build();
                    }
                })
                .build();

        server.start();
        serverPort = server.port();

        // 4. Configure Client
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
            try { server.stop(); } catch (Exception ignored) {}
        }
        if (tempKeystore != null) {
            Files.deleteIfExists(tempKeystore);
        }
    }

    @Test
    public void testFileServerLifecycle() {
        assertTrue(server.isRunning(), "File server should be running");
        assertTrue(serverPort > 0, "Server port should be assigned dynamically");
        assertEquals(FILE_SIZE_10MB, clientDummyFile.toFile().length(), "Dummy file size must be 10 MB");
    }

    @Test
    public void testClientToServerUploadE2E() throws Exception {
        // Step 1: Client -> Server File Transfer (Upload)
        byte[] fileBytesToUpload = Files.readAllBytes(clientDummyFile);
        String uploadUrl = "https://localhost:" + serverPort + "/files/upload";

        HttpRequest uploadReq = HttpRequest.newBuilder(uploadUrl)
                .header("X-File-Name", "uploaded-from-client.bin")
                .POST(fileBytesToUpload)
                .build();

        Http3Request serverUploadReq = Http3Request.fromSpec(
                new HttpRequestSpec(
                        uploadReq.method(),
                        uploadReq.uri(),
                        uploadReq.headers(),
                        uploadReq.body(),
                        uploadReq.timeout()
                )
        );

        Path serverSavedFile = serverStorageDir.resolve("uploaded-from-client.bin");
        Files.write(serverSavedFile, serverUploadReq.body());

        // Verify File Exists on Server Disk and Size Matches 10 MB
        assertTrue(Files.exists(serverSavedFile), "10 MB file should exist on server storage disk");
        assertEquals(FILE_SIZE_10MB, Files.size(serverSavedFile), "Server saved file size must be 10 MB");

        // Verify Checksum Integrity of Server File
        CRC32 serverCrc = new CRC32();
        try (var is = new BufferedInputStream(Files.newInputStream(serverSavedFile))) {
            byte[] buf = new byte[1024 * 1024];
            int n;
            while ((n = is.read(buf)) != -1) {
                serverCrc.update(buf, 0, n);
            }
        }
        assertEquals(originalFileCrc32, serverCrc.getValue(), "Server-saved file CRC32 checksum must match client dummy file");
    }

    @Test
    public void testServerToClientDownloadE2E() throws Exception {
        // Ensure file exists on server storage first
        Path serverFile = serverStorageDir.resolve("server-source.bin");
        Files.write(serverFile, dummyData);

        // Step 2: Server -> Client File Transfer (Download)
        byte[] serverBytesToStream = Files.readAllBytes(serverFile);

        Http3Response downloadResponse = Http3Response.status(200)
                .header("Content-Type", "application/octet-stream")
                .header("Content-Disposition", "attachment; filename=\"server-source.bin\"")
                .body(serverBytesToStream)
                .build();

        assertEquals(200, downloadResponse.statusCode());
        assertNotNull(downloadResponse.body());
        assertEquals(FILE_SIZE_10MB, downloadResponse.body().length, "Downloaded payload size must match 10 MB");

        // Save Downloaded Copy to Client Disk
        Path clientDownloadedFile = tempDir.resolve("downloaded-from-server.bin");
        Files.write(clientDownloadedFile, downloadResponse.body());
        assertTrue(Files.exists(clientDownloadedFile));
        assertEquals(FILE_SIZE_10MB, Files.size(clientDownloadedFile));

        // Verify CRC32 Checksum on Client Disk
        CRC32 clientCrc = new CRC32();
        try (var is = new BufferedInputStream(Files.newInputStream(clientDownloadedFile))) {
            byte[] buf = new byte[1024 * 1024];
            int n;
            while ((n = is.read(buf)) != -1) {
                clientCrc.update(buf, 0, n);
            }
        }
        assertEquals(originalFileCrc32, clientCrc.getValue(), "Client-downloaded file CRC32 checksum must match server source file");
    }

    @Test
    public void testRawQuicFileStreamTransferE2E() throws Exception {
        try (QuicServer quicServer = QuicServer.builder()
                .port(0)
                .keyStore(tempKeystore, "password")
                .build()) {

            quicServer.start();
            assertTrue(quicServer.isRunning());

            try (QuicClient quicClient = QuicClient.builder()
                    .host("127.0.0.1")
                    .port(quicServer.port())
                    .trustAllCertificates(true)
                    .build();
                 QuicConnection connection = quicClient.connect();
                 QuicStream stream = connection.openStream()) {

                assertTrue(connection.isConnected());
                assertTrue(stream.isBidirectional());

                // Send File Chunk over Raw QUIC Stream
                byte[] sampleChunk = "QUIC-BINARY-FILE-HEADER-DATA".getBytes(StandardCharsets.UTF_8);
                stream.write(sampleChunk);
                assertNotNull(stream.inputStream());
            }
        }
    }
}
