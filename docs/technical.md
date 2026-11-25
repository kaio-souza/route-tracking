# Technical

## 1. Stack

| Layer | Choice | Version |
|---|---|---|
| Build | Gradle wrapper | 8.6 |
| Android plugin | Android Gradle Plugin | 8.4.2 |
| Language | Kotlin (+ Compose compiler plugin) | 2.0.21 |
| Annotation processor | KSP | 2.0.21-1.0.28 |
| UI | Jetpack Compose + Material 3 (BOM) | 2024.10.01 |
| Navigation | Navigation Compose | 2.8.4 |
| Lifecycle | Lifecycle (runtime-compose, viewmodel-compose, service) | 2.8.7 |
| Map | MapLibre Native Android | 11.13.5 |
| Location | Play Services Location (FusedLocationProviderClient) | 21.3.0 |
| Persistence | Room | 2.6.1 |
| JDK | OpenJDK 21 (build), Java 17 bytecode | |

`compileSdk` and `targetSdk` 34, `minSdk` 26. The APK includes only the `arm64-v8a` and `armeabi-v7a` ABIs (MapLibre native libraries).

## 2. Architecture

Single-Activity app with Compose and unidirectional data flow:

```
UI (Composables) --events--> Service / Repository --> Room
        ^                              |
        +------ StateFlow / Flow ------+
```

- **No dependency injection framework (D14).** `AppContainer`, created in `DistravaApp`, provides the database, repository, maps and preferences. Screens access the container through `applicationContext`.
- **Recording state** lives in `RecordingStateHolder` (a `StateFlow` with `Idle`, `Recording`, `Finishing`, `Finished`, `Discarded`). The service writes it; the UI observes it. Because it is state rather than an event, the UI does not lose the result if it is recreated.

### Packages

```
com.distrava.app
├── DistravaApp.kt               # Application + AppContainer
├── MainActivity.kt              # NavHost and simulator trigger (debug)
├── ui/
│   ├── Format.kt                # km, km/h, h:mm:ss, dates (pt-BR)
│   ├── theme/                   # colors and theme
│   ├── map/                     # home + recording, MapLibre in Compose, location
│   ├── history/                 # history and detail/summary
│   └── offlinemaps/             # import and remove maps
├── recording/
│   ├── RecordingService.kt      # location foreground service
│   ├── RecordingState.kt        # shared state
│   ├── LocationFilter.kt        # drops bad points
│   ├── StatsCalculator.kt       # time, distance, speeds
│   ├── Geo.kt / TrackPoint.kt   # Haversine and point model
│   └── RideSimulator.kt         # fake route for testing (debug)
├── data/
│   ├── db/                      # Room: entities, DAO, database
│   └── ActivityRepository.kt    # record, finish, image, delete
├── maps/
│   ├── OfflineMapStore.kt       # .pmtiles files in filesDir/maps
│   └── MapStyle.kt              # builds the MapLibre style JSON
└── share/
    ├── RouteProjection.kt       # lat/lon -> pixels (Web Mercator)
    ├── SummaryImageRenderer.kt  # PNG 1080 × 1920
    └── ImageExporter.kt         # gallery (MediaStore) and sharing (FileProvider)
```

`Geo`, `LocationFilter`, `StatsCalculator`, `RouteProjection` and `Format` are pure Kotlin and have unit tests in `app/src/test`.

> **Note on language:** UI strings are still in Brazilian Portuguese (`res/values/strings.xml`), as required by RNF06. The screen labels mentioned in these docs are translated to English.

## 3. Ride recording

### Foreground service

- `RecordingService` (`LifecycleService`) with `foregroundServiceType="location"`, started when the user taps Start. Because it starts while the app is visible, it **does not need `ACCESS_BACKGROUND_LOCATION`** (D15).
- Persistent notification with a timer (`setUsesChronometer`).
- `PARTIAL_WAKE_LOCK` during recording (12 h limit), so Doze does not pause the CPU with the screen off.
- `START_STICKY`: if the system kills the process, the service is recreated without an intent and resumes the ride with status `GRAVANDO` ("recording") in the database.

Service actions: `START` (new ride), `RESUME` (continue an interrupted ride) and `STOP` (finish).

### Location collection

- `FusedLocationProviderClient` with `PRIORITY_HIGH_ACCURACY`, 1 s interval.
- On the home screen, when not recording, the current position comes from a separate subscription (2 s) that is only active while the screen is visible.

### Point filter (`LocationFilter`)

A point is discarded if:

1. `accuracy` > 25 m.
2. The implied speed since the last accepted point is > 70 km/h (GPS jump).
3. The timestamp is earlier than or equal to the last accepted point's.

### Calculations (`StatsCalculator`)

- Distance: sum of Haversine distances between consecutive accepted points.
- Moving time: sum of the intervals where the segment speed was > 1 km/h.
- Average speed: distance / moving time (D09).
- Max speed: 95th percentile of the 5-segment moving averages, ignoring windows with a point less accurate than 15 m (falls back to all windows if none qualify), to cut GPS spikes.
- A ride with fewer than 2 points or less than 50 m is discarded (RF03).

### Elevation (`ElevationCalculator`)

Calculated when the detail screen opens, from the stored GPS altitude. Nothing extra is saved in the database, so older rides also get it.

- Points without altitude are skipped, but still count toward the distance axis.
- The altitude series is smoothed with a centered 5-point moving average.
- Climb and descent are counted from extreme to extreme: a turn (top or valley) is only confirmed after the altitude moves back by 3 m. GPS noise of a few meters on flat ground gives 0 m.
- The chart uses at most 300 samples. The block is hidden when the ride has fewer than 2 points with altitude.

### Persistence during recording

- The ride is created in the database when it starts, with status `GRAVANDO` ("recording").
- Points are kept in memory and written to Room every 5 s (RNF03).
- On finish: writes the remaining points, calculates statistics, generates the image and changes the status to `FINALIZADO` ("finished").
- When the app opens, a `GRAVANDO` ride without an active service shows the Continue / Finish dialog (RF08).

### Simulator (debug only)

To test without leaving home:

- The test button below Start on the home screen (labeled in Portuguese in the app), or
- `adb shell am start -n com.distrava.app.debug/com.distrava.app.MainActivity --ez simulate_ride true`

Debug builds use the package `com.distrava.app.debug` (`applicationIdSuffix`), so they install next to the release app with separate data and never mix test rides with real ones.

The service starts with a fake out-and-back route on Av. Boa Viagem (geometry taken from the OSM tiles, ~23 km/h, 30 s stop at the turnaround), 10 times faster than real time. The altitude is fictitious (two gentle hills of about 12 m plus 1 m of noise) so the elevation chart can be tested. The button does not exist and the extra is ignored in release builds.

### No GPS signal

- Until the first position arrives, the map centers on the last known position from any provider (even approximate, zoom 13) and the screen shows "Searching for GPS signal".
- The screen shows satellites ("X with signal, Y in use", via `GnssStatus.Callback`), so the user knows whether GPS is responding.
- When the app opens, it asks the system for satellite orbit data and time (`force_xtra_injection` and `force_time_injection`, permission `ACCESS_LOCATION_EXTRA_COMMANDS`). With internet access, the first fix drops from minutes to seconds (measured on the Moto G7 Power: 3 s, 13 satellites, 3 m). Without internet and without recent orbit data, GPS does a "cold start" and can take 5 to 15 min standing still in an open area. Devices without a SIM depend on Wi-Fi for this: opening the app at home before leaving helps.
- A finished ride with no points is discarded with the no-GPS message (`DiscardReason.NO_GPS`); with points but less than 50 m, the short-ride message is shown (`TOO_SHORT`).

## 4. Data

### Room entities (`data/db/Entities.kt`)

- `activities`: `id`, `startedAt`, `finishedAt`, `status` (`GRAVANDO` or `FINALIZADO`), `distanceMeters`, `totalTimeMs`, `movingTimeMs`, `avgSpeedMps`, `maxSpeedMps`, `imagePath`.
- `track_points`: `id`, `activityId` (FK with `CASCADE`), `time`, `lat`, `lon`, `altitude`, `speedMps`, `accuracyM`.
- `pois` (since database version 2): `id`, `lat`, `lon`, `name`, `kind` (`agua`, `oficina`, `banheiro`, `comida` or `outro`), `createdAt`.
- Favorites are columns of `activities`: `favorite` (boolean, default `0`) and `name` (nullable, set when the ride is starred).

Versions: 1 to 2 adds `pois`; 2 to 3 adds a nullable `name` to `activities`; 3 to 4 drops that column (`@DeleteColumn` spec `RemoveRouteName`) and adds `favorite`; 4 to 5 adds `name` back for favorites. All are Room `AutoMigration`s.

Internal units are always SI (meters, milliseconds, m/s). Conversion to km and km/h happens only in `Format`.

Volume: 1 point/s, about 3,600 points per hour. The exported schema is in `app/schemas/`.

## 5. Offline maps

### Constraint

The public OpenStreetMap tile servers prohibit bulk downloads for offline use. That is why the app uses local files.

### Approach (D11, D12, D17)

- Format: **PMTiles v3** with vector tiles in the **Protomaps Basemap** (v4) schema.
- Source: the public Protomaps daily build (`https://build.protomaps.com/AAAAMMDD.pmtiles`), cropped to a rectangle with `tools/pmtiles_extract.py`. The script reads only the directories and tiles of the region through HTTP Range requests, without downloading the whole planet. Step by step in [offline-maps.md](offline-maps.md).
- The app imports the file through the Android file picker and copies it to `filesDir/maps/`. The header is validated (`PMTiles` + version 3).
- `MapStyle` builds the style JSON: one vector source `pmtiles://file://…` per installed region, and the layers from `assets/map/layers.json` repeated for each source, grouped by type (all water below all roads, etc.).
- Glyphs (label fonts) are bundled in `assets/glyphs/` (Noto Sans Regular and Medium, Latin ranges), loaded through `asset://`. Labels use `name:pt` with a fallback to `name`.
- "© OpenStreetMap" attribution shown on the map (info button).

The Mata Sul + Recife area (`-35.95,-9.00,-34.80,-7.90`, zoom 0 to 15) is about 36 MB.

### Dark map

`MapStyle.build(…, dark = true)` swaps the colors of `layers.json` property by property, through a fixed palette (`DARK_PALETTE`). Label halos always become the dark background color, even where the light style uses white, because the same white is used for road fill. The screens pick the style from the current theme. The summary image keeps the light map on purpose.

### Overlays on the map (`DistravaMap`)

Drawn on top of the style, from bottom to top:

1. Previous rides (`historyRoutes`): thin purple lines at 55% opacity, so they never cover the current route. Up to 200 finished rides, each reduced to 400 points. Turned on by the history button on the home screen (saved in preferences).
2. Current route (orange, with a white casing) and start/end markers.
3. Points of interest: circle colored by kind, with the name below. Long-press on the map creates one; tapping the circle opens it.
4. Current position.

The favorite chosen for overlay (`overlayRoute`) is drawn in blue (`#1E88E5`, 4.5 px, 85% opacity) between the previous rides and the current route. Points of interest use `POI_OPACITY` (0.8) and are hidden entirely when the user turns them off (the home screen passes an empty list).

### QR code payload (`share/QrPayload.kt`)

Text = `distrava1:` + base64url(deflate(JSON)), generated with ZXing in error correction level L. The text must stay within 2,800 characters so it fits in one code.

- Favorite: `{"t":"r","n":name,"s":start in epoch seconds,"p":polyline}`. The polyline is Google's encoded polyline with 5 decimal digits, from at most 300 points.
- Points of interest: `{"t":"p","i":[[name,kind,lat,lon], ...]}`.

On import, a favorite is saved as a finished ride (`ActivityRepository.importFavorite`), so its statistics and image are recomputed. Per-point times are not in the payload: they are set 1 second apart from the start, so moving time is approximate.

## 6. Summary image

- `MapSnapshots` uses the MapLibre `MapSnapshotter` to render the offline map into a bitmap, without a MapView. The route (line + start/end markers) is embedded in the style itself as a GeoJSON source, so it sits exactly on top of the streets. Framing: the route's bounding box with a 12% margin (minimum ~300 m). One snapshot at a time (each opens an OpenGL context), 20 s timeout.
- `SummaryImageRenderer` builds the 1080 × 1920 PNG with `Canvas`: header, map edge to edge (y 230 to 1250, density 2.6), "© OpenStreetMap" attribution and statistics (D20). Without an installed map, it draws only the route on a plain background (Web Mercator projection, up to 4,000 points).
- File at `filesDir/images/{id}_v{VERSION}.png`. When the layout changes, `SummaryImageRenderer.VERSION` is bumped and old images are regenerated when the detail view opens.
- History thumbnails: 84 dp square snapshot, cached at `filesDir/thumbs/{id}_{hash of maps}_{px}.png` (regenerated if the installed maps change).
- Sharing: `FileProvider` + `ACTION_SEND`. Saving: `MediaStore` in `Pictures/distrava` (Android 8 and 9 require `WRITE_EXTERNAL_STORAGE`).

## 7. Permissions

| Permission | Reason | Notes |
|---|---|---|
| `ACCESS_FINE_LOCATION` | precise GPS | requested when the app opens, together with the approximate one |
| `ACCESS_COARSE_LOCATION` | required together with fine from Android 12 | if only approximate is granted, the app explains and does not record |
| `FOREGROUND_SERVICE` | recording service | |
| `FOREGROUND_SERVICE_LOCATION` | service type on Android 14+ | |
| `POST_NOTIFICATIONS` | recording notification on Android 13+ | requested when the first ride starts |
| `WAKE_LOCK` | keep the CPU active during recording | |
| `ACCESS_LOCATION_EXTRA_COMMANDS` | request orbit data (A-GPS) from the system | normal permission, no dialog |
| `WRITE_EXTERNAL_STORAGE` | save to the gallery | only up to Android 9 (`maxSdkVersion=28`) |

Not requested: `ACCESS_BACKGROUND_LOCATION` and `INTERNET` (the app does not access the network).

### Behavior by Android version

| Version | Attention point |
|---|---|
| 8 and 9 (API 26 and 28) | saving to the gallery requires storage permission |
| 10 (API 29, test device) | `foregroundServiceType="location"` required |
| 12 (API 31) | user may grant only approximate location |
| 13 (API 33) | notifications need a runtime permission |
| 14 (API 34) | `FOREGROUND_SERVICE_LOCATION` permission required |

Manufacturers such as Motorola, Xiaomi and Samsung may stop services to save battery. On the first Start, the app suggests disabling battery optimization (only once).

## 8. Map camera during recording

- The current position always stays at the center of the **visible area** (above the bottom panel), using `padding` in the `CameraPosition`.
- If the user drags the map, it stops following; after 8 s without touch it re-centers on its own. If the zoom is farther out than 14, it goes back to 16 (street level).
- To save CPU, the camera only animates (300 ms) if the position moved more than 12 px on screen. Each animation makes MapLibre redraw at 60 fps; animating for 600 ms on every GPS point cost ~58% CPU.

## 9. Measured performance

Moto G7 Power (Android 10, Snapdragon 632, 3 GB), **release** build (no R8), Mata Sul + Recife map, 17/11/2026. RAM = total PSS (`dumpsys meminfo`); CPU = `top`, 100% = one core.

| Situation | RAM | CPU |
|---|---|---|
| Cold start | | 0.84 s to first screen (debug: 3.5 to 4.1 s) |
| Idle on the map | 129 MB | 4% |
| Recording, screen on (GPS 1 Hz, standing still) | 131 MB | 20% (before camera optimization: 59%) |
| Recording, app in background | 103 MB | 1% |
| Recording, screen off | ~100 MB | 1.5% |

- About 45 MB of the RAM is map graphics memory (OpenGL); it goes away when the app goes to the background.
- Release APK: 31.7 MB, built **without a bundled map** (only MapLibre native libraries for `arm64-v8a` and `armeabi-v7a`). With the default Mata Sul + Recife map bundled, the APK is about 68 MB (see [offline-maps.md](offline-maps.md), section 3). Splitting by ABI would leave each APK at about half that size.
- Battery consumption and the comparison with Strava have not been measured yet (Strava is not installed on the test device).

## 10. Tests

- **Unit tests (JVM):** `./gradlew testDebugUnitTest`. They cover statistics, GPS filter, elevation, route projection and formatting.
- **On the device:** simulator (section 3) + acceptance criteria checklist in [product.md](product.md).
- **Field:** real ride with the screen off for at least 30 min to validate RNF02 and RNF03.
