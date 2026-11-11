package pl.pricemonitor.fetcher.feed;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import pl.pricemonitor.fetcher.FetcherProperties;

/** Downloads price files over HTTP with strict timeouts. */
@Component
public class FeedClient {

    private final RestClient restClient;

    public FeedClient(RestClient.Builder builder, FetcherProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        this.restClient = builder.requestFactory(factory)
                .defaultHeader("User-Agent", "PriceMonitor-Fetcher/0.1 (projekt dyplomowy)")
                .build();
    }

    /**
     * @throws org.springframework.web.client.RestClientException on connection errors, timeouts and non-2xx responses
     */
    public byte[] download(String url) {
        byte[] body = restClient.get().uri(url).retrieve().body(byte[].class);
        return body == null ? new byte[0] : body;
    }
}
