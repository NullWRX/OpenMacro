# OpenMacro

A free, open-source calorie and macro tracker for Android. The features other trackers charge for, such as barcode scanning, label scanning and custom macro targets, are free here, with no ads, no account and no subscription.

> **Status: v0.2.1, early preview.** The calorie and macro tracker is the focus. Workout tracking is planned for later.

## Features

**Logging**
- Food diary by day, with your own meal names (Breakfast, Lunch, Dinner, Snacks by default)
- Barcode scanning with Open Food Facts lookup; scanned products are saved for offline use
- Nutrition-label scanning: point the camera at the label and the values fill in live (Play build)
- One search across your foods, bundled USDA generic foods and, on request, Open Food Facts
- Recent foods ranked for the meal you're adding to, favorites, and one-tap re-logging of your usual amount
- Custom foods per serving, per 100 g or per 100 ml, with a check that calories match the macros
- Quick add for calories and macros only
- Copy a meal or a whole day; move or copy single items; undo delete
- New items default to the meal that fits the time of day

**Goals and targets**
- Fitness goals that set your calories and macros: Lose fat, Recomp, Maintain, Build muscle (lean bulk) and Bulk
- A custom goal: calories from a weekly change or an exact number, macros by percent, g per kg or lb, or grams
- Energy needs from your profile (Mifflin-St Jeor plus activity), with a live preview and safety limits
- Targets can recalculate as you log your weight

**Progress**
- Weight log with a smoothed trend line and weekly rate of change

**Your data**
- Everything is stored on the phone
- Full JSON backup and restore, and CSV export of your diary and weights
- Metric or imperial, kcal or kJ, light, dark or wallpaper colors

The full research, feature list and roadmap live in the project spec document. Planned next: saved meals, recipes, recipe import, adaptive TDEE, micronutrients, fasting timer, charts, widgets and Health Connect.

## Two builds

| Build | Barcode scanner | Label scanner | Where |
| --- | --- | --- | --- |
| `play` | ML Kit (on-device, offline) | Yes | Google Play, GitHub releases |
| `fdroid` | ZXing | Not yet (needs a free OCR engine) | F-Droid |

Both builds process camera images on the phone. Neither contains ads, analytics or trackers.

## Data sources

- **Open Food Facts**: packaged foods by barcode. Database under the [ODbL](https://opendatacommons.org/licenses/odbl/1-0/); images CC BY-SA. The app follows the [API rules](https://openfoodfacts.github.io/openfoodfacts-server/api/): a custom User-Agent, product reads one per scan, and search only when you ask, never as you type.
- **USDA FoodData Central**: generic foods, bundled with the app. Public domain (CC0). Generate the bundled file with the "Refresh USDA food data" workflow; see `app/src/main/assets/foods/README.md`.

## Building

Requirements: JDK 17 and the Android SDK (Android Studio works out of the box).

```bash
./gradlew :core:test                 # pure-Kotlin logic tests (label parser, energy math, trend)
./gradlew assemblePlayDebug          # Play flavor debug APK
./gradlew assembleFdroidDebug        # F-Droid flavor debug APK
```

APKs land in `app/build/outputs/apk/`. CI builds and tests every push; see `.github/workflows/`.

## Project layout

```
core/        Pure Kotlin, no Android: nutrition math, TDEE, weight trend, label parser, barcodes
app/         Android app (Kotlin, Jetpack Compose, Room, DataStore, CameraX)
  src/main/    shared code
  src/play/    ML Kit scanners
  src/fdroid/  ZXing scanner
tools/usda/  Script that builds the bundled USDA food file
```

## Versioning

Versions are `vMAJOR.MINOR.PATCH`, set in [`version.properties`](version.properties). See [CONTRIBUTING.md](CONTRIBUTING.md) for how updates are numbered and released, and [CHANGELOG.md](CHANGELOG.md) for what changed.

## License

OpenMacro is free software under the [GNU General Public License v3](LICENSE). Food data keeps its own licenses (above).

Not medical advice. Targets are estimates; check with a professional before big changes, especially if you are pregnant, under 18 or managing a health condition.
