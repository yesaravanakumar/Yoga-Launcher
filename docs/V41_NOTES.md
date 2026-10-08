# v41 changes

1. **Arc on the left** - `dside` now defaults to Left. Existing installs are moved once (flag `al41`);
   switch back any time in Settings > Themes & Look > Arcs > Arc side.
2. **Battery percentage** - the WebView battery API goes stale while the screen is off (or is missing), so the
   lock screen showed old or jumping values. The percentage now comes from Android:
   `YogaBridge.getBattery()` + a `BATTERY_CHANGED` receiver in `MainActivity` push `window.setBattery(...)`.
   The lock screen, Widgets battery card and Auto battery saver all use the same value, and the lock screen
   updates live while it is showing. Extra states: "Fully charged", "Plugged in".
3. **Yoga lock screen instead of the stock one** - while the Yoga lock screen is on, the launcher is allowed to
   sit above Android's lock screen (`setShowWhenLocked`, see `applyLockOver`). The lock screen is prepared when the
   screen turns off, so the home screen never flashes. Swipe up calls `KeyguardManager.requestDismissKeyguard`,
   so Android still asks for PIN / pattern / fingerprint; cancelling keeps you on the Yoga lock screen.
   If Android is really locked, the Yoga lock screen is always shown (the "screen off for..." delay is ignored),
   so the launcher is never left uncovered. New switch: Lock Screen > "Show it instead of the Android lock screen".
   Limit: only works when the launcher was on screen when the phone locked.
4. **Settings** - "Themes & Look" is closed when Settings opens.
