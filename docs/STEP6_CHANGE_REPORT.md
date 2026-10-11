# Step 6 change report: real behaviour pass

Files changed: `YogaBridge.kt`, `MainActivity.kt`, `assets/index.html`, new `tools/smoke/e2e.js`.

## Verified here
- Kotlin: all 5 sources compile with Kotlin 1.9.24 against the Android 34 stubs (0 errors, deprecation warnings only).
- `index.html`: all 25 inline scripts pass `node --check`; `smoke-load` 0 errors; `smoke-monkey` unchanged from before (only jsdom limits: `body.scrollTo`, `navigation`).
- `tools/smoke/e2e.js`: 29 checks pass against a mock Android bridge (see list at the bottom).

## NOT verified
Nothing was run on a phone or in Gradle. Backup "Save as", the share sheet, clipboard, native dialogs, the flashlight state and permission fallbacks are written to Android's documented behaviour but need one real-device pass. Run the GitHub Actions build first.

## Native changes
- `launch`, `uninstall`, `openUrl`, `appInfo`, `launchShortcut` return true/false; failures show a toast. System apps get "Android doesn't allow uninstalling it".
- New: `copyText`, `shareText`, `saveFile(name, mime, content)` (Android "Save as", result via `window.onFileSaved`), `hasCalendarPerm`.
- `onJsAlert` / `onJsConfirm` / `onJsPrompt` implemented, so every `confirm()` (uninstall, reset section, reset all, delete stored key, restore) now works.
- Flashlight tracks the real torch state through a TorchCallback.
- Contacts and calendar requests go through the "denied twice, open app settings" fallback. Calendar grant refreshes the Business card.
- Lock-screen message is correct on API 26-27 and when Android refuses.

## Page changes
- Backup v2 saves every launcher key (home layout, folders, Business slots/people, modes, hidden apps, renames, wallpaper). Caches are skipped. Restore only accepts known key patterns with valid JSON, asks for confirmation, mirrors settings/theme to the native copy and reloads. Old v1 backups still restore.
- Smart Modes export uses the same save path.
- Clipboard (time, calculator, diagnostics, error log) uses the bridge and only says "Copied" on success. Share uses the Android share sheet.
- Night mode alarm and Smart Modes battery read the values Kotlin really sends.
- Business calendar card: no "Allow" button once granted; says "access is off" when it is.
- Inside the Android app: no demo apps, no fake long-press shortcuts, no simulators, no placeholder widget, no Commute toggle (no route source exists).
- Empty task / city / checklist input now says what is missing. Multi-select Pin/Hide report real counts and ask for a selection first.
- Failed wallpaper save no longer switches the setting. An app is added to "recent" only after Android opened it.
- Business contact picker asks for Contacts permission and blocks duplicates.
- Privacy text now names Open-Meteo geocoding, BigDataCloud and Google Suggest.

## Still open
- Timers only fire while the launcher is open (needs a native alarm + notification path).
- Commute needs a routing source before the toggle can come back.
- `index.html` was not re-audited control by control in this pass; the earlier ~670/706 click sweep still stands.
