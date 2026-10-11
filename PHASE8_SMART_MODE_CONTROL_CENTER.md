# Yoga Launcher — Phase 8: Smart Mode Control Center

Phase 8 builds on Phase 7 in-place. No existing launcher screen or Android source file is removed.

## Features
- Priority-based automation rules (1–5; 5 is highest). The first matching enabled rule at the highest priority wins.
- Pause/resume individual rules without deleting them.
- Recent activity history for mode switches, rule edits, and automation toggles (keeps the most recent 30 entries locally).
- Export smart-mode rules and automation preference to `yoga-smart-modes-backup.json`.
- Import and validate a Yoga Launcher backup; only known mode IDs and supported triggers are accepted, with a maximum of 12 rules.
- Live diagnostics for automation enabled state, active rule count, battery availability, and network state.
- The global automation remains opt-in. Manual mode selection still pauses automation for 30 minutes.

## Test checklist
1. Open Smart Modes and create two rules that match the same condition.
2. Set different priorities and verify the higher-priority rule is selected first.
3. Pause one rule and confirm it is skipped; resume it and confirm it participates again.
4. Export a backup, remove a rule, import the backup, and verify restoration.
5. Check Recent activity, then clear the history.
6. Confirm existing app drawer, widgets, themes, compass, media controls, and mode dashboards still work.

## Limitations carried forward
- The evaluator still runs in the WebView while Yoga Launcher is active; this phase does not claim guaranteed background execution.
- Background/boot automation, Bluetooth driving detection, and native foreground-service behavior remain future native integration work.
- An Android APK build must be run in Android Studio or CI with Gradle 8.7 available.
