# Video Extension Author Guide

## Purpose

This document explains the package metadata and runtime expectations for video extensions introduced by the Phase 2 backend plumbing.

It is written for extension authors and maintainers preparing separate video extension packages.

## Package Type

Video extensions must declare a package-level extension type.

Required manifest metadata:

```xml
<meta-data
    android:name="tachiyomi.extension.type"
    android:value="video" />
```

Supported values:

- `manga`
- `video`

Notes:

- values are parsed case-insensitively
- if the metadata is missing, the app defaults the package to `manga` for backward compatibility
- video extensions should always declare `video` explicitly; relying on fallback is incorrect for new video packages

## Existing Required Metadata

Video extensions still follow the same base extension package rules as manga extensions.

Expected metadata remains:

- `tachiyomi.extension.class`
- `tachiyomi.extension.factory` when applicable
- `tachiyomi.extension.nsfw`

The runtime now also reads:

- `tachiyomi.extension.type`

## Runtime Interfaces

Video packages must expose video source interfaces, not manga source interfaces.

Use:

- `VideoSource`
- `VideoCatalogueSource`
- `VideoSourceFactory`

Do not expose:

- `Source`
- `CatalogueSource`
- `SourceFactory`

If a package is marked `video`, the loader will instantiate only video runtime types.
If a package is marked `video` but exports manga source classes, the load will fail.

## Factory Expectations

If the package uses a factory, it must implement:

```kotlin
class MyVideoSourceFactory : VideoSourceFactory {
    override fun createSources(): List<VideoSource> {
        ...
    }
}
```

The factory output must be `VideoSource` instances only.

## Source Preference Namespace

Video configurable-source preferences now use a separate namespace from manga source preferences.

Current key formats:

- manga source prefs: `source_<profileId>_<sourceId>`
- video source prefs: `video_source_<profileId>_<sourceId>`

Implications:

- manga and video source IDs do not share the same preference store name
- preferences are still profile-scoped
- configurable video sources should use `ConfigurableVideoSource` and `videoSourcePreferences()` rather than trying to access manga source preferences directly

## Hidden Source Visibility

Visibility is now tracked separately by media type.

Current keys:

- manga: `hidden_manga_catalogues`
- video: `hidden_video_catalogues`

Implications:

- installing a video extension initializes video-source visibility only
- manga source visibility and video source visibility do not share the same hidden-source set

## Repo Index Metadata

Repository entries for video extensions must use a package-level `type` field.

Example:

```json
{
  "name": "Tachiyomi: Example Video",
  "pkg": "eu.kanade.tachiyomi.extension.all.examplevideo",
  "apk": "tachiyomi-all.examplevideo-v1.0.0.apk",
  "lang": "en",
  "code": 1,
  "version": "1.5.0",
  "nsfw": 0,
  "type": "video",
  "sources": [
    {
      "id": 123456789L,
      "lang": "en",
      "name": "Example Video",
      "baseUrl": "https://example.com"
    }
  ]
}
```

Notes:

- repo `type` is also parsed case-insensitively
- missing `type` defaults to `manga`, again only for backward compatibility
- new video repos should always emit `type: "video"`

## Current Phase 2 Scope

At the current Phase 2 state:

- video extensions are loadable and register into `VideoSourceManager`
- video extension backend grouping exists
- current visible extension UI remains manga-only

This means:

- video packages can be parsed, loaded, and registered
- they are not yet surfaced through a user-facing video extension browser in the app shell

## Common Failure Modes

### 1. Missing `tachiyomi.extension.type`

Effect:

- package is treated as `manga`
- video runtime classes will not load correctly under the manga branch

Fix:

- add `tachiyomi.extension.type = video`

### 2. Package marked `video` but exposes manga source classes

Effect:

- loader rejects the package during runtime instantiation

Fix:

- expose `VideoSource` or `VideoSourceFactory` only

### 3. Video configurable source writes to manga preference APIs

Effect:

- wrong preference namespace
- profile/media separation is broken

Fix:

- use `ConfigurableVideoSource`
- use `videoSourcePreferences()` or `videoPreferenceKey()`

## Author Checklist

- [ ] Add `tachiyomi.extension.type = video`
- [ ] Use `VideoSource` or `VideoSourceFactory`
- [ ] Do not export manga `Source` runtime classes from a video package
- [ ] Use `ConfigurableVideoSource` if the source has preferences
- [ ] Ensure repo index entries include `type: "video"`
- [ ] Keep one package scoped to one media type only
