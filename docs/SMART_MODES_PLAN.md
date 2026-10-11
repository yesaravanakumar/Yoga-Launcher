# Smart Modes: audit and implementation plan (Phase 1)

## Stack (actual, from the repository)
- Android app, Kotlin, `minSdk 26`, `targetSdk 34`, AGP 8.5.2, Kotlin 1.9.24, no third-party libraries.
- The whole UI is one WebView page: `app/src/main/assets/index.html` (~6,760 lines, 20 inline script blocks, no build step).
- Native side: `MainActivity` (WebView host, lifecycle), `YogaBridge` (`window.Android` JS interface: launch, contacts, calendar, media, battery, notifications, permissions, role request), `YogaWidgets` (Android AppWidget host), `PushReceiver`, `BadgeService`, `LockService`.
- HOME launcher: registered (`CATEGORY_HOME` + `DEFAULT` intent filter, `singleTask`), default-home role request via `RoleManager` (request code 7206). Back/Home handled through `window.onBack` / `window.onHome`.
- Package visibility: `QUERY_ALL_PACKAGES` plus a LAUNCHER `<queries>` block. Normal for a launcher; keep.
- Persistence: page `localStorage` (keys `mdc-sm*`) plus native `SharedPreferences("yoga")` for settings/theme.
- Build: GitHub Actions (`.github/workflows/build.yml`) builds the debug APK. No Android SDK or network in this workspace, so a Gradle build cannot be run here. Every phase is checked by JS syntax checks (all 20 script blocks currently parse) and Kotlin review; the real build result must come from CI or Android Studio.

## What already exists (v52 to v56, preserved)
| Area | State |
|---|---|
| 17 modes | Present in `v52-smart-modes`: accent theme, background tint, keyword-matched shortcuts to apps really installed |
| Mode switcher | Chip on home opens a bottom sheet with a grid; Default restores the user's own theme; choice persisted (`mdc-smode`) |
| Drive compass | In the sheet only; uses `deviceorientation(absolute)`; shows "unavailable" if no sensor |
| Widgets | Timers, checklists, notes, unit/currency (user-typed rate), battery/network, water, night clock, favourite contacts (system dialer), media controls (real media session) |
| Custom modes | Editor (name, icon, colour, up to 12 apps), max 6, persisted |
| Automation | Time-of-day rules, off by default, manual override |
| Manage modes | Show/hide and reorder |
| Onboarding | One-time card in the sheet; no permissions requested |

## Gaps against the brief
1. Modes change colours only; the home screen layout, background and centrepiece do not change per mode.
2. Drive compass is a small dial inside the sheet, not the home-screen centrepiece; heading uses the WebView event, not the native rotation-vector sensor; no screen-rotation correction or accuracy flag.
3. No mode transition animation, no per-mode wallpaper, no light theme variants, no animation intensity / reduced-motion setting for modes.
4. Mode ids differ from the brief in two places (`entertain`, `comms`); aliases needed so external references work.
5. No favourite/recent modes, no per-mode grid size / card size / icon size / widget visibility.
6. Shortcuts: missing apps are silently dropped; no install/unavailable state and no per-shortcut alternate app.
7. Widgets missing: weather, calendar agenda / meeting countdown, sunrise/sunset, steps, Do-Not-Disturb shortcut, flashlight, emergency contact, bedtime reminder, saved destinations.
8. Automation: no calendar trigger, no Bluetooth trigger, no reboot-safe scheduling, no loop guard beyond the daily key.
9. Storage: no versioned schema or migration, no corrupt-data recovery test.
10. Accessibility: partly done (labels, roles); needs font-scale and contrast review across 17 palettes.

## Phase plan (each phase ends with a compiled zip)
| Phase | Scope | Main files |
|---|---|---|
| 1 | Audit, plan, baseline checks (this document) | `docs/SMART_MODES_PLAN.md` |
| 2 | Shared mode architecture: versioned mode config store with migration, id aliases, favourites/recents, shared card + widget-container components, animated transition (250 ms, reduced-motion aware) | `index.html` (new `v57` block) |
| 3 | Mode home layouts: per-mode background, centrepiece, grid/card size, widget visibility; Drive compass dial as centrepiece; missing-app states | `index.html` |
| 4 | Custom mode builder upgrades (live preview, duplicate, widgets, grid/card size, default mode); automation: calendar + Bluetooth trigger settings, loop guard, quick override | `index.html`, `YogaBridge.kt` |
| 5 | Android integration: native rotation-vector compass, weather/sun data, calendar agenda, Bluetooth receiver, permissions and lifecycle | `YogaBridge.kt`, `MainActivity.kt`, manifest |
| 6 | Test pass, accessibility polish, limitations document, completion report | docs, fixes |

## Permissions policy
No permission is added until the phase that needs it. Planned: `BLUETOOTH_CONNECT` (Android 12+, only when the user turns on a car trigger). Already declared and reused: `READ_CALENDAR`, location, `READ_CONTACTS`. No Accessibility use for launching apps.

## Constraints I will keep
- Additive edits only; no existing screen, setting or bridge method is removed.
- No fabricated data: weather, altitude, exchange rates, device states, prices stay "unavailable" unless a real source supplies them.
- No payment automation; payment apps are plain launch shortcuts.

---
## Phase 2 status: shared mode architecture (done)
New block `v57-mode-core` at the end of `index.html` (plus a small CSS block). Only two guarded one-line hooks were added to older code (v52 `apply()` and `ORD()`).

- **Versioned config store** `mdc-smcfg` (schema v2) with a migration chain, input validation, and corrupt-data recovery (bad JSON is kept under `mdc-smcfg-bad` and defaults load; nothing crashes).
- **Id aliases**: `entertainment` maps to `entertain`, `communication` maps to `comms`; `YogaModes.set()` accepts both.
- **Favourite modes** (max 8, shown first in the grid) and **recent modes** (last 6, quick buttons), via a new "Quick access" panel in the mode sheet.
- **Mode transition**: 250 ms fade/slide in the new accent colour; skipped when the phone asks for reduced motion or the user switches it off.
- **Reusable components** for later phases: `SMC.appCard(app)` (renders an honest disabled "not installed" card when the app is missing) and `SMC.panel(title)`.
- **Keyboard navigation**: arrow keys move between mode tiles; visible focus rings.
- Preserved from before: selected mode survives restarts, hide/reorder, custom modes, schedule.

Tests: all 21 script blocks parse; 16 headless checks pass (migration from older shapes, invalid/future versions, corrupt storage, alias resolution, favourites cap, recents dedupe, stable sort, persistence, reduced motion). The visual transition and the Quick access panel have not been run on a device or in a browser here; the Gradle build can only run in CI.
