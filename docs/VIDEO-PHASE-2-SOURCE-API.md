# Video Phase 2: Source and Extension Runtime Foundation

## Purpose

This document plans Phase 2 of video support: turning the locked video source API direction into runtime extension loading and source-manager plumbing that can carry video packages without leaking manga `Source` assumptions.

Phase 0 locked the architecture and Phase 1 completed typed profile separation. Phase 2 is the backend/runtime phase that makes video extensions loadable and registerable, while keeping manga extension behavior intact.

This phase does not build the real video browse UI, title flows, data storage, or player yet. It establishes the runtime contracts those later phases will depend on.

## Phase Goal

Establish a dedicated extension and source runtime foundation for video.

At the end of this phase:

- repo metadata can identify a package as `manga` or `video`
- installed packages can expose type metadata before runtime instantiation
- extension loading branches by package type
- runtime extension models are typed and no longer centered on manga `Source`
- manga runtime source registration stays in `SourceManager`
- video runtime source registration lives in a new `VideoSourceManager`
- video configurable-source preferences use their own provider namespace
- current manga extension UI continues to work, while video extension plumbing remains backend-only for now

## Locked Decisions

- missing repo or package `type` metadata defaults to `manga` for backward compatibility
- Phase 2 only adds backend plumbing for video extension flows; it does not wire visible video extension browsing into the placeholder video shell yet
- `SourceManager` remains manga-only
- `VideoSourceManager` is added in parallel
- `VideoCatalogueSource` remains fully parallel to manga `CatalogueSource`

## Out Of Scope

This phase does not implement:

- real video browse screens in `VIDEO` profiles
- video title details flows
- video episode details flows
- video library/history/updates repositories
- Media3 playback
- end-user video extension browsing inside `VideoBrowseTab`

## Why This Phase Exists

The video source interfaces now exist in `source-api`, but the app runtime is still manga-shaped.

Relevant current seams:

- `app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionLoader.kt`
  - only instantiates `Source` and `SourceFactory`
- `app/src/main/java/eu/kanade/tachiyomi/extension/model/Extension.kt`
  - installed and available runtime models still carry manga `Source`
- `app/src/main/java/eu/kanade/tachiyomi/extension/model/LoadResult.kt`
  - success payload still assumes manga installed extensions only
- `app/src/main/java/eu/kanade/tachiyomi/extension/ExtensionManager.kt`
  - registration and package/source lookups assume every extension exposes manga sources
- `app/src/main/java/eu/kanade/tachiyomi/source/AndroidSourceManager.kt`
  - subscribes only to manga extension sources and registers only manga `StubSource`
- `app/src/main/java/eu/kanade/tachiyomi/extension/api/ExtensionApi.kt`
  - repo parsing does not yet read a package-level `type`
- `app/src/main/java/eu/kanade/domain/extension/interactor/GetExtensionsByType.kt`
  - current extension aggregation is still manga-only

Without this phase, later video UI or playback work would either:

- fake video packages as manga packages
- keep pushing `if (video)` checks into manga extension flows
- or require a larger rewrite once runtime registration becomes unavoidable

## Desired Behavior

### Manga extensions

- keep current install/update/trust behavior
- keep current manga source registration behavior
- keep current manga extension UI behavior
- continue working when repo entries omit package type metadata

### Video extensions

- identify as `video` through repo metadata and installed package metadata
- load through a video-specific runtime branch
- register only into `VideoSourceManager`
- use video source preference storage, not manga source preference storage
- stay backend-only in this phase unless explicitly inspected in tests or internal flows

## Workstreams

## 1. Package Type Metadata And Compatibility

Add type metadata handling to both repo parsing and installed-package loading.

### Planned changes

- extend repo JSON parsing to read a package-level `type`
- treat missing `type` as `manga`
- read installed package type metadata before class instantiation
- keep the current lib-version gate in place while making video compatibility rules explicit

### Files

- `app/src/main/java/eu/kanade/tachiyomi/extension/api/ExtensionApi.kt`
- `app/src/main/java/eu/kanade/tachiyomi/extension/model/ExtensionType.kt`
- `app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionLoader.kt`

### Decisions

- package-level `type` is authoritative when present
- missing package type defaults to `ExtensionType.MANGA`
- Phase 2 should not break existing manga repos or installed manga extensions that do not yet declare `type`

### Checklist

- [x] Parse repo package `type`
- [x] Default missing repo `type` to `manga`
- [x] Read installed package `type` metadata
- [x] Default missing installed package `type` to `manga`
- [ ] Document Phase 2 compatibility assumptions for old manga packages

## 2. Typed Runtime Extension Models

Split runtime extension models by media type instead of always carrying manga `Source` values.

### Planned changes

- keep shared package metadata at the base layer only
- split installed and available runtime payloads by media type
- update `LoadResult` success handling to work with typed installed extensions
- avoid one always-manga-shaped `Extension.Installed`

### Files

- `app/src/main/java/eu/kanade/tachiyomi/extension/model/Extension.kt`
- `app/src/main/java/eu/kanade/tachiyomi/extension/model/LoadResult.kt`
- `app/src/main/java/eu/kanade/domain/extension/model/Extensions.kt`

### Decisions

- shared package metadata can remain shared
- source-bearing payloads must be media-typed
- untrusted package metadata can stay untyped because no sources are instantiated yet

### Checklist

- [x] Split installed runtime models by media type
- [x] Split available runtime models by media type
- [x] Update `LoadResult.Success` to handle typed installed extensions
- [x] Update extension aggregation models to stop assuming manga payloads

## 3. Loader Branching By Package Type

Load manga and video packages through parallel instantiation paths.

### Planned changes

- branch extension loading after reading package type metadata
- keep current manga `Source` / `SourceFactory` path intact
- add `VideoSource` / `VideoSourceFactory` path for video packages
- compute package language/display metadata from the type-appropriate runtime payload

### Files

- `app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionLoader.kt`
- `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/VideoSourceFactory.kt`
- `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/VideoCatalogueSource.kt`

### Decisions

- loader branching happens before runtime class instantiation
- manga and video packages do not share a single instantiated source list type
- the manga path should remain minimally disrupted

### Checklist

- [x] Add type-aware loader entry path
- [x] Keep manga package loading behavior intact
- [x] Add video package loading behavior
- [x] Compute package lang/metadata for video packages without manga `Source`

## 4. Source Manager Split And Registration

Register runtime sources into separate managers by media type.

### Planned changes

- keep `SourceManager` manga-only
- add `VideoSourceManager`
- add an app runtime implementation parallel to `AndroidSourceManager`
- register video sources only into the video manager
- keep manga stub-source registration separate from any later video fallback strategy

### Files

- `domain/src/main/java/tachiyomi/domain/source/service/SourceManager.kt`
- new `domain/.../VideoSourceManager.kt`
- `app/src/main/java/eu/kanade/tachiyomi/source/AndroidSourceManager.kt`
- new `app/.../source/AndroidVideoSourceManager.kt`
- `app/src/main/java/eu/kanade/tachiyomi/di/AppModule.kt`
- `app/src/main/java/eu/kanade/tachiyomi/extension/ExtensionManager.kt`

### Decisions

- `SourceManager` should not become a mixed-media manager
- `VideoSourceManager` should be parallel, even if its first implementation is narrower than manga’s
- video stub-source persistence is deferred unless it becomes necessary for fallback labels in this phase

### Checklist

- [x] Add `VideoSourceManager` interface
- [x] Add runtime `AndroidVideoSourceManager`
- [x] Keep manga source manager registration unchanged for manga packages
- [x] Register video packages into `VideoSourceManager` only
- [x] Update DI wiring for both managers

## 5. Extension Manager And Lookup Plumbing

Make extension install/update/trust flows shared, while lookup and registration become media-aware.

### Planned changes

- keep one shared `ExtensionManager`
- update installed/available/untrusted flows to work with typed runtime models
- make package lookup and icon lookup safe for typed payloads
- keep manga-only source lookup helpers scoped to manga consumers unless parallel video helpers are needed

### Files

- `app/src/main/java/eu/kanade/tachiyomi/extension/ExtensionManager.kt`
- `app/src/main/java/eu/kanade/domain/extension/interactor/GetExtensionsByType.kt`
- `app/src/main/java/eu/kanade/domain/extension/interactor/GetExtensionSources.kt`
- `app/src/main/java/eu/kanade/domain/source/interactor/GetIncognitoState.kt`

### Decisions

- shared package management stays in `ExtensionManager`
- manga-specific source lookup helpers should not silently start returning video payloads
- video extension browsing remains backend-only in this phase

### Checklist

- [x] Update `ExtensionManager` flows for typed extension payloads
- [ ] Keep manga extension UI/interactors limited to manga payloads
- [x] Add parallel backend-only hooks for later video extension browsing if needed
- [x] Keep source-incognito and package lookups correct for manga flows

## 6. Video Source Preference Namespace

Wire video configurable-source preferences through a separate provider.

### Planned changes

- add an app-side `VideoSourcePreferenceProvider`
- keep profile scoping behavior parallel to manga source prefs
- avoid sharing manga source preference keys with video source IDs

### Files

- `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/ConfigurableVideoSource.kt`
- `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/VideoSourcePreferenceProvider.kt`
- new app-side provider parallel to `mihon/feature/profiles/core/ProfileSourcePreferenceProvider.kt`
- `app/src/main/java/eu/kanade/tachiyomi/di/AppModule.kt`

### Decisions

- video source preferences are profile-scoped
- video preference keys must not share the manga source preference namespace implicitly

### Checklist

- [x] Add app-side profile-aware video source preference provider
- [x] Bind provider in DI
- [x] Verify configurable video sources resolve profile-scoped prefs through the video namespace

## 7. Backend-Only UI Preparation

Keep current manga extension UI working, but avoid prematurely exposing video extension browsing.

### Planned changes

- update current extension list/details flows so they stay manga-only
- avoid routing video packages into existing manga extension screens
- leave visible video extension browsing for a later phase

### Files

- `app/src/main/java/eu/kanade/tachiyomi/ui/browse/extension/ExtensionsScreenModel.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/browse/extension/details/ExtensionDetailsScreenModel.kt`
- manga browse UI consumers of extension flows

### Decisions

- backend plumbing first
- no user-visible video extension browsing in this phase
- current manga browse/extension UI should remain behaviorally unchanged

### Checklist

- [ ] Keep current extension list/details behavior manga-only
- [x] Exclude video packages from manga extension presentation flows
- [ ] Leave `VideoBrowseTab` placeholder-backed in this phase

## Implementation Batches

1. Metadata and typed runtime model split
2. Loader branching and `VideoSourceManager`
3. Shared manager lookup cleanup and video source preference provider
4. Manga UI containment, tests, and docs

## Test Plan

### Metadata

- parse repo entries with explicit `type = video`
- parse repo entries with missing `type` and verify fallback to `manga`
- verify installed package metadata fallback to `manga` when absent

### Loader

- load manga packages through the current `Source` path
- load video packages through the `VideoSource` path
- verify wrong-type class instantiation is rejected safely

### Registration

- verify manga packages register only in `SourceManager`
- verify video packages register only in `VideoSourceManager`
- verify manga `AndroidSourceManager` behavior remains unchanged for installed manga extensions

### Preferences

- verify configurable video source preferences resolve through the video provider namespace
- verify video source preferences stay profile-scoped and separate from manga source prefs

### Manga UI containment

- verify current extension list screens exclude video packages
- verify manga extension details screens only work with manga payloads

## Exit Criteria

Phase 2 is complete when all of the following are true.

- video package metadata is recognized from repo and installed-package paths
- runtime extension models are typed by media type
- video packages load without pretending to be manga `Source`s
- manga and video runtime source registration are split into separate managers
- configurable video source preferences use a separate profile-scoped namespace
- current manga extension UI remains intact and does not accidentally surface video packages

## Current Verification

- `./gradlew :app:compileDebugKotlin` passes
- `./gradlew :app:testDebugUnitTest --tests "eu.kanade.domain.extension.interactor.GetVideoExtensionsTest" --tests "eu.kanade.tachiyomi.extension.api.ExtensionApiTypeParsingTest" --tests "mihon.feature.profiles.core.ProfileVideoSourcePreferenceProviderTest" --tests "eu.kanade.presentation.more.settings.screen.SettingsProfileVisibilityTest" --tests "eu.kanade.tachiyomi.ui.main.MainActivityStartupGateTest" --tests "mihon.feature.profiles.core.ProfileScopedBackupProtoTest"` passes
- repo package `type` parsing now defaults missing or unknown values to `manga`
- installed package metadata now defaults missing `tachiyomi.extension.type` to `manga`
- runtime extension models are now split between manga and video payloads
- video runtime registration now flows through a dedicated `VideoSourceManager`
- extension visibility initialization is now split across manga and video hidden-source keys
- video configurable-source preferences now use a separate profile-scoped `video_source_<profileId>_<sourceId>` namespace
- backend-only video extension grouping and video source-item flows now exist parallel to the manga extension interactors
- current manga extension presentation/search/detail flows are filtered to manga payloads only

## Handoff To Phase 3

Once Phase 2 is complete, the next doc should break down Phase 3 into concrete work items covering:

- app/domain/data video title and episode models
- video library/history/updates repositories and queries
- category relations for video titles
- playback progress persistence
- optional video stub-source persistence if still needed
