# Contributing

Thanks for helping. Issues and pull requests are welcome.

## Ground rules

- **Nothing behind a paywall, ever.** No ads, analytics, trackers or required accounts.
- **Offline first.** Logging must work without a connection; the network only adds lookups.
- **Fewer taps.** If a change adds a step to logging food, it needs a very good reason.
- **Respect data sources.** Follow the Open Food Facts API rules (no search-as-you-type, custom User-Agent) and keep attribution visible.
- **F-Droid stays free software.** Proprietary libraries (such as ML Kit) go only in `src/play/`; `src/fdroid/` must build with free dependencies.

## Code

- Kotlin, Jetpack Compose, Material 3. Logic that doesn't need Android goes in `core/` with unit tests.
- Keep screens' state in a ViewModel; keep composables free of database calls.
- Run `./gradlew :core:test assemblePlayDebug assembleFdroidDebug` before opening a pull request. CI runs the same.
- Database changes need a Room migration and a bumped database version; schemas are exported to `app/schemas/`.

## Versioning

Versions are `vMAJOR.MINOR.PATCH`, kept in `version.properties` (the only place to change it).

| Change | Bump | Example |
| --- | --- | --- |
| Bug fix, copy or small polish | PATCH | v0.1.0 → v0.1.1 |
| New feature or a user-visible change | MINOR, PATCH back to 0 | v0.1.1 → v0.2.0 |
| First stable release, or a change that breaks backups or data | MAJOR, others back to 0 | v0.9.3 → v1.0.0 |

`versionCode` is computed as `MAJOR * 10000 + MINOR * 100 + PATCH`, so MINOR and PATCH stay below 100. Versions below 1.0.0 are marked as pre-releases.

## Releasing an update

1. Bump `version.properties`.
2. Move the `[Unreleased]` notes in `CHANGELOG.md` under a new `## [x.y.z] - YYYY-MM-DD` heading.
3. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (under 500 characters) for F-Droid.
4. Commit as `Release vX.Y.Z`, then tag and push: `git tag vX.Y.Z && git push origin main vX.Y.Z`.
5. The Release workflow checks the tag against `version.properties`, builds both flavors and publishes a GitHub release with the changelog section as notes.

To sign release APKs, add repository secrets `OM_KEYSTORE_BASE64` (the keystore, base64), `OM_KEYSTORE_PASSWORD`, `OM_KEY_ALIAS` and `OM_KEY_PASSWORD`. Without them the APKs are unsigned.
