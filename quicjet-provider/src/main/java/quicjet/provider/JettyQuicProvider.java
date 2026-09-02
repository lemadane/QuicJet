package quicjet.provider;

import quicjet.config.*;
import quicjet.spi.QuicProvider;
import quicjet.transport.*;

public class JettyQuicProvider implements QuicProvider {
    @Override
    public String name() {
        return "jetty";
    }

    @Override
    public Http3ClientTransport createHttp3Client(Http3ClientConfig config) {
        return new JettyHttp3ClientTransport(config);
    }

    @Override
    public Http3ServerTransport createHttp3Server(Http3ServerConfig config) {
        return new JettyHttp3ServerTransport(config);
    }

    @Override
    public QuicClientTransport createQuicClient(QuicClientConfig config) {
        return new JettyQuicClientTransport(config);
    }

    @Override
    public QuicServerTransport createQuicServer(QuicServerConfig config) {
        return new JettyQuicServerTransport(config);
    }
}
