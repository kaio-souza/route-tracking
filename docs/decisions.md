# Decisions

Log of project decisions. When something is decided, move it from "Open" to "Made", with a date.

## Made

| # | Date | Decision | Reason |
|---|---|---|---|
| D01 | 2026-11-17 | Android-only app, no iOS or web | focus |
| D02 | 2026-11-17 | The home screen is the offline map | one tap to start |
| D03 | 2026-11-17 | `applicationId` and namespace: `com.distrava.app` | |
| D04 | 2026-11-17 | `minSdk` 26 (Android 8.0) | |
| D05 | 2026-11-17 | Kotlin + Jetpack Compose, command-line build with the Gradle wrapper | |
| D06 | 2026-11-17 | Data only on the device, no server in the MVP | privacy, simplicity |
| D07 | 2026-11-17 | Map coverage: Brazil only, one map per state | |
| D08 | 2026-11-17 | First test map: Pernambuco; the Mata Sul + Recife area is acceptable to start | user's region |
| D09 | 2026-11-17 | Average speed = distance / moving time; total time is also displayed | stops don't drag the average down, same as Strava |
| D10 | 2026-11-17 | No manual pause in the MVP | moving time already excludes stops |
| D11 | 2026-11-17 | MapLibre Native Android map with local vector tiles | open source, no API key, native offline support |
| D12 | 2026-11-17 | MVP: map generated on the computer and imported into the app; later, per-state download from a project URL | public OSM tiles prohibit bulk downloads |
| D13 | 2026-11-17 | ~~Summary image with a plain background~~ replaced by D20. 1080 × 1920 format kept | Stories format |
| D14 | 2026-11-17 | Manual `AppContainer`, no Hilt | less setup at the start |
| D15 | 2026-11-17 | Foreground service of type `location`, no `ACCESS_BACKGROUND_LOCATION` | fewer permissions |
| D16 | 2026-11-17 | Interface only in Brazilian Portuguese, texts in `strings.xml` | consistent with D07 |
| D17 | 2026-11-17 | Map format: PMTiles v3 (Protomaps schema), cropped from the Protomaps daily build with `tools/pmtiles_extract.py`. Replaces the idea of `.mbtiles` + Planetiler | downloads only the region (36 MB in ~10 s for Mata Sul + Recife), no OSM extract processing or tool installation; MapLibre reads `pmtiles://` natively |
| D18 | 2026-11-17 | Kotlin 2.0.21 with the Compose compiler plugin, KSP for Room | stable version compatible with AGP 8.4.2 and Gradle 8.6 |
| D19 | 2026-11-17 | In the summary image, "TIME" is the moving time | consistent with the average speed (D09) |
| D20 | 2026-11-17 | Summary image and history thumbnails show the offline map behind the route (`MapSnapshotter`); plain background only when no map is installed | user request: a line alone doesn't show where you went |

## Open

| # | Question | Options |
|---|---|---|
| Q04 | Activity types | bicycle only in the MVP, or sport selection now |
| Q05 | Extra image format | add 1080 × 1350 (feed) besides Stories |
