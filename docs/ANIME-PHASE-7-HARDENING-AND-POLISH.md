# Anime Phase 7: Hardening and Polish

## Purpose

This document tracks Phase 7 of video support: validating the current video implementation against real source-pattern risks, then polishing the now-complete video shell once the runtime behavior is stable.

Phase 6 completed the product-facing shell for `VIDEO` profiles. Phase 7 is split into two tracks so runtime hardening and UX cleanup do not compete with each other.

## Phase Goal

Stabilize the current video feature for wider source coverage while cleaning up the highest-value UX/copy inconsistencies on the completed video shell.

At the end of this phase:

- the current interactive video flows are validated across a small compatibility matrix
- stream resolution and source compatibility assumptions are documented and hardened where needed
- external-player behavior is validated as best effort, with limitations documented clearly
- major manga-shaped or raw/awkward copy on video surfaces is cleaned up
- the current video shell has better consistency and better test coverage

## Split Structure

Phase 7 is intentionally split into two sub-phases:

1. `Phase 7A`: compatibility hardening
2. `Phase 7B`: product polish

An immediate bugfix bucket sits in front of 7A to resolve one known `VideoMoreTab` issue and one related product question before the broader work starts.

## Locked Scope

- `Phase 7A` should focus on hardening the current interactive flows, not introducing new product surfaces.
- `Phase 7B` should focus on copy, empty/error states, consistency, and tests for existing video UI.
- video updates badges remain disabled until a real video unseen-updates counter/notification pipeline exists.
- a real video background/manual library update sync pipeline remains out of scope for this phase.
- external-player work in this phase is best-effort validation, not a full redesign.
- manual QA planning in this phase should focus on core flows only.

## Out Of Scope

This phase does not implement:

- video background/manual sync worker pipeline
- video updates badge/notification pipeline
- downloader or offline video support
- local video support
- mixed manga/video search
- broad device matrix validation
- full external-player redesign/proxying for protected streams

## Why This Phase Exists

Phase 6 completed the visible shell, but the current implementation still has real rollout risks and clear polish debt:

- `app/src/main/java/eu/kanade/tachiyomi/ui/video/player/ResolveVideoStream.kt`
  - stream selection is still naïve and currently trusts the first returned stream
- `app/src/main/java/eu/kanade/tachiyomi/ui/video/player/VideoPlayerActivity.kt`
  - external-player handoff is best-effort only and may fail on header/cookie-sensitive streams
- `domain/src/main/java/tachiyomi/domain/video/interactor/SyncVideoWithSource.kt`
  - source identity/reconciliation still depends on source output stability
- `app/src/main/java/eu/kanade/tachiyomi/ui/video/VideoMoreTab.kt`
  - current toggle handling should be verified/fixed before broader polish work
- the completed video shell still has copy/empty/error consistency gaps across history, updates, library, details, and More

Without Phase 7:

- wider source coverage would be risky
- current external-player behavior would remain under-validated
- future bug reports would be harder to classify because compatibility assumptions are not yet documented
- the video shell would still feel uneven even though its core navigation is complete

## Current Repo State Before Phase 7

### Already implemented and available

- real `VideoBrowseTab`
- real `VideoLibraryTab`
- real `VideoHistoryTab`
- real `VideoUpdatesTab`
- real `VideoMoreTab`
- in-app Media3 playback with progress/history persistence
- best-effort external-player handoff
- video-only global search
- profile-safe history/updates/more shell routing

### Known remaining gaps

- no real video unseen-updates counter or badge pipeline
- no real video background/manual sync pipeline
- stream ranking/fallback logic remains minimal
- source identity drift across extensions has not been broadly validated
- video shell copy/error-state consistency is still uneven
- screen-model/UI coverage for the new video shell is still thin

## Immediate Bugfix Bucket

Before 7A starts, resolve the known `VideoMoreTab` issue and clarify the related product semantics.

### Targeted items

- verify/fix reactive toggle behavior for `downloadedOnly` and `incognitoMode` in `VideoMoreTab`
- review whether `downloadedOnly` is a valid video setting while video remains streaming-only

Current implementation note:

- `downloadedOnly` currently appears to have no meaningful video-shell consumer, so the expected fix path is to remove it from `VideoMoreTab` rather than preserve a dead video setting

### Checklist

- [x] Verify `VideoMoreTab` toggles update correctly in UI
- [x] Fix `VideoMoreTab` state handling if toggles are not reactive
- [x] Decide whether `downloadedOnly` should remain visible for `VIDEO` profiles
- [x] Remove or relabel `downloadedOnly` if it does not make sense for video

## Phase 7A: Compatibility Hardening

Goal: validate and harden the current interactive video flows across source-pattern classes.

### Scope

- stream resolution hardening
- source identity/reconciliation hardening
- external-player validation
- small compatibility matrix for core flows
- documentation of extension/runtime assumptions and limitations

### 1. Stream Resolution Hardening

Focus files:

- `app/src/main/java/eu/kanade/tachiyomi/ui/video/player/ResolveVideoStream.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/video/player/VideoPlayerViewModel.kt`
- `app/src/test/java/eu/kanade/tachiyomi/ui/video/player/ResolveVideoStreamTest.kt`

Planned changes:

- add timeout behavior around source init and stream fetch
- stop assuming the first returned stream is always the best playable one
- add simple ranking/fallback behavior when multiple streams are returned
- improve error mapping for bad/expired/unsupported stream cases

### 2. Source Identity / Sync Hardening

Focus files:

- `domain/src/main/java/tachiyomi/domain/anime/interactor/SyncAnimeWithSource.kt`
- `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/model/SAnime.kt`
- `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/model/SEpisode.kt`

Planned changes:

- define and document canonical matching assumptions for title/episode identity
- test URL-shape drift and duplication scenarios
- verify playback/history stability when source outputs shift in low-risk ways

### 3. External-Player Validation

Focus files:

- `app/src/main/java/eu/kanade/tachiyomi/ui/video/player/VideoPlayerActivity.kt`

Planned changes:

- validate current handoff behavior for:
  - plain progressive stream
  - HLS stream
  - referer-sensitive stream
  - cookie-sensitive stream
  - tokenized/expiring URL stream
- document which cases are best effort only
- avoid redesigning the handoff unless a very small safe improvement is obvious

### 4. Core Compatibility Matrix

Use generic source-pattern classes rather than named extensions.

Matrix categories:

- progressive public stream
- HLS stream
- referer-required stream
- cookie-auth stream
- tokenized/expiring URL stream
- multi-stream result where first stream is bad and a later stream is usable

Core flows to validate:

- source load
- browse/search
- details refresh
- episode open
- in-app playback
- resume
- history entry
- updates row open
- library continue-watching
- external-player handoff (best effort)

### 7A Checklist

- [x] Add source-init / stream-fetch timeout handling where needed
- [x] Harden stream ranking/fallback behavior
- [x] Validate tokenized URL handling
- [x] Validate cookie-based auth flows
- [x] Validate referer-sensitive flows
- [x] Validate external-player best-effort behavior
- [x] Validate the generic source-pattern compatibility matrix
- [x] Document extension/runtime compatibility assumptions and limitations

### Current Compatibility Notes

- in-app playback remains the supported path for header/cookie/referer-sensitive video sources
- external-player handoff remains best effort and may fail when the target player ignores `Browser.EXTRA_HEADERS`
- tokenized/expiring URLs are supported only insofar as sources return a still-valid stream at open time; expired tokens should surface as stream-resolution failures rather than silent bad playback
- current stream selection now prefers deterministic ranking over raw source order, but it still depends on sources returning at least one valid playable stream URL
- source/title/episode reconciliation still assumes URL stability; broad migration/repair logic for source URL-shape drift is still a later concern if real compatibility failures appear

## Phase 7B: Product Polish

Goal: clean up the highest-value UX/copy inconsistencies on the completed video shell.

### Scope

- error/copy normalization
- video terminology pass
- empty/error/retry-state cleanup
- cross-screen consistency cleanup
- test coverage for new video screens and screen models

### 1. Error / Copy Normalization

Focus files:

- `app/src/main/java/eu/kanade/tachiyomi/ui/video/updates/VideoUpdatesScreenModel.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/video/VideoLibraryScreenModel.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/video/VideoScreenModel.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/video/player/VideoPlayerViewModel.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/video/browse/globalsearch/VideoGlobalSearchScreen.kt`

Planned changes:

- remove raw throwable text from user-facing video screens
- replace remaining hardcoded or awkward fallbacks with resource-backed copy
- normalize user-safe error handling across video surfaces

### 2. Video Terminology Pass

Planned changes:

- replace manga/reading-oriented strings on video surfaces
- add video-specific watch/history/episode wording where needed
- review copy across history, updates, library, details, more, and global search

### 3. Empty / Error / Retry States

Focus files:

- `app/src/main/java/eu/kanade/presentation/video/updates/VideoUpdatesScreen.kt`
- `app/src/main/java/eu/kanade/presentation/video/history/VideoHistoryScreen.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/video/VideoLibraryTab.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/video/VideoScreen.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/video/browse/globalsearch/VideoGlobalSearchScreen.kt`

Planned changes:

- add retry affordances where appropriate
- make empty states explicit and video-specific
- add clearer “no results / no pinned sources / filters hid all results” behavior where useful

### 4. Cross-Screen UI Consistency

Focus files:

- `VideoLibraryTab.kt`
- `VideoScreen.kt`
- `presentation/video/history/VideoHistoryScreen.kt`
- `presentation/video/updates/VideoUpdatesScreen.kt`

Planned changes:

- define a shared video item interaction spec:
  - cover shape/aspect
  - start vs resume affordance
  - completed treatment
  - progress display
- align history, updates, library, and details around that spec where practical

### 5. Screen / Screen-Model Test Coverage

Targets:

- `VideoHistoryScreenModel`
- `VideoUpdatesScreenModel`
- `VideoLibraryScreenModel`
- `VideoScreenModel`
- `VideoGlobalSearchScreenModel`

Planned changes:

- add screen-model tests for loading/success/error/filter states
- add UI smoke tests for empty/success/error rendering where feasible

### 7B Checklist

- [x] Normalize video error handling and fallback copy
- [x] Replace remaining manga-shaped terminology on video surfaces
- [x] Improve empty/error/retry states on core video screens
- [x] Align video item interaction semantics across tabs
- [x] Add targeted screen-model coverage for the new video shell

## Test Plan

### Immediate bugfix bucket

- verify More-tab toggle UI updates immediately
- verify toggle persistence survives recomposition / reopen
- verify `downloadedOnly` visibility/behavior matches the chosen product direction

### Phase 7A

- stream fetch timeout behavior works as intended
- resolver can recover from “bad first stream, good later stream” cases
- tokenized/cookie/referer-sensitive sources work or fail with clear documented limits
- current external-player handoff behavior is validated and documented
- core flows pass on each planned source-pattern class

### Phase 7B

- no raw/internal error text leaks into video screens
- video copy is watch/history oriented across the shell
- empty/error states render meaningful guidance
- screen-model tests cover the main state transitions for the new video tabs

## Rollout Notes

- keep video updates badges disabled for now
- keep video background/manual sync out of scope for this phase
- treat external player as best effort unless validation reveals a very small safe improvement
- keep manual QA limited to core flows, not a broad device matrix
- avoid mixing compatibility hardening and UX polish in the same batch unless a bug clearly crosses both concerns

## Exit Criteria

Phase 7 is complete when all of the following are true.

- the immediate `VideoMoreTab` bugfix bucket is complete
- current interactive video flows are validated across the planned source-pattern matrix
- stream resolution/external-player limitations are documented and no high-severity compatibility blockers remain in core flows
- major copy/error/empty-state inconsistencies on the video shell are cleaned up
- the new video screen models have targeted test coverage

## Current Status

- Phase 7 is complete at the current scope.
- the immediate `VideoMoreTab` bugfix bucket is complete: `incognitoMode` is reactive again and `downloadedOnly` has been removed from the video More surface because it had no meaningful video-shell consumer while video remains streaming-only
- `Phase 7A` compatibility hardening is complete at the current scope: `ResolveVideoStream` now has bounded source-init and stream-fetch waits, deterministic stream ranking, documented compatibility assumptions, and documented best-effort limits for protected/external-player cases
- `Phase 7B` product polish is complete at the current scope: safer error fallbacks, more video-specific copy, clearer empty/error states, and targeted screen-model coverage now land across the core video shell
- final verification passed with `./gradlew :app:compileDebugKotlin`
- final verification passed with `./gradlew :app:testDebugUnitTest --tests "eu.kanade.tachiyomi.ui.video.player.ResolveVideoStreamTest" --tests "eu.kanade.tachiyomi.ui.video.player.VideoPlayerViewModelTest" --tests "tachiyomi.domain.video.interactor.SyncVideoWithSourceTest" --tests "eu.kanade.tachiyomi.ui.video.history.VideoHistoryScreenModelTest" --tests "eu.kanade.tachiyomi.ui.video.updates.VideoUpdatesScreenModelTest" --tests "eu.kanade.tachiyomi.ui.video.VideoLibraryScreenModelTest"`
