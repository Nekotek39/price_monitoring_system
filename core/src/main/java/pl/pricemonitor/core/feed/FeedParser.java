package pl.pricemonitor.core.feed;

import java.io.InputStream;
import java.util.List;

/** Reads a shop price file into the common offer model. One implementation per format. */
public interface FeedParser {

    FeedFormat format();

    /**
     * @throws FeedParseException when the file is malformed or a mandatory field is missing
     */
    List<FeedOffer> parse(InputStream input);
}
