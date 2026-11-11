package pl.pricemonitor.core.feed;

/** The price file cannot be read. */
public class FeedParseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FeedParseException(String message) {
        super(message);
    }

    public FeedParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
