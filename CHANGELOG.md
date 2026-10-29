# Changelog

All notable changes to Northvane. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and versions follow [Semantic Versioning](https://semver.org/).

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
