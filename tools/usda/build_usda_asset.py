#!/usr/bin/env python3
"""Build the bundled USDA generic-foods asset for OpenMacro.

Reads one or more FoodData Central CSV downloads (SR Legacy and/or Foundation Foods zips from
https://fdc.nal.usda.gov/download-datasets) and writes:

  app/src/main/assets/foods/usda_foods.tsv.gz   tab-separated, values per 100 g
  app/src/main/assets/foods/usda_version.txt    changes whenever the data changes

Columns: fdc_id, name, category, kcal, protein, carbs, fat, fiber, sugar, sat_fat, sodium_mg,
portions. Portions look like "1 cup, chopped=128;1 medium=110" (label=grams). Empty cell = unknown.

USDA FoodData Central data is public domain (CC0 1.0). Standard library only, so it runs anywhere.

Usage:
  python3 tools/usda/build_usda_asset.py --out app/src/main/assets/foods SR_LEGACY.zip [FOUNDATION.zip ...]
"""

from __future__ import annotations

import argparse
import csv
import gzip
import hashlib
import io
import os
import sys
import zipfile

FORMAT_VERSION = "1"

# FoodData Central nutrient ids.
ENERGY_KCAL = ["1008", "2048", "2047"]  # kcal; then Atwater specific, Atwater general (Foundation)
PROTEIN = ["1003"]
FAT = ["1004", "1085"]
CARBS = ["1005", "1050"]  # by difference; then summation
FIBER = ["1079"]
SUGAR = ["2000", "1063"]
SAT_FAT = ["1258"]
SODIUM = ["1093"]

WANTED = set(ENERGY_KCAL + PROTEIN + FAT + CARBS + FIBER + SUGAR + SAT_FAT + SODIUM)
KEEP_TYPES = {"sr_legacy_food", "foundation_food"}


def find_member(zf: zipfile.ZipFile, name: str) -> str | None:
    for member in zf.namelist():
        if member.endswith("/" + name) or member == name:
            return member
    return None


def read_csv(zf: zipfile.ZipFile, name: str):
    member = find_member(zf, name)
    if member is None:
        return
    with zf.open(member) as raw:
        yield from csv.DictReader(io.TextIOWrapper(raw, encoding="utf-8-sig", newline=""))


def clean(text: str) -> str:
    return " ".join((text or "").replace("\t", " ").split())


def fmt(value: float) -> str:
    text = f"{value:.2f}".rstrip("0").rstrip(".")
    return "0" if text in ("", "-0") else text


def first(values: dict[str, float], ids: list[str]) -> float | None:
    for nutrient_id in ids:
        if nutrient_id in values:
            return values[nutrient_id]
    return None


def portion_label(row: dict, units: dict[str, str]) -> str:
    description = clean(row.get("portion_description", ""))
    modifier = clean(row.get("modifier", ""))
    try:
        amount = float(row.get("amount") or 1)
    except ValueError:
        amount = 1.0
    unit = units.get(row.get("measure_unit_id", ""), "")
    if description and not modifier:
        return description
    parts = [fmt(amount)]
    if unit and unit != "undetermined":
        parts.append(unit)
    if modifier:
        parts.append(modifier)
    elif description:
        parts.append(description)
    return " ".join(parts) if len(parts) > 1 else description


def load(paths: list[str]) -> list[list[str]]:
    rows: list[list[str]] = []
    for path in paths:
        with zipfile.ZipFile(path) as zf:
            categories = {r["id"]: clean(r.get("description", "")) for r in read_csv(zf, "food_category.csv")}
            units = {r["id"]: clean(r.get("name", "")) for r in read_csv(zf, "measure_unit.csv")}
            foods: dict[str, dict] = {}
            for r in read_csv(zf, "food.csv"):
                if r.get("data_type") in KEEP_TYPES:
                    foods[r["fdc_id"]] = r
            nutrients: dict[str, dict[str, float]] = {fdc_id: {} for fdc_id in foods}
            for r in read_csv(zf, "food_nutrient.csv"):
                fdc_id = r.get("fdc_id")
                if fdc_id in nutrients and r.get("nutrient_id") in WANTED:
                    try:
                        nutrients[fdc_id].setdefault(r["nutrient_id"], float(r["amount"]))
                    except (TypeError, ValueError):
                        pass
            portions: dict[str, list[tuple[int, str, float]]] = {}
            for r in read_csv(zf, "food_portion.csv"):
                fdc_id = r.get("fdc_id")
                if fdc_id not in foods:
                    continue
                try:
                    grams = float(r.get("gram_weight") or 0)
                    seq = int(float(r.get("seq_num") or 0))
                except ValueError:
                    continue
                label = portion_label(r, units).replace("=", " ").replace(";", ",")
                if grams > 0 and label:
                    portions.setdefault(fdc_id, []).append((seq, label, grams))

            for fdc_id, food in foods.items():
                values = nutrients[fdc_id]
                kcal = first(values, ENERGY_KCAL)
                if kcal is None:
                    continue  # unusable without energy
                name = clean(food.get("description", ""))
                if not name:
                    continue

                def cell(ids: list[str]) -> str:
                    v = first(values, ids)
                    return "" if v is None else fmt(v)

                seen: set[str] = set()
                portion_cells = []
                for _, label, grams in sorted(portions.get(fdc_id, [])):
                    if label.lower() in seen:
                        continue
                    seen.add(label.lower())
                    portion_cells.append(f"{label}={fmt(grams)}")
                rows.append([
                    fdc_id,
                    name,
                    categories.get(food.get("food_category_id", ""), ""),
                    fmt(kcal),
                    cell(PROTEIN) or "0",
                    cell(CARBS) or "0",
                    cell(FAT) or "0",
                    cell(FIBER),
                    cell(SUGAR),
                    cell(SAT_FAT),
                    cell(SODIUM),
                    ";".join(portion_cells),
                ])
    rows.sort(key=lambda r: (r[1].lower(), r[0]))
    return rows


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", required=True, help="assets/foods directory to write into")
    parser.add_argument("zips", nargs="+", help="FoodData Central CSV zip files")
    args = parser.parse_args()

    rows = load(args.zips)
    if not rows:
        print("No foods found; check the input files.", file=sys.stderr)
        return 1

    header = ["fdc_id", "name", "category", "kcal", "protein", "carbs", "fat", "fiber", "sugar",
              "sat_fat", "sodium_mg", "portions"]
    text = "\n".join("\t".join(r) for r in [header] + rows) + "\n"
    data = text.encode("utf-8")

    os.makedirs(args.out, exist_ok=True)
    # mtime=0 keeps the gzip byte-for-byte reproducible, which F-Droid builds rely on.
    with open(os.path.join(args.out, "usda_foods.tsv.gz"), "wb") as fh:
        with gzip.GzipFile(fileobj=fh, mode="wb", compresslevel=9, mtime=0) as gz:
            gz.write(data)
    digest = hashlib.sha256(data).hexdigest()[:16]
    sources = "+".join(sorted(os.path.basename(p).replace(".zip", "") for p in args.zips))
    with open(os.path.join(args.out, "usda_version.txt"), "w", encoding="utf-8") as fh:
        fh.write(f"v{FORMAT_VERSION}-{digest}\n# {sources}\n")
    print(f"Wrote {len(rows)} foods ({len(data) // 1024} KiB uncompressed), version v{FORMAT_VERSION}-{digest}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
