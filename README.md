# CineVozz Android

CineVozz is a native Android research app for discovering, streaming and downloading movies hosted by the Internet Archive when item metadata indicates an approved open licence.

## Features in this starter build
- Native Kotlin + Jetpack Compose interface with a dark purple theme
- Search Internet Archive's movie catalogue
- Filter results to a conservative allowlist of Creative Commons BY, BY-SA, CC0, Public Domain Mark, or an explicit public-domain rights statement
- In-app playback using AndroidX Media3 / ExoPlayer
- Download queue through Android DownloadManager into Movies/CineVozz

## Build APK
A GitHub Actions workflow builds a debug APK on every push to `main`, and can also be started manually from the Actions tab.

When the workflow succeeds:
1. Open the repository's **Actions** tab.
2. Open the latest **Build CineVozz APK** run.
3. Download the **CineVozz-debug-apk** artifact.
4. Extract the artifact ZIP; it contains `app-debug.apk`.

This is a debug APK for testing, not a Play Store release. It is not signed with a private release key.

## Content and rights
The app does not unlock paid OTT services or redistribute subscription-only titles. Internet Archive contains mixed-rights uploads. Metadata-based filtering is only a preliminary technical check, not a legal determination. Verify each item's rights on its item page and do not use content if its licence is unclear, disputed, or restrictive.

The current version searches and downloads eligible Internet Archive MP4 files. It does not yet include TMDB posters, OTT integrations, series catalogue, advanced subtitle controls, or a dedicated in-app offline library.

## Data endpoints
- `https://archive.org/advancedsearch.php`
- `https://archive.org/metadata/{identifier}`
- `https://archive.org/download/{identifier}/{filename}`
