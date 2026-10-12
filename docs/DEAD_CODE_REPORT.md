# Dead-code report (v1.57)

Method: TypeScript checker over all 35 inline scripts (unused locals, unreachable code, unused labels, duplicate declarations), a symbol-aware reference scan for functions/vars (counting strings, inline handlers and the Kotlin side), a CSS selector scan against every class/id in the HTML and JS (dynamic prefixes like `'ck-f-'+x` were checked by hand), unused `@keyframes` and `--vars`, Kotlin `@JavascriptInterface` methods vs. JS calls, and asset/resource references.

Checked afterwards: page loads with 0 errors; 461-control monkey click run gives the same result before and after (0 page errors, same 34 distinct bridge calls); no new "cannot find name" diagnostics.

## Removed - JavaScript (`app/src/web/index.src.html`)
| Item | Kind |
|---|---|
| `FLA`, `FLS` | unused constants (flow drawer) |
| `sbd()` | function never called |
| `ripple()` | function never called |
| `ptLoop()` | parallax loop, only referenced by itself (parallax is permanently disabled) |
| `ptApply()` | only caller was `ptLoop()` |
| `sweep` (in the drawer-header scope) | arrow function never called |
| `touchStart`, `hub`, `rg`, `NS`, `DAYS`, `tg`, `hd`, `dark`, `base`, `evOk`, `cells` | unused variables / helpers |
| `a` in `gdemo()`, `L` in `ldIc`, `H`, `sq`, `v` (clamp of `D.v`), `i` (`.awi` count) | unused locals / write-only |

## Removed - CSS (37 rules, ~3.7 KB)
Selectors whose class or id exists nowhere in the markup or JS: `.btn-mauve`, `.ydr-scrim`, `.ydr-flow*`, `.ydr-glow`, `.ydr-knob*`, `.ydr-num`, `.grid.gm*`, `#mhue`, `.tname`, `.tlabs*`, `.v49lbl*`, `#awHud*`, plus the unused custom property `--gr3`.

## Removed - assets
`app/src/main/assets/img/mandala-royal.webp` and `mandala-medal.webp` (~230 KB): not referenced by the page or Kotlin; the mandala is drawn as vector SVG now.

## Checked and kept
- Kotlin: no unused functions or imports. Every `@JavascriptInterface` method is called from JS (`glob` is an internal helper). The "unused" overrides are Android framework callbacks.
- No dead `@keyframes`, no unreachable code, no duplicate declarations.
- Dynamic class families are live: `ck-*`, `ip-*`, `sh-*`, `gp-*`, `fx-*`, `fl-*`, `cab-*`.

## Left alone on purpose (your call)
- Tilt-parallax is disabled in code (`ptOn=()=>false`, `ptKick=()=>{}`) but its settings controls (`ptn`, `ptDemo`, ...) still exist. Removing the whole feature touches Settings UI.
- Markup ids never referenced: `hsOpen`, `cmdOpt`, `amFolder`, `grpLock`, `v48quick`, `dLayNote`, `mxOn`, `dlPre`, `ckAcc`.
- `docs/V19_SETUP.md` and `docs/native-v19/V19_SETUP.md` are identical copies.
