package pl.pricemonitor.api.product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.pricemonitor.api.config.ApiProperties;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Server-Sent Events: pushes fresh offers to browsers waiting for on-demand results. */
@Service
public class OfferStreamService {

    private static final Logger log = LoggerFactory.getLogger(OfferStreamService.class);

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ApiProperties properties;

    public OfferStreamService(ApiProperties properties) {
        this.properties = properties;
    }

    public SseEmitter subscribe(Long productId, OffersResponse initial) {
        SseEmitter emitter = new SseEmitter(properties.sseTimeout().toMillis());
        emitters.computeIfAbsent(productId, id -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(productId, emitter));
        emitter.onTimeout(() -> remove(productId, emitter));
        emitter.onError(e -> remove(productId, emitter));
        send(productId, emitter, "offers", initial);
        return emitter;
    }

    public Set<Long> subscribedProducts() {
        return Set.copyOf(emitters.keySet());
    }

    public void publish(Long productId, String event, Object payload) {
        List<SseEmitter> list = emitters.get(productId);
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            send(productId, emitter, event, payload);
        }
    }

    private void send(Long productId, SseEmitter emitter, String event, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(event).data(payload, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE client of product {} disconnected", productId);
            remove(productId, emitter);
        }
    }

    private void remove(Long productId, SseEmitter emitter) {
        emitters.computeIfPresent(productId, (id, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }
}
