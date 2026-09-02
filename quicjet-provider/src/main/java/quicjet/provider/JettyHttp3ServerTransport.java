package quicjet.provider;

import quicjet.config.Http3ServerConfig;
import quicjet.exception.Http3Exception;
import quicjet.model.HttpHeaderMap;
import quicjet.model.HttpRequestSpec;
import quicjet.model.HttpResponseSpec;
import quicjet.transport.Http3RequestHandler;
import quicjet.transport.Http3ServerTransport;

import org.eclipse.jetty.http3.server.HTTP3ServerConnectionFactory;
import org.eclipse.jetty.quic.quiche.server.QuicheServerConnector;
import org.eclipse.jetty.quic.quiche.server.QuicheServerQuicConfiguration;
import org.eclipse.jetty.server.DatagramServerConnector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class JettyHttp3ServerTransport implements Http3ServerTransport {
    private static final Logger LOG = LoggerFactory.getLogger(JettyHttp3ServerTransport.class);

    private final Http3ServerConfig config;
    private final Server server;
    private final DatagramServerConnector connector;
    private final Map<String, Http3RequestHandler> routes = new ConcurrentHashMap<>();
    private volatile Http3RequestHandler defaultHandler;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public JettyHttp3ServerTransport(Http3ServerConfig config) {
        this.config = config;

        QueuedThreadPool threadPool = new QueuedThreadPool();
        if (config.virtualThreadsEnabled()) {
            try {
                java.lang.reflect.Method m = Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
                java.util.concurrent.ExecutorService executor = (java.util.concurrent.ExecutorService) m.invoke(null);
                threadPool.setVirtualThreadsExecutor(executor);
            } catch (Exception ignored) {
                threadPool.setVirtualThreadsExecutor(Executors.newCachedThreadPool());
            }
        }
        this.server = new Server(threadPool);


        SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
        if (config.keyStorePath() != null) {
            sslContextFactory.setKeyStorePath(config.keyStorePath().toString());
            if (config.keyStorePassword() != null) {
                sslContextFactory.setKeyStorePassword(config.keyStorePassword());
            }
            if (config.keyManagerPassword() != null) {
                sslContextFactory.setKeyManagerPassword(config.keyManagerPassword());
            }
        }

        HttpConfiguration httpConfig = new HttpConfiguration();
        HTTP3ServerConnectionFactory http3Factory = new HTTP3ServerConnectionFactory(httpConfig);

        DatagramServerConnector datagramConnector;
        try {
            QuicheServerQuicConfiguration quicConfig = new QuicheServerQuicConfiguration();
            quicConfig.configure(sslContextFactory);
            datagramConnector = new QuicheServerConnector(server, sslContextFactory, quicConfig, http3Factory);
        } catch (Exception e) {
            LOG.warn("Initializing DatagramServerConnector fallback due to TLS configuration exception: {}", e.getMessage());
            datagramConnector = new DatagramServerConnector(server, http3Factory);
        }

        datagramConnector.setHost(config.host());
        datagramConnector.setPort(config.port());

        this.connector = datagramConnector;
        this.server.addConnector(connector);

        this.server.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(Request request, Response response, Callback callback) throws Exception {
                String method = request.getMethod();
                String path = request.getHttpURI().getPath();

                Http3RequestHandler handler = findHandler(method, path);

                if (handler == null) {
                    LOG.debug("No handler found for {} {}", method, path);
                    response.setStatus(404);
                    response.write(true, BufferUtil.toBuffer("404 Not Found"), callback);
                    return true;
                }

                byte[] bodyBytes;
                try {
                    bodyBytes = readRequestBody(request, config.maxRequestBodySize());
                } catch (Http3Exception e) {
                    LOG.warn("Request body rejected for {} {}: {}", method, path, e.getMessage());
                    response.setStatus(413);
                    response.write(true, BufferUtil.toBuffer("413 Payload Too Large: " + e.getMessage()), callback);
                    return true;
                }

                HttpHeaderMap reqHeaders = new HttpHeaderMap();
                request.getHeaders().forEach(field -> reqHeaders.add(field.getName(), field.getValue()));

                URI requestUri = URI.create(request.getHttpURI().asString());
                HttpRequestSpec spec = new HttpRequestSpec(method, requestUri, reqHeaders, bodyBytes, null);

                HttpResponseSpec resultSpec;
                try {
                    resultSpec = handler.handle(spec);
                } catch (Exception e) {
                    LOG.error("Unhandled exception handling {} {}", method, path, e);
                    response.setStatus(500);
                    response.write(true, BufferUtil.toBuffer("500 Internal Server Error: " + e.getMessage()), callback);
                    return true;
                }

                response.setStatus(resultSpec.statusCode());
                resultSpec.headers().asMap().forEach((k, v) -> {
                    for (String val : v) {
                        response.getHeaders().add(k, val);
                    }
                });

                if (resultSpec.bodyStream() != null) {
                    try (InputStream is = resultSpec.bodyStream()) {
                        byte[] buffer = new byte[16384];
                        int n;
                        while ((n = is.read(buffer)) != -1) {
                            response.write(false, ByteBuffer.wrap(buffer, 0, n), Callback.NOOP);
                        }
                        callback.succeeded();
                    } catch (Exception e) {
                        LOG.error("Error streaming response body", e);
                        callback.failed(e);
                    }
                } else {
                    byte[] resBody = resultSpec.body();
                    if (resBody != null && resBody.length > 0) {
                        response.write(true, ByteBuffer.wrap(resBody), callback);
                    } else {
                        callback.succeeded();
                    }
                }

                return true;
            }
        });
    }

    private Http3RequestHandler findHandler(String method, String path) {
        if (path == null) path = "/";
        String key = method.toUpperCase() + ":" + path;
        Http3RequestHandler handler = routes.get(key);
        if (handler != null) return handler;

        if (path.endsWith("/") && path.length() > 1) {
            handler = routes.get(method.toUpperCase() + ":" + path.substring(0, path.length() - 1));
            if (handler != null) return handler;
        } else if (!path.endsWith("/")) {
            handler = routes.get(method.toUpperCase() + ":" + path + "/");
            if (handler != null) return handler;
        }

        for (Map.Entry<String, Http3RequestHandler> entry : routes.entrySet()) {
            String routeKey = entry.getKey();
            if (routeKey.endsWith("/*")) {
                String prefix = routeKey.substring(0, routeKey.length() - 1);
                if (key.startsWith(prefix)) {
                    return entry.getValue();
                }
            }
        }

        return defaultHandler;
    }

    private byte[] readRequestBody(Request request, long maxBytes) throws Exception {
        InputStream is = Request.asInputStream(request);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long totalRead = 0;
        int n;
        while ((n = is.read(buf)) != -1) {
            totalRead += n;
            if (totalRead > maxBytes) {
                throw new Http3Exception("Payload Too Large: Exceeded limit of " + maxBytes + " bytes");
            }
            baos.write(buf, 0, n);
        }
        return baos.toByteArray();
    }

    @Override
    public void registerRoute(String method, String path, Http3RequestHandler handler) {
        routes.put(method.toUpperCase() + ":" + path, handler);
        LOG.debug("Registered HTTP/3 route: {} {}", method.toUpperCase(), path);
    }

    @Override
    public void setDefaultHandler(Http3RequestHandler handler) {
        this.defaultHandler = handler;
        LOG.debug("Set default HTTP/3 request handler");
    }

    @Override
    public synchronized void start() throws Exception {
        if (running.compareAndSet(false, true)) {
            LOG.info("Starting Jetty HTTP/3 Server Transport on {}:{}...", config.host(), config.port());
            server.start();
            LOG.info("Jetty HTTP/3 Server Transport started on port {}", port());
        }
    }

    @Override
    public synchronized void stop() throws Exception {
        if (running.compareAndSet(true, false)) {
            LOG.info("Stopping Jetty HTTP/3 Server Transport...");
            server.stop();
            LOG.info("Jetty HTTP/3 Server Transport stopped.");
        }
    }

    @Override
    public boolean isRunning() {
        return running.get() && server.isRunning();
    }

    @Override
    public int port() {
        return connector.getLocalPort();
    }
}

