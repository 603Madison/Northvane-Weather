# Northvane

[![CI](https://github.com/603Madison/Northvane-Weather/actions/workflows/ci.yml/badge.svg)](https://github.com/603Madison/Northvane-Weather/actions/workflows/ci.yml)

A clean, private weather app for Android.

Northvane shows current conditions, hourly and 10-day forecasts, animated radar
with a future cast, and National Weather Service alerts. It uses free NOAA/NWS
data, can take current conditions from **your own WeeWX station**, and keeps
working over **satellite links such as T-Mobile T-Satellite**. No API key is
built into the app, and there are no accounts, ads or analytics.

| | |
|---|---|
| **Forecast numbers** | Hourly and daily wind, gusts, rain/snow/ice amounts, humidity, cloud cover, thunder chance (NWS gridpoint data) |
| **Power outage outlook** | Optional daily estimate tuned for New England (gusts + leaf-on season, ice, wet snow, saturated ground, alerts) |
| **Current conditions** | Your WeeWX station → nearest NWS observation → OpenWeatherMap (optional) → NWS hourly estimate |
| **Hourly / daily forecast** | NWS (api.weather.gov) → OpenWeatherMap (optional, e.g. outside the US) |
| **Alerts & notifications** | NWS active alerts for every saved location, checked in the background |
| **Radar** | NOAA MRMS (official) or NEXRAD composite, plus NOAA HRRR model future cast |
| **Satellite** | Opts in to Android 16 constrained satellite networks; automatic data-saver mode |
| **Look & feel** | Material 3, light/dark/system theme, optional wallpaper colors, °F/°C |

<p>
<img src="docs/screenshots/today_light.png" width="200" alt="Today, Portland ME">
<img src="docs/screenshots/warning_red.png" width="200" alt="Red Today screen during a warning">
<img src="docs/screenshots/outage_outlook_dark.png" width="200" alt="Power outage outlook">
<img src="docs/screenshots/today_dark_weewx.png" width="200" alt="Dark theme with a local WeeWX station">
</p>
<p>
<img src="docs/screenshots/hourly_card.png" width="300" alt="Consolidated hourly card">
<img src="docs/screenshots/outdoor_outlook.png" width="300" alt="Outdoor activity outlook">
</p>
<p>
<img src="docs/screenshots/mud_pollen_foliage.png" width="250" alt="Trail mud, pollen and fall foliage">
<img src="docs/screenshots/pollen_key_costs.png" width="250" alt="Pollen API key and costs">
<img src="docs/screenshots/locations_nicknames.png" width="200" alt="Location nicknames and notifications">
</p>
<p>
<img src="docs/screenshots/alerts_dark.png" width="200" alt="NWS alerts">
<img src="docs/screenshots/today_satellite_metric.png" width="200" alt="Satellite mode, metric">
</p>

*Screenshots are rendered from real NWS and NOAA ACIS responses for Portland, ME by `ScreenshotTest` (the outage example adds a synthetic nor'easter day; the warning, pollen and nickname examples use sample data).*

## Install

Download the latest APK from [Releases](https://github.com/603Madison/Northvane-Weather/releases),
check it against `SHA256SUMS.txt`, and open it on your phone (you'll be asked to
allow installs from your browser or file manager). Northvane runs on Android 8.0+.
The satellite features need Android 16+ and a carrier that supports satellite data.

## Status

Version 1.0.0. The data layer is tested against captured and live NWS, NOAA and
IEM responses, and every screen is rendered in tests. Still to be exercised on
real hardware: the radar map, notifications, connecting to a WeeWX station on a
LAN, and behavior on an actual satellite connection. Bug reports from those
setups are especially welcome. See [CONTRIBUTING.md](CONTRIBUTING.md).

## Features

### Weather data
- **NWS first, for free.** Grid metadata for each location is cached for two
  weeks, so a refresh is a handful of small, gzip-compressed requests.
- **WeeWX override.** Give a station its position and a radius (1–25 mi);
  its readings (temperature, humidity, wind, pressure, rain, UV, solar) replace
  the NWS observation for every saved place within that radius. Sky condition and visibility are filled from NWS. Stale station
  data falls back to NWS automatically. See [`weewx/README.md`](weewx/README.md).
- **OpenWeatherMap only as a last resort**, off by default, using *your* key
  entered in the app. It's used only when NWS/WeeWX can't supply a section,
  such as for locations outside the US.
- Offline-friendly: the last result per location is cached on the device.

### Forecast details
- Real numbers, not just the forecast wording. The NWS **gridpoint forecast**
  (~10 KB compressed) adds gusts, wind direction, rain/snow/ice amounts, sky
  cover, humidity, feels-like temperature and thunder probability to every
  hour and day.
- **Hourly** view: one tall card with every number for the next 36 hours:
  feels-like, precipitation chance (with a bar) and amount, wind direction and
  speed, gusts, humidity, dew point and cloud cover.
- **Daily** view: tap a day for its wind and gusts, totals, humidity range,
  cloud cover, thunder chance and the outage outlook. If detailed data is turned
  off for satellite, amounts are read from the NWS forecast text instead.
- **Sun** card: the sun's path across the sky with its current position, time
  to sunset or sunrise, day length and the change since yesterday.

### Outdoor outlook (optional)
Daily 0–100 scores, with reasons and practical recommendations, for **hiking**,
**camping** (overnight), **off-roading**, **mountain biking**, **paddling** and
**stargazing** (overnight; uses cloud cover and the moon's phase). Each
activity weighs what matters to it: heat and humidity, cold and wind chill,
rain and thunderstorms, gusts (and stronger summit winds), snow and ice, trail
mud and active warnings. Tips include sleeping-bag ratings for the overnight
low, rainfly and guyline advice, microspikes or snowshoes, early starts before
afternoon storms, and New England seasonal notes (black flies, ticks, hunting
season blaze orange, short days).

### Trail mud (optional)
A daily mud level (dry, damp, muddy, very muddy, frozen, snow-covered) from a
soil water balance run over the last 2–5 months of **observed** NOAA data
(ACIS gridded station analysis: daily highs, lows and precipitation; free and
keyless) and on through the forecast. It models rain, snowmelt, drying, and the
**spring thaw**: frost depth is tracked from freezing and thawing degree-days,
so mud season shows up while the ground thaws from the top over still-frozen
subsoil (validated against Concord, NH, winter–spring 2026).

### Fall foliage (optional)
There's no free machine-readable foliage feed, so Northvane estimates the color
stage and peak from latitude, elevation and this fall's observed night-time
temperatures (cool nights hurry color, a warm fall delays it, hard freezes and
drought drop leaves early), and flags windy or very wet days that will strip
leaves. Links to the official Maine and New Hampshire reports.

### Pollen (optional, your own key)
NOAA/NWS don't forecast pollen. Northvane can use the **Google Pollen API**
(5-day tree, grass and weed levels and main plants). It needs your own key,
and Settings › Data sources explains the cost. Google gives 5,000 free requests a
month, then charges US$10 per 1,000 (prices checked October 2026). Northvane asks at most
every 6 hours per place (about 124 a month), so typical use is free. The key is
sent in a header and never in the URL. Coordinates are rounded to about 1 km, and
the request carries the app's package and certificate so you can restrict the key.

### Power outage outlook (optional)
A rough, transparent daily score (Low / Elevated / High / Very high) built for
New England's tree-lined overhead lines. Each day lists the factors behind it:

- **Gusts**, weighted up while trees are in leaf, when the same wind brings
  down far more limbs. Leaf cover follows the season for the location:
  leaf-out in May (later up north and at elevation), full canopy through peak
  color, then dropping, so bare winter trees don't count.
- **Ground state**: frozen ground in winter anchors roots (less uprooting),
  while thawing, waterlogged ground in mud season lets moderate wind topple trees.
- **Ice** from freezing rain, roughly following the Sperry-Piltz ice index
  (0.25" scattered outages, 0.5"+ widespread), worse with wind.
- **Heavy wet snow** near freezing or with a low snow-to-liquid ratio, and
  especially early or late-season snow on leafed trees (October 2011, October 2020).
- **Saturated ground** (1"+ of rain) combined with wind, **thunderstorms**, heat,
  and active **NWS alerts** (High Wind, Ice Storm, Winter Storm, and others).

This is not a utility forecast. Turn it on or off in Settings › Forecast extras.

### Alerts
- Background checks every 15/30/60 minutes (WorkManager) for the saved
  locations you choose (Locations › ⋮ › Alert notifications, or Settings).
- When an NWS **warning** is in effect, the Today screen turns red. Watches,
  advisories and statements keep the normal look.
- **Send test notification** in Settings shows exactly how a warning will look and sound.
- Separate notification channels for **Warnings** (high priority), **Watches**,
  **Advisories** and **Statements**, each opt-in.
- Updates to an alert replace its earlier notification instead of stacking.
  Notifications expire when the alert ends, and tapping one opens the Alerts tab.

### Radar
- Past hour from **NOAA MRMS** via the NWS time-enabled image service (default),
  or the **IEM NEXRAD composite** as an alternative. Frames follow the actual
  scan times in NOAA's image catalog, and each frame draws the newest scan for
  every region (CONUS, Alaska, Hawaii, Puerto Rico, Guam).
- **Future cast**: up to 3 hours of HRRR simulated reflectivity (NOAA's
  rapid-refresh model, served as map tiles by the Iowa Environmental Mesonet).
- **Satellite imagery** toggle on the map: GOES-East/West visible by day and
  infrared at night (or pick visible, infrared or water vapor) from NOAA
  nowCOAST, drawn under the radar.
- A pin marks the selected location.
- Keyless OpenStreetMap basemap (OpenFreeMap) via MapLibre, with light and
  dark styles.

### Satellite connectivity (T-Satellite and similar)
- The manifest declares
  `android.telephony.PROPERTY_SATELLITE_DATA_OPTIMIZED`, so on Android 16+ the
  app is allowed onto constrained satellite networks and is listed among
  satellite-enabled apps in system Settings.
- `ConnectivityMonitor` follows Google's guidance: it requests a network
  *without* `NET_CAPABILITY_NOT_BANDWIDTH_CONSTRAINED` and treats it as
  constrained if that capability is missing or the transport is
  `TRANSPORT_SATELLITE`.
- **You choose what goes over satellite** (Settings › Satellite & data saver),
  with typical sizes shown for each item: alerts, background alert checks,
  current conditions, WeeWX station (internet-reachable), daily forecast,
  forecast details, hourly forecast, recent weather history, pollen, radar,
  radar future cast, satellite imagery and the OpenWeatherMap fallback. Limits apply on satellite automatically, always, or
  never.
- Anything turned off keeps showing its last downloaded data (expired alerts
  are dropped), and the Today screen lists what's paused. Timeouts are generous
  for high-latency links.

## Building

Requirements: a current Android Studio (with AGP 9.4 support), JDK 17+ and the
Android SDK platform 37 (Android 17). The app targets API 37 and runs on Android 8.0+.

```bash
./gradlew :core:test                        # parsers (incl. captured live NWS responses), fallback logic, security policy
./gradlew :core:test -Pnorthvane.live=true    # also hit the real NWS/NOAA/IEM services end to end
./gradlew :app:testDebugUnitTest            # renders screens to app/build/outputs/screenshots (Robolectric)
./gradlew :app:lintDebug                    # Android lint
./gradlew :app:assembleDebug                # installable debug APK in app/build/outputs/apk/debug/
```

Release builds are minified with R8. To sign one, create a git-ignored
`keystore.properties` next to `settings.gradle.kts`:

```properties
storeFile=/path/to/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

then run `./gradlew :app:assembleRelease`.

The Gradle wrapper uses Gradle 9.6.0 and verifies the distribution's SHA-256
checksum before running it.

## Project layout

```
core/   Pure Kotlin/JVM: API clients (NWS, WeeWX, OpenWeatherMap), parsers,
        source fallback/merge (WeatherRepository), alert tracking, radar
        frame math, unit formatting, URL/cleartext security policy. Unit tested.
app/    Android: Compose UI, MapLibre radar, WorkManager alert checks,
        Keystore-encrypted secret store, DataStore settings, satellite
        connectivity monitor.
weewx/  WeeWX skin + setup guide for the local-station feed.
docs/   Security and privacy notes.
```

## Configuration in the app

- **Settings › Data sources**
  - *NWS contact email* (optional). It goes in the User-Agent sent to
    weather.gov only, as NWS requests.
  - *WeeWX stations*: URL, format, optional login (Basic, bearer token or
    custom header), station position and radius, and max data age. Includes a
    **Test connection** button. On Android 17+, a station on your home
    network needs the **Nearby devices** permission. The app asks for it
    when you test or save such a station, and only then.
  - *Google Pollen API*: your key, with the cost explained, a **Test** button
    and the package/SHA-1 for restricting the key.
  - *OpenWeatherMap*: enable/disable and enter your API key. The key is stored
    encrypted and never shown again.
- **Settings › Severe weather alerts**: categories, check frequency, channels.
- **Settings › Satellite & data saver**: when limits apply, and a checkbox per data type.
- **Settings › Forecast extras**: power outage outlook, outdoor activities
  (and which ones), trail mud, fall foliage, pollen. Each is a separate switch.
- **Settings › Radar**: past-radar source, future cast on/off and range,
  satellite imagery and type.
- **Locations**: rename a place (nickname), choose whether it sends alert
  notifications, remove it.
- **Settings › Appearance**: System / Light / Dark, wallpaper colors, units.

## Data sources and attribution

- Weather, alerts and radar: NOAA / National Weather Service (public domain).
- HRRR future-cast tiles and the alternative NEXRAD composite: Iowa
  Environmental Mesonet, Iowa State University.
- Recent observed weather: NOAA Regional Climate Centers, ACIS (public domain).
- Satellite imagery: NOAA nowCOAST (GOES-East/West).
- Basemap: OpenFreeMap; © OpenMapTiles; © OpenStreetMap contributors.
- Optional: Google Pollen API, OpenWeatherMap (your own accounts and keys).
- Activity, mud, foliage and outage outlooks are Northvane's own estimates.

See [`docs/SECURITY.md`](docs/SECURITY.md) for the security and privacy design, [`SECURITY.md`](SECURITY.md) for reporting vulnerabilities, [`CHANGELOG.md`](CHANGELOG.md) for release notes and [`docs/RELEASING.md`](docs/RELEASING.md) for how releases are made.
