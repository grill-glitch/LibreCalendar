# LibreCalendar

A Chinese calendar app for Android — Kotlin + Jetpack Compose (Material 3).
Lunar calendar, solar terms, Chinese holidays and workday adjustments, all
offline-first with optional online holiday data.

[中文说明](README.zh-CN.md)

## Features

- **Month / Week / Day / Agenda views** — swipe between months, tap a date to
  see its events, tap an event to edit it (with delete confirmation)
- **Lunar calendar & solar terms** — built-in astronomical engine, no network
  required
- **Chinese holidays & workday adjustments** — configurable data source:
  - **小米日历** (MIUI calendar API) — default for the `standard` flavor
  - **holiday-cn** ([NateScarlet/holiday-cn](https://github.com/NateScarlet/holiday-cn))
    — open data, supports **custom mirror** (settings → calendar data source)
- **Events** — create / edit / delete, reminders, repeating events
- **Tags & colors** — event colors from tags, custom colors, or a stable
  per-title hash (same title → same color); manage tags and icons in the
  label page
- **Dynamic date icon** — launcher icon shows today's date (any launcher)
- **24h day timeline** — tap a time slot to select it; new events default to
  the selected time

## Flavors

| Flavor | Package | Holiday data source |
|---|---|---|
| `standard` | `org.librelab.calendar` | MIUI calendar API (default), holiday-cn optional |
| `libre` | `org.librelab.calendar.libre` | holiday-cn (default), fully offline fallback |

Both flavors fall back to the built-in local holiday engine when offline.

## Build

```bash
# release (signed with keystore.properties if present)
./gradlew :app:assembleStandardRelease :app:assembleLibreRelease
```

Requires JDK 17+, Android SDK 36, AGP 9.3.1 / Kotlin 2.3.21.

## License

Apache-2.0
