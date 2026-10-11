# Web source layout (Step 10)

The page is one file again. Edit this, not `assets/index.html`:

- `app/src/web/index.src.html` - the whole launcher page (home, dial, Arc Wave drawer, settings, widgets, search, menus, core JS).

The Step 9 split into `home.html` and `drawer.html` was dropped: the v1.51 page already contains both parts, and the old part files no longer matched it. `tools/build-web.js` still works the same way. It copies `index.src.html` to `app/src/main/assets/index.html`, and it still expands `<!--@use file:name-->` markers if you later add part files again (there are none now).

Build: run `node tools/build-web.js` after editing (needs Node). GitHub Actions runs it before Gradle. The generated `assets/index.html` is committed too, so Android Studio builds work even if you forget.
