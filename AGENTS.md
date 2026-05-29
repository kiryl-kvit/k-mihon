# Repo Notes

## Layout
- `app/` is the runtime app. Shared code lives in `core/*`, `data`, `domain`, `presentation-*`, `source-*`, `i18n`, `extensions-lib`, `telemetry`, and `macrobenchmark`.
- Custom Gradle plugins and tasks live in `gradle/build-logic/`. `settings.gradle.kts` enables type-safe project accessors and rejects project-level repositories, so add repos only there and use catalog/accessor entries instead of hardcoded versions or string project paths.

## Toolchain
- Use the Gradle wrapper (`9.4.1`) and JDK `21` from `.github/.java-version`.
- Android SDK/NDK and Java compatibility come from `gradle/mihon.versions.toml` plus build logic; do not hardcode them per module.
- Build scripts shell out to `git` for commit count/SHA/build time, so `git` must be on `PATH`.

## Validation
- Run `./gradlew spotlessCheck` / `./gradlew spotlessApply` from the repo root.
- CI runs `spotlessCheck` -> `testFossUnitTest` -> `verifySqlDelightMigration` -> `assembleFoss -Penable-updater`.
- App unit tests run on the `foss` buildType (`testBuildType = "foss"`); focused example: `./gradlew :app:testFossUnitTest --tests '...'`.
- After touching `data/src/main/sqldelight`, run `./gradlew verifySqlDelightMigration`.

## Generated Files
- Edit `app/src/main/shortcuts.xml`; the variant task generates `xml/shortcuts.xml` with `${applicationId}` substituted.
- `i18n/src/commonMain/moko-resources/**/strings.xml` and `plurals.xml` drive the generated `@xml/locales_config`; `base` maps to `en`, and empty locale resources are skipped.
- App namespace is `eu.kanade.tachiyomi`, but `applicationId` is `app.mihon`.
- Gradle properties toggle build features: `include-telemetry`, `enable-updater`, `disable-code-shrink`, `include-dependency-info`.
- Releases are tag-driven: `v*` creates the GitHub release/APK; `sdk-*` warms JitPack for `extensions-lib`, which JitPack builds on OpenJDK 17 via `:extensions-lib:publishToMavenLocal`.
