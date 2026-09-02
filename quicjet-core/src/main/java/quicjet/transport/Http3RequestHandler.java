package quicjet.transport;

import quicjet.model.HttpRequestSpec;
import quicjet.model.HttpResponseSpec;

@FunctionalInterface
public interface Http3RequestHandler {
    HttpResponseSpec handle(HttpRequestSpec request) throws Exception;
}
