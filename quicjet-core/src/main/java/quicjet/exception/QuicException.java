package quicjet.exception;

public class QuicException extends RuntimeException {
    public QuicException(String message) {
        super(message);
    }

    public QuicException(String message, Throwable cause) {
        super(message, cause);
    }
}
