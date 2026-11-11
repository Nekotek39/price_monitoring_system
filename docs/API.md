# Dokumentacja REST API – Price Monitor

Pełny opis wszystkich endpointów HTTP systemu: parametry, ciała żądań, struktury odpowiedzi, kody błędów
i efekty uboczne. Dokument jest punktem odniesienia przy budowie front-endu (React) i przy dalszej rozbudowie API.

Architekturę i algorytmy opisuje [DOKUMENTACJA.md](DOKUMENTACJA.md), uruchomienie – [README](../README.md).

## Spis treści

1. [Informacje ogólne](#1-informacje-ogólne)
2. [Produkty i porównanie cen](#2-produkty-i-porównanie-cen-apiproducts)
3. [Dopasowania ofert](#3-dopasowania-ofert-apimatches)
4. [Moduł cenowy sprzedawcy](#4-moduł-cenowy-sprzedawcy-apisellerssellerid)
5. [Panel administratora](#5-panel-administratora-apiadmin)
6. [Endpointy techniczne](#6-endpointy-techniczne)
7. [Symulator sklepów (:8090)](#7-symulator-sklepów-8090)
8. [Słowniki wartości (enumy)](#8-słowniki-wartości-enumy)
9. [Typowe scenariusze dla front-endu](#9-typowe-scenariusze-dla-front-endu)
10. [Znane ograniczenia i propozycje zmian](#10-znane-ograniczenia-i-propozycje-zmian)
11. [Jak dodać nowy endpoint](#11-jak-dodać-nowy-endpoint)

---

## 1. Informacje ogólne

### Usługi i adresy

| Usługa | Adres bazowy | Zawartość |
|---|---|---|
| `api` | `http://localhost:8080` | całe publiczne API systemu (`/api/**`), Swagger, Actuator |
| `fetcher` | `http://localhost:8081` | **brak** endpointów biznesowych – tylko Actuator (`/actuator/health`, `/actuator/info`) |
| `shop-simulator` | `http://localhost:8090` | pliki cenowe sklepów, sterowanie symulacją, atrapa API sklepu sprzedawcy |

- Swagger UI: `http://localhost:8080/swagger-ui.html`, specyfikacja OpenAPI (JSON): `http://localhost:8080/v3/api-docs`.
- Kolekcja Postmana: [`requests.postman_collection.json`](../requests.postman_collection.json).

### Konwencje

- Format: JSON (`Content-Type: application/json`), kodowanie UTF-8. Wyjątek: strumień SSE (`text/event-stream`).
- **Uwierzytelnianie: brak.** Tożsamość sprzedawcy jest przekazywana w ścieżce (`/api/sellers/{sellerId}/...`),
  a weryfikującego dopasowanie – w parametrze `reviewerId`. Po dodaniu Spring Security te identyfikatory
  będą brane z zalogowanego użytkownika (patrz [sekcja 10](#10-znane-ograniczenia-i-propozycje-zmian)).
- Daty i czasy: `Instant` serializowany jako ISO-8601 w UTC, np. `"2026-11-11T10:15:30.123456Z"`.
- Kwoty: liczby dziesiętne JSON (`BigDecimal`) w PLN, np. `1349.00`. Nie używać `float` po stronie klienta
  do obliczeń – traktować jako wartości do wyświetlenia lub parsować jako decimal.
- Identyfikatory: liczby całkowite (`Long`). Wyjątek: identyfikator produktu w sklepie sprzedawcy
  (`shopProductId`, np. `"MS-001"`) i kody sklepów symulatora (`s1`–`s5`) to łańcuchy.
- Pola puste są zwracane jako `null` (nie są pomijane).
- Odpowiedzi to rekordy `*View` / `*Response` / `*Summary` – encje JPA nie wychodzą poza warstwę serwisów.
- Listy nie są stronicowane; tam, gdzie mogłyby być duże, mają twardy limit (opisany przy endpoincie).

### Błędy (RFC 7807 Problem Details)

Każdy błąd ma postać `application/problem+json`:

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Produkt 999 nie istnieje",
  "instance": "/api/products/999"
}
```

| Status | Kiedy | Źródło |
|---|---|---|
| `400 Bad Request` | błędne dane wejściowe: walidacja `@Valid`, zły typ parametru (np. `status=FOO`), nieznany klucz ustawienia, `IllegalArgumentException` z logiki | `ApiExceptionHandler`, domyślna obsługa Springa (`spring.mvc.problemdetails.enabled=true`) |
| `404 Not Found` | brak zasobu (`NotFoundException`) lub nieistniejąca ścieżka | `ApiExceptionHandler` |
| `409 Conflict` | konflikt stanu (`ConflictException`, `IllegalStateException`), np. druga reguła dla tego samego produktu | `ApiExceptionHandler` |
| `502 Bad Gateway` | zewnętrzne API (sklep sprzedawcy) nie odpowiada / zwraca błąd (`RestClientException`) | `ApiExceptionHandler` |
| `500 Internal Server Error` | nieobsłużony błąd | Spring |

Komunikaty `detail` z logiki biznesowej są po polsku i nadają się do pokazania użytkownikowi.
Przy błędzie walidacji `@Valid` `detail` jest ogólny (`"Invalid request content."`) – szczegóły pól nie są
zwracane (propozycja zmiany w sekcji 10).

---

## 2. Produkty i porównanie cen (`/api/products`)

Kontroler: `api/.../product/ProductController.java`, logika: `ProductService.java`.

### 2.1. `GET /api/products` – wyszukiwanie produktów

| Parametr | Typ | Domyślnie | Opis |
|---|---|---|---|
| `q` | string | – | fraza: fragment nazwy (bez wielkości liter), dokładny kod EAN/GTIN lub nazwa z literówkami (`pg_trgm`, `word_similarity ≥ 0.5`) |
| `limit` | int | `20` | liczba wyników, przycinana do zakresu **1–100** |

- Bez `q` (lub z pustym) – zwraca produkty z katalogu posortowane alfabetycznie (do `limit`).
- Z `q` – kolejność: najpierw trafienia podciągu, potem wg podobieństwa, potem alfabetycznie.
- **Efekt uboczny:** wyszukiwanie z `q` zapisuje wpis w tabeli `wyszukiwanie` (bez powiązania z produktem –
  nie wpływa na popularność).

Odpowiedź `200` – `ProductSummary[]`:

```json
[
  {
    "id": 1,
    "name": "Apple iPhone 16 128GB Czarny",
    "brand": "Apple",
    "model": "iPhone 16",
    "category": "Smartfony",
    "gtin": "2000000010007",
    "variantAttributes": { "storage": "128gb", "color": "czarny" },
    "lowestPrice": 4140.29,
    "offerCount": 5
  }
]
```

| Pole | Opis |
|---|---|
| `variantAttributes` | atrybuty wariantu (klucze znormalizowane: `storage`, `color`, `size`, `bundle`…); może być `{}` |
| `lowestPrice` | najniższa **dostępna** cena spośród ofert dopasowanych do produktu (bez kosztu wysyłki); `null`, gdy brak dostępnych ofert |
| `offerCount` | liczba ofert dopasowanych do produktu (także niedostępnych) |

### 2.2. `GET /api/products/{id}` – szczegóły produktu

Odpowiedź `200` – pojedynczy `ProductSummary` (jak wyżej). `404`, gdy produkt nie istnieje. Bez efektów ubocznych.

### 2.3. `GET /api/products/{id}/offers` – porównanie cen

Główny endpoint widoku produktu.

**Efekty uboczne** (ważne – to nie jest „czysty” GET):
1. Zapisuje wyświetlenie produktu w `wyszukiwanie` → **zwiększa popularność** produktu
   (popularny = liczba wyświetleń w oknie `popularity.window-days` ≥ `popularity.threshold`).
2. W trybie `DUAL`: dla każdego sklepu, którego oferta jest starsza niż `data.max-age-minutes` (i sklep nie jest
   oznaczony jako niedostępny), planuje zadanie `ON_DEMAND` o wysokim priorytecie (bez duplikatów).
   Sklepy te są zwracane w `refreshingShops`, a świeże dane przyjdą przez [SSE](#24-get-apiproductsidoffersstream--strumień-aktualizacji-sse).
   W trybie `CYCLIC_ONLY` nic nie jest planowane, `refreshing` jest zawsze `false`.

Odpowiedź `200` – `OffersResponse`:

```json
{
  "productId": 13,
  "productName": "Sony WH-1000XM6 Czarne",
  "popular": false,
  "refreshing": true,
  "refreshingShops": ["TechPunkt", "MegaSklep"],
  "offers": [
    {
      "offerId": 41,
      "shopId": 1,
      "shopName": "ElektroMax",
      "name": "Sony WH-1000XM6 czarne słuchawki bezprzewodowe",
      "price": 1449.00,
      "shippingCost": 0.00,
      "available": true,
      "url": "https://elektromax.example/p/s1-1012",
      "fetchedAt": "2026-11-11T10:02:11.481Z",
      "ageSeconds": 754,
      "matchMethod": "IDENTIFIER",
      "matchConfidence": 1.000,
      "lowConfidence": false
    }
  ],
  "generatedAt": "2026-11-11T10:14:45.902Z"
}
```

| Pole | Opis |
|---|---|
| `popular` | produkt jest odświeżany cyklicznie (przekroczył próg popularności) |
| `refreshing` | `true`, jeśli trwa pobieranie „na żądanie” (= `refreshingShops` niepuste) |
| `refreshingShops` | nazwy sklepów, dla których zlecono / trwa odświeżanie |
| `offers` | oferty **posortowane: najpierw dostępne, potem rosnąco po cenie** (`null` na końcu). Oferty bez żadnej zapisanej ceny są pomijane |
| `offers[].price`, `shippingCost`, `available` | z ostatniego rekordu ceny oferty |
| `offers[].fetchedAt` | kiedy proces pobierający ostatnio potwierdził ofertę (`null`, jeśli nigdy) |
| `offers[].ageSeconds` | wiek danych w sekundach liczony na chwilę `generatedAt` (`null`, gdy `fetchedAt` = `null`) |
| `offers[].matchMethod` | `IDENTIFIER` / `ATTRIBUTES` / `NAME` / `MANUAL` lub `null` |
| `offers[].matchConfidence` | pewność dopasowania 0–1 (`null`, gdy brak wpisu dopasowania) |
| `offers[].lowConfidence` | `true`, gdy dopasowanie oparte tylko na nazwie/atrybutach (`ATTRIBUTES`, `NAME`) – UI powinien to oznaczyć |
| `generatedAt` | czas wygenerowania odpowiedzi |

### 2.4. `GET /api/products/{id}/offers/stream` – strumień aktualizacji (SSE)

`Accept: text/event-stream`. Połączenie wygasa po `pricemonitor.api.sse-timeout` (domyślnie **2 min**) –
klient (`EventSource`) wznawia je automatycznie.

Zdarzenia:

| `event` | `data` | Kiedy |
|---|---|---|
| `offers` | `OffersResponse` (jak w 2.3) | natychmiast po podłączeniu (stan bieżący) oraz po każdym zakończonym zadaniu pobrania, które może dotyczyć produktu (`IMPORT`/`CYCLIC` – wszyscy subskrybenci, `ON_DEMAND` – tylko subskrybenci tego produktu) |
| `error` | `{"shop": "TechPunkt", "message": "Nie udało się odświeżyć danych sklepu TechPunkt - pokazujemy ostatnią znaną cenę."}` | zadanie `ON_DEMAND` tego produktu zakończyło się błędem |

W zdarzeniu `offers` pole `refreshingShops` to sklepy, dla których w kolejce są jeszcze aktywne
(`PENDING`/`IN_PROGRESS`) zadania tego produktu – gdy lista się opróżni, odświeżanie się skończyło.

Podłączenie do strumienia **nie** liczy się jako wyświetlenie i **nie** planuje pobierania (używa `snapshot`).
`404` przy nieistniejącym produkcie jest zwracane przed otwarciem strumienia.

```js
const es = new EventSource(`/api/products/${id}/offers/stream`);
es.addEventListener('offers', e => setOffers(JSON.parse(e.data)));
es.addEventListener('error', e => { if (e.data) showToast(JSON.parse(e.data).message); });
```

> Uwaga: zdarzenie nazywa się `error`, tak samo jak wbudowane zdarzenie błędu połączenia `EventSource`.
> Rozróżnienie: zdarzenie z serwera ma `e.data`, błąd połączenia – nie.

### 2.5. `GET /api/products/{id}/history` – historia cen

| Parametr | Typ | Domyślnie | Opis |
|---|---|---|---|
| `days` | int | `7` | ile dni wstecz; wartości < 1 traktowane jako 1 |

Odpowiedź `200` – `PricePoint[]` posortowane rosnąco po `fetchedAt`, ze wszystkich sklepów:

```json
[
  {
    "shopId": 3,
    "shopName": "Cyfrowy Świat",
    "price": 1479.00,
    "shippingCost": 14.99,
    "available": true,
    "source": "CYCLIC",
    "fetchedAt": "2026-11-10T08:00:03.112Z"
  }
]
```

`source` – ścieżka, która zapisała cenę: `IMPORT`, `CYCLIC`, `ON_DEMAND`. Rekord ceny powstaje przy zmianie
ceny, kosztu wysyłki lub dostępności (także gdy oferta zniknęła z pliku → `available: false`), więc `fetchedAt` to chwila wykrycia zmiany, a nie ostatniego potwierdzenia. Do wykresu: grupować po `shopId` i rysować schodkowo do „teraz”. `404`, gdy produkt nie istnieje.

---

## 3. Dopasowania ofert (`/api/matches`)

Kontroler: `api/.../matching/MatchController.java`, logika: `MatchingService.java`.
Dopasowanie = powiązanie oferty sklepu z produktem z katalogu. Pary blisko progu trafiają do ręcznej weryfikacji.

Wspólny typ odpowiedzi – `MatchView`:

```json
{
  "id": 57,
  "offerId": 88,
  "offerName": "Słuchawki Sony WH1000XM6 (czarny)",
  "shopName": "Okazja24",
  "productId": 13,
  "productName": "Sony WH-1000XM6 Czarne",
  "method": "NAME",
  "confidence": 0.612,
  "status": "TO_REVIEW",
  "createdAt": "2026-11-11T09:00:12.004Z"
}
```

### 3.1. `GET /api/matches` – lista dopasowań wg statusu

| Parametr | Typ | Domyślnie | Opis |
|---|---|---|---|
| `status` | `MatchStatus` | `TO_REVIEW` | `AUTOMATIC`, `TO_REVIEW`, `APPROVED`, `REJECTED` |

Odpowiedź `200` – `MatchView[]` posortowane od najstarszych. Brak limitu.

### 3.2. `POST /api/matches/{id}/approve` – zatwierdzenie

| Parametr | Typ | Wymagany | Opis |
|---|---|---|---|
| `reviewerId` | long | nie | id użytkownika zatwierdzającego; nieistniejący id jest **ignorowany** (zapisywany `null`) |

Ustawia status `APPROVED` i przypisuje ofertę do produktu. Odpowiedź `200` – `MatchView`. `404`, gdy brak dopasowania.

### 3.3. `POST /api/matches/{id}/reject` – odrzucenie

Parametry jak w 3.2. Ustawia `REJECTED`; jeśli oferta była przypisana do tego produktu – odpina ją
(`produkt_id = null`). Odpowiedź `200` – `MatchView`.

Uwaga: oba endpointy nie sprawdzają bieżącego statusu – można np. zatwierdzić wcześniej odrzucone dopasowanie.

### 3.4. `POST /api/matches` – ręczne dopasowanie

Ciało (`ManualMatchRequest`):

```json
{ "offerId": 88, "productId": 13, "reviewerId": 1 }
```

| Pole | Wymagane | Opis |
|---|---|---|
| `offerId` | tak | oferta (`404`, gdy nie istnieje) |
| `productId` | tak | produkt (`404`, gdy nie istnieje) |
| `reviewerId` | nie | jak w 3.2 |

Jeśli para już istnieje – zostaje zatwierdzona (`APPROVED`, metoda bez zmian); w przeciwnym razie powstaje
nowe dopasowanie `MANUAL` z `confidence = 1`. Oferta zostaje przypisana do produktu.
Odpowiedź **`200`** – `MatchView`; `400` przy braku wymaganych pól.

### 3.5. `POST /api/matches/run` – dopasowanie nowych ofert

Uruchamia dopasowywanie ofert, które nigdy nie były dopasowywane (normalnie dzieje się to automatycznie
po każdym zadaniu `IMPORT`). Odpowiedź `200` – `MatchingSummary`:

```json
{ "processed": 12, "automatic": 9, "toReview": 2, "unmatched": 1 }
```

`unmatched` obejmuje także oferty używane/odnowione (nie są dopasowywane do produktów nowych).

---

## 4. Moduł cenowy sprzedawcy (`/api/sellers/{sellerId}`)

Kontroler: `api/.../pricing/PricingRuleController.java`, logika: `PricingRuleService.java`, `PricingService.java`.

Dla wszystkich endpointów `sellerId` musi wskazywać użytkownika o roli `SELLER`:
nieistniejący → `404`, inna rola → `400`. Reguła innego sprzedawcy jest traktowana jak nieistniejąca (`404`).
W danych startowych sprzedawcą jest użytkownik **2**.

### Typ `PricingRuleView`

```json
{
  "id": 1,
  "productId": 13,
  "productName": "Sony WH-1000XM6 Czarne",
  "shopProductId": "MS-001",
  "strategy": "UNDERCUT_AMOUNT",
  "parameters": { "amount": "1.00" },
  "minPrice": 1350.00,
  "maxPrice": null,
  "maxChangePercent": 10.00,
  "minIntervalMinutes": 30,
  "mode": "NOTIFY",
  "active": true,
  "competitorShopIds": [1, 2, 3],
  "competitorShopNames": ["ElektroMax", "TechPunkt", "Cyfrowy Świat"]
}
```

### Typ `PricingRuleRequest` (ciało POST/PUT)

```json
{
  "productId": 13,
  "shopProductId": "MS-001",
  "strategy": "UNDERCUT_PERCENT",
  "parameters": { "percent": "2" },
  "minPrice": 1350.00,
  "maxPrice": 1700.00,
  "maxChangePercent": 10,
  "minIntervalMinutes": 30,
  "mode": "AUTOMATIC",
  "active": true,
  "competitorShopIds": [1, 2, 3]
}
```

| Pole | Typ | Wymagane | Walidacja / znaczenie |
|---|---|---|---|
| `productId` | long | tak | produkt z katalogu; **nie można go zmienić w PUT** (`400`) |
| `shopProductId` | string | tak, niepusty | id produktu w sklepie sprzedawcy (używane w wywołaniach API sklepu) |
| `strategy` | `StrategyType` | tak | `MATCH_LOWEST`, `UNDERCUT_AMOUNT`, `UNDERCUT_PERCENT`, `AVERAGE_BAND` |
| `parameters` | mapa string→string | nie | parametry strategii (tabela niżej); wartości jako **łańcuchy**, przecinek dziesiętny akceptowany |
| `minPrice` | decimal | tak, ≥ 0.01 | cena minimalna – nigdy nie schodzimy niżej |
| `maxPrice` | decimal | nie, ≥ 0.01 | cena maksymalna; musi być ≥ `minPrice` (`400`) |
| `maxChangePercent` | decimal | tak, 0–100 | maksymalna jednorazowa zmiana ceny w % |
| `minIntervalMinutes` | int | nie (domyślnie 0), ≥ 0 | minimalny odstęp między zastosowanymi zmianami |
| `mode` | `PricingMode` | tak | `AUTOMATIC` – zmiana ceny przez API sklepu + e-mail; `NOTIFY` – tylko e-mail z rekomendacją |
| `active` | boolean | nie (domyślnie `false`!) | tylko aktywne reguły reagują automatycznie na zmiany cen konkurencji |
| `competitorShopIds` | long[] | nie | sklepy-konkurenci; pusta/brak = wszystkie monitorowane. Każdy musi istnieć i mieć typ `MONITORED` (`400`) |

Parametry strategii:

| Strategia | Parametr | Domyślnie | Wynik (przed ograniczeniami) |
|---|---|---|---|
| `MATCH_LOWEST` | – | – | najniższa cena konkurencji |
| `UNDERCUT_AMOUNT` | `amount` (zł) | `1` | najniższa − `amount` |
| `UNDERCUT_PERCENT` | `percent` (%) | `1` | najniższa × (1 − `percent`/100) |
| `AVERAGE_BAND` | `band` (%) | `5` | utrzymanie ceny w paśmie ± `band`% wokół średniej konkurencji |

> Parametry **nie są walidowane przy zapisie**. Wartość nieliczbowa lub ujemna zostanie wykryta dopiero przy
> ocenie reguły (`evaluate` → `400`; w ocenie automatycznej – błąd w logu). Front-end powinien walidować sam.

### 4.1. `GET /api/sellers/{sellerId}/rules` – lista reguł

Odpowiedź `200` – `PricingRuleView[]` posortowane po `id`.

### 4.2. `GET /api/sellers/{sellerId}/rules/{ruleId}` – jedna reguła

Odpowiedź `200` – `PricingRuleView`; `404`, gdy reguła nie istnieje lub należy do innego sprzedawcy.

### 4.3. `POST /api/sellers/{sellerId}/rules` – utworzenie reguły

Ciało: `PricingRuleRequest`. Odpowiedź **`201 Created`** – `PricingRuleView` (bez nagłówka `Location`).

Błędy: `400` (walidacja, `maxPrice < minPrice`, zły konkurent), `404` (produkt), **`409`** – sprzedawca ma już
regułę dla tego produktu (jedna reguła na produkt).

### 4.4. `PUT /api/sellers/{sellerId}/rules/{ruleId}` – zmiana reguły

Ciało: pełny `PricingRuleRequest` (zastępuje wszystkie pola; to nie jest PATCH – pominięte `competitorShopIds`
oznacza „wszyscy konkurenci”, pominięte `active` = `false`). `productId` musi być taki jak w regule.
Odpowiedź `200` – `PricingRuleView`.

### 4.5. `DELETE /api/sellers/{sellerId}/rules/{ruleId}` – usunięcie reguły

Odpowiedź **`204 No Content`**. Usuwa także historię decyzji tej reguły i listę konkurentów (kaskada w bazie).

### 4.6. `POST /api/sellers/{sellerId}/rules/{ruleId}/evaluate` – natychmiastowa ocena reguły

Uruchamia silnik cenowy dla reguły teraz (niezależnie od `active`) i **wykonuje decyzję** – dokładnie tak,
jak przy automatycznym wyzwoleniu przez zmianę cen konkurencji:

1. Pobiera aktualną cenę z API sklepu sprzedawcy (`GET {api_url}/products/{shopProductId}`, nagłówek `X-Api-Key`).
2. Zbiera ceny konkurentów (do decyzji brane są tylko oferty z wiarygodnym dopasowaniem).
3. Silnik: strategia → łańcuch ograniczeń (min, max, max zmiana %, minimalny odstęp).
4. Wykonanie wg `outcome`:

| `outcome` | Co się dzieje | Zapis w historii decyzji |
|---|---|---|
| `CHANGE` + `AUTOMATIC` | `PUT {api_url}/products/{id}/price`, e-mail do sprzedawcy | `APPLIED` (lub `FAILED` + alert do admina, gdy zmiana się nie udała) |
| `CHANGE` + `NOTIFY` | e-mail z rekomendacją | `SENT` (bez powtórzeń przy tej samej najniższej cenie) |
| `KEEP_BELOW_MINIMUM` | cena zostaje, e-mail do sprzedawcy | `KEPT` (bez powtórzeń) |
| `STALE_DATA` | zlecane pobranie „na żądanie” w sklepach `staleShopIds` | – |
| `NO_CHANGE`, `TOO_SOON`, `NO_DATA` | nic | – |

Odpowiedź `200` – `PricingDecision` (rekord z modułu `core`):

```json
{
  "outcome": "CHANGE",
  "currentPrice": 1500.00,
  "proposedPrice": 1448.00,
  "finalPrice": 1448.00,
  "lowestCompetitorPrice": 1449.00,
  "staleShopIds": [],
  "reason": "Najniższa cena konkurencji: 1 449,00 zł (ElektroMax). …"
}
```

| Pole | Opis |
|---|---|
| `proposedPrice` | wynik strategii przed ograniczeniami (`null`, gdy nie liczono, np. `NO_DATA`) |
| `finalPrice` | cena po ograniczeniach; równa `currentPrice`, o ile `outcome ≠ CHANGE` |
| `lowestCompetitorPrice` | najniższa wiarygodna cena konkurencji lub `null` |
| `staleShopIds` | sklepy ze zbyt starymi danymi (tylko przy `STALE_DATA`) |
| `reason` | wyjaśnienie po polsku do pokazania sprzedawcy |

Błędy:
- `409` – sprzedawca nie ma podłączonego sklepu typu `SELLER`, albo API sklepu nie zwróciło ceny,
- `502` – API sklepu sprzedawcy nie odpowiada (timeout `seller-api-timeout`, domyślnie 3 s) lub zwraca błąd;
  dodatkowo wysyłany jest alert e-mail do administratora,
- `400` – błędny parametr strategii.

### 4.7. `GET /api/sellers/{sellerId}/decisions` – historia decyzji cenowych

Odpowiedź `200` – `DecisionView[]`, **100 najnowszych** ze wszystkich reguł sprzedawcy, od najnowszej:

```json
[
  {
    "id": 14,
    "ruleId": 1,
    "productName": "Sony WH-1000XM6 Czarne",
    "previousPrice": 1500.00,
    "newPrice": 1448.00,
    "lowestCompetitorPrice": 1449.00,
    "reason": "…",
    "status": "SENT",
    "createdAt": "2026-11-11T10:20:00.000Z"
  }
]
```

`status`: `APPLIED`, `SENT`, `KEPT`, `FAILED`. Dla `KEPT` `newPrice` = `previousPrice`.

---

## 5. Panel administratora (`/api/admin`)

Kontroler: `api/.../admin/AdminController.java`.

### 5.1. `GET /api/admin/settings` – ustawienia systemu

Odpowiedź `200` – obiekt klucz → wartość (zawsze łańcuchy), klucze posortowane alfabetycznie:

```json
{
  "data.max-age-minutes": "30",
  "indexing.interval-minutes": "10",
  "indexing.mode": "DUAL",
  "popularity.threshold": "15",
  "popularity.window-days": "7"
}
```

| Klucz | Domyślnie | Dozwolone | Znaczenie |
|---|---|---|---|
| `popularity.threshold` | `15` | int > 0 | ile wyświetleń w oknie czyni produkt popularnym |
| `popularity.window-days` | `7` | int > 0 | okno liczenia popularności (dni) |
| `data.max-age-minutes` | `30` | int > 0 | dopuszczalny wiek danych; starsze → pobranie „na żądanie” / `STALE_DATA` w module cenowym |
| `indexing.interval-minutes` | `10` | int > 0 | interwał indeksowania cyklicznego |
| `indexing.mode` | `DUAL` | `DUAL`, `CYCLIC_ONLY` | model dwutorowy albo model odniesienia (tylko cykliczne odświeżanie wszystkiego) |

Ustawienia są w tabeli `ustawienie`, więc `api` i `fetcher` widzą te same wartości bez restartu.

### 5.2. `PUT /api/admin/settings` – zmiana ustawień

Ciało: obiekt z **dowolnym podzbiorem** kluczy (zmiana częściowa):

```json
{ "popularity.threshold": "20", "indexing.mode": "CYCLIC_ONLY" }
```

Wszystkie wpisy są walidowane przed zapisem (wszystko albo nic). Nieznany klucz lub zła wartość → `400`
(`"Unknown setting: …"`, `"Invalid value of …"`). Liczby można też wysłać jako liczby JSON (Jackson zamieni je na
łańcuch). Odpowiedź `200` – pełna mapa ustawień po zmianie (jak w 5.1).

### 5.3. `POST /api/admin/import` – ręczny pełny import

Planuje zadanie `IMPORT` dla każdego monitorowanego sklepu, który nie ma już aktywnego importu.
Po zakończeniu importu automatycznie uruchamia się dopasowywanie ofert.

Odpowiedź `200`: `{ "scheduledJobs": 5 }` (`0`, gdy wszystkie importy już trwają).

### 5.4. `GET /api/admin/jobs` – kolejka zadań pobierania

| Parametr | Typ | Wymagany | Opis |
|---|---|---|---|
| `status` | `JobStatus` | nie | `PENDING`, `IN_PROGRESS`, `DONE`, `FAILED` |

Odpowiedź `200` – `JobView[]`, **100 najnowszych** (po `createdAt` malejąco):

```json
[
  {
    "id": 512,
    "shopId": 2,
    "productId": 13,
    "type": "ON_DEMAND",
    "priority": 10,
    "status": "DONE",
    "attempts": 1,
    "error": null,
    "createdAt": "2026-11-11T10:14:45.880Z",
    "startedAt": "2026-11-11T10:14:46.020Z",
    "finishedAt": "2026-11-11T10:14:46.311Z"
  }
]
```

`priority`: `ON_DEMAND` = 10, `CYCLIC` = 5, `IMPORT` = 1 (większy = pobierany wcześniej). `productId` jest `null` dla `IMPORT`/`CYCLIC`. `error` – komunikat ostatniego błędu (dla `FAILED` i ponawianych).

### 5.5. `GET /api/admin/shops` – sklepy

Odpowiedź `200` – `ShopView[]` posortowane po `id`:

```json
[
  {
    "id": 1,
    "name": "ElektroMax",
    "type": "MONITORED",
    "feedFormat": "JSON",
    "feedUrl": "http://localhost:8090/shops/s1/feed",
    "status": "AVAILABLE",
    "unavailableUntil": null
  },
  {
    "id": 6,
    "name": "Mój Sklep (sprzedawca)",
    "type": "SELLER",
    "feedFormat": null,
    "feedUrl": null,
    "status": "AVAILABLE",
    "unavailableUntil": null
  }
]
```

`status = UNAVAILABLE` + `unavailableUntil` – sklep wyłączony z pobierania po powtarzających się błędach
(na `shop-unavailable-for`, domyślnie 5 min). Klucz API sklepu sprzedawcy **nie** jest zwracany.

---

## 6. Endpointy techniczne

| Usługa | Endpoint | Opis |
|---|---|---|
| `api`, `fetcher` | `GET /actuator/health` | `{"status":"UP"}` (z bazą danych) – do healthchecków Dockera / k8s |
| `api`, `fetcher` | `GET /actuator/info` | informacje o aplikacji |
| `api` | `GET /swagger-ui.html` | interaktywna dokumentacja |
| `api` | `GET /v3/api-docs` | specyfikacja OpenAPI 3 (JSON) – można z niej generować klienta TS (`openapi-typescript`, `orval`) |

---

## 7. Symulator sklepów (`:8090`)

Moduł `shop-simulator` – środowisko testowe, **nie** jest częścią produkcyjnego API. Stan w pamięci,
wczytywany z `simulator-data.json` (restart = stan początkowy). Błędy: `404` (ProblemDetail) dla nieznanego
kodu sklepu / produktu sprzedawcy.

### 7.1. Pliki cenowe – `GET /shops/{code}/feed`

`code`: `s1`–`s5` (ElektroMax `JSON`, TechPunkt `JSON`, Cyfrowy Świat `XML`, MegaSklep `XML`, Okazja24 `JSON`).

JSON (`application/json`):

```json
{
  "shop": "ElektroMax",
  "generatedAt": "2026-11-11T10:00:00Z",
  "offers": [
    {"id": "S1-1000", "name": "Apple iPhone 16 128GB Czarny", "brand": "Apple", "gtin": "2000000010007",
     "mpn": "SIM-P01-IPHONE16", "attributes": {"pamięć": "128gb", "kolor": "czarny"}, "condition": "new",
     "price": "4226.37", "shipping": "0.00", "available": true, "url": "https://elektromax.example/p/s1-1000"}
  ]
}
```

XML (`application/xml`, format porównywarek cenowych):

```xml
<offers>
  <o id="S3-1000" url="https://..." price="4199.00" ship="0.00" avail="1" stan="nowy">
    <name><![CDATA[Apple iPhone 16 128GB Czarny]]></name>
    <attrs>
      <a name="Producent">Apple</a>
      <a name="EAN">2000000010007</a>
      <a name="Kod_producenta">SIM-P01-IPHONE16</a>
      <a name="Kolor">czarny</a>
    </attrs>
  </o>
</offers>
```

Przy wstrzykniętej awarii: `DOWN` → `503`, `SLOW` → odpowiedź po `delayMs`.

### 7.2. Sterowanie symulacją – `/simulator`

| Metoda i ścieżka | Parametry | Odpowiedź |
|---|---|---|
| `POST /simulator/scenario/start` | `durationHours` (24), `fraction` (0.2), `seed` (1), `timeScale` (1) | `ScenarioStatus`. Zmienia się `fraction` ofert w losowych chwilach w ciągu `durationHours / timeScale`; ten sam `seed` = identyczny plan. Przerywa poprzedni scenariusz i czyści listę zmian |
| `POST /simulator/scenario/stop` | – | `ScenarioStatus` |
| `GET /simulator/scenario` | – | `{"running": true, "startedAt": "…", "planned": 120, "executed": 37}` |
| `GET /simulator/changes` | – | `ChangeEvent[]`: `{at, shopCode, offerId, truth, oldPrice, newPrice}` (`truth` = kod produktu, np. `P13`) |
| `GET /simulator/truth` | – | `TruthEntry[]`: `{shop, offerId, product, price, available}` – „prawdziwe” bieżące ceny |
| `POST /simulator/reset` | – | zatrzymuje scenariusz, przywraca ceny początkowe; `{"status": "reset"}` |
| `POST /simulator/shops/{code}/failure` | `mode` = `NONE`/`DOWN`/`SLOW` (wymagany), `delayMs` (0) | `{"shop": "s2", "mode": "SLOW", "delayMs": 6000}` |

### 7.3. Atrapa API sklepu sprzedawcy – `/seller`

Wymaga nagłówka `X-Api-Key: seller-secret` (brak nagłówka → `400`, zły klucz → `401`).
Ten sam kontrakt musi spełnić prawdziwy adapter (np. WooCommerce) – patrz `SellerShopClient`.

| Metoda i ścieżka | Ciało | Odpowiedź |
|---|---|---|
| `GET /seller/products` | – | `[{"id": "MS-001", "name": "…", "price": 1500.00}, …]` |
| `GET /seller/products/{id}` | – | `{"id", "name", "price"}`; `404` dla nieznanego id |
| `PUT /seller/products/{id}/price` | `{"price": 1448.00}` (> 0, inaczej `400`) | `{"id", "name", "price"}` po zmianie |
| `GET /seller/changes` | – | `[{"at", "productId", "oldPrice", "newPrice"}]` – historia zmian od startu symulatora |

---

## 8. Słowniki wartości (enumy)

| Enum | Wartości | Gdzie |
|---|---|---|
| `MatchStatus` | `AUTOMATIC` (przyjęte automatycznie), `TO_REVIEW` (do weryfikacji), `APPROVED`, `REJECTED` | `MatchView.status`, `GET /api/matches?status` |
| `MatchMethod` | `IDENTIFIER` (GTIN/MPN), `ATTRIBUTES` (marka+model+atrybuty), `NAME` (podobieństwo nazw), `MANUAL` | `MatchView.method`, `OfferView.matchMethod` |
| `StrategyType` | `MATCH_LOWEST`, `UNDERCUT_AMOUNT`, `UNDERCUT_PERCENT`, `AVERAGE_BAND` | reguły cenowe |
| `PricingMode` | `AUTOMATIC`, `NOTIFY` | reguły cenowe |
| `DecisionOutcome` | `CHANGE`, `NO_CHANGE`, `KEEP_BELOW_MINIMUM`, `TOO_SOON`, `STALE_DATA`, `NO_DATA` | `PricingDecision.outcome` |
| `DecisionStatus` | `APPLIED`, `SENT`, `KEPT`, `FAILED` | `DecisionView.status` |
| `JobType` | `IMPORT` (wszystkie oferty sklepu), `CYCLIC` (popularne), `ON_DEMAND` (jeden produkt) | `JobView.type` |
| `JobStatus` | `PENDING`, `IN_PROGRESS`, `DONE`, `FAILED` | `JobView.status`, `GET /api/admin/jobs?status` |
| `PriceSource` | `IMPORT`, `CYCLIC`, `ON_DEMAND` | `PricePoint.source` |
| `ShopType` | `MONITORED` (konkurent z plikiem cen), `SELLER` (sklep sprzedawcy z API) | `ShopView.type` |
| `ShopStatus` | `AVAILABLE`, `UNAVAILABLE` | `ShopView.status` |
| `IndexingMode` | `DUAL`, `CYCLIC_ONLY` | ustawienie `indexing.mode` |
| `UserRole` | `ADMIN`, `SELLER`, `BUYER` | (jeszcze nie w API) |

Dane startowe (`V2__dane_startowe.sql`): użytkownicy 1 = admin, 2 = sprzedawca, 3 = kupujący;
sklepy 1–5 monitorowane, 6 = sklep sprzedawcy; produkty 1–20; reguły 1 (produkt 13, `NOTIFY`) i 2 (produkt 7, `AUTOMATIC`).

---

## 9. Typowe scenariusze dla front-endu

**Widok produktu (kupujący)**
1. `GET /api/products?q=…` → lista wyników.
2. Po wejściu w produkt: `GET /api/products/{id}/offers` → render tabeli (oznaczyć `lowConfidence`, pokazać „dane sprzed X min” z `ageSeconds`).
3. Jeśli `refreshing = true` → otworzyć `EventSource` na `/offers/stream`, pokazać „odświeżamy: {refreshingShops}”;
   każde zdarzenie `offers` podmienia tabelę; gdy `refreshing = false` – zamknąć strumień (lub zostawić, by łapać kolejne zmiany).
4. Wykres: `GET /api/products/{id}/history?days=30`.

**Panel sprzedawcy**
1. `GET /api/sellers/2/rules`, formularz → `POST`/`PUT` (sklepy do wyboru konkurentów: `GET /api/admin/shops` filtrowane po `type = MONITORED`).
2. Przycisk „Sprawdź teraz” → `POST …/rules/{id}/evaluate`, pokazać `outcome` i `reason`.
3. Historia → `GET /api/sellers/2/decisions`.

**Panel administratora**
- Ustawienia: `GET`/`PUT /api/admin/settings`.
- Weryfikacja dopasowań: `GET /api/matches` → `approve`/`reject`; brakujące → `POST /api/matches`.
- Monitoring: `GET /api/admin/jobs?status=FAILED`, `GET /api/admin/shops`, `POST /api/admin/import`.

**CORS:** API nie ma konfiguracji CORS. Przy front-endzie na innym porcie (np. Vite `:5173`) najprościej
ustawić proxy dev-serwera na `http://localhost:8080` albo dodać `WebMvcConfigurer#addCorsMappings` w `api`.

---

## 10. Znane ograniczenia i propozycje zmian

| Obszar | Stan obecny | Propozycja |
|---|---|---|
| Uwierzytelnianie | brak; `sellerId` w ścieżce, `reviewerId` w parametrze | Spring Security (sesja lub JWT), role `ADMIN`/`SELLER`/`BUYER`; ścieżki `/api/me/rules`, `/api/admin/**` tylko dla `ADMIN`; `reviewerId` z kontekstu bezpieczeństwa |
| Błędy walidacji | `400` bez listy pól | handler `MethodArgumentNotValidException` dodający `errors: [{field, message}]` do ProblemDetail |
| Parametry strategii | walidowane dopiero przy ocenie | walidacja przy `POST`/`PUT` (wywołanie strategii na próbnych danych) |
| Stronicowanie | twarde limity (100) lub brak | `Pageable` (`page`, `size`, `sort`) dla `/matches`, `/admin/jobs`, `/decisions` |
| `POST /api/matches`, `POST /rules` | `200` / `201` bez `Location` | `201` + nagłówek `Location` |
| Approve/reject | brak kontroli przejść statusu | `409` przy zmianie już zweryfikowanego dopasowania |
| Wylogowane wyświetlenia | `GET /offers` zawsze liczy wyświetlenie (także odświeżenia strony) | deduplikacja per użytkownik/sesja w krótkim oknie |
| Brakujące endpointy | – | CRUD sklepów (`/api/admin/shops`), CRUD produktów katalogu, lista ofert niedopasowanych (`/api/offers?unmatched=true`), konto użytkownika, statystyki do eksperymentu (liczba zadań, średni wiek danych) |
| Wersjonowanie | brak | prefiks `/api/v1` przed pierwszym publicznym wydaniem |

---

## 11. Jak dodać nowy endpoint

1. **DTO**: rekord `*View` (odpowiedź) i `*Request` (żądanie, z adnotacjami `jakarta.validation`) w pakiecie funkcji (`api/.../<funkcja>/`).
2. **Serwis**: logika w `@Service` z `@Transactional` (`readOnly = true` dla odczytów); mapowanie encja → DTO w serwisie,
   bo `open-in-view` jest wyłączone (leniwe relacje nie zadziałają w kontrolerze).
3. **Błędy**: rzucać `NotFoundException` (404), `ConflictException` (409), `IllegalArgumentException` (400) –
   `ApiExceptionHandler` zamieni je na ProblemDetail. Komunikaty po polsku.
4. **Kontroler**: cienki `@RestController`, `@Valid` na `@RequestBody`, `@ResponseStatus(CREATED/NO_CONTENT)` gdzie trzeba,
   `@Operation(summary = "…")` dla Swaggera.
5. **Test**: przypadek w `ApiIntegrationTest` (MockMvc na PostgreSQL z Testcontainers, dane startowe dostępne).
6. **Dokumentacja**: dopisać endpoint do tego pliku i do `requests.postman_collection.json`.
