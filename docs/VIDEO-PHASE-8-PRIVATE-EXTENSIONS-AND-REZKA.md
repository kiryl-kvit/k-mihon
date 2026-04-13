# Video Phase 8: Private Extensions and Rezka

## Purpose

This document tracks Phase 8 of video support: moving from app-only video-shell work to a real private extension workspace and the first real-world video source.

Phase 7 completed the current app-side hardening and polish work. Phase 8 starts the private-use extension track by creating a separate extensions repo, then using it to validate the app against `https://rezka.ag/`.

## Phase Goal

Prove the current video stack against a real source by:

- keeping extension source code in a separate repo at `~/projects/k-mihon-extensions`
- building one private-use-only video extension package
- targeting `rezka.ag` as the first real source
- covering all four Rezka sections from the start:
  - films
  - series
  - cartoons
  - anime
- validating manual APK sideload install and end-to-end app flows against real content

At the end of this phase:

- the separate extensions repo exists and builds locally
- one private Rezka extension package installs on the emulator
- Rezka content is available through four source entries from the same extension package
- browse, search, details, episode open, and in-app playback work well enough to exercise the app's real video flows
- the temporary local dependency on the sibling app checkout is documented clearly as a development-only solution that must be refactored later

## Locked Decisions

- This phase is for private use only. It is not intended for public distribution.
- Extension source code must live outside the app repo in `~/projects/k-mihon-extensions`.
- Development may reference the sibling `../k-mihon` checkout locally, with `source-api` as the intended API surface.
- The local sibling-checkout dependency is a development-only solution and must be replaced after the video extension API reaches a stable state.
- The first real source target is `https://rezka.ag/`.
- The first Rezka extension package must cover films, series, cartoons, and anime from the start.
- The Rezka package should expose multiple `VideoCatalogueSource` entries via `VideoSourceFactory` rather than force all sections through one overloaded source.
- Built-in playback remains the only required supported path. External-player behavior remains best effort only.

## Why This Phase Exists

The app-side video shell is now real enough to test, but it still lacks one critical thing: a real installable video extension that exercises the end-to-end flows.

Current reality:

- video extensions can already load and register into `VideoSourceManager`
- the current visible extension UI remains manga-only
- manual APK sideload is therefore the fastest and lowest-risk way to start real video-source validation
- the current user goal is private real-world use, not public extension distribution

Rezka is the right first target because it matches the intended product use much more closely than public/open test sites:

- it has the content mix the private deployment actually wants
- it exposes the four top-level sections the product should support from day one
- it forces the runtime to handle real-world video-source complexity instead of only idealized public streams

## Current Constraints

- `source-api` is not yet a clean standalone extension SDK; it still has internal coupling to sibling app modules and build logic.
- The separate extensions repo should therefore treat the local sibling-checkout dependency as temporary by design, not as the final architecture.
- Rezka is a higher-complexity target than a public/open-content site and likely includes AJAX player calls, multiple translations, season/episode switching, and header/cookie/referer-sensitive playback.
- The current app does not yet have a dedicated user-facing video extension browser/install flow, so manual sideload is the correct initial install path.

## Scope

Phase 8 includes:

- creating the separate `~/projects/k-mihon-extensions` repo
- defining a temporary local development dependency on the sibling `k-mihon` checkout
- documenting that the local dependency model must be refactored after the API stabilizes
- building one private Rezka extension package
- exposing four Rezka sources:
  - Rezka Films
  - Rezka Series
  - Rezka Cartoons
  - Rezka Anime
- supporting browse, latest, search, details, episode listing, and in-app playback entry
- mapping non-episodic titles to a synthetic single episode when needed
- validating manual sideload installation and update behavior on the emulator
- running end-to-end QA against the app's library, continue-watching, history, updates, and player flows

## Out Of Scope

Phase 8 does not include:

- public distribution of the Rezka extension
- publishing a public extension repository
- broad multi-source work before Rezka works
- downloader or offline video support
- local file video support
- a new app-side video extension browser/install UX
- a new app-side per-title translation-selection UI surface
- full external-player redesign for protected streams

## Workspace Strategy

### Repo placement

- create a separate private repo at `~/projects/k-mihon-extensions`
- keep Rezka and later private video sources in that repo only

### Development dependency model

During development, the extensions repo may reference the sibling `../k-mihon` checkout locally.

Hard requirement:

- the target API surface is `source-api`
- if the current `source-api` build still requires temporary coupling to sibling app modules or build logic, that coupling must stay explicit and documented as temporary

This is not the final architecture.

Required follow-up after the video extension API stabilizes:

- extract or publish a versioned, standalone extension SDK/artifact set
- remove the direct sibling-checkout dependency from `~/projects/k-mihon-extensions`
- make the extensions repo build independently from the app checkout

## Packaging Strategy

Use one Rezka extension package that exposes multiple sources via `VideoSourceFactory`.

Planned source entries:

- `Rezka Films`
- `Rezka Series`
- `Rezka Cartoons`
- `Rezka Anime`

Reasons:

- Rezka already has these top-level sections in the site structure
- this keeps browse/latest endpoints simple per source
- this avoids a large media-type filter sheet in the first extension
- it fits naturally with the app's current video global search, which already searches across multiple video sources

## Content Mapping Strategy

### Title identity

- preserve canonical Rezka title URLs as the primary source identity key wherever possible
- normalize URLs consistently so sync/reconciliation remains stable

### Episodic vs non-episodic mapping

- films and other one-off titles should produce a synthetic single episode entry so the app always has a playable item to open
- episodic titles should expose real episode lists
- season information should be included in episode naming when required for clarity

### Search behavior

- each Rezka source should support search within its own section
- the app's existing video global search should then aggregate across the four Rezka sources automatically

## Translation And Stream Strategy

Rezka is expected to be more complex than a simple public-file source.

Plan assumptions:

- built-in playback is the primary supported path
- stream requests may require headers, cookies, and referer propagation
- the extension must return `VideoRequest` metadata rich enough for the in-app player to work when streams are protected
- external-player behavior should be treated as best effort only

Initial behavior target:

- resolve one deterministic playable translation/voice path per title at a time
- use source-level preferences where a stable global preference is sufficient
- avoid blocking the first usable extension on a brand-new app-side per-title translation picker

If Rezka makes deterministic translation selection impossible without a richer UI contract, document that explicitly before expanding the app surface.

## Implementation Batches

### 8A. Workspace Bootstrap

Goal: create the private extensions repo and prove that a video extension APK can be built and sideloaded.

Checklist:

- [ ] Create `~/projects/k-mihon-extensions`
- [ ] Choose the initial repo layout and Gradle structure
- [ ] Wire the temporary local sibling-checkout dependency on `../k-mihon`
- [ ] Document clearly that the local dependency model is development-only and must be refactored after stabilization
- [ ] Build one installable stub video extension APK

### 8B. Rezka Foundation

Goal: build the shared Rezka parser/foundation and expose the four section-specific sources.

Checklist:

- [ ] Add the Rezka extension package and `VideoSourceFactory`
- [ ] Expose `Rezka Films`, `Rezka Series`, `Rezka Cartoons`, and `Rezka Anime`
- [ ] Implement browse/popular flows for each source
- [ ] Implement latest flows for each source where the site structure supports them safely
- [ ] Implement search for each source
- [ ] Parse title cards, covers, metadata, and canonical URLs consistently

### 8C. Details, Episodes, And Streams

Goal: make Rezka titles actually playable through the current app.

Checklist:

- [ ] Parse title details into `SVideo`
- [ ] Map non-episodic titles to a synthetic single episode
- [ ] Parse real episode lists for episodic titles
- [ ] Handle season/episode naming cleanly
- [ ] Extract playable streams from Rezka's player flow
- [ ] Preserve required headers/cookies/referer in `VideoRequest`
- [ ] Apply deterministic default translation/stream selection
- [ ] Document current failure modes and unsupported cases

### 8D. App Integration Validation

Goal: validate the current app against a real installed video extension.

Checklist:

- [ ] Install the Rezka extension APK on the emulator manually
- [ ] Verify all four Rezka sources appear in the video browse flow
- [ ] Verify source browse, latest, and search flows
- [ ] Verify details open and episode open
- [ ] Verify in-app playback and resume
- [ ] Verify library add/favorite behavior
- [ ] Verify continue-watching
- [ ] Verify history entries
- [ ] Verify updates behavior when new episodes are available and sync is triggered
- [ ] Record known Rezka-specific limitations

### 8E. Optional Private Repo Automation

Goal: only if it is still useful after direct APK sideload works, add private repo/index automation.

Checklist:

- [ ] Decide whether direct APK sideload is enough for private use
- [ ] If not, generate a local private repo layout with APK artifacts and `index.min.json`
- [ ] Keep private repo/index work secondary to the core APK-based validation path

## Test Plan

### Build and install

- build the Rezka extension APK locally from `~/projects/k-mihon-extensions`
- install it on the emulator
- verify the app loads the installed video extension into `VideoSourceManager`

### Browse and search

- browse each of the four Rezka sources
- open latest/popular entries in each section
- run section search inside each source
- run app-level video global search and confirm the four Rezka sources participate

### Details and episodes

- open a film title and verify the synthetic single episode path
- open a series title and verify real episode lists
- open a cartoons title and verify both episodic and non-episodic mapping where applicable
- open an anime title and verify real episode handling

### Playback and state

- start playback in-app
- resume partially watched playback
- complete playback and verify watched/completed state updates
- verify continue-watching card behavior
- verify history entry creation
- verify library row/play action state

### Compatibility and failure handling

- verify header/cookie/referer-sensitive streams still work in-app
- verify external-player handoff remains best effort only
- verify stream-resolution failures surface as clear app errors instead of silent bad playback

## Exit Criteria

Phase 8 is complete when all of the following are true:

- `~/projects/k-mihon-extensions` exists and can build the private Rezka extension locally
- the Rezka extension package installs on the emulator
- the package exposes all four Rezka sections through `VideoSourceFactory`
- the app can browse, search, open details, open episodes, and play Rezka content in-app
- the temporary local dependency model is documented clearly as development-only
- the follow-up requirement to replace the local sibling-checkout dependency with a stable SDK is documented clearly and left as an explicit later refactor

## Follow-On Work After Phase 8

Once Rezka works well enough for private use, the likely next follow-ups are:

- stabilize and extract a proper standalone video extension SDK
- remove the temporary direct dependency on the sibling app checkout
- add private repo/index automation only if manual sideload becomes too awkward
- decide whether the app needs a dedicated video extension browser/install surface
