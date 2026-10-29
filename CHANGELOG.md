# Changelog

All notable changes to Northvane. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and versions follow [Semantic Versioning](https://semver.org/).

## [1.2.0] - 2026-10-29

### Added
- **Personal allergy score**: pick your allergens (birch, oak, maple, grass, ragweed, outdoor mold, air pollution and more) and how strongly you react; get a daily 0–100 score with the main culprits and tips. Pollen comes from the Google Pollen API (plant-level where reported), mold is estimated from season, humidity, rain and leaf litter, and rain, dry wind, thunderstorms and poor air quality adjust the score.
- **Air quality (AQI)** card: EPA AirNow monitors and forecasts with your free key, otherwise Open-Meteo's CAMS model (no key).
- **UV index** card: EPA's hourly UV forecast for your ZIP code (no key), with today's peak and sun-protection advice.
- **Themes**: 7 static themes (Northvane, Harbor, Evergreen, Maple, Lupine, Aurora, Driftwood), 2 weather themes that change with conditions (Live sky, Temperature), and a Custom theme with your accent color and a gradient or your own photo as the background.
- **Traffic tab** (can be turned off): work zones from state DOT WZDx feeds found through USDOT's registry (no key; NH/VT/ME publish one), crashes and closures from New England 511 (free key), and an optional live speed layer from TomTom (your key; free tier explained in Settings).

### Changed
- Paddling scores 0 when it has been below freezing long enough for lakes to ice over or rivers to carry ice, and stays there until spring ice-out; shore ice lowers the score earlier.

## [1.1.0] - 2026-10-29

### Added
- **Outdoor outlook**: daily 0–100 scores with reasons and recommendations for hiking, camping (overnight), off-roading, mountain biking, paddling and stargazing (cloud cover and moon phase). Choose which activities to show.
- **Trail mud** level from a water-balance model over observed NOAA (ACIS) rain and temperatures, including spring thaw / mud season, snowmelt and frozen ground.
- **Fall foliage** estimate: color stage, estimated peak, this year's shift from observed temperatures, and leaf-stripping wind/rain days.
- **Pollen** forecast from the Google Pollen API with your own key, with the cost explained in Settings (5,000 free requests/month; at most one request per place every 6 hours).
- **Satellite imagery** on the radar map (GOES visible/infrared/water vapor from NOAA nowCOAST) and a pin for the selected location.
- **Location nicknames** and **per-location alert notifications**.
- The Today screen turns **red while an NWS warning is in effect** (watches, advisories and statements unchanged).
- Satellite settings for recent weather history, pollen and satellite imagery.

### Changed
- The hourly forecast is one tall card with every value (feels-like, precipitation chance and amount, wind, gusts, humidity, dew point, clouds) instead of switchable chips.
- WeeWX stations now have a position and radius and serve every saved place within it, instead of being paired with one location (existing pairings keep working until edited).
- The power outage outlook is season-aware: leaf cover follows estimated leaf-out and fall foliage for the location, frozen ground lowers uprooting risk and thawing ground raises it.

## [1.0.0] - 2026-10-29

First release.

### Weather
- Current conditions, 48-hour hourly forecast, 7-day daily forecast from the National Weather Service, with no API key.
- Real numbers in forecasts: gusts, wind direction, rain/snow/ice amounts, humidity, cloud cover, feels-like and thunder chance (NWS gridpoint data).
- Optional power outage outlook per day, tuned for New England (gusts with leaf-on season, ice, wet snow, saturated ground, thunderstorms, NWS alerts).
- Sun card with the sun's path, time to sunrise/sunset, day length and change since yesterday.
- Animated radar: NOAA MRMS (or NEXRAD via the Iowa Environmental Mesonet) plus HRRR model future cast.

### Sources
- WeeWX station support (Northvane JSON template, `realtime.txt`, `clientraw.txt`) that overrides current conditions for a linked location.
- Optional OpenWeatherMap fallback using your own API key, stored encrypted.

### Alerts
- Background NWS watch/warning/advisory notifications per saved location, with separate channels and a test notification button.

### Satellite
- Opts in to Android 16+ constrained satellite networks (T-Satellite and similar).
- Per-feature control over what is allowed over satellite, with typical data sizes.

### Privacy and security
- HTTPS-only for all built-in services; plain HTTP only for local-network WeeWX servers, never with credentials.
- Keystore-encrypted secrets, no backups, no analytics or trackers.
- Precise or approximate location, foreground only.
