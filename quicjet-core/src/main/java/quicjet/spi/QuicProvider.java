package quicjet.spi;

import quicjet.config.Http3ClientConfig;
import quicjet.config.Http3ServerConfig;
import quicjet.config.QuicClientConfig;
import quicjet.config.QuicServerConfig;
import quicjet.transport.Http3ClientTransport;
import quicjet.transport.Http3ServerTransport;
import quicjet.transport.QuicClientTransport;
import quicjet.transport.QuicServerTransport;

public interface QuicProvider {
    String name();

    QuicClientTransport createQuicClient(QuicClientConfig config);
    QuicServerTransport createQuicServer(QuicServerConfig config);

    Http3ClientTransport createHttp3Client(Http3ClientConfig config);
    Http3ServerTransport createHttp3Server(Http3ServerConfig config);
}
