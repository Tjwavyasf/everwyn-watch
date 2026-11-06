# Everwyn Watch — Changelog

Built from the git history of `claude/funny-feynman-ifcll1` (which contains all Everwyn Watch commits). Dates are commit dates. Short hashes refer to that history.

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
