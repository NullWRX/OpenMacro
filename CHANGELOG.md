# Changelog

All notable changes to OpenMacro. Versions follow `vMAJOR.MINOR.PATCH` (see CONTRIBUTING.md).

## [Unreleased]

## [0.1.0] - 2026-10-12

First preview of the calorie and macro tracker.

### Added
- Food diary by day with configurable meals, a daily summary ring, and protein, carb and fat progress bars
- Barcode scanning: ML Kit in the Play build, ZXing in the F-Droid build; UPC-E codes are expanded to UPC-A
- Open Food Facts barcode lookup, with found products saved on the phone; on-request Open Food Facts search
- Nutrition-label scanning (Play build): OCR rows are rebuilt from text positions, parsed for US and EU labels, and combined across frames by voting
- Flow for unknown barcodes: scan the label or type the values, keeping the barcode
- Local full-text food search ranked by favorites, use, and source
- Recent foods ranked for the current meal, favorites, and one-tap re-logging of the last amount
- Custom foods per serving, per 100 g or per 100 ml, with servings per package and a 4-4-9 calorie check
- Quick add for calories and optional macros
- Copy a meal from yesterday or to any day; copy a whole day; move or copy single items; undo delete
- Default meal by time of day
- Onboarding with Mifflin-St Jeor TDEE, activity level and weekly goal rate; editable targets for calories, macros, fiber and sodium
- Weight log with an exponentially smoothed trend, gap handling and weekly rate
- JSON backup and restore; CSV export of diary and weights
- Units (kg or lb, kcal or kJ), light and dark themes, optional wallpaper colors
- Loader for a bundled USDA FoodData Central database and a script and workflow to generate it
- CI: tests, both flavors' debug APKs and lint on every push; tagged releases build APKs and a GitHub release

### Not yet included
- The bundled USDA data file itself (run the "Refresh USDA food data" workflow once)
- Submitting new products and photos back to Open Food Facts
- Label scanning in the F-Droid build

[Unreleased]: https://github.com/NullWRX/OpenMacro/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/NullWRX/OpenMacro/releases/tag/v0.1.0
