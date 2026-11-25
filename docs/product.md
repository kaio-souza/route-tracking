# Product

## 1. Vision

distrava records bike rides anywhere, including places with no mobile signal, and turns each ride into an image to share. Everything stays on the device: no sign-up, no server, no ads.

### Target audience

Cyclists who want to record and share their rides in a simple way, including on trails and roads with little or no data coverage.

### Principles

1. **Offline first.** After downloading the map for a region, nothing depends on the internet.
2. **One tap to start.** The home screen is the map, with a Start button.
3. **Reliable recording.** A ride is never lost, even if the app is closed or the phone restarts.
4. **Private by default.** Data leaves the device only when the user shares the image.

## 2. Scope

### MVP (version 0.1)

- Offline map on the home screen with the current position.
- Start and finish a ride.
- Background recording (screen off).
- Time, distance and average speed calculation.
- Summary image of the ride, save and share.
- Ride history.
- Import/download an offline map of a region.

### Version 0.2

- Dark mode for the whole app, map included (RF09).
- Elevation: climb, descent and profile chart (RF10).
- Points of interest marked on the map (RF11).
- Previous rides drawn on the map (RF12).
- Max speed resistant to GPS spikes (RF04).

### Out of the MVP

- Manual pause and resume (D10 in [decisions.md](decisions.md)).
- GPX export.
- Other sports (running, walking).
- Speed charts.
- Accounts, cloud, social network, rankings, segments, Strava integration.
- iOS and web.

## 3. Functional requirements

Each requirement has acceptance criteria that serve as a test checklist on the device.

### RF01: Offline map on the home screen

The app opens directly on the map, centered on the current position.

Acceptance criteria:
- With airplane mode on and a map of the region installed, the map appears complete.
- The current position appears as a dot and the map centers on it when the app opens.
- Without an installed map, the screen shows a message leading to the Offline maps screen.
- There is a button to re-center on the current position.

### RF02: Start a ride

Acceptance criteria:
- The Start button is only enabled with location permission granted and GPS on; otherwise the app explains what is missing.
- When started, the timer starts at zero and a persistent notification "Recording ride" appears.
- The route is drawn in orange on the map in real time.
- The screen shows elapsed time, distance and current speed.
- Recording continues with the screen off and the app in the background.

### RF03: Finish a ride

Acceptance criteria:
- Finishing asks for confirmation (to avoid accidental taps).
- When finished, the notification disappears and the ride is saved.
- Rides with fewer than 2 valid points or less than 50 m are discarded, with a warning to the user.
- The app opens the Summary screen with the final data.

### RF04: Ride statistics

Calculated when the ride finishes and saved with the ride:

| Metric | Definition |
|---|---|
| Total time | end minus start |
| Moving time | sum of the intervals where speed was above 1 km/h |
| Distance | sum of the distances between consecutive valid points |
| Average speed | distance / moving time (D09) |
| Max speed | highest recorded speed, after noise filtering |

Display formats: distance in `km` with 2 decimals (`12,34 km`), time as `h:mm:ss`, speed in `km/h` with 1 decimal (`18,7 km/h`). The app currently uses Brazilian Portuguese number formatting (decimal comma), as shown in the examples.

### RF05: Summary image

Generated when the ride finishes and can be regenerated later from the history.

Acceptance criteria:
- PNG image of 1080 × 1920 px (Stories format).
- Can be saved to the gallery and shared through the Android share sheet.
- Image generation works offline.

Proposed layout:

```
+-----------------------------+
|  distrava          17/11/26 |
|                             |
|                             |
|  (route trace in orange)    |
|                             |
|                             |
|  DISTANCE      TIME         |
|  12,34 km      0:42:10      |
|                             |
|  AVG SPEED     MAX SPEED    |
|  18,7 km/h     34,2 km/h    |
+-----------------------------+
```

- "TIME" is the moving time (D19).
- The route area shows the offline map edge to edge, with the route on top and "© OpenStreetMap" in a corner (D20). Without an installed map, a plain background is used.
- Sand background `#EFECE5`, orange route `#FC6A03`, graphite text.
- Start marker (hollow circle) and end marker (filled circle).
- Route centered and scaled to fit the area, keeping its proportions.

### RF06: History

Acceptance criteria:
- List ordered from newest to oldest, with date, distance, time and a route thumbnail on the map (D20).
- Tapping an item opens the Detail view: map with the route, statistics and the image.
- A ride can be deleted (with confirmation).

### RF07: Offline maps

Acceptance criteria:
- Coverage limited to Brazil, with one map per state (the first will be Pernambuco).
- The screen lists installed regions with name and disk size.
- Allows adding a region by importing a `.pmtiles` file with the Android file picker (D12, D17). How to generate one: [offline-maps.md](offline-maps.md).
- Allows removing a region.

### RF08: Ride recovery

Acceptance criteria:
- If the system terminates the app during recording, when reopened the app offers to "Continue" or "Finish" the ride in progress.
- No recorded point is lost.

### RF09: Dark mode

Acceptance criteria:
- A button on the home screen switches between light and dark. Until the user chooses, the app follows the system theme.
- In dark mode, the map, the cards, the buttons and the system bars are dark. All text stays readable.
- The choice survives closing the app.
- The summary image stays light, because it is meant for posting.

### RF10: Elevation

Acceptance criteria:
- The ride detail shows climb, descent and an elevation chart by distance.
- GPS altitude noise on flat ground does not produce climb.
- Rides without altitude hide the block instead of showing zeros.

### RF11: Points of interest

Acceptance criteria:
- A long-press on the map opens a dialog to pick a kind (water, bike shop, restroom, food, other) and an optional name.
- Points appear on the map with the kind color and the name, also during recording. They are translucent (80%) so they don't hide the route.
- A button on the home screen shows or hides all points. The choice survives closing the app.
- Tapping a point shows it and allows deleting it.
- Points are stored on the device only.

### RF13: Favorites

Acceptance criteria:
- Starring a ride asks for a name (pre-filled with the date). Unstarring removes the name.
- The History screen has two tabs: **All** and **Favorites**. Favorites show their name.
- A favorite can be drawn on the map in blue, under the current route. A chip above the start button shows which one, and tapping it changes or clears it.
- The chosen favorite survives closing the app.

### RF14: Sharing by QR code

Acceptance criteria:
- A favorite can be shared as a QR code from its details screen.
- All points of interest can be shared as one QR code from the History screen.
- The receiver scans the code inside the app (camera permission is asked only then). Favorites are added to the Favorites tab; points are added to the map without duplicates.
- No account, server or file is needed. A content that does not fit in one QR code is refused with a message.

### RF12: Previous rides on the map

Acceptance criteria:
- A button on the home screen shows or hides past routes.
- Past routes are drawn under the current route and never hide it.
- The choice survives closing the app.

## 4. Non-functional requirements

| ID | Requirement |
|---|---|
| RNF01 | Works 100% offline, except for map downloads and sharing |
| RNF02 | Battery use during recording of at most 8% per hour on the test device (target to validate) |
| RNF03 | Maximum loss of 5 s of points in case of abrupt shutdown |
| RNF04 | Data only in the app's internal storage |
| RNF05 | App opens on the map in under 2 s on the test device |
| RNF06 | Interface in Brazilian Portuguese, with texts in `strings.xml` to allow translation |
| RNF07 | Screens usable with one hand and with gloves: main buttons at least 64 dp |

## 5. Screens and navigation

```
                 +------------------+
                 |  Map (home)      |<-----------------+
                 +------------------+                  |
           Start |     |  History     | Maps           |
                   v     v              v              |
          +-----------+ +-----------+ +-------------+  |
          | Recording | | History   | | Offline     |  |
          +-----------+ +-----------+ | maps        |  |
           Finish |        |          +-------------+  |
                     v        v                         |
          +-----------+ +-----------+                   |
          |  Summary  | | Detail    |                   |
          +-----------+ +-----------+                   |
                |  Done                                  |
                +----------------------------------------+
```

| Screen | Main content |
|---|---|
| Map | offline map, position dot, points of interest, Start button, shortcuts to History and Maps, buttons for previous rides, theme and recenter |
| Recording | map with the route, timer, distance, current speed, Finish button |
| Summary | statistics, image preview, Share and Save buttons |
| History | list of rides |
| Detail | map with the route, statistics, elevation chart, image, delete |
| Offline maps | installed regions, add, remove |
