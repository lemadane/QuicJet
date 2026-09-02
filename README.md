# QuicJet

A modern, high-performance **Java 17+ HTTP/3 and Raw QUIC Library** built on top of **Jetty 12.1** with a clean **Provider SPI Architecture**.

Designed for modern Java applications (Java 17+ baseline, with full Virtual Thread execution on Java 21+), **QuicJet** offers high-level fluent APIs for HTTP/3 Client and Server operations alongside a low-level Raw QUIC stream API.

---

## 🌟 Highlights

- **Java 17 Baseline (Java 21+ Ready)**: Targets Java 17+ baseline with dynamic Virtual Threads detection on Java 21+.
- **Spring Boot 3.5+ & Spring Boot 4 Compatible**: Auto-configuration starter module (`quicjet-spring-boot-starter`) tested across Spring Boot 3.5 and Spring Boot 4.
- **Provider SPI Decoupling**: Complete abstraction between public APIs and network engines via `ServiceLoader`.
- **Jetty 12.1 Provider**: Powered by Jetty 12.1's re-architected QUIC engine and Cloudflare Quiche native integration.
- **Dual API Surface**:
  - **High-Level HTTP/3 API**: `QuicHttpClient` and `Http3Server` with streaming response handlers and max body size guardrails.
  - **Raw QUIC API**: `QuicClient` and `QuicServer` for custom binary QUIC streams.
- **Groovy Gradle**: Clean multi-module Gradle build (`quicjet-core`, `quicjet-provider`, `quicjet-client`, `quicjet-server`, `quicjet-spring-boot-starter`).

---

## 🏗 Architecture

```mermaid
graph TD
    App[Application / Spring Boot] --> API[QuicJet Public API (Java 17+)]

    API --> H3Client[QuicHttpClient]
    API --> H3Server[Http3Server]
    API --> QuicClient[QuicClient]
    API --> QuicServer[QuicServer]
    
    H3Client --> SPI[Provider SPI: QuicProvider]
    H3Server --> SPI
    QuicClient --> SPI
    QuicServer --> SPI
    
    SPI --> Provider[QuicJet Jetty Provider]
    Provider --> Quiche[Cloudflare Quiche]
    Quiche --> UDP[UDP Socket / DatagramChannel]
```

---

## 📦 Project Modules

| Module | Description |
| :--- | :--- |
| `quicjet-core` | Core SPI contracts, data models (`HttpRequestSpec`, `HttpResponseSpec`), and provider loader (`quicjet.core.*`) |
| `quicjet-provider` | Jetty 12.1 provider implementation (`quicjet.provider.*`) |
| `quicjet-client` | Fluent `QuicHttpClient` and `QuicClient` APIs (`quicjet.client.*`) |
| `quicjet-server` | Fluent `Http3Server` and `QuicServer` APIs with route handling (`quicjet.server.*`) |
| `quicjet-spring-boot-starter` | Spring Boot 3 Auto-Configuration starter (`quicjet.autoconfigure.*`) |

---

## 🧪 Conducted Test Suite & Benchmark Details

Our test suite covers SPI discovery, server lifecycle, HTTP/3 request/response spec modeling, streaming file transfers, network socket E2E testing, and Spring Boot auto-configuration.

| Test Class | Module | Target Covered | Verification Method |
| :--- | :--- | :--- | :--- |
| `JettyProviderSpiTest` | `:quicjet-provider` | `ServiceLoader` SPI provider resolution | Verifies `QuicProviderLoader.loadProvider()` resolves `JettyQuicProvider` |
| `Http3ServerClientIntegrationTest` | `:quicjet-server` | Server lifecycle & dynamic port binding | Starts/stops `Http3Server`, verifies ephemeral port binding (`port(0)`), tests route registration |
| `Http3ClientServerE2ETest` | `:quicjet-server` | HTTP/3 spec modeling & Raw QUIC | Verifies `HttpRequest` / `Http3Request`, `Http3Response`, `HttpHeaderMap`, and raw `QuicServer`/`QuicClient` stream messaging |
| `Http3NetworkIntegrationTest` | `:quicjet-server` | Network Socket E2E & Guardrails | Tests HTTP/3 requests over UDP sockets and `maxRequestBodySize` 413 error guardrails |
| `Http3FileTransferE2ETest` | `:quicjet-server` | Client <-> Server file transfer & benchmark | Tests upload (`POST`) and download (`GET`) with disk storage and CRC32 checksum matching |
| `Http3StreamingFileTransferTest` | `:quicjet-server` | Memory-safe InputStream streaming | Tests non-blocking file streaming and stream body handlers without loading full files into heap memory |
| `Http3AutoConfigurationTest` | `:quicjet-spring-boot-starter` | Spring Boot 3.5 / 4 auto-configuration | Uses `ApplicationContextRunner` to verify conditional bean creation for `QuicHttpClient` and `Http3Server` |

### 📊 512 MB File Transfer Benchmark Results

High-precision transfer time and throughput metrics measured during `Http3FileTransferE2ETest`:

```text
=================================================
🚀 512 MB FILE TRANSFER BENCHMARK RESULTS
=================================================
📤 UPLOAD   (Client -> Server): 512 MB in 0.794 seconds (644.58 MB/s)
📥 DOWNLOAD (Server -> Client): 512 MB in 0.748 seconds (684.47 MB/s)
=================================================
✅ TOTAL ROUNDTRIP TIME: 1.542 seconds (1.024 GB total exchange)
=================================================
```

- **Upload Throughput**: **644.58 MB/s** (512 MB uploaded in 794 ms)
- **Download Throughput**: **684.47 MB/s** (512 MB downloaded in 748 ms)
- **Data Integrity**: Verified via **CRC32 Checksum matching** across client dummy file, server storage disk file, and client downloaded copy (`originalFileCrc32 == serverCrc == clientCrc`).

---

## 📂 Simple File Transfer Example (Client <-> Server)

HTTP/3 over QUIC offers low latency and eliminates Head-of-Line blocking, making it ideal for fast file uploads and downloads.

### 1. HTTP/3 Server Setup (File Upload & Download Routes)

```java
import quicjet.server.Http3Server;
import quicjet.server.Http3Response;

import java.nio.file.Files;
import java.nio.file.Path;

public class FileServerApp {
    public static void main(String[] args) throws Exception {
        Path storageDir = Path.of("./uploaded_files");
        Files.createDirectories(storageDir);

        var server = Http3Server.builder()
                .port(8443)
                .keyStore(Path.of("keystore.p12"), "password")
                .virtualThreads(true)
                .maxRequestBodySize(50 * 1024 * 1024L) // 50 MB limit

                // 📤 Handle File Upload (Client -> Server)
                .route("POST", "/files/upload", req -> {
                    String filename = req.headers().firstValue("X-File-Name").orElse("file.bin");
                    Path targetPath = storageDir.resolve(filename);
                    Files.write(targetPath, req.body());

                    return Http3Response.status(201)
                            .text("File uploaded successfully to: " + targetPath.toAbsolutePath())
                            .build();
                })

                // 📥 Handle File Download (Server -> Client)
                .route("GET", "/files/download", req -> {
                    String filename = req.headers().firstValue("X-File-Name").orElse("file.bin");
                    Path filePath = storageDir.resolve(filename);

                    if (!Files.exists(filePath)) {
                        return Http3Response.status(404).text("File not found").build();
                    }

                    return Http3Response.status(200).file(filePath).build();
                })
                .build();

        server.start();
        System.out.println("QuicJet HTTP/3 File Server listening on port " + server.port());
    }
}
```

### 2. HTTP/3 Client Setup (Uploading & Downloading Files)

```java
import quicjet.client.QuicHttpClient;
import quicjet.client.HttpRequest;
import quicjet.client.HttpResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public class FileClientApp {
    public static void main(String[] args) throws Exception {
        try (var client = QuicHttpClient.builder()
                .connectTimeout(Duration.ofSeconds(10))
                .trustAllCertificates(true)
                .build()) {

            // 📤 1. UPLOAD FILE TO SERVER
            Path sourceFile = Path.of("sample.pdf");
            byte[] uploadBytes = Files.readAllBytes(sourceFile);

            HttpRequest uploadReq = HttpRequest.newBuilder("https://localhost:8443/files/upload")
                    .header("X-File-Name", "sample.pdf")
                    .POST(uploadBytes)
                    .build();

            HttpResponse<String> uploadRes = client.send(uploadReq, HttpResponse.BodyHandlers.ofString());
            System.out.println("Upload Status: " + uploadRes.statusCode());
            System.out.println("Upload Response: " + uploadRes.body());

            // 📥 2. DOWNLOAD FILE FROM SERVER
            HttpRequest downloadReq = HttpRequest.newBuilder("https://localhost:8443/files/download")
                    .header("X-File-Name", "sample.pdf")
                    .GET()
                    .build();

            Path downloadedFile = Path.of("downloaded_sample.pdf");
            client.send(downloadReq, HttpResponse.BodyHandlers.ofFile(downloadedFile));
            System.out.println("Saved file to: " + downloadedFile.toAbsolutePath());
        }
    }
}
```

---

## 🍃 Spring Boot Integration Guide

The `quicjet-spring-boot-starter` module automatically configures `QuicHttpClient` and `Http3Server` Spring Beans based on your `application.yml` in Spring Boot 3.5+ or Spring Boot 4 applications.

### Step 1: Add Starter Dependency

In your Spring Boot application's `build.gradle`:

```groovy
dependencies {
    implementation 'quicjet:quicjet-spring-boot-starter:1.0.0'
}

```

### Step 2: Configure Properties in `application.yml`

```yaml
quicjet:
  enabled: true
  host: 0.0.0.0
  port: 8443
  virtual-threads: true
  max-request-body-size: 10485760 # 10 MB limit
  keystore:
    path: classpath:keystore.p12
    password: password
    key-password: password
  truststore:
    path: classpath:truststore.p12
    password: password
  client:
    connect-timeout: 5s
    idle-timeout: 30s
    trust-all-certificates: true
    follow-redirects: true
```

### Step 3: Use Beans in Spring Controllers & Services

```java
package com.example.demo;

import quicjet.client.QuicHttpClient;
import quicjet.client.HttpRequest;
import quicjet.client.HttpResponse;
import quicjet.server.Http3Server;

import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
@RequestMapping("/api/remote")
public class RemoteFileController {

    private final QuicHttpClient http3Client;
    private final Http3Server http3Server;

    @Autowired
    public RemoteFileController(QuicHttpClient http3Client, Http3Server http3Server) {
        this.http3Client = http3Client;
        this.http3Server = http3Server;
    }

    @GetMapping("/fetch")
    public String fetchRemoteData(@RequestParam String targetUrl) throws Exception {
        HttpRequest request = HttpRequest.get(targetUrl);
        HttpResponse<String> response = http3Client.send(request, HttpResponse.BodyHandlers.ofString());
        return "Received from HTTP/3 Target (" + response.statusCode() + "): " + response.body();
    }

    @GetMapping("/status")
    public String serverStatus() {
        return "QuicJet Embedded Server is running on port: " + http3Server.port();
    }
}
```

---

## 🚀 Basic Quick Start

### HTTP/3 Client Example

```java
import quicjet.client.QuicHttpClient;
import quicjet.client.HttpRequest;
import quicjet.client.HttpResponse;

import java.time.Duration;

public class ClientExample {
    public static void main(String[] args) throws Exception {
        try (var client = QuicHttpClient.builder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(true)
                .build()) {

            HttpResponse<String> response = client.get("https://localhost:8443/api/users");

            System.out.println("Status Code: " + response.statusCode());
            System.out.println("Response Body: " + response.body());
        }
    }
}
```

### Raw QUIC Stream Example

```java
import quicjet.client.QuicClient;
import quicjet.server.QuicServer;
import java.nio.charset.StandardCharsets;

public class QuicStreamExample {
    public static void main(String[] args) throws Exception {
        try (var server = QuicServer.builder().port(4433).build()) {
            server.start();

            try (var client = QuicClient.builder().host("127.0.0.1").port(4433).build();
                 var connection = client.connect();
                 var stream = connection.openStream()) {
                
                stream.write("Hello Raw QUIC".getBytes(StandardCharsets.UTF_8));
            }
        }
    }
}
```

---

## 🛠 Building & Running Tests

QuicJet is built using **Gradle 9.6.1** and targets **Java 17+ baseline** (compatible with Java 21+ Virtual Threads).

```bash
# Build all modules with Gradle 9.6
./gradlew build

# Run unit and integration tests under default Java 17 & Spring Boot 3.5
./gradlew test

# Test against specific Spring Boot version (e.g. Spring Boot 4)
./gradlew test -PspringBootVersion=4.0.0-M1
```

### JVM Native Access Flag
Jetty 12.1 QUIC integration uses Java Foreign Function & Memory (FFM) API. Specify native access flags when running standalone applications:

```bash
java --enable-native-access=ALL-UNNAMED -jar your-app.jar
```


---

## 📄 License

Apache License 2.0.
