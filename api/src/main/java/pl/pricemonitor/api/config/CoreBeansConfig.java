package pl.pricemonitor.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.pricemonitor.core.matching.MatcherSettings;
import pl.pricemonitor.core.matching.OfferMatcher;
import pl.pricemonitor.core.pricing.PricingEngine;

/** Framework-free domain services from the core module exposed as Spring beans. */
@Configuration
public class CoreBeansConfig {

    @Bean
    public PricingEngine pricingEngine() {
        return new PricingEngine();
    }

    @Bean
    public OfferMatcher offerMatcher(ApiProperties properties) {
        return new OfferMatcher(new MatcherSettings(properties.matcherAcceptThreshold(), properties.matcherReviewMargin()));
    }
}
