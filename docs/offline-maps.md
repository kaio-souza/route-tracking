# Offline maps

How to generate and install the map of a region. Coverage: Brazil, one file per state or custom area (D07).

## 1. Generate the file on your computer

Requirements: Python 3 (standard library only) and internet access.

```sh
python3 tools/pmtiles_extract.py mata-sul-recife.pmtiles --bbox=-35.95,-9.00,-34.80,-7.90
```

The script:

1. Reads the header and directories of the Protomaps daily build (`https://build.protomaps.com/AAAAMMDD.pmtiles`, default: yesterday's build).
2. Computes which tiles fall inside the rectangle, from zoom 0 to 15.
3. Downloads only those tiles using HTTP Range requests, grouped into a few requests.
4. Writes a new PMTiles v3 file containing only the region.

Options:

| Option | Use |
|---|---|
| `--bbox=LON_MIN,LAT_MIN,LON_MAX,LAT_MAX` | region rectangle (required) |
| `--maxzoom=N` | maximum zoom (default 15; lower means a smaller file and less street detail) |
| `--source=URL` | another PMTiles source, for example a build from a specific date |

If yesterday's build does not exist yet, use `--source` with an earlier date.

## 2. Ready-made areas

| Region | bbox | Approximate size |
|---|---|---|
| Mata Sul + Recife | `-35.95,-9.00,-34.80,-7.90` | 36 MB (measured on 17/11/2026) |
| Pernambuco (mainland) | `-41.40,-9.50,-34.80,-7.25` | to be measured |

The rectangles include parts of neighboring states. The low zoom levels (state and country views) come from tiles that cover an area larger than the rectangle, so the map also shows nearby cities outside the rectangle, without street detail.

## 3. Map bundled in the APK

The build copies `maps/mata-sul-recife.pmtiles` into the APK (`assets/maps/`, not recompressed). On first launch the app installs that map by itself into `filesDir/maps/`; nothing needs to be imported.

- Another default map: `./gradlew assembleRelease -PdefaultMap=pernambuco` (uses `maps/pernambuco.pmtiles`).
- If the file does not exist in `maps/`, the build warns and produces an APK without a bundled map.
- If the user removes the bundled map in the app, it is not reinstalled.
- Cost: the APK grows by the size of the map. A release APK without a bundled map is about 32 MB; with the default Mata Sul + Recife map (36 MB) it is about 68 MB. The map also takes up space twice on the device (inside the APK and in the installed copy), because MapLibre needs to read the file directly from disk.

## 4. Install other maps on the phone

```sh
adb push mata-sul-recife.pmtiles /sdcard/Download/
```

In the app: **Mapas > Importar mapa (.pmtiles)** (the app UI is still in Portuguese; the menu means "Maps > Import map"), then choose the file in Downloads. The file name becomes the region name. To update a region, import a file with the same name: it replaces the previous one.

You can also copy the file over USB or with any file manager app; the app only needs to be able to open the file through the Android picker.

## 5. License

Data © OpenStreetMap contributors, licensed under ODbL. Tiles from the Protomaps Basemap. The attribution appears in the map's info button.
