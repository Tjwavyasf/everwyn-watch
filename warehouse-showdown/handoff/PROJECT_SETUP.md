# PROJECT SETUP

## Run the game
- **Just open it:** double-click `warehouse-showdown/index.html` (or any older version). It needs internet for three.js r128 (`https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js`) and Google Fonts (optional; fallbacks exist).
- **Local server (recommended):** `cd warehouse-showdown && python3 -m http.server 8000`, then open `http://localhost:8000/`.
- **Offline:** the backup includes `tools/vendor/three.min.js` (three@0.128.0, MIT). To run offline, change the `<script src=...three.min.js>` line to `../tools/vendor/three.min.js` **in a scratch copy only**. Don't commit that change.

No build step, no package manager, no dependencies beyond three.js r128.

## Dependency versions (verified 2026-11-06)
| Thing | Version |
|---|---|
| three.js | r128 (npm `three@0.128.0`), from cdnjs |
| Fonts | Google Fonts: Anton, Chakra Petch 500/700, VT323 (Clocked In); Cormorant SC (Long Shift) |
| Test runner | Node.js v22, Playwright (preinstalled in the Claude Code cloud container) with bundled Chromium |

## Test (automated)
The tests in `tools/tests/` are Playwright scripts written during development. They have **hard-coded paths** from the dev container. Edit the two constants at the top (`THREE_JS` path and the `file:///…/index.html` URL) before running.

```bash
mkdir -p npm3 && cd npm3 && npm init -y && npm install three@0.128.0 && cd ..
node tools/tests/c1.js   # full Chapter 1 playthrough with debug hooks (about 3–5 min headless)
node tools/tests/c2.js   # boss: touch forklift ram, defeat, retry reset; landscape + portrait
```
Chromium flags used: `--use-gl=swiftshader --enable-webgl --ignore-gpu-blocklist`. The tests route the three.js CDN URL to the local npm copy and block Google Fonts.
Expected `c1.js` result: steps 1→3→4→5→6→7→8, boss HP 800/600/450/250/0, `end end 10`, `ERRORS []`.

Syntax check without a browser: extract the `<script>` block and run `node --check`.

## Manual test checklist
1. New Game → arrival → walk to the door.
2. Order desk dialogue → read the work order at the printer → go east.
3. Break room dialogue → clock in (glitch) → follow the coworker east.
4. Carry 4 boxes to Section B (try dropping one outside the zone and picking it up again).
5. Outbreak: beat 1 + 2 hostiles.
6. Loading dock → Jose: hit to fill posture → drive the forklift into him (5 times) → ending card.
7. Die once at the boss → Retry → Jose is back to full HP, 0 rams.

## Build / deploy
- **Public site:** push to `main` (with the owner's OK). Workflow `.github/workflows/leaderboard.yml` runs on pushes touching `warehouse-showdown/**`, copies `warehouse-showdown/index.html` to `leaderboard-site/clocked-in/index.html`, and deploys GitHub Pages → https://tjwavyasf.github.io/everwyn-watch/clocked-in/. It also runs every 15 minutes (leaderboard refresh), which republishes the game too.
- **Claude artifacts** (optional previews): the page must be published **without** `<!doctype>`, `<html>`, `<head>`, `<body>` and viewport/charset meta lines (the artifact host adds them). Command used:
  `sed -E '/^(<!doctype html>|<html lang="en">|<head>|<meta charset="utf-8">|<meta name="viewport".*|<\/head>|<body>|<\/body>|<\/html>)$/d' index.html > artifact.html`

## Restore from the backup zip
- `warehouse-showdown/`: exact copy of the game folder (all versions + docs).
- `.github/workflows/`: the two workflow files as of `f001b81`.
- `everwyn-watch.bundle`: the full git repo with history. Restore with `git clone everwyn-watch.bundle everwyn-watch`.
- Check integrity with `sha256sum -c CHECKSUMS.sha256` (run inside the unzipped folder).
