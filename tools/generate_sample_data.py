#!/usr/bin/env python3
"""Generates consistent sample data for the simulated environment.

Outputs:
  persistence/src/main/resources/db/migration/V2__dane_startowe.sql  (catalog, shops, users, pricing rules)
  shop-simulator/src/main/resources/simulator-data.json             (5 shops with offers + seller shop)

Extend PRODUCTS to ~100 items and run:  python3 tools/generate_sample_data.py
The data is deterministic (fixed random seed). Offers deliberately contain typical matching problems:
different naming styles, missing GTIN codes, variants of the same model, used/refurbished items and bundles.
"""
import json
import random
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SEED = 20261111

# key, name, brand, model, category, variant attributes (canonical keys), base price [PLN]
PRODUCTS = [
    ("P01", "Apple iPhone 16 128GB Czarny", "Apple", "iPhone 16", "Smartfony", {"storage": "128gb", "color": "czarny"}, 3999),
    ("P02", "Apple iPhone 16 256GB Czarny", "Apple", "iPhone 16", "Smartfony", {"storage": "256gb", "color": "czarny"}, 4599),
    ("P03", "Apple iPhone 16 128GB Biały", "Apple", "iPhone 16", "Smartfony", {"storage": "128gb", "color": "biały"}, 3999),
    ("P04", "Samsung Galaxy S25 256GB Granatowy", "Samsung", "Galaxy S25", "Smartfony", {"storage": "256gb", "color": "granatowy"}, 4299),
    ("P05", "Samsung Galaxy S25 Ultra 512GB Tytanowy", "Samsung", "Galaxy S25 Ultra", "Smartfony", {"storage": "512gb", "color": "tytanowy"}, 6499),
    ("P06", "Xiaomi 15 256GB Zielony", "Xiaomi", "15", "Smartfony", {"storage": "256gb", "color": "zielony"}, 3699),
    ("P07", "Sony PlayStation 5 Pro", "Sony", "PlayStation 5 Pro", "Konsole", {}, 3399),
    ("P08", "Sony PlayStation 5 Slim Digital", "Sony", "PlayStation 5 Slim Digital", "Konsole", {}, 2099),
    ("P09", "Microsoft Xbox Series X 1TB", "Microsoft", "Xbox Series X", "Konsole", {"storage": "1tb"}, 2499),
    ("P10", "Nintendo Switch 2", "Nintendo", "Switch 2", "Konsole", {}, 1999),
    ("P11", "Nintendo Switch 2 + Mario Kart World", "Nintendo", "Switch 2", "Konsole", {"bundle": "tak"}, 2199),
    ("P12", "Apple AirPods Pro 2", "Apple", "AirPods Pro 2", "Słuchawki", {}, 1099),
    ("P13", "Sony WH-1000XM6 Czarne", "Sony", "WH-1000XM6", "Słuchawki", {"color": "czarny"}, 1549),
    ("P14", "Apple MacBook Air 13 M4 16GB 256GB", "Apple", "MacBook Air 13 M4", "Laptopy", {"storage": "256gb"}, 5199),
    ("P15", "Lenovo Legion 5 16IRX9 32GB 1TB", "Lenovo", "Legion 5 16IRX9", "Laptopy", {"storage": "1tb"}, 6999),
    ("P16", "Samsung QE55Q80D 55\" QLED", "Samsung", "QE55Q80D", "Telewizory", {"size": "55"}, 3899),
    ("P17", "LG OLED55C4 55\" OLED", "LG", "OLED55C4", "Telewizory", {"size": "55"}, 4999),
    ("P18", "Apple Watch Series 10 46mm", "Apple", "Watch Series 10", "Smartwatche", {"size": "46mm"}, 2199),
    ("P19", "DJI Mini 4 Pro", "DJI", "Mini 4 Pro", "Drony", {}, 3799),
    ("P20", "Logitech MX Master 3S Grafitowa", "Logitech", "MX Master 3S", "Akcesoria", {"color": "grafitowy"}, 449),
]

SHOPS = [
    # code, name, format, naming style, gtin share, mpn share
    ("s1", "ElektroMax", "JSON", "full", 1.0, 1.0),
    ("s2", "TechPunkt", "JSON", "reordered", 0.7, 0.0),
    ("s3", "Cyfrowy Świat", "XML", "category", 0.9, 0.8),
    ("s4", "MegaSklep", "XML", "short", 0.5, 0.0),
    ("s5", "Okazja24", "JSON", "promo", 0.6, 0.3),
]
SELLER = {"code": "seller", "name": "Mój Sklep (sprzedawca)", "apiKey": "seller-secret",
          "products": [{"id": "MS-001", "productKey": "P13", "price": "1500.00"},
                       {"id": "MS-002", "productKey": "P07", "price": "3299.00"}]}

COLOR_EN = {"czarny": "black", "biały": "white", "granatowy": "navy", "tytanowy": "titanium", "zielony": "green",
            "grafitowy": "graphite"}
CATEGORY_PREFIX = {"Smartfony": "Smartfon", "Konsole": "Konsola", "Słuchawki": "Słuchawki bezprzewodowe",
                   "Laptopy": "Laptop", "Telewizory": "Telewizor", "Smartwatche": "Smartwatch", "Drony": "Dron",
                   "Akcesoria": "Mysz"}


def ean13(n: int) -> str:
    """Fake but checksum-valid EAN-13 from the 200-299 prefix range (reserved for internal numbering)."""
    body = f"20{n:010d}"
    total = sum(int(d) * (3 if i % 2 else 1) for i, d in enumerate(body))
    return body + str((10 - total % 10) % 10)


def money(x) -> str:
    return str(Decimal(x).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP))


def mpn_of(p):
    return "SIM-" + p[0] + "-" + "".join(ch for ch in p[3].upper() if ch.isalnum())[:8]


def offer_name(style, p, rnd):
    key, name, brand, model, cat, attrs, _ = p
    if style == "full":
        return name
    if style == "reordered":
        parts = [brand.upper(), model]
        if "storage" in attrs:
            parts.append(attrs["storage"].upper().replace("GB", " GB").replace("TB", " TB"))
        if "color" in attrs:
            parts.append(attrs["color"])
        if "size" in attrs:
            parts.append(attrs["size"] + " cali" if attrs["size"].isdigit() else attrs["size"])
        if attrs.get("bundle"):
            parts.append("zestaw z grą Mario Kart World")
        return " ".join(parts)
    if style == "category":
        return f"{CATEGORY_PREFIX[cat]} {name}"
    if style == "short":
        return name.replace("Apple ", "").replace("Samsung ", "Samsung ").replace(" Czarny", " czarny")
    if style == "promo":
        return f"{name} - PROMOCJA" if rnd.random() < 0.5 else f"{name} (gwarancja PL)"
    raise ValueError(style)


def main():
    rnd = random.Random(SEED)
    gtins = {p[0]: ean13(1000 + i) for i, p in enumerate(PRODUCTS)}
    shops_json = []
    for s_idx, (code, sname, fmt, style, gtin_share, mpn_share) in enumerate(SHOPS):
        offers = []
        for p_idx, p in enumerate(PRODUCTS):
            key, name, brand, model, cat, attrs, base = p
            must_have = key == "P13" and code in ("s1", "s2", "s3")
            if not must_have and rnd.random() > 0.85:
                continue
            price = Decimal(base) * Decimal(str(rnd.uniform(0.96, 1.06)))
            if key == "P13":
                price = Decimal(rnd.choice([1529, 1549, 1569, 1599]))
            offer_attrs = {}
            if style in ("full", "reordered", "category"):
                for k, v in attrs.items():
                    if k == "bundle":
                        continue
                    if style == "reordered":
                        offer_attrs[{"storage": "storage", "color": "color", "size": "screen"}[k]] = (
                            COLOR_EN.get(v, v) if k == "color" else v)
                    else:
                        offer_attrs[{"storage": "pamięć", "color": "kolor", "size": "przekątna"}[k]] = v
            offers.append({
                "id": f"{code.upper()}-{1000 + p_idx}",
                "name": offer_name(style, p, rnd),
                "brand": brand if style != "short" else None,
                "gtin": gtins[key] if rnd.random() < gtin_share else None,
                "mpn": mpn_of(p) if rnd.random() < mpn_share else None,
                "attributes": offer_attrs,
                "condition": "new",
                "price": money(price),
                "shipping": money(0 if price > 1000 else 14.99),
                "available": rnd.random() > 0.05,
                "url": f"https://{sname.lower().replace(' ', '')}.example/p/{code}-{1000 + p_idx}",
                "truth": key,
            })
        if code == "s5":
            # Difficult cases for the matching module.
            extra = [
                ("[Outlet] Apple iPhone 16 128GB Czarny - używany", "used", "P01", 3199),
                ("Sony PlayStation 5 Pro odnowiona (refurbished)", "refurbished", "P07", 2799),
                ("Pad Sony DualSense Edge", "new", None, 899),
                ("Samsung Galaxy S25 Ultra 256GB Czarny", "new", None, 5899),
            ]
            for i, (n, cond, truth, pr) in enumerate(extra):
                offers.append({"id": f"S5-{2000 + i}", "name": n, "brand": n.split()[1] if n.startswith("[") else n.split()[0],
                               "gtin": None, "mpn": None, "attributes": {}, "condition": cond, "price": money(pr),
                               "shipping": money(0), "available": True,
                               "url": f"https://okazja24.example/p/s5-{2000 + i}", "truth": truth})
        shops_json.append({"code": code, "name": sname, "format": fmt, "offers": offers})

    sim = {"shops": shops_json,
           "seller": {"apiKey": SELLER["apiKey"],
                      "products": [{"id": sp["id"], "name": next(p[1] for p in PRODUCTS if p[0] == sp["productKey"]),
                                    "price": sp["price"]} for sp in SELLER["products"]]}}
    out_json = ROOT / "shop-simulator/src/main/resources/simulator-data.json"
    out_json.write_text(json.dumps(sim, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    def q(v):
        return "NULL" if v is None else "'" + str(v).replace("'", "''") + "'"

    lines = ["-- Sample data for the simulated environment (generated by tools/generate_sample_data.py).",
             "-- Passwords are placeholders: authentication is not implemented yet.",
             "INSERT INTO uzytkownik (id, email, haslo_hash, rola) VALUES",
             "    (1, 'admin@pricemonitor.local', '{noop}admin', 'ADMIN'),",
             "    (2, 'sprzedawca@pricemonitor.local', '{noop}sprzedawca', 'SELLER'),",
             "    (3, 'kupujacy@pricemonitor.local', '{noop}kupujacy', 'BUYER');",
             "SELECT setval('uzytkownik_id_seq', 3);", "",
             "INSERT INTO sklep (id, nazwa, typ, wlasciciel_id, adres_pliku_cen, format_danych, api_url, api_klucz_enc, "
             "status, min_odstep_zapytan_s) VALUES"]
    rows = []
    for i, (code, sname, fmt, *_rest) in enumerate(SHOPS, start=1):
        rows.append(f"    ({i}, {q(sname)}, 'MONITORED', NULL, 'http://localhost:8090/shops/{code}/feed', '{fmt}', "
                    f"NULL, NULL, 'AVAILABLE', 2)")
    rows.append(f"    (6, {q(SELLER['name'])}, 'SELLER', 2, NULL, NULL, 'http://localhost:8090/seller', "
                f"{q(SELLER['apiKey'])}, 'AVAILABLE', 1)")
    lines.append(",\n".join(rows) + ";")
    lines += ["SELECT setval('sklep_id_seq', 6);", "",
              "INSERT INTO produkt (id, nazwa, marka, model, gtin, mpn, kategoria, atrybuty_wariantu) VALUES"]
    rows = []
    for i, p in enumerate(PRODUCTS, start=1):
        key, name, brand, model, cat, attrs, _ = p
        rows.append(f"    ({i}, {q(name)}, {q(brand)}, {q(model)}, {q(gtins[key])}, {q(mpn_of(p))}, {q(cat)}, "
                    f"{q(json.dumps(attrs, ensure_ascii=False))}::jsonb)")
    lines.append(",\n".join(rows) + ";")
    lines += [f"SELECT setval('produkt_id_seq', {len(PRODUCTS)});", "",
              "-- Example from section C1.1: product for 1500 zł, minimum price 1350 zł, 1 zł below the lowest price",
              "-- of three competitors chosen by the seller, notification mode.",
              "INSERT INTO regula_cenowa (id, uzytkownik_id, produkt_id, id_produktu_w_sklepie, strategia, parametry, "
              "cena_min, cena_max, max_zmiana_proc, min_odstep_min, tryb, aktywna) VALUES",
              "    (1, 2, 13, 'MS-001', 'UNDERCUT_AMOUNT', '{\"amount\": \"1.00\"}'::jsonb, 1350.00, NULL, 10.00, 30, 'NOTIFY', TRUE),",
              "    (2, 2, 7, 'MS-002', 'MATCH_LOWEST', '{}'::jsonb, 2999.00, 3599.00, 5.00, 60, 'AUTOMATIC', TRUE);",
              "SELECT setval('regula_cenowa_id_seq', 2);",
              "INSERT INTO regula_konkurent (regula_id, sklep_id) VALUES (1, 1), (1, 2), (1, 3);", ""]
    out_sql = ROOT / "persistence/src/main/resources/db/migration/V2__dane_startowe.sql"
    out_sql.write_text("\n".join(lines), encoding="utf-8")
    print(f"written {out_json.relative_to(ROOT)} and {out_sql.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
