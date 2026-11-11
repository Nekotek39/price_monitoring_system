-- Database schema of the price monitoring system (PostgreSQL 16).
-- Table and column names follow the ERD from the thesis; enum values are stored in English
-- so that they map 1:1 to Java enums (@Enumerated(EnumType.STRING)).
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE uzytkownik (
    id          BIGSERIAL PRIMARY KEY,
    email       VARCHAR(255) NOT NULL UNIQUE,
    haslo_hash  VARCHAR(255) NOT NULL,
    rola        VARCHAR(20)  NOT NULL CHECK (rola IN ('BUYER', 'SELLER', 'ADMIN')),
    utworzono   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE sklep (
    id                   BIGSERIAL PRIMARY KEY,
    nazwa                VARCHAR(100) NOT NULL,
    typ                  VARCHAR(20)  NOT NULL CHECK (typ IN ('MONITORED', 'SELLER')),
    wlasciciel_id        BIGINT REFERENCES uzytkownik (id) ON DELETE SET NULL,
    adres_pliku_cen      VARCHAR(255),
    format_danych        VARCHAR(10) CHECK (format_danych IN ('XML', 'JSON')),
    api_url              VARCHAR(255),
    api_klucz_enc        TEXT,
    status               VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'UNAVAILABLE')),
    niedostepny_do       TIMESTAMPTZ,
    min_odstep_zapytan_s INTEGER      NOT NULL DEFAULT 2
);

CREATE TABLE produkt (
    id                BIGSERIAL PRIMARY KEY,
    nazwa             VARCHAR(255) NOT NULL,
    marka             VARCHAR(100) NOT NULL,
    model             VARCHAR(100) NOT NULL,
    gtin              VARCHAR(14),
    mpn               VARCHAR(64),
    kategoria         VARCHAR(100) NOT NULL,
    atrybuty_wariantu JSONB        NOT NULL DEFAULT '{}'::jsonb,
    utworzono         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE wyszukiwanie (
    id            BIGSERIAL PRIMARY KEY,
    produkt_id    BIGINT REFERENCES produkt (id) ON DELETE SET NULL,
    uzytkownik_id BIGINT REFERENCES uzytkownik (id) ON DELETE SET NULL,
    fraza         VARCHAR(255) NOT NULL,
    utworzono     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE oferta (
    id                BIGSERIAL PRIMARY KEY,
    sklep_id          BIGINT       NOT NULL REFERENCES sklep (id) ON DELETE CASCADE,
    produkt_id        BIGINT REFERENCES produkt (id) ON DELETE SET NULL,
    id_w_sklepie      VARCHAR(64)  NOT NULL,
    nazwa_w_sklepie   VARCHAR(500) NOT NULL,
    adres_url         VARCHAR(500),
    gtin              VARCHAR(14),
    mpn               VARCHAR(64),
    atrybuty_wariantu JSONB        NOT NULL DEFAULT '{}'::jsonb,
    stan_towaru       VARCHAR(20)  NOT NULL CHECK (stan_towaru IN ('NEW', 'USED', 'REFURBISHED', 'UNKNOWN')),
    ostatnie_pobranie TIMESTAMPTZ,
    UNIQUE (sklep_id, id_w_sklepie)
);

CREATE TABLE cena (
    id            BIGSERIAL PRIMARY KEY,
    oferta_id     BIGINT        NOT NULL REFERENCES oferta (id) ON DELETE CASCADE,
    cena          NUMERIC(10, 2) NOT NULL,
    koszt_dostawy NUMERIC(10, 2),
    dostepnosc    BOOLEAN       NOT NULL,
    zrodlo        VARCHAR(20)   NOT NULL CHECK (zrodlo IN ('IMPORT', 'CYCLIC', 'ON_DEMAND')),
    pobrano       TIMESTAMPTZ   NOT NULL
);

CREATE TABLE dopasowanie (
    id              BIGSERIAL PRIMARY KEY,
    oferta_id       BIGINT       NOT NULL REFERENCES oferta (id) ON DELETE CASCADE,
    produkt_id      BIGINT       NOT NULL REFERENCES produkt (id) ON DELETE CASCADE,
    metoda          VARCHAR(20)  NOT NULL CHECK (metoda IN ('IDENTIFIER', 'ATTRIBUTES', 'NAME', 'MANUAL')),
    pewnosc         NUMERIC(4, 3) NOT NULL,
    status          VARCHAR(20)  NOT NULL CHECK (status IN ('AUTOMATIC', 'TO_REVIEW', 'APPROVED', 'REJECTED')),
    zweryfikowal_id BIGINT REFERENCES uzytkownik (id) ON DELETE SET NULL,
    utworzono       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (oferta_id, produkt_id)
);

CREATE TABLE regula_cenowa (
    id                    BIGSERIAL PRIMARY KEY,
    uzytkownik_id         BIGINT        NOT NULL REFERENCES uzytkownik (id) ON DELETE CASCADE,
    produkt_id            BIGINT        NOT NULL REFERENCES produkt (id) ON DELETE CASCADE,
    id_produktu_w_sklepie VARCHAR(64)   NOT NULL,
    strategia             VARCHAR(30)   NOT NULL CHECK (strategia IN ('MATCH_LOWEST', 'UNDERCUT_AMOUNT', 'UNDERCUT_PERCENT', 'AVERAGE_BAND')),
    parametry             JSONB         NOT NULL DEFAULT '{}'::jsonb,
    cena_min              NUMERIC(10, 2) NOT NULL,
    cena_max              NUMERIC(10, 2),
    max_zmiana_proc       NUMERIC(5, 2) NOT NULL,
    min_odstep_min        INTEGER       NOT NULL,
    tryb                  VARCHAR(20)   NOT NULL CHECK (tryb IN ('AUTOMATIC', 'NOTIFY')),
    aktywna               BOOLEAN       NOT NULL DEFAULT TRUE,
    CHECK (cena_max IS NULL OR cena_max >= cena_min),
    UNIQUE (uzytkownik_id, produkt_id)
);

CREATE TABLE regula_konkurent (
    id        BIGSERIAL PRIMARY KEY,
    regula_id BIGINT NOT NULL REFERENCES regula_cenowa (id) ON DELETE CASCADE,
    sklep_id  BIGINT NOT NULL REFERENCES sklep (id) ON DELETE CASCADE,
    UNIQUE (regula_id, sklep_id)
);

CREATE TABLE decyzja_cenowa (
    id                         BIGSERIAL PRIMARY KEY,
    regula_id                  BIGINT        NOT NULL REFERENCES regula_cenowa (id) ON DELETE CASCADE,
    cena_poprzednia            NUMERIC(10, 2) NOT NULL,
    cena_nowa                  NUMERIC(10, 2) NOT NULL,
    najnizsza_cena_konkurencji NUMERIC(10, 2) NOT NULL,
    uzasadnienie               TEXT          NOT NULL,
    status                     VARCHAR(20)   NOT NULL CHECK (status IN ('APPLIED', 'SENT', 'KEPT', 'FAILED')),
    utworzono                  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Job queue between the main application and the fetching process.
-- Workers take jobs with SELECT ... FOR UPDATE SKIP LOCKED.
CREATE TABLE zadanie_pobrania (
    id                BIGSERIAL PRIMARY KEY,
    sklep_id          BIGINT      NOT NULL REFERENCES sklep (id) ON DELETE CASCADE,
    oferta_id         BIGINT REFERENCES oferta (id) ON DELETE SET NULL,
    produkt_id        BIGINT REFERENCES produkt (id) ON DELETE SET NULL,
    typ               VARCHAR(20) NOT NULL CHECK (typ IN ('IMPORT', 'CYCLIC', 'ON_DEMAND')),
    priorytet         SMALLINT    NOT NULL,
    status            VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'IN_PROGRESS', 'DONE', 'FAILED')),
    liczba_prob       SMALLINT    NOT NULL DEFAULT 0,
    blad              TEXT,
    utworzono         TIMESTAMPTZ NOT NULL DEFAULT now(),
    nie_wczesniej_niz TIMESTAMPTZ NOT NULL DEFAULT now(),
    rozpoczeto        TIMESTAMPTZ,
    zakonczono        TIMESTAMPTZ
);

-- System settings changed by the administrator at runtime (read by api and fetcher).
CREATE TABLE ustawienie (
    klucz   VARCHAR(100) PRIMARY KEY,
    wartosc VARCHAR(255) NOT NULL
);

CREATE INDEX idx_produkt_nazwa_trgm ON produkt USING gin (nazwa gin_trgm_ops);
CREATE INDEX idx_oferta_nazwa_trgm ON oferta USING gin (nazwa_w_sklepie gin_trgm_ops);
CREATE INDEX idx_produkt_gtin ON produkt (gtin);
CREATE INDEX idx_oferta_produkt ON oferta (produkt_id);
CREATE INDEX idx_wyszukiwanie_produkt_czas ON wyszukiwanie (produkt_id, utworzono DESC);
CREATE INDEX idx_cena_oferta_czas ON cena (oferta_id, pobrano DESC);
CREATE INDEX idx_zadanie_kolejka ON zadanie_pobrania (priorytet DESC, utworzono) WHERE status = 'PENDING';
CREATE INDEX idx_zadanie_zakonczono ON zadanie_pobrania (zakonczono);
CREATE INDEX idx_decyzja_regula_czas ON decyzja_cenowa (regula_id, utworzono DESC);

INSERT INTO ustawienie (klucz, wartosc) VALUES
    ('popularity.threshold', '15'),
    ('popularity.window-days', '7'),
    ('data.max-age-minutes', '30'),
    ('indexing.interval-minutes', '10'),
    ('indexing.mode', 'DUAL');
