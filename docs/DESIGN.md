# Yoga Launcher: DESIGN.md (master file for the small details)

**How to use this file**
- It describes how the app looks and behaves, in plain words. You never edit code.
- Change a value in the **Your choice** column (or write a new line), then tell me "apply DESIGN.md". I update the app to match and tell you what changed.
- **Now** = what the app does today (read from v1.51). Leave **Your choice** empty to keep it.
- Anything in the "Open decisions" section is waiting for you.

---

## 1. Colours

| Item | Now | Your choice |
|---|---|---|
| Main accent (clock, buttons, highlights) | Orange `#ff5a00` | |
| Main text | Warm white `#f3f1ee` | |
| Second accent | Grey `#9a9ea6` | |
| Dim text (dates, hints, labels) | Grey-blue `#7c8296` | |
| Page background | Near-black `#050506` | |
| Dock background | Dark glass `rgba(16,18,27,.55)` | |
| Search bar background | Dark glass `rgba(22,23,26,.7)` | |
| Settings sheet background | Dark `rgba(14,15,22,.95)` | |
| Toast (small message) background | Dark `rgba(22,24,32,.9)` | |
| High-contrast mode | Dim text becomes lighter `#c3c9db`, borders brighter | |
| Default theme | Matte orange (the first-run look) | |
| Themes in the picker | 60 themes (Aurora, Sunset, Matrix, Gold, Ember, Forest, Saffron, Lava, Copper ...). Each is three colours: text, accent 1, accent 2 | |

## 2. Text

| Item | Now | Your choice |
|---|---|---|
| Font | Phone's own system font (Roboto on most Androids) | |
| Hour number | 64 px, extra bold, tight letter spacing | |
| Date line | 14 px, regular, wide letter spacing (2 px), dim | |
| Search bar text | 15 px, medium | |
| Toast text | 12 px | |
| Small labels and hints | 10.5 to 11 px, dim | |
| Settings small text | 10 px | |
| Font floor (smallest allowed text) | Not enforced yet; some text is 9 to 10 px | e.g. never below 11 px |
| Phone's font-size setting | Ignored on purpose, so the clock never stretches | |
| Numbers | Fixed-width digits so the clock does not jiggle | |

## 3. Sizes, spacing and shapes

The page is designed on a 320 x 640 "phone" and scaled to your screen, so sizes below are design pixels.

| Item | Now | Your choice |
|---|---|---|
| Dock | Rounded bar, corner radius 26, 4 icon slots plus the drawer button | |
| Dock icon | About 57 px wide and tall | |
| Search bar | Corner radius 23, one line | |
| Pill (seconds / battery chip) | 46 px tall, fully rounded, glass look | |
| Toast | Corner radius 14 | |
| App icon size | 100% (adjustable in Settings) | |
| App icon shape | Square-rounded ("sq"); other shapes in Settings | |
| Home grid | 4 columns x 5 rows | |
| Drawer side | Left (dial and action wheel), A-Z rail on the right | |
| Settings gear button | 64 px round | |
| Minimum tap size | Not enforced yet | e.g. 44 px everywhere |

## 4. Motion

| Item | Now | Your choice |
|---|---|---|
| Overall feel | Smooth, springy ("spring" curve), short | |
| Hour number press | 0.4 s spring | |
| Pill pop-in at start | 0.8 s spring, after 0.5 s | |
| Clock hands | "Tick" (moves each second); "Smooth" is an option | |
| Drawer open | Cascade of apps along the arc | |
| Lock screen unlock | Slides up and fades, 0.28 s | |
| Reduce motion | Follows the phone's "Remove animations" switch | |
| Battery saver | Follows Android Battery Saver; calms decorative loops | |
| Auto Performance mode | Switches on at 20% battery, back at 30% or when charging | |

## 5. Touch and feedback

| Item | Now | Your choice |
|---|---|---|
| Long-press time | 420 ms (allowed 250 to 900) | |
| Swipe distance for a gesture | 60 px | |
| Haptics | On. Light tick, normal click, heavy click at end stops. Stays quiet if the phone's touch feedback is off | |
| Hold the hour | Opens Settings | |
| Tap the hour | Opens the app drawer | |
| Swipe down | Search | |
| Swipe up / left / right | Apps / Widgets / Apps (gestures are changeable) | |
| Hold an app, drag | To dock or home screen | |
| Pinch zoom | Off (blocked) | |
| Back button | Closes whatever is open; never quits the launcher | |
| Home button | Closes all panels and returns to the clock | |

## 6. Screens

**Home**: date and weather top-left, logo top-right, greeting card, next event and next alarm, big hour on the left with the dial, search bar, dock at the bottom.

**App drawer (Arc Wave)**: apps follow a curved wave; the selected app sits on a left dial with an action wheel (info, edit, favourite, hide, uninstall and more); orange A-Z rail on the right; HIDE APP pill. First open shows a one-time hint about dragging.

**Settings**: search box at the top; Simple and All settings views; groups include System (Backup and Restore) and Lock Screen; "Reset all" at the bottom needs two taps.

**Lock screen (Yoga lock)**
| Item | Now | Your choice |
|---|---|---|
| When it shows | After the screen was off; delay 0 s | |
| Dims after | 12 s | |
| Rotating cards | Every 5 s: weather, next event, next alarm, battery, now playing, notifications, next task | |
| Notification privacy | Shows only a count ("3 unread"), never the text | |
| Unlock | Swipe up; Android's PIN / fingerprint appears if the phone is really locked | |
| Back and Home | Do nothing while the lock screen is up | |
| Above Android's lock screen | On | |

## 7. Wording (tone and fixed texts)

- Tone: short, calm, plain. No jargon, no exclamation marks.
- Examples used today: "Type a task first", "Time copied", "Invalid backup file", "Tap again to erase everything", "Hold an app, then drag it to the dock or home screen".
- Errors say what happened and what to do next.
- Your wording changes:

## 8. Rules (always true)

1. The lock screen never shows notification text.
2. Back never quits the launcher.
3. Nothing important is only a colour; every state also has text or an icon.
4. No fake data on a real phone (demo apps and simulator buttons stay hidden).
5. Anything destructive asks twice (uninstall, Reset all, restore).
6. Add your own rules here:

## 9. Open decisions (waiting for you)

- [ ] Font floor: pick the smallest allowed text size
- [ ] Minimum tap size: pick 44 px or another number
- [ ] Pinch zoom: allow, or keep blocked
- [ ] Contact tap: ask for confirmation before calling or messaging
- [ ] Weather: add an on/off switch and a privacy line in About
- [ ] Reduced motion: how calm should it be (stop everything, or only loops)
- [ ] Lock screen hardening: block drawer, dock, widgets and notification previews while it is up
- [ ] Landscape and tablet layout (its own project, last)
- [ ] Business home screen: remove for good, or bring back later

## 10. Change log

Write one line each time you change something: date, what, why.
- (start) Starter version made from v1.51.
