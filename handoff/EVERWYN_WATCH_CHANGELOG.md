# Everwyn Watch — Changelog

Built from the git history of `claude/funny-feynman-ifcll1` (which contains all Everwyn Watch commits). Dates are commit dates. Short hashes refer to that history.

## 2.0.0 — 2026-11-08 (branch `everwyn-app`, based on 1.14.0) — renamed "Everwyn App"
- Rebrand: app title, login, manifest and mod display name are "Everwyn App". Mod id, config/data names (`everwyn-watch`) unchanged on purpose.
- Fantasy theme layered at the end of the page's CSS: forest green, gold trim, vine borders (inline SVG), Cinzel Decorative title (Google Fonts), gold buttons/chips, leaf corner on cards. Features and layout unchanged.
- Inventory editor restyled like a Minecraft inventory with a resource-pack look: dark slate slots, gold-trimmed panels, parchment player card showing the player's own skin from the front (`drawSkinFront`, 64x64 and legacy 64x32 skins, outer layer), trinkets row.
- Real mob models in 3D (`MOB_MODELS`, `rbMobModel`, `rbMcBox`): Minecraft's own cuboid sizes/UVs/pivots for zombie, husk, drowned, skeleton, stray, wither skeleton, creeper, cow, mooshroom, pig, sheep (+ wool layer), chicken; textures from `/api/asset` (minecraft/textures/entity/...). Legs swing, heads turn. Others stay boxes.
- Known: in the headless software renderer used for testing, thin brown "spikes" appeared on flat grass in tabletop view; geometry checks found no bad triangles, so it is probably a software-renderer artifact. Check on a real phone.

## 1.14.0 — 2026-11-08 (branch `everwyn-inventory`, based on 1.13.0)
- `InventoryEditor`: actions `inv_move` (from/to refs: `{kind:"player", name, part:"main"|"ender"|"trinket", slot|key}`, `{kind:"block", dim,x,y,z, slot}`, `{kind:"death", id, slot}` copy-only; count 0 = whole stack; stacks merge, otherwise swap) and `inv_delete`. Online players change live (screens synced); offline players' main/armor/offhand/ender are edited in their playerdata file (backup `_old` written first). Trinket slots via the Trinkets API by reflection (no hard dependency; online only).
- `/api/container?dim&x&y&z`: chest (double chests combined), barrel or any block-entity inventory; searches up to 12 blocks up/down for one (the 2D map doesn't know heights) and returns its real `y`.
- `/api/inventory` and death details now include `trinkets` and, for shulker boxes / bundles / backpacks, the `inside` items (any list of item compounds in the item's NBT, 3 levels deep).
- `DeathVault`: snapshots (and the 1-second backup) save trinkets; restore puts them back into their slots (or gives them if the slot is gone); trinkets appear in the death item list from slot 1000.
- App: Minecraft-style inventory editor (player panel → Open inventory, offline profile → Open inventory, death → Open next to their inventory, map hold → Open the chest/container here), tabs Ender chest / Another player / Their deaths, hold bar (Half, Just 1, What's inside, Delete, Cancel).

## 1.13.0 — 2026-11-08 (branch `everwyn-spawn-ledger`, based on 1.12.0)
- Spawn mobs: `Spawner`, `/api/spawnables` (every summonable living mob, vanilla + modded, with tame/baby flags; bosses left out), action `spawn_mob` (dim, x, y, z, type, count 1–20, owner, name, baby; spot must be loaded). App: map hold menu → Spawn a mob here; player panel → Spawn a mob / pet (next to them, owner pre-filled).
- Tame: spawned tameables (TameableEntity, horses/llamas) can be tamed for a player; action `tame_entity` tames a mob on the map or gives a pet to someone else. `/api/mobs` now marks `tamable` and pets' `owner`.
- Fix: Ledger lookups showed "Server error". `LedgerBridge` now finds a custom database location in config/ledger.toml, opens Ledger's WAL database read-only and falls back to a normal (still read-only use) connection, and reports any failure (including driver/native errors) as a readable message in the app plus a warning in the server log.

## 1.12.0 — 2026-11-07 (branch `everwyn-follow-cam`, based on 1.11.0)
- Follow camera in 3D (both modes): picking a player locks an orbit camera on them (smooth live position). Follow bar: Behind / Front / Side / Top / Free, −/+ distance, ✕ stop. One-finger drag orbits, pinch zooms, twist turns. Moving in Free cam (joystick/WASD) or panning in Tabletop stops following; Free cam continues from the follow camera's spot.
- Underground: Free cam no longer stops at the ground (only at y −80). When the camera, or the followed player, is more than 3 blocks under the surface, real-block chunks are drawn as a band around that height (−36…+20, steps of 8) instead of the surface, the simple far terrain is hidden and the sky goes dark. Rock between the camera and the player is see-through (back faces).
- Schematic placer: `SchematicPlacer`, `/api/schematic` (POST upload of a .nbt structure, max 8 MB and 200,000 blocks of space; GET status), actions `schematic_place` (dim, x, y, z, rot 0/90/180/270; refuses if any chunk in the area isn't loaded) and `schematic_undo` (last 3 placements, restores blocks and block-entity data). App: map hold menu → Place schematic here / Undo last schematic. Admins only.

## 1.11.0 — 2026-11-07 (branch `everwyn-crash-reports`, based on 1.10.0)
Crash reports from players' modpacks.
- New subproject `crash-reporter/` (Everwyn Crash Reporter 1.0.0): a client-only Fabric mod with no Minecraft code (needs only Fabric Loader, `preLaunch` entrypoint). Sends new `crash-reports/crash-*.txt` from a shutdown hook when the game crashes; on the next launch sends anything unsent: crash reports, `hs_err_pid*.log` (Java crashes) and archived `logs/*.log.gz` that show a failed start (incompatible mods, mixin errors…). Settings in `config/everwyn-crash-reporter.properties` (enabled, url, key, pack); sent files remembered in `config/everwyn-crash-reporter-sent.txt`; first run only sends the last 3 days; max 3 MB per report (end of file kept).
- Server: `POST /crash` (needs `X-Crash-Key` = new config `crashKey`, generated automatically; not the app password; 20 per hour per address; 4 MB max), `CrashReports` stores them in `config/everwyn-watch-crashes/` (last 300) with a summary: description, root cause, Fabric's incompatible-mods lines, the non-Minecraft packages in the stack trace, and known graphics-driver files for Java crashes. The server's own `crash-reports/` (last 14 days) are imported at start. Feed event, ntfy (`notifyCrash`) and Discord alert.
- `/crash-reporter.jar` (the reporter is packed inside the server jar by the build), `/api/crashes`, `/api/crash?id=`, actions `crash_delete`, `crash_new_key`, `crash_notify`.
- App: Server tab › Crash reports, with a setup card (download, config file to copy, new key, alert toggle) and the full report view (copy, save, delete).
- Build: `settings.gradle` includes `crash-reporter`; root `processResources` packs its jar; the workflow also uploads `everwyn-crash-reporter.jar`.

## 1.10.0 — 2026-11-07 (branch `everwyn-real-blocks`, based on 1.9.3)
Real blocks in the 3D view ("look at the world like a god"):
- Server: `ChunkExport` + `/api/chunk` (every block of a chunk, palette + base64 indexes, gzipped, plus surface biome per 4x4 column with temperature/downfall/colors). Loaded chunks: sections copied on the server thread, encoded on a web thread; others read from region files. Never loads chunks; 40 chunk reads/s max, short cache.
- Server: `/api/asset?p=<ns>/(blockstates|models|textures)/...` serves vanilla + mod assets from the same sources as the item icons (`IconRenderer.asset`).
- Server: `Live` + `/api/live` (players with pose, sneaking, swimming, sleeping, elytra, arm swing, slim arms; mobs near the view with size and spawn-egg colors). `/skin?u=` serves the whole skin (`Faces.skin`), and slim-arm skins are remembered.
- App: block models resolved in the browser (blockstates variants/multipart, parents, element rotation, face UV/rotation, cullface, tint), texture atlas, Minecraft face shading + ambient occlusion, water/lava, biome tints, glass/leaves culling. Chunks within 5 of the view in full detail; height map elsewhere, with a shader mask hiding it under real chunks.
- App: player models with skins and outer layer (walk, sneak, swim, elytra, sleep, arm swing), mobs as boxes, smoothed between 5 updates/s. Players above the slice are hidden (names stay).
- App: **See inside** slider (and ▲/▼) slices the world at a height to look into builds and caves; the Nether is sliced under the roof automatically. **Real/Simple** button.
- Known limits: blocks Minecraft draws with code (signs, banners, skulls, chests, beds) are missing or plain stand-in shapes; no block lighting (caves are fully lit); mobs are boxes, not their real models; animated textures show their first frame.

## 1.9.3 — 2026-11-06 (branch `everwyn-1.9.2`)
Fix: deaths sometimes missing from the Deaths tab, so items couldn't be given back.
- Cause: a death was only saved if the inventory copy taken in Fabric's `ALLOW_DEATH` event existed; if another mod's handler ran first (emptying or cancelling), or the death skipped that event, the death was silently dropped. The death handler also updated the map marker and challenges before saving, so an error there could stop the save.
- The inventory copy now runs in an early event phase (before other mods), and a copy of every online player's inventory is kept in memory, refreshed every second. If the copy from the moment of death is missing or empty, the one from up to a second before is used. A death is now always recorded (marked when items couldn't be copied), the vault is saved first, and each step has its own error handling. Copies older than 10 s (left from a totem save) are ignored.
- Every death is written to the server log ("Everwyn Watch saved X's death: N item stacks …").
- Deaths tab shows a note when items came from the backup copy or couldn't be copied. Death records gain a `source` field (`death` / `backup` / `none`); old records still load.

## 1.9.2 — 2026-11-06 (branch `everwyn-1.9.2`, not yet on main)
Prepared in ChatGPT (imported unchanged as commit "Import the 1.9.2 source…"), then reviewed, fixed and built in Claude.
- **Clear all hostile mobs** (Server tab, admins): removes unnamed, non-persistent, non-challenge hostile mobs from loaded chunks in every dimension. Never loads chunks.
- **Automatic mob protection** (`MobGuard`, Server tab, admins): every 200 ticks trims excess hostiles in loaded chunks, farthest from players first. Defaults: 16 per chunk, 200 per dimension, never within 48 blocks of a player, at most 25 removed per scan. Named, persistent, challenge, riding/ridden mobs are kept. After 60 s below the emergency TPS (default 14) the limits drop to 75% until TPS recovers (one ntfy/Discord alert per 30 min).
- **Whitelist** list with **Unwhitelist** button (admins, with confirmation; does not kick or ban).
- **Typed item quantities** with − / + buttons in the care package / give items builder.
- **Upload mod JAR** (owner only): one Fabric server mod, max 50 MB, checks `fabric.mod.json`, refuses client-only mods and Everwyn Watch itself, backs up older JARs with the same mod id to `everwyn-mod-backups/`, saves it as `mods/ew-upload-<modid>.jar`. Takes effect after a restart; compatibility is not checked.
- **Restart server** (owner only): 10-second in-game countdown, then a clean stop. **Off by default**: it only works after `"hostRestartsOnStop": true` is set in `config/everwyn-watch.json`, which should only be done once BisectHosting is confirmed to start the server again after a normal stop.
- New config fields (added with defaults; old configs keep working): `mobGuardEnabled`, `mobGuardPerChunk`, `mobGuardPerDimension`, `mobGuardPlayerDistance`, `mobGuardMaxRemovedPerScan`, `mobGuardEmergencyTps`, `hostRestartsOnStop`.
- New endpoints: `/api/mob-guard`, `/api/whitelist`, `/api/mod-upload` (POST), `/api/restart` (GET status / POST start). New actions: `mob_guard_settings`, `clear_all_hostiles`, `unwhitelist`.
- Fixes made during review: `clearAllHostiles` and `MobGuard` called entity methods on the `Monster` interface (would not compile) — now use `MobEntity` filtered to hostiles, like the existing `clearHostiles`; the shared number popup had been limited to 0–9999 which broke negative/large teleport and waypoint coordinates — the − / + stepper now only applies to quantity fields; mob protection box could get stuck "loading" for non-admins; Unwhitelist button text was cut off; unwhitelist used `Whitelist.contains()`, which is protected in 1.20.1 (compile error) — now uses `PlayerManager.isWhitelisted()`.

## 1.9.0 — 2026-11-05 (branch `claude/funny-feynman-ifcll1`, not yet on main)
- `3167fd7` **Map: Tabletop 3D and Free cam views of the real terrain.** Map tiles now also store top-block heights (saved as `<x>_<z>.heights` next to each tile PNG); new `/api/heights`; `/api/tile` serves the cached image even while rate-limited; web app gains Map / Tabletop 3D / Free cam modes (three.js 0.160.0 loaded on demand). README updated. Version 1.8.0 → 1.9.0.

## 2026-11-05
- `e587b39` Modpack installer card in the app (Server tab).
- `ea5a904` Modpack installer on the server: install a CurseForge export zip (downloads mods, skips client-only, backs up old mods, undo).

## 2026-11-04
- `7f8a6be` Mobs on the map in spawn egg colors, with filters and remove.
- `37f2f8a` Publish the leaderboard to GitHub Pages every 15 minutes for embedding.
- `0988351` Fix black map (restore tile endpoint), add map diagnostics, voice listening via Simple Voice Chat; **drop screen spectating** (decision).
- `6689045` Spectate: client screen sender, server relay, Simple Voice Chat listening (later removed except voice).
- `a907a05` Show the owner as "Everwyn God" in game and in the app (renameable).
- `e5674e4` Fix Xaero [Add] (dash dimension format); pick which death items to give back.
- `b673ce0` Draw the whole explored world from the save files, not just loaded chunks.
- `9b8adbc` App: Server tab, leaderboard + public page, Ledger panel, trails, offline profiles, schedules, helper logins, Discord settings.
- `df52d6f` Wire new features into startup and ticks.
- `8393182` Server: helper logins, audit log, Ledger, trails, lag monitor, leaderboards, schedules, offline inventories, Discord.
- `dab098a` App: faces, real icons everywhere, new player actions, bans, world controls, Deaths tab, notification settings.
- `bc47410` Server: item icons, player faces, more admin tools, death vault, push notifications.
- `c1f0738` Care package and mob wave builder in the app.
- `883ea86` Fix spawn call for 1.20.1.
- `74ddd03` Care packages and mob wave challenges (server).
- `d860a38` Save map tiles to disk; open the map at world spawn.
- `e4bc914` Publish the built jar as a release download.
- `ef515c9` Initial: server dashboard mod with phone web app.

## Decisions on record
- Screen spectating dropped (0988351); voice listening kept.
- 3D terrain built from the existing map tiles + heights instead of full chunk data (smallest change, phone-friendly). Full-block 3D was proposed as a follow-up; **user has not decided** (see NEXT_STEPS).
- three.js is loaded from a CDN only when 3D is opened (the app already used Google Fonts from the internet).

## Not Everwyn Watch (for reference only)
`main` also contains 8 commits (2026-11-05/06) for the separate Warehouse Showdown / "Clocked In" game in `warehouse-showdown/`, and small workflow edits that publish it on the Pages site at `/clocked-in/` and make the jar build ignore `warehouse-showdown/**`.
