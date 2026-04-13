# Anime Phase 1: Profile Type Foundation

## Purpose

This document tracks Phase 1 of video support: introducing typed profiles and making the app shell respond to profile type.

Phase 0 locked the architecture. Phase 1 turns those decisions into the first product-facing foundation without implementing real video sources, video data, or playback yet.

The goal is to make `MANGA` and `VIDEO` profiles first-class, keep existing manga behavior unchanged, and prevent video profiles from ever falling back into manga screens by accident.

## Phase Goal

Make the app profile-aware by product type.

At the end of this phase:

- existing profiles behave as `MANGA`
- new profiles can be created as `MANGA` or `VIDEO`
- profile type is immutable after creation
- home-tab defaults are seeded by profile type
- `VIDEO` profiles route into a video-specific shell, even if some screens are placeholders
- source visibility defaults are separated for manga and video
- backup and restore preserve profile type

## Out Of Scope

This phase does not implement:

- real video sources
- `AnimeSourceManager`
- video library/history/updates repositories
- video playback
- Media3 screens
- real video detail/episode flows

If a `VIDEO` profile reaches the home shell in this phase, it should reach placeholder video tabs rather than manga tabs.

## Why This Phase Exists

The current profile system separates state by `profile_id`, but not by product type.

Relevant current seams:

- `data/src/main/sqldelight/tachiyomi/data/profiles.sq`
  - profile rows currently have no type
- `app/src/main/java/mihon/feature/profiles/core/Profile.kt`
  - runtime profile model currently has no type
- `app/src/main/java/mihon/feature/profiles/core/ProfileDatabase.kt`
  - insert/update/mapping logic currently assumes untyped profiles
- `app/src/main/java/mihon/feature/profiles/core/ProfileManager.kt`
  - profile creation seeds manga-oriented defaults only
- `core/common/src/main/kotlin/mihon/core/common/HomeScreenTabs.kt`
  - default tabs are global and media-agnostic
- `app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeScreen.kt`
  - all content tabs currently resolve directly to manga screens
- `app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt`
  - source visibility still uses one manga-oriented hidden-source key
- `app/src/main/java/mihon/feature/profiles/core/ProfileScopedBackup.kt`
  - profile backup payload currently has no type field
- `app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/BackupRestorer.kt`
  - profile restore currently cannot preserve product type

Without Phase 1, later video phases would either:

- leak video profiles into manga screens
- keep adding profile-type checks into manga UI paths
- or require reworking profile creation and restore later

## Desired Behavior

### Manga profiles

- keep the current app behavior
- use the current shell and current tab implementations
- continue to use manga source visibility keys
- continue to use current backup behavior, but with profile type preserved

### Video profiles

- use `VIDEO` as immutable profile type
- get their own home-shell resolution
- get their own source-visibility defaults and keys
- get placeholder video tabs in this phase
- do not route into `LibraryTab`, `UpdatesTab`, `HistoryTab`, `BrowseTab`, or `MoreTab` directly

## Workstreams

## 1. Profile Storage And Model Typing

Add profile type to storage and runtime models.

### Planned changes

- add `type` to `profiles`
- map `type` into the runtime `Profile` model
- use `ProfileType` for all profile creation paths
- keep type immutable after creation

### Files

- `data/src/main/sqldelight/tachiyomi/data/profiles.sq`
- `data/src/main/sqldelight/tachiyomi/migrations/14.sqm`
- `app/src/main/java/mihon/feature/profiles/core/Profile.kt`
- `domain/src/main/java/tachiyomi/domain/profile/model/ProfileType.kt`
- `app/src/main/java/mihon/feature/profiles/core/ProfileDatabase.kt`
- `data/src/main/java/tachiyomi/data/DatabaseAdapter.kt`
- `app/src/main/java/eu/kanade/tachiyomi/di/AppModule.kt`

### Decisions

- existing profiles migrate to `ProfileType.MANGA`
- type is stored as an integer-backed enum value
- update operations do not support type mutation

### Checklist

- [x] Add `type` to the profile schema
- [x] Add migration for existing installs
- [x] Add `ProfileType` DB adapter wiring
- [x] Add `type` to `Profile`
- [x] Update `ProfileDatabase.mapProfile()`
- [x] Update profile insert methods to require `ProfileType`
- [x] Ensure profile update paths cannot mutate `type`

## 2. Profile Creation And Management UI

Make type visible and selectable at profile creation time.

### Planned changes

- replace the name-only create dialog with a name-plus-type dialog
- display type in profile management UI
- keep rename/archive/delete flows unchanged

### Files

- `app/src/main/java/mihon/feature/profiles/ui/ProfilesSettingsScreen.kt`
- `app/src/main/java/mihon/feature/profiles/ui/ProfilePickerScreen.kt`
- profile-related string resources

### Decisions

- profile type is chosen at creation time only
- profile type is shown in UI, but not editable later

### Checklist

- [x] Add profile type picker to create-profile dialog
- [x] Update `ProfileManager.createProfile()` call sites
- [x] Show type label/badge in profile cards
- [x] Show type indicator in profile picker tiles if useful
- [x] Keep archive/delete safeguards unchanged

## 3. Typed Default Seeding

Seed profile-scoped defaults according to type.

### Planned changes

- make default home tabs type-aware
- make default startup tab type-aware
- seed source-visibility defaults separately for manga and video
- keep existing profiles untouched apart from migration to `MANGA`

### Files

- `core/common/src/main/kotlin/mihon/core/common/HomeScreenTabs.kt`
- `core/common/src/main/kotlin/mihon/core/common/CustomPreferences.kt`
- `app/src/main/java/mihon/feature/profiles/core/ProfileManager.kt`

### Decisions

- `MANGA` profiles keep current defaults
- `VIDEO` profiles start with `Library`, `Updates`, `History`, `Browse`, and `More`
- startup tab defaults should be valid for the profile type

### Checklist

- [x] Add type-aware default home-tab helpers
- [x] Add type-aware startup-tab helper
- [x] Seed app-state tab preferences on profile creation
- [x] Seed type-appropriate source visibility defaults on profile creation
- [x] Keep current default-profile behavior intact

## 4. Home Shell Resolution By Profile Type

Prevent video profiles from reaching manga tabs.

### Planned changes

- make `HomeScreen` resolve content tabs using `activeProfile.type`
- keep shared tab labels in `HomeScreenTabs`
- add placeholder video tab objects/screens for Phase 1

### Files

- `app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeScreen.kt`
- new video placeholder tab files under the video UI package to be introduced

### Decisions

- same top-level tab names are allowed
- underlying tab objects must diverge by profile type
- placeholders are acceptable in this phase

### Recommended placeholder set

- `VideoLibraryTab`
- `VideoUpdatesTab`
- `VideoHistoryTab`
- `VideoBrowseTab`
- `VideoMoreTab`

### Checklist

- [x] Make launch-tab resolution profile-type aware
- [x] Make content-tab resolution profile-type aware
- [x] Add placeholder video tabs/screens
- [x] Ensure profile switching re-resolves into the correct shell
- [x] Ensure no `VIDEO` profile can land in manga tabs

## 5. Source Visibility Split

Separate manga and video source visibility keys.

### Planned changes

- stop treating `hidden_catalogues` as the universal hidden-source set
- introduce separate hidden-source keys for manga and video
- update profile creation seeding accordingly

### Files

- `app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt`
- `app/src/main/java/eu/kanade/domain/source/service/ProfileHiddenSourceIds.kt`
- `app/src/main/java/mihon/feature/profiles/core/ProfileManager.kt`
- extension/source visibility consumers discovered during implementation

### Decisions

- separate keys per media type
- `hidden_catalogues` should be treated as legacy manga state for migration purposes only

### Checklist

- [x] Add separate hidden-source preference keys
- [x] Migrate legacy `hidden_catalogues` into manga visibility state
- [x] Seed empty or type-appropriate video visibility state
- [x] Update source-visibility readers used by profile creation/switching

## 6. Backup And Restore Typing

Preserve profile type in profile-scoped backups.

### Planned changes

- add type to `ProfileBackup`
- write type during backup
- restore type during upsert
- avoid mutating an existing profile’s type on restore conflicts

### Files

- `app/src/main/java/mihon/feature/profiles/core/ProfileScopedBackup.kt`
- `app/src/main/java/eu/kanade/tachiyomi/data/backup/create/BackupCreator.kt`
- `app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/BackupRestorer.kt`

### Recommended restore conflict rule

- if a profile UUID already exists with the same type, update allowed fields
- if a profile UUID already exists with a different type, do not mutate type automatically
- log/report the conflict and keep the existing profile type

### Checklist

- [x] Add `type` to `ProfileBackup`
- [x] Include type in profile backup creation
- [x] Restore type on insert
- [x] Handle UUID/type mismatch safely on restore
- [x] Verify existing backups remain readable

## 7. Startup And Switching Validation

Make sure typed profiles behave correctly when selected at launch or switch time.

### Planned checks

- picker startup flow still works
- startup auth flow still works
- switching between `MANGA` and `VIDEO` profiles re-resolves the shell correctly
- startup tab fallback remains valid for the profile type

### Files

- `app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeScreen.kt`
- `app/src/main/java/mihon/feature/profiles/core/ProfileManager.kt`

### Checklist

- [x] Validate startup gate behavior with mixed profile types
- [x] Validate auth-required profiles after typing changes
- [x] Validate shell switching on profile change
- [x] Validate fallback tab resolution for `VIDEO` profiles

## Risks

## High Risk

- `VIDEO` profiles accidentally resolving to current manga tabs
- profile creation seeding incomplete, leaving invalid startup tabs for video profiles
- restore paths mutating an existing profile into a different type

## Medium Risk

- hidden-source migration leaving manga source visibility inconsistent
- profile settings screens still assuming all profiles share the same tab behavior
- placeholder video tabs being too thin to catch routing mistakes

## Low Risk

- adding the profile-type column itself should be straightforward because profiles already live in a dedicated table

## Implementation Order

1. Schema and model typing
2. Profile creation API and UI
3. Type-aware default seeding
4. Home-screen type-aware resolution
5. Placeholder video tabs
6. Source-visibility key split
7. Backup/restore type preservation
8. Startup/switch validation

## Test Plan

### Migration

- upgrade an existing DB and verify all existing profiles are `MANGA`
- verify the default profile remains usable

### Creation

- create a new `MANGA` profile and confirm current behavior remains intact
- create a new `VIDEO` profile and confirm it enters the video shell

### Switching

- switch between `MANGA` and `VIDEO` profiles and verify home tab resolution updates correctly
- verify startup tab fallback remains valid after switching

### Backup/Restore

- backup a mixed set of `MANGA` and `VIDEO` profiles
- restore into a fresh install and verify types are preserved
- restore into an install with conflicting profile UUID/type combinations and verify type is not silently changed

## Exit Criteria

Phase 1 is complete when all of the following are true.

- profile storage and runtime models include immutable `ProfileType`
- existing installs migrate all profiles to `MANGA`
- new profile creation requires choosing `MANGA` or `VIDEO`
- `VIDEO` profiles route to a video-specific shell, even if placeholder-backed
- manga and video source visibility use separate keys
- profile backup/restore preserves type safely
- existing manga profiles continue to behave as before

## Handoff To Phase 2

Once Phase 1 is complete, the next doc should break down Phase 2 into concrete work items covering:

- extension metadata typing
- loader branching for manga vs video packages
- typed extension models
- `AnimeSourceManager`
- video browse/extension screen models
- extension author API guidance

## Tracking Checklist

- [x] Add `ProfileType` to SQL schema and adapters
- [x] Add `ProfileType` to runtime profile models and DB mapping
- [x] Require type during profile creation
- [x] Show profile type in profile-management UI
- [x] Seed type-aware home tabs and startup tab
- [x] Add placeholder video tab implementations
- [x] Make `HomeScreen` resolve tabs by profile type
- [x] Split hidden-source visibility keys for manga and video
- [x] Preserve profile type in backup/restore
- [x] Validate startup and profile switching behavior with mixed profile types

## Current Verification

- `./gradlew :app:compileDebugKotlin` passes
- `./gradlew :app:testDebugUnitTest --tests "mihon.core.common.HomeScreenTabsTest" --tests "mihon.feature.profiles.core.ProfileAwareLibraryPreferencesTest" --tests "mihon.feature.profiles.ui.ProfileShortcutsTest"` passes
- `./gradlew :app:testDebugUnitTest --tests "eu.kanade.tachiyomi.ui.main.MainActivityStartupGateTest" --tests "mihon.feature.profiles.core.ProfileScopedBackupProtoTest"` passes
- `./gradlew :app:testDebugUnitTest --tests "eu.kanade.presentation.more.settings.screen.SettingsProfileVisibilityTest"` passes
- `HomeScreen` launch, fallback, and profile-switch routing now carry `ProfileType` through tab resolution, including video placeholder tabs
- legacy protobuf bytes for `ProfileBackup` and `ProfileScopedBackup` still decode with `type = MANGA`
- startup gate logic is validated for picker-first flow, locked `VIDEO` profiles, and app-unlock auth skipping
- settings navigation/search now hide manga-only settings categories for `VIDEO` profiles, including tablet/two-pane and direct destination routes
