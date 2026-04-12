# Video Phase 6: History, Updates, and Settings

## Purpose

This document tracks Phase 6 of video support: replacing the remaining placeholder-backed `VIDEO` shell tabs with real watch-oriented `History` and `Updates` surfaces, then tightening the `More`/settings experience so video profiles feel complete outside playback.

Phase 5 completed the core browse -> details -> player -> library shell. Phase 6 finishes the remaining product-facing video tabs without collapsing back into manga-specific history, updates, or settings assumptions.

## Phase Goal

Make `VIDEO` profiles feel complete outside playback by shipping real `History` and `Updates` tabs, plus a minimal video-safe `More`/settings pass.

At the end of this phase:

- `VideoHistoryTab` is a real watch-history surface rather than a placeholder
- `VideoUpdatesTab` is a real recent-updates surface rather than a placeholder
- `VideoMoreTab` is no longer placeholder-backed
- video history rows open video details/player flows rather than manga reader flows
- video updates rows open video details/player flows rather than manga reader flows
- `VIDEO` profiles expose only video-safe settings/more actions
- notification, shortcut, and tab-default behavior for `VIDEO` profiles has been reviewed for manga leakage

## Locked Scope

- Phase 6 includes real `VideoHistoryTab`, `VideoUpdatesTab`, and a minimal-difference `VideoMoreTab`.
- Phase 6 keeps `History` and `Updates` video-specific in state, actions, and routing.
- Phase 6 may reuse neutral presentation components where they stay media-agnostic.
- Phase 6 should prefer smaller parallel screen models over parameterizing manga history/updates state too far.
- `VideoUpdatesTab` should follow the same recent-window shape used by manga updates.
- `VideoUpdatesTab` has no v1 filter sheet.
- `VideoMoreTab` should stay minimally divergent from the existing shared `More` layout unless implementation pressure proves otherwise.
- incognito mode remains present for video profiles, but wording should become watch-history neutral.

## Out Of Scope

This phase does not implement:

- downloader or offline video support
- local video support
- mixed manga/video search
- a full video-specific settings framework if a narrow video-safe pass is enough
- broad multi-source hardening and compatibility sweeps
- advanced player features like subtitles, PiP, background audio, or richer external-player controls

## Why This Phase Exists

Phase 5 left the video shell in a strong but still visibly incomplete state:

- `app/src/main/java/eu/kanade/tachiyomi/ui/video/VideoTabs.kt`
  - `VideoHistoryTab`, `VideoUpdatesTab`, and `VideoMoreTab` are still placeholder-backed
- `domain/src/main/java/tachiyomi/domain/video/repository/VideoHistoryRepository.kt`
  - watch-history data already exists, but there is no real video history tab wired to it
- `domain/src/main/java/tachiyomi/domain/video/repository/VideoUpdatesRepository.kt`
  - recent update data already exists, but there is no real video updates tab wired to it
- `app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsProfileVisibility.kt`
  - settings visibility already branches by `ProfileType`, but `VideoMoreTab` is still a placeholder so the video shell does not expose a coherent product-facing “more” surface

Without Phase 6:

- `VIDEO` profiles still ship with obvious placeholder tabs
- recent watching activity exists in repositories but is not visible as a first-class tab
- recent episode updates exist in repositories but are not visible as a first-class tab
- `VIDEO` profiles still do not have a usable product-facing `More` tab

## Current Repo State Before Phase 6

### Already implemented and available

- Phase 5 browse/details/library/player routing is complete
- `VideoHistoryRepository` supports:
  - history search
  - latest-history lookup
  - clear single row
  - clear by video
  - clear all
- `VideoUpdatesRepository` supports watched/unwatched queries with `after` and `limit`
- `VideoPlaybackStateRepository` and `VideoHistoryRepository` already power continue-watching and watch-progress UX in video details/library
- settings visibility already hides manga-only settings surfaces for `VIDEO` profiles

### Remaining gaps

- `VideoHistoryTab` placeholder replacement
- `VideoUpdatesTab` placeholder replacement
- `VideoMoreTab` placeholder replacement
- review of notification/shortcut/profile-default behavior for `VIDEO` profiles

## Sequencing Decision

Phase 6 should ship in this order:

1. `VideoHistoryTab`
2. `VideoUpdatesTab`
3. `VideoMoreTab` minimal divergence pass
4. notification, shortcut, and tab-default review

Reason:

- history already has the cleanest product-facing repository seam and aligns directly with the existing player/resume model
- updates already has query support, but the UI needs more deliberate scope control to avoid inheriting manga-only update actions
- more/settings should stay narrow until the watch-facing tabs are real

## Workstreams

## 1. Real Video History Tab

Replace the placeholder history tab with a watch-oriented history screen.

### Planned changes

- add `VideoHistoryScreenModel`
- add `VideoHistoryTab`
- add a video-specific history presentation surface
- support search by video title and episode name
- support player routing and details routing from history rows
- support destructive history actions in the first pass

### Expected behavior

- row tap or explicit resume action opens `VideoPlayerActivity(videoId, episodeId)`
- cover/title tap opens `VideoScreen(videoId)`
- clear-row and clear-all actions are available in v1
- history groups by watched date using the same high-level relative-date grouping shape as manga history
- history remains watch-oriented and should not expose manga-only actions like duplicate resolution, tracking, or reader actions

### Data concern to verify during implementation

`video_history.sq` currently resets rows by setting `last_watched = 0`, while visible-history queries filter with `last_watched IS NOT NULL`.

Phase 6 must verify that cleared rows disappear from visible history immediately. If they do not, fix the query/reset behavior as part of this slice.

### Checklist

- [x] Replace placeholder `VideoHistoryTab`
- [x] Add `VideoHistoryScreenModel`
- [x] Show grouped watch-history rows
- [x] Support search by title or episode name
- [x] Open details from history rows
- [x] Open player/resume from history rows
- [x] Add clear-row action
- [x] Add clear-all action
- [x] Verify or fix cleared-row visibility semantics

## 2. Real Video Updates Tab

Replace the placeholder updates tab with a recent video-updates screen.

### Planned changes

- add `VideoUpdatesScreenModel`
- add `VideoUpdatesTab`
- query updates using the same recent-window shape used by manga updates
- keep the UI narrower than manga updates
- route row actions into video details/player flows

### Expected behavior

- updates are driven by favorited video titles and episode fetch timestamps
- v1 uses a recent-window bound like manga updates rather than unbounded history
- row tap opens `VideoPlayerActivity(videoId, episodeId)`
- cover/title tap opens `VideoScreen(videoId)`
- no v1 filter sheet
- no chapter/bookmark/download multi-select action mode should be carried over unless a concrete video use case appears

### Checklist

- [x] Replace placeholder `VideoUpdatesTab`
- [x] Add `VideoUpdatesScreenModel`
- [x] Use manga-style recent-window bounding for video updates
- [x] Show recent episode updates for favorited video titles
- [x] Open details from update rows
- [x] Open player from update rows
- [x] Keep v1 free of manga-style filter/action-mode behavior

## 3. Minimal Video More Tab

Replace the placeholder `More` tab with a minimal video-safe surface.

### Planned changes

- replace `VideoMoreTab` placeholder with a real screen
- keep overall structure close to the shared `More` layout when components remain media-neutral
- expose only video-safe actions for `VIDEO` profiles
- relabel incognito wording so it is watch-history neutral

### Expected behavior

- keep shared/general destinations that remain valid:
  - settings
  - about
  - support
  - profiles
  - data/storage
- keep or hide entries based on video safety rather than manga parity
- avoid inventing a full parallel video settings IA unless concrete implementation pressure appears

### Checklist

- [x] Replace placeholder `VideoMoreTab`
- [x] Keep only video-safe more/actions for `VIDEO` profiles
- [x] Relabel incognito/history wording for video profiles
- [x] Reuse shared `More` UI only where it stays media-neutral

## 4. Notification, Shortcut, and Default-Behavior Review

Review the remaining shell behavior for manga leakage after the Phase 6 tabs land.

### Planned changes

- verify tab reselect/default behavior for new video tabs
- verify shortcut and notification routing does not assume manga reader flows in `VIDEO` profiles
- verify any updates badges/counts do not incorrectly reuse manga-only state in video mode

### Checklist

- [x] Review tab reselect/default behavior for `VIDEO` profiles
- [x] Review notification flows for video profiles
- [x] Review quick actions and shortcuts for video profiles
- [x] Review profile-scoped defaults for video shell behavior

## Presentation Reuse Rules

Safe to reuse when helpful:

- `SearchToolbar`
- `AppBar`
- generic `EmptyScreen` / `LoadingScreen`
- relative-date helpers
- generic confirmation dialogs where copy is still valid

Do not directly reuse if it pulls manga semantics with it:

- manga history state/actions tied to reader/chapter flows
- manga updates action mode and chapter download/bookmark/read-state actions
- duplicate-manga or merge-specific behaviors
- manga-only settings destinations exposed through `More`

## Implementation Batches

1. `VideoHistoryTab` screen model, presentation, and routing
2. history clear/reset semantics verification or fix
3. `VideoUpdatesTab` screen model and recent-window UI
4. `VideoMoreTab` minimal video-safe surface
5. notification/shortcut/default behavior review

## Test Plan

### Video history

- search filters history rows by title or episode name
- rows group by watched date correctly
- tapping resume opens the correct `videoId` and `episodeId`
- clearing a row removes it from visible history
- clearing all empties the visible history tab

### Video updates

- recent-window bound behaves like manga updates
- only favorited video titles appear
- updates open the correct details/player destination
- no manga-only filter/action-mode behavior leaks in

### Video more/settings

- `VIDEO` profiles expose only allowed more/settings actions
- incognito wording is no longer reading-specific in video mode
- hidden manga-only settings remain unavailable in `VIDEO` profiles

### Shell review

- tab reselect behavior is correct for new video tabs
- notifications/shortcuts do not route into manga-only UI from `VIDEO` profiles

## Rollout Notes

- keep Phase 6 video tabs lighter than manga when manga behavior is chapter/download/reader specific
- prefer direct player/details routes over trying to preserve manga interaction parity
- if a backend mismatch is discovered in history reset semantics, fix it during Phase 6 rather than layering UI workarounds on top
- do not broaden Phase 6 into hardening work for many source types; that remains Phase 7

## Exit Criteria

Phase 6 is complete when all of the following are true.

- `VideoHistoryTab` is a real watch-history surface
- `VideoUpdatesTab` is a real recent-updates surface
- `VideoMoreTab` is no longer placeholder-backed
- history and updates rows route into video details/player flows only
- `VIDEO` profile more/settings actions are coherent and video-safe
- notification, shortcut, and default-behavior review is complete

## Current Status

- Phase 6 planning is now defined in this document
- `VideoHistoryTab` is now implemented as a real watch-history surface with search, grouped rows, details/player routing, and clear-row/clear-all actions
- video history clear/reset visibility semantics were tightened so rows cleared by setting `last_watched = 0` no longer remain visible in history queries
- `VideoUpdatesTab` is now implemented as a real recent-updates surface with manga-style recent-window bounding, grouped rows, and direct details/player routing, while staying free of manga action-mode/filter-sheet behavior
- `VideoMoreTab` is now implemented as a minimal video-safe More surface with relabeled incognito copy and shared/general destinations only
- Phase 6 shell audit is complete: tab defaults/reselect behavior now matches the new video tabs, shortcut routing remains profile-safe, and the home updates badge explicitly stays manga-only until a real video unseen-updates counter/notification pipeline exists

Detailed Phase 7 plan: `docs/VIDEO-PHASE-7-HARDENING-AND-POLISH.md`
