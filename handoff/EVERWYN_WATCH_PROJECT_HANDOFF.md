# Everwyn Watch — Project Handoff

_For any AI assistant or developer (ChatGPT, Codex, Claude, a person) continuing this project. Start with `AGENTS.md` at the repo root._

_Prepared 2026-11-06 from the repository and the development conversation. Facts here are taken from the code, git history and that conversation; anything uncertain is marked._

## 1. What it is

Everwyn Watch is a **server-side Fabric mod for Minecraft 1.20.1** (Java 17). It runs a small built-in web server inside the Minecraft server and serves a **phone web app** (installable to the home screen as a PWA) that admins use to watch and manage the "Everwyn" SMP: a live map, players, chat feed, admin tools, and more. Players do **not** need to install anything.

- Repository: `github.com/Tjwavyasf/everwyn-watch` (owner: Tjwavyasf, mod author "Mr.Wavy")
- App root: **repository root** (`build.gradle`, `src/`, …). It is a single Gradle project.
- **Not part of this app:** `warehouse-showdown/` (the "Clocked In" / Warehouse Showdown browser game) exists only on `main` and is a separate project. Do not mix its code with Everwyn Watch. It is **excluded** from this backup.

## 2. Repository state (verified 2026-11-06)

| Item | Value |
|---|---|
| Branch with the newest Everwyn Watch work | `claude/funny-feynman-ifcll1` |
| Last code commit | `3167fd7bb155f494ad503e283f5ed00ff3d683a6` ("Map: Tabletop 3D and Free cam views of the real terrain"). A later commit on the same branch only adds `handoff/` and `AGENTS.md` (docs, no code). |
| Uncommitted changes | none (working tree clean, pushed to origin) |
| `main` | `f001b8142f6ff2245a1f3827862c440d155420c2` |
| Relationship | `main` is 8 commits ahead (all Warehouse Showdown / Clocked In, plus small edits to both workflow files); the 3D commit (1) is **not merged into main**. A test merge merges cleanly. |
| Pull requests | none open or closed |
| Release | `latest` (published 2026-11-06 13:06 UTC) — built from **main**, so it does **not** include the 3D map |
| CI for 3167fd7 | "Build mod jar" run #27 (id 42107740528): **success** |

Other remote branches: `adminname`, `challenges`, `fixes`, `fullmap`, `mobs`, `packinstaller`, `spectate`, `upgrade`, `upgrade2` are fully contained in main (old feature branches). `cftest` has one extra commit adding a temporary `.github/workflows/cftest.yml` ("Temporary: test CurseForge downloads from Actions") that is not in main. `claude/sleepy-einstein-ipmhac` holds handoff docs for **Warehouse Showdown** (not this app).

## 3. Architecture

**Server (Java, `src/main/java/dev/mrwavy/everwynwatch/`)**

| File | Role |
|---|---|
| `EverwynWatch.java` | Mod entrypoint; loads config, wires services, starts web server, tick hooks |
| `Services.java` | Holder for all services |
| `WatchConfig.java` | `config/everwyn-watch.json` (port, token, toggles, ntfy, Discord, accounts…) |
| `WebServer.java` | JDK `HttpServer`; static files + `/api/*`; token auth via `X-Watch-Token`, lockout after 10 wrong tries |
| `Accounts.java` | Roles OWNER / ADMIN / HELPER / VIEWER, helper logins, audit log (`config/everwyn-watch-audit.json`) |
| `AdminActions.java` | All `/api/action` actions (kick, heal, tp, give, ban, time/weather, waypoints, packages, waves, schedules, accounts, …) |
| `Snapshotter.java` | Builds `/api/state` (players, worlds, spawn, waypoints, deaths, challenges, settings) |
| `TileRenderer.java` | Draws 128×128 top-down map tiles in vanilla map colors from loaded chunks **and** region files (never loads/generates chunks); caches PNG + per-pixel heights on disk |
| `Mobs.java` | Mobs in view, spawn-egg colors |
| `Markers.java` | Waypoints + last deaths (`config/everwyn-watch-markers.json`) |
| `Challenges.java` | Mob-wave challenges (`config/everwyn-watch-challenges.json`) |
| `DeathVault.java` | Full inventory backup on every death (`<world>/everwyn-watch/deaths.json`), restore |
| `Catalog.java`, `IconRenderer.java` | Item list; renders real item/block icons from Mojang textures (downloads once, ~25 MB, cached in `config/everwyn-watch-cache/icons`) |
| `Faces.java` | Player skin faces |
| `Inventories.java` | Online/offline inventories, ender chest |
| `EventLog.java` | Chat/join/death/advancement/admin feed |
| `Trails.java` | Movement trails (1/6/24 h) |
| `PerfMonitor.java` | TPS/memory/entity history, lag finder |
| `Leaderboards.java` | Playtime, kills, deaths, mined, distance, scoreboards; public `/board` |
| `Scheduler.java` | Scheduled waves, packages, announcements, restart warnings |
| `LedgerBridge.java` | Reads Ledger mod's SQLite (read-only) and runs its rollback |
| `Notifier.java`, `Discord.java` | ntfy push notifications, Discord webhook |
| `VoicePlugin.java`, `VoiceBridge.java` | Optional Simple Voice Chat listening (16 kHz stream, nothing recorded) |
| `OriginsCompat.java` | Shows Origins mod origin |
| `PackInstaller.java` | Installs a CurseForge modpack export zip on the server (downloads mods, skips client-only, backs up old mods, undo) |

**Web app (`src/main/resources/assets/everwyn-watch/web/`)**: `index.html` is the whole app (vanilla HTML/CSS/JS, no build step, ~2,830 lines): tabs Map / Players / Deaths / Server / Feed, bottom sheets, builders. `board.html` = public leaderboard page. `manifest.webmanifest` + icons for the PWA. Pixel style using the Google Font "Silkscreen".

**3D map (inside `index.html`)**: uses **three.js 0.160.0** loaded on demand via `import()` from `cdn.jsdelivr.net` (only when a 3D mode is opened). Terrain meshes are built client-side from `/api/tile` (colors) + `/api/heights` (gzipped little-endian int16, 128×128, `-32768` = unknown).

**Other**: `leaderboard-site/` = GitHub Pages copy of the leaderboard (`.github/workflows/leaderboard.yml`, every 15 min from `http://66.248.198.125:9063/board.json`). `.github/workflows/build.yml` builds the jar on every push and publishes the `latest` release **only from main**.

**Dependencies** (`build.gradle`, `gradle.properties`): fabric-loom 1.7-SNAPSHOT, Minecraft 1.20.1, Yarn 1.20.1+build.10, Fabric Loader 0.15.11, Fabric API 0.92.2+1.20.1, `de.maxhenkel.voicechat:voicechat-api:2.5.0` (compileOnly). Java 17. Gradle 8.10 in CI. No database server: data is JSON files plus the tile cache; Ledger's SQLite is only read.

**Mod version**: `1.9.0` (gradle.properties).

## 4. Implemented functionality (in code)

Map (live players, Overworld/Nether/End, pinch zoom, whole explored world from save files, diagnostics), waypoints with Xaero's Minimap share (+ "newer Xaero format" toggle), deaths on map, mobs on map with filters and remove, movement trails, **3D Tabletop and Free cam views** (branch only, see §2); Players tab with health/food/XP/origin/scoreboards/inventory/armor, offline player lookup; Feed; admin actions (message, heal, feed, tp, gamemode, kick, ban/unban, kill, smite, give, clear effects/inventory, to spawn, time/weather, broadcast); care packages; mob wave challenges; death vault with partial restore and YIGD grave warning; real item icons and skin faces; push notifications (ntfy) and Discord; helper logins with roles and audit log; lag monitor + lag finder; leaderboards (+ public page, GitHub Pages copy); scheduled events; Ledger grief investigation + rollback; voice listening via Simple Voice Chat; modpack installer (CurseForge export zip) with undo; owner shown as "Everwyn God" (renameable).

Removed on purpose: screen spectating (added in 6689045, dropped in 0988351).

## 5a-00000. Latest: version 1.14.0 (branch `everwyn-inventory`)

Inventory editor (move items between inventories, ender chests, other players, chests, death backups; trinkets and backpack contents) and trinkets in death backups. Discussed, not built: real mob models (vanilla mobs need hand-made models; GeckoLib modded mobs could be read from their files). See changelog.

## 5a-0000. Version 1.13.0 (branch `everwyn-spawn-ledger`)

Spawn mobs / tame pets for a player, and the Ledger "Server error" fix (errors now readable). If Ledger still fails, the app shows the exact reason. See changelog.

## 5a-000. Version 1.12.0 (branch `everwyn-follow-cam`)

Follow camera (Behind/Front/Side/Top/Free, close/far), underground viewing, and the .nbt schematic placer. See changelog.

## 5a-00. Version 1.11.0 (branch `everwyn-crash-reports`)

Crash reports from players' modpacks: new `crash-reporter/` subproject (a second, client-side mod for the modpack) plus Crash reports in the Server tab. The repo now builds two jars. See changelog.

## 5a-0. Version 1.10.0 (branch `everwyn-real-blocks`)

Real blocks + live players in 3D, and the See inside slice. See changelog. Tested with a mock server and the real 1.20.1 textures; not yet on the live server.

## 5a. Version 1.9.3 (branch `everwyn-1.9.2`)

1.9.3 fixes deaths not being saved to the Deaths tab (see changelog). Not yet confirmed on the live server.

### 1.9.2

Branched from `claude/funny-feynman-ifcll1` (keeps the 3D map). Adds clear all hostile mobs, automatic mob protection (`MobGuard.java`), whitelist + unwhitelist, typed item quantities, owner mod JAR upload (`SingleModUpload.java`) and a guarded owner restart (`RestartController.java`, disabled until `hostRestartsOnStop` is true). Full list and fixes in the changelog; ChatGPT's own notes in `EVERWYN_WATCH_UPDATE_2026-11-06.md`. Not tested on the live server yet. Not merged into main.

## 5. Previous completed task (1.9.0)

**Tabletop 3D + Free cam map views** (commit 3167fd7, pushed to `claude/funny-feynman-ifcll1`, CI green). Details:
- Mode switch under the dimension picker: **Map / Tabletop 3D / Free cam**; last mode remembered (`localStorage ew_mapmode`).
- Tabletop: drag pan, pinch zoom, twist to rotate (⟲ ⟳ buttons), two-finger slide or **Tilt** button (top-down → angled → low).
- Free cam: left-thumb joystick, drag to look, ▲▼ fly, speed ×1/×3/×8, WASD/Space/Shift + mouse on desktop, stays above ground.
- Players (face + name + small body, smoothed movement), waypoints, deaths, spawn in 3D; tap markers opens the usual menus; long-press ground opens the spot menu with the real height; follow-player chips work.
- Server: tiles store per-pixel top-block heights (`<x>_<z>.heights`, gzip, next to the tile PNGs); old tiles get heights on their next redraw; `/api/tile` now returns the old PNG even while rate-limited.
- Detail falls off with distance (1×1, 2×2, 4×4 blocks per cell).

## 6. Current task / open question (not started)

The user asked: **"Can I see every block?"** Answer given: no — the 3D view shows only the **top block of each x/z column**, in flat map colors. Three options were proposed and **the user has not chosen yet**:
1. Real block textures on the top surface.
2. Full real blocks (with textures) within a few chunks of the camera, far land as now — likely what the user means.
3. Option 2 plus a cave/height slice slider.

Nothing for these has been implemented. Wait for the user's choice.

## 7. Known bugs and limitations

- The 3D work is **not on main**, so the `latest` release jar does not include it. To ship it: merge `claude/funny-feynman-ifcll1` into main (merges cleanly) — only with the user's permission.
- 3D was tested only in a headless browser against a mock server with fake terrain, **not on the real server or a real phone**. The Java side compiled in CI but has not been run on the live server.
- 3D needs internet on the viewing device (three.js from jsDelivr). If blocked, a toast says so and the flat map still works.
- 3D shows top surfaces only: no caves, overhangs, interiors, trunks under leaves; Nether shows the floor under the roof.
- Mobs are not shown in 3D.
- No automated tests exist in the repository.
- Connection is plain HTTP; the token gives admin power (README safety notes).
- The repo's `README.md` does not yet mention the modpack installer, voice listening or mobs-on-map (they are implemented).
- `cftest` branch holds a temporary workflow that was never cleaned up (not in main).

## 8. Design rules and restrictions

- Keep the existing pixel/blocky Minecraft look (Silkscreen font, `--bg/--panel/--grass…` CSS variables, square buttons with inset shadows), plain-language UI text, phone-first.
- Server-only mod; must never load or generate chunks; keep TPS-safe rate limits (`maxTileRendersPerSecond`).
- No build step for the web app (single `index.html`).
- Stack: Fabric 1.20.1 / Java 17 / vanilla JS. Do not replace.
- The user's standing rules for the next conversation: do not rebuild or redesign, do not remove features, do not rename files unnecessarily, do not change data formats without authorization, only make requested changes, keep docs updated, do not push/force-push/merge without authorization.
