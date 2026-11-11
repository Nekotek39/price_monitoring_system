# Price Monitor – backend (Spring Boot)

Backend systemu monitorowania cen konkurencji z modułem dynamicznego dostosowywania cen
(projekt dyplomowy, WSB Merito Poznań). Java 21, Spring Boot 3.5, PostgreSQL 16, Maven (projekt wielomodułowy).

Szczegółowy opis działania kodu (architektura, algorytmy, kolejka zadań, konfiguracja, rozszerzanie): [docs/DOKUMENTACJA.md](docs/DOKUMENTACJA.md).

## Moduły

| Moduł | Co zawiera | Port |
|---|---|---|
| `core` | Czysta Java bez Springa: silnik cenowy (wzorzec Strategia + łańcuch ograniczeń), dopasowywanie ofert (GTIN/MPN, atrybuty wariantu, podobieństwo trigramowe), model pliku cenowego | – |
| `persistence` | Encje JPA, repozytoria, migracje Flyway (schemat + dane startowe), ustawienia systemu, popularność produktów | – |
| `api` | Aplikacja główna: REST API + SSE, harmonogram indeksowania, ścieżka „na żądanie”, dopasowywanie ofert, moduł cenowy, powiadomienia e-mail | 8080 |
| `fetcher` | Proces pobierający: bierze zadania z kolejki (`FOR UPDATE SKIP LOCKED`), pobiera i parsuje pliki XML/JSON, zapisuje oferty i historię cen | 8081 |
| `shop-simulator` | 5 sklepów symulowanych publikujących pliki cenowe, scenariusz zmian cen z zapisem „prawdziwych” cen, atrapa API sklepu sprzedawcy | 8090 |

Przepływ danych (zgodny z architekturą z sekcji C2):

```
przeglądarka ──REST/SSE──> api ──zadania──> [zadanie_pobrania w PostgreSQL] <──SKIP LOCKED── fetcher ──HTTP──> sklepy (pliki XML/JSON)
                            │                                                                     │
                            ├── dopasowywanie ofert, moduł cenowy  <── ceny/oferty w bazie <──────┘
                            └── zmiana ceny przez API sklepu sprzedawcy / e-mail
```

## Uruchomienie

Wymagania: JDK 21, Maven 3.9+, Docker (do bazy).

```bash
docker compose up -d                 # PostgreSQL :5432 i Mailpit (skrzynka e-mail: http://localhost:8025)
                                     # inny port bazy: DB_PORT=5433 (ustaw tę samą zmienną dla api i fetchera)
mvn install -DskipTests              # buduje wszystkie moduły

mvn -pl shop-simulator spring-boot:run   # 1. sklepy symulowane      (http://localhost:8090)
mvn -pl api spring-boot:run              # 2. aplikacja główna       (migruje bazę; Swagger: http://localhost:8080/swagger-ui.html)
mvn -pl fetcher spring-boot:run          # 3. proces pobierający     (można uruchomić kilka instancji: --server.port=8082)
```

W IntelliJ IDEA można też po prostu uruchomić klasy `SimulatorApplication`, `ApiApplication`, `FetcherApplication`.
Przy pierwszym starcie `api` planuje import wszystkich sklepów, a po imporcie automatycznie dopasowuje oferty do katalogu.

Przykładowe żądania są w `requests.postman_collection.json` (import do Postmana).

## Najważniejsze endpointy

Pełny opis wszystkich endpointów (parametry, ciała, odpowiedzi, błędy, efekty uboczne): [docs/API.md](docs/API.md).

| Metoda i ścieżka | Opis |
|---|---|
| `GET /api/products?q=iphone` | wyszukiwanie (podciąg, kod EAN, podobieństwo `pg_trgm` – literówki) |
| `GET /api/products/{id}/offers` | porównanie cen, oferty posortowane rosnąco; nieaktualne dane są zlecane do pobrania „na żądanie” (`refreshing: true`) |
| `GET /api/products/{id}/offers/stream` | SSE – zdarzenia `offers` (świeże dane) i `error` (sklep niedostępny) |
| `GET /api/products/{id}/history?days=7` | historia cen |
| `GET /api/matches?status=TO_REVIEW`, `POST /api/matches/{id}/approve` | ręczna weryfikacja dopasowań |
| `GET/POST/PUT/DELETE /api/sellers/{sellerId}/rules` | konfiguracja modułu cenowego przez sprzedawcę |
| `POST /api/sellers/{sellerId}/rules/{id}/evaluate` | natychmiastowe uruchomienie reguły |
| `GET /api/sellers/{sellerId}/decisions` | historia decyzji cenowych |
| `GET/PUT /api/admin/settings` | próg popularności, okno, dopuszczalny wiek danych, interwał i tryb indeksowania |
| `GET /api/admin/jobs`, `POST /api/admin/import` | kolejka zadań, ręczny import |

Dane startowe (`V2__dane_startowe.sql`): użytkownicy 1 = administrator, 2 = sprzedawca, 3 = kupujący;
sklepy 1–5 = sklepy symulowane, 6 = sklep sprzedawcy; 20 produktów; reguła 1 to przykład z sekcji C1.1
(cena 1 500 zł, minimum 1 350 zł, 1 zł taniej od najtańszego z trzech wskazanych konkurentów).

## Model dwutorowy i eksperyment (sekcja B1.4)

- Popularność produktu = liczba wyświetleń ofert (`GET /offers`) w oknie czasowym. Próg i okno ustawia administrator.
- Tryb `DUAL`: co `indexing.interval-minutes` odświeżane są oferty popularnych produktów, reszta „na żądanie”;
  dodatkowo co 6 h pełny import (nowe oferty).
- Tryb `CYCLIC_ONLY` (model odniesienia): co interwał odświeżane są wszystkie oferty, bez ścieżki „na żądanie”.

Przebieg pomiaru:
1. `PUT /api/admin/settings {"indexing.mode": "DUAL"}` (albo `CYCLIC_ONLY`),
2. `POST http://localhost:8090/simulator/scenario/start?durationHours=24&fraction=0.2&seed=1&timeScale=60`
   (24-godzinny scenariusz w 24 minuty, ten sam `seed` = te same zmiany w każdym powtórzeniu),
3. obciążenie skryptem k6 (zapytania o produkty),
4. porównanie odpowiedzi API (`price`, `ageSeconds`) z `GET /simulator/truth` i `GET /simulator/changes`;
   liczba żądań do sklepów = liczba zadań w `zadanie_pobrania`.

Awarie sklepów do testów obsługi błędów: `POST /simulator/shops/s2/failure?mode=DOWN` (albo `SLOW&delayMs=6000`, `NONE`).

## Testy

```bash
mvn verify                    # testy jednostkowe + integracyjne (Testcontainers; bez Dockera są pomijane)
mvn verify -Pcoverage-check   # dodatkowo wymaga 90% pokrycia linii w module core (wymaganie niefunkcjonalne)
```
Raport pokrycia: `*/target/site/jacoco/index.html` (JaCoCo działa automatycznie poza Windowsem, np. w CI;
na Windowsie trzeba go włączyć flagą `-Pcoverage` – nie zadziała, jeśli ścieżka do `.m2` zawiera polskie znaki).

## Zmiany w schemacie względem ERD z pracy

- `oferta.adres_url` – adres strony oferty,
- `zadanie_pobrania.nie_wczesniej_niz`, `zadanie_pobrania.rozpoczeto` – ponawianie z opóźnieniem i wykrywanie przerwanych zadań,
- tabela `ustawienie` – ustawienia administratora wspólne dla `api` i `fetcher`,
- wartości słownikowe (status, typ, rola itd.) zapisywane po angielsku, zgodnie z enumami w kodzie.

## Do zrobienia

- uwierzytelnianie (Spring Security) – na razie id sprzedawcy jest w ścieżce URL,
- szyfrowanie kluczy API sklepów (`api_klucz_enc` jest przechowywany jawnie),
- adapter WooCommerce REST API (interfejs jak w `SellerShopClient`),
- skrypty k6 do pomiarów,
- front-end (React).
