# Changelog

All notable changes to OpenMacro. Versions follow `vMAJOR.MINOR.PATCH` (see CONTRIBUTING.md).

## [Unreleased]

## [0.2.1] - 2026-10-12

### Fixed
- Releases are now signed with one permanent key. v0.1.0 and v0.2.0 were each signed with a different temporary key, so v0.2.0 would not install over v0.1.0. From v0.2.1 on, every update installs over the previous version.

### Upgrading from v0.1.0 or v0.2.0
Back up in Settings > Your data > Back up everything, uninstall the old app, install v0.2.1, then restore the backup. This is needed once.

## [0.2.0] - 2026-10-12

Fitness goals: pick what you're training for and the app sets your calories and macros.

### Added
- Goal presets with their own calorie and macro rules:
  - **Lose fat**: about 0.5% of body weight a week, protein 2.0 g/kg
  - **Recomp**: small deficit (0.25% a week), protein 2.2 g/kg
  - **Maintain**: maintenance calories, protein 1.6 g/kg
  - **Build muscle** (lean bulk): about 0.25% a week surplus, protein 1.8 g/kg
  - **Bulk**: about 0.5% a week surplus, protein 1.6 g/kg
  - Fat is a share of calories (25 to 30%) and never below 0.5 g/kg; carbs fill the rest
- **Custom goal**: set calories from a weekly change (lose, maintain or gain) or as an exact number, and split macros by percent, by grams per kg or lb of body weight, or in exact grams
- Adjustable weekly rate for each preset, with a suggested value for your weight and a cap of 1% of body weight a week
- Live preview of the targets while you edit, showing maintenance calories, deficit or surplus, each macro's share of energy, and warnings (calorie minimum, percentages not adding to 100, carbs pushed to zero, very low fat)
- Targets can follow your weight: logging a new weigh-in recalculates them for the goal
- Fiber target (automatic or your own) and sodium limit as part of the goal
- The current goal shows on the diary and in Settings; onboarding now has a goal step
- Goal settings are included in backups

### Changed
- Existing targets are kept exactly: after updating, they appear as a custom goal in grams with weight-following off
- Goals and targets moved into one "Goal and targets" screen

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

[Unreleased]: https://github.com/NullWRX/OpenMacro/compare/v0.2.1...HEAD
[0.2.1]: https://github.com/NullWRX/OpenMacro/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/NullWRX/OpenMacro/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/NullWRX/OpenMacro/releases/tag/v0.1.0
