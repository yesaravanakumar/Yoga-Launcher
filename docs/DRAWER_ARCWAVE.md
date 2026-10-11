# Arc Wave app drawer (v60)

The app drawer now looks like the reference screenshot:
- Apps flow down a curve on the right; drag or fling up and down.
- The selected app sits on a dial on the left with its name, a curved action wheel
  (options/rename, pin, add to home, Play Store, app info, uninstall) and a "HIDE APP" pill.
- A-Z rail on the right with an orange frame. Only letters that have apps are shown. Press or slide on it to jump;
  the first dot opens search.
- The cyan arrow on the left edge closes the drawer. Back and Home also close it.
- Animations: apps fly in on open, the selected icon and name pop on every change, the orange arc glows. All stop with reduced motion.

Code: `<script id="v60-arcwave">` at the end of index.html. It takes over YD.open, YD.stop, YD.build and YD.relayout.
The old Crescent drawer code is still in the file, hidden and unused. Remove it in the dead-code stage.
Category chips, drawer style picker and multi-select are not shown in this drawer.

## Badges (v1.55)
Notification dots / counts and NEW pills are drawn on the wave icons and the selected icon. See `V1.55_NOTES.md`.
