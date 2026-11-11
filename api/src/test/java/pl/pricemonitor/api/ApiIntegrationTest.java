package pl.pricemonitor.api;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests of the REST API on a real PostgreSQL with the sample data (V2 migration).
 * Requires Docker; skipped automatically without it.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "pricemonitor.scheduling.enabled=false")
@AutoConfigureMockMvc
@Transactional
class ApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager em;

    /** Inserts a matched offer of product 13 (Sony WH-1000XM6) with one price row. */
    private void offer(long shopId, String price, String fetchedAgo) {
        Long offerId = jdbc.queryForObject("""
                INSERT INTO oferta (sklep_id, produkt_id, id_w_sklepie, nazwa_w_sklepie, stan_towaru, ostatnie_pobranie)
                VALUES (?, 13, ?, 'Sony WH-1000XM6', 'NEW', now() - CAST(? AS interval)) RETURNING id
                """, Long.class, shopId, "T-" + shopId, fetchedAgo);
        jdbc.update("INSERT INTO cena (oferta_id, cena, dostepnosc, zrodlo, pobrano) VALUES (?, CAST(? AS numeric), true, 'CYCLIC', now())",
                offerId, price);
        jdbc.update("INSERT INTO dopasowanie (oferta_id, produkt_id, metoda, pewnosc, status) "
                + "VALUES (?, 13, 'IDENTIFIER', 1.0, 'AUTOMATIC')", offerId);
    }

    @Test
    void searchesProductsByNameAndWithTypos() throws Exception {
        mvc.perform(get("/api/products").param("q", "iphone"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
        mvc.perform(get("/api/products").param("q", "playstaton 5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].brand").value("Sony"));
        mvc.perform(get("/api/products/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void sortsOffersByPriceAndFetchesStaleDataOnDemand() throws Exception {
        offer(1, "1549.00", "1 minute");
        offer(2, "1499.00", "2 hours");

        mvc.perform(get("/api/products/13/offers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offers.length()").value(2))
                .andExpect(jsonPath("$.offers[0].price").value(1499.0))
                .andExpect(jsonPath("$.offers[0].shopName").value("TechPunkt"))
                .andExpect(jsonPath("$.refreshing").value(true))
                .andExpect(jsonPath("$.refreshingShops[0]").value("TechPunkt"));

        Integer jobs = jdbc.queryForObject("SELECT count(*) FROM zadanie_pobrania "
                + "WHERE typ = 'ON_DEMAND' AND produkt_id = 13 AND sklep_id = 2 AND status = 'PENDING'", Integer.class);
        assertEquals(Integer.valueOf(1), jobs);
    }

    @Test
    void popularityFollowsAdministratorThreshold() throws Exception {
        mvc.perform(put("/api/admin/settings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"popularity.threshold\": \"abc\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/settings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"popularity.threshold\": \"3\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['popularity.threshold']").value("3"));

        mvc.perform(get("/api/products/7/offers")).andExpect(jsonPath("$.popular").value(false));
        mvc.perform(get("/api/products/7/offers"));
        mvc.perform(get("/api/products/7/offers")).andExpect(jsonPath("$.popular").value(true));
    }

    @Test
    void matchesImportedOffersByGtin() throws Exception {
        String gtin = jdbc.queryForObject("SELECT gtin FROM produkt WHERE id = 1", String.class);
        jdbc.update("INSERT INTO oferta (sklep_id, id_w_sklepie, nazwa_w_sklepie, gtin, stan_towaru) "
                + "VALUES (1, 'NEW-1', 'Telefon Apple iPhone 16 128GB czarny', ?, 'NEW')", gtin);

        mvc.perform(post("/api/matches/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.automatic").value(1));
        em.flush(); // test shares the service transaction - push pending JPA changes before plain SQL
        Long productId = jdbc.queryForObject("SELECT produkt_id FROM oferta WHERE id_w_sklepie = 'NEW-1'", Long.class);
        assertEquals(Long.valueOf(1), productId);
    }

    @Test
    void validatesPricingRules() throws Exception {
        mvc.perform(get("/api/sellers/2/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].competitorShopIds.length()").value(3));
        mvc.perform(get("/api/sellers/1/rules")).andExpect(status().isBadRequest());

        String body = """
                {"productId": %d, "shopProductId": "MS-X", "strategy": "MATCH_LOWEST", "parameters": {},
                 "minPrice": %s, "maxPrice": %s, "maxChangePercent": 5, "minIntervalMinutes": 30,
                 "mode": "NOTIFY", "active": true, "competitorShopIds": [1, 2]}
                """;
        mvc.perform(post("/api/sellers/2/rules").contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(1, "1000", "900")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/sellers/2/rules").contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(13, "1000", "null")))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/sellers/2/rules").contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(1, "3500", "4500")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.competitorShopIds.length()").value(2));
        mvc.perform(post("/api/sellers/2/rules").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\": 2}"))
                .andExpect(status().isBadRequest());
    }
}
