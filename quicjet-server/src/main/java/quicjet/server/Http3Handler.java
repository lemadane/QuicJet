package quicjet.server;

@FunctionalInterface
public interface Http3Handler {
    Http3Response handle(Http3Request request) throws Exception;
}
