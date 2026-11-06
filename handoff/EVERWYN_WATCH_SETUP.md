# Everwyn Watch — Setup, Build, Test, Deploy

## Requirements
- Java 17 and Gradle 8.10+ (CI uses Temurin 17 + Gradle 8.10). No Gradle wrapper is committed; use an installed `gradle`.
- Network access to `maven.fabricmc.net`, Maven Central and the Gradle plugin portal (Loom downloads Minecraft and mappings on first build).
- Server: Fabric 1.20.1 with Fabric API. Optional mods it integrates with: Origins, Simple Voice Chat, Ledger, YIGD, Xaero's Minimap (client side, for waypoints).

## Get the code
Preferred (GitHub is the source of truth):
`git clone https://github.com/Tjwavyasf/everwyn-watch && cd everwyn-watch && git checkout claude/funny-feynman-ifcll1`

From the offline backup `EVERWYN_WATCH_FULL_BACKUP.zip` (kept by the owner, not in the repo): source is in `everwyn-watch/`; full history: `git clone git/everwyn-watch-history.bundle everwyn-watch-git -b claude/funny-feynman-ifcll1`; integrity: `cd everwyn-watch && sha256sum -c ../SHA256SUMS-source.txt`.

## Build
```
gradle build --no-daemon
```
Jar: `build/libs/everwyn-watch-<version>.jar` (not the `-sources`/`-dev` jars). Easiest path from a phone: push to GitHub → Actions → "Build mod jar" → download artifact `everwyn-watch-jar`. Pushes to **main** also replace the `latest` release download.

## Install / deploy on the Minecraft host
1. Put the jar in the server's `mods` folder (Fabric API required).
2. Add an extra port in the host panel ("Additional ports"/"Port allocation").
3. Start once, stop, edit `config/everwyn-watch.json`: set `port`; copy `token` (the owner password). See `everwyn-watch.config.example.json` for every field (placeholders only).
4. Start again. Open `http://SERVER-IP:PORT` on a phone, paste the token, Share → Add to Home Screen.

There is **no `.env`**. All configuration is `config/everwyn-watch.json` (generated on first start). Never commit it: it contains the owner token, helper tokens, ntfy topic and Discord webhook.

## Files the mod writes at runtime (not in the repo)
- `config/everwyn-watch.json`, `-markers.json`, `-challenges.json`, `-schedule.json`, `-audit.json`
- `config/everwyn-watch-cache/` (icons, trails, stats history)
- `<world>/everwyn-watch/tiles/<dim>/<x>_<z>.png` and `.heights`, `<world>/everwyn-watch/deaths.json`
- `everwyn-pack/` in the game folder (modpack installer work/backups)

## GitHub Pages leaderboard
`.github/workflows/leaderboard.yml` copies `BOARD_URL` (`http://66.248.198.125:9063/board.json`) every 15 minutes into `leaderboard-site/` and deploys Pages. Turn on "Public leaderboard" in the app (Server tab). On `main` this workflow also publishes the separate Clocked In game at `/clocked-in/`.

## Tests and checks
- No automated test suite exists in the repo.
- Checks that can be run: `gradle build`; JS syntax check of the web app, e.g. extract the `<script>` blocks of `index.html`/`board.html` and run `node --check`; JSON check of `fabric.mod.json` and `manifest.webmanifest`.
- Manual 3D test used in development: a small Python mock of `/api/state`, `/api/tile`, `/api/heights` plus Playwright screenshots (not committed).
