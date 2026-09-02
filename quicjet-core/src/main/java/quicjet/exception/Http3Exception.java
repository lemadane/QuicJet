package quicjet.exception;

public class Http3Exception extends QuicException {
    private final int statusCode;

    public Http3Exception(String message) {
        this(message, 0);
    }

    public Http3Exception(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public Http3Exception(String message, Throwable cause) {
        this(message, 0, cause);
    }

    public Http3Exception(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
