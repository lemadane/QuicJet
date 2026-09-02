package quicjet.provider;

import quicjet.config.Http3ClientConfig;
import quicjet.exception.Http3Exception;
import quicjet.model.HttpHeaderMap;
import quicjet.model.HttpRequestSpec;
import quicjet.model.HttpResponseSpec;
import quicjet.transport.Http3ClientTransport;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.Result;
import org.eclipse.jetty.client.BytesRequestContent;
import org.eclipse.jetty.client.BufferingResponseListener;
import org.eclipse.jetty.http3.client.HTTP3Client;
import org.eclipse.jetty.http3.client.transport.HttpClientTransportOverHTTP3;
import org.eclipse.jetty.io.Transport;
import org.eclipse.jetty.quic.client.ClientQuicConfiguration;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JettyHttp3ClientTransport implements Http3ClientTransport {
    private static final Logger LOG = LoggerFactory.getLogger(JettyHttp3ClientTransport.class);

    private final Http3ClientConfig config;
    private final HttpClient jettyHttpClient;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public JettyHttp3ClientTransport(Http3ClientConfig config) {
        this.config = config;

        SslContextFactory.Client sslContextFactory = new SslContextFactory.Client();
        if (config.trustAllCertificates()) {
            sslContextFactory.setTrustAll(true);
        }

        if (config.trustStorePath() != null) {
            sslContextFactory.setTrustStorePath(config.trustStorePath().toString());
            if (config.trustStorePassword() != null) {
                sslContextFactory.setTrustStorePassword(config.trustStorePassword());
            }
        }

        ClientQuicConfiguration quicConfig = new ClientQuicConfiguration();
        HTTP3Client http3Client = new HTTP3Client(quicConfig);

        HttpClientTransportOverHTTP3 transport = new HttpClientTransportOverHTTP3(http3Client, Transport.UDP_IP);

        this.jettyHttpClient = new HttpClient(transport);
        this.jettyHttpClient.setSslContextFactory(sslContextFactory);
        this.jettyHttpClient.setFollowRedirects(config.followRedirects());

        if (config.connectTimeout() != null) {
            this.jettyHttpClient.setConnectTimeout(config.connectTimeout().toMillis());
        }
    }

    @Override
    public synchronized void start() throws Exception {
        if (running.compareAndSet(false, true)) {
            LOG.info("Starting Jetty HTTP/3 Client Transport...");
            jettyHttpClient.start();
            LOG.info("Jetty HTTP/3 Client Transport started.");
        }
    }

    @Override
    public synchronized void stop() throws Exception {
        if (running.compareAndSet(true, false)) {
            LOG.info("Stopping Jetty HTTP/3 Client Transport...");
            jettyHttpClient.stop();
            LOG.info("Jetty HTTP/3 Client Transport stopped.");
        }
    }


    @Override
    public boolean isRunning() {
        return running.get() && jettyHttpClient.isRunning();
    }

    @Override
    public CompletableFuture<HttpResponseSpec> sendAsync(HttpRequestSpec requestSpec) {
        CompletableFuture<HttpResponseSpec> future = new CompletableFuture<>();
        try {
            if (!isRunning()) {
                start();
            }

            URI uri = requestSpec.uri();
            Request request = jettyHttpClient.newRequest(uri)
                    .method(requestSpec.method());

            if (requestSpec.headers() != null) {
                requestSpec.headers().asMap().forEach((k, v) -> {
                    for (String val : v) {
                        request.headers(headers -> headers.add(k, val));
                    }
                });
            }

            byte[] body = requestSpec.body();
            if (body != null && body.length > 0) {
                request.body(new BytesRequestContent(body));
            }

            if (requestSpec.timeout() != null) {
                request.timeout(requestSpec.timeout().toMillis(), TimeUnit.MILLISECONDS);
            }

            request.send(new BufferingResponseListener() {
                @Override
                public void onComplete(Result result) {
                    if (result.isFailed()) {
                        future.completeExceptionally(result.getFailure());
                        return;
                    }

                    Response response = result.getResponse();
                    HttpHeaderMap resHeaders = new HttpHeaderMap();
                    response.getHeaders().forEach(field -> resHeaders.add(field.getName(), field.getValue()));

                    byte[] resContent = getContent();
                    HttpResponseSpec spec = new HttpResponseSpec(response.getStatus(), resHeaders, resContent != null ? resContent : new byte[0]);
                    future.complete(spec);
                }
            });
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    @Override
    public HttpResponseSpec send(HttpRequestSpec request) throws Exception {
        try {
            long timeoutMs = request.timeout() != null ? request.timeout().toMillis() : 30000;
            return sendAsync(request).get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new Http3Exception("HTTP/3 request failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() throws Exception {
        stop();
    }
}
