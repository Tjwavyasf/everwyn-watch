# Dead Wave: handoff

A top-down zombie survival shooter for phones and desktop. You play in a procedurally generated town, scavenge guns and ammo, board up houses, and survive escalating waves, solo or in online co-op for up to 4 players.

- **Live (co-op works here):** https://tjwavyasf.github.io/everwyn-watch/dead-wave/
- **Claude preview (solo only, built-in art only):** https://claude.ai/artifact/29fTEky1asaW2nnWx522WK
- **Player guide:** [README.md](README.md) · **Sprite guide:** [sprites/README.md](sprites/README.md) · **Art credits:** [sprites/lpc/CREDITS.md](sprites/lpc/CREDITS.md)

## Where things stand

Everything below is built, tested in a headless browser, and live on `main`:

- Town map, twin-stick shooting, magazines and limited ammo, gun/ammo/plank/med/grenade drops, house loot that restocks each wave.
- Boardable doors and windows (up to 3 boards). Zombies pathfind to players with a flow field, break boards and climb in.
- Shove, dodge roll, sprint with stamina; 5 zombie types plus a boss every 5th wave.
- XP perks between level-ups, coins and a permanent Armory (saved in `localStorage` under `deadwave.v1`).
- Online co-op for up to 4 players using the same PeerJS setup as Clocked In.
- Character art from the Liberated Pixel Cup (LPC) set: 8 selectable survivors, 6 walker variants, runner, spitter, bloater, brute, boss, with 4-direction animations.

**Not yet verified:** a real co-op session between two devices over the internet. The sandbox it was built in blocks the PeerJS broker, so co-op was only tested with two browser windows linked by a simulated connection (`tests/coop_bridge.js`). Do a two-phone test first.

**Balance is a first pass:** ammo drop rates, board strength, zombie counts and speeds are untuned by real play.

## Repository layout

```
dead-wave/
  index.html            The whole game (HTML, CSS and JS in one file, about 1,800 lines)
  README.md             Player-facing guide
  HANDOFF.md            This file
  sprites/
    manifest.json       Which PNG sheets the game uses (currently the LPC set)
    README.md           Sheet layout and manifest reference for artists
    lpc/                Built LPC character sheets, CREDITS.md, build_lpc.py
    sample/             Sample pack made from the old built-in art (?sprites=sample)
  tests/                Playwright scripts used during development
.github/workflows/
  publish-clocked-in.yml  On push to main: deploys Clocked In at / and copies dead-wave/ to /dead-wave/
  leaderboard.yml         Every 15 min and on push: deploys the leaderboard site, also copies dead-wave/
  build.yml               Builds the Minecraft mod jar (ignores dead-wave/ changes)
```

Both Pages workflows deploy the same site and the last one to run wins, so **any change to how Dead Wave is published must be made in both** `publish-clocked-in.yml` and `leaderboard.yml`. They copy the whole `dead-wave/` folder (`cp -r dead-wave/. …`).

## How index.html is organized

Sections are marked with `/* ---------------- name ---------------- */` comments, roughly in this order:

| Section | What it holds |
|---|---|
| save | `meta` object (coins, best wave, Armory upgrades, unlocks, look, sound) in `localStorage` |
| pixel painter | `paint()` / `post()`: built-in procedural sprite drawing (auto outline and shading) |
| characters | Built-in fallback art: `person()` survivors and zombies, brute, bloater, boss, customization options (`LOOK_FIELDS`) |
| guns, icons, props | Built-in pixel art for guns, UI icons, cars, furniture, trees, fences |
| light/fx stamps | Radial gradient stamps for lighting, glow, smoke, fireballs |
| town map | `genTown(seed)`: tile grid, houses, openings (`OPENS`), props, loot points, minimap. Deterministic per seed (needed for co-op) |
| ground tiles | 16×16 tile textures, rendered lazily into 512px chunks |
| audio | Synth sound effects (`sfx(name)`) |
| content | `GUNDEF`, `PERK`, Armory `UPS`, zombie stats `ZT`, `waveCfg()` |
| tiles: collision, LOS, flow | `solidP` / `solidZ` / `passZ` / `blocksB`, `collideT`, `los`, multi-source BFS `buildFlow()` |
| zombies, player, guns, moves | Spawning, damage, drops, `fire()`, `shove()`, `roll()`, boarding (`doUse`) |
| update | `updLocal` (input, movement, shooting), `updWorld` (host/solo simulation), `updGuest` (guest interpolation), `updShared` |
| render | Pixel world on `#cv` (low-res, nearest-neighbor), then `renderFX` on `#fx` (full-res lighting, bloom, numbers) |
| online co-op | PeerJS connect, host snapshot `snap()` at 15 Hz, `hostRecv` / `guestRecv`, lobby UI |
| PNG sprite sheets | `loadSheets()`, `drawSheet()`, animation state, corpses. Falls back to built-in art per character |
| character customization | Customize screen: sheet survivors when the manifest provides them, otherwise the built-in options |
| flow | Main loop, HUD, run start/end, title/Armory screens |

`window.__dw` exposes internals for the test scripts. It is harmless, but remove it if you want a cleaner build.

## Key systems

**Units.** One tile is 16 px. The pixel canvas is about 310 px on the short side when the LPC sheets load (`VIEWK`, set from `manifest.json` → `view`), otherwise 250.

**Co-op.** Host-authoritative. The host runs zombies, waves, loot and boards, and sends snapshots (zombies, players, changed openings, grenades, acid, hit numbers). Guests run their own movement and shooting locally and send poses, shots, pickups, boarding, shoves and grenades. XP and coins are shared; perks are per player, and level-up cards wait until between waves in co-op. Players go down and respawn at half HP when the wave is cleared, and the run ends when everyone is down. The PeerJS room ID is `dw1-` plus a 5-letter code. ICE/TURN servers are copied from Clocked In (`peerOpts()`). Co-op only works from an http(s) address; previews that block WebRTC can't do it.

**Sprites.** `sprites/manifest.json` lists characters (`survivor`, `walker`, `runner`, `spitter`, `bloater`, `brute`, `boss`) with frame size, anchor, direction order, animations and either `sheet`, `variants` or `layers`. See `sprites/README.md`. Sheets are fetched over HTTP, so opening `index.html` from disk, or in the Claude preview, uses the built-in art instead.

**Rebuilding the LPC sheets.** `sprites/lpc/build_lpc.py` assembles them from the LPC generator repo. To change outfits or add survivors, edit the `SURVIVORS` / `WALKERS` / `OTHERS` recipes and run:

```
git clone --depth 1 --filter=blob:none --sparse https://github.com/LiberatedPixelCup/Universal-LPC-Spritesheet-Character-Generator.git repo
git -C repo sparse-checkout set sheet_definitions palette_definitions
python3 build_lpc.py fetch   # pulls just the art the recipes use
python3 build_lpc.py         # writes sprites/lpc/*.png and sprites/manifest.json
```

It expects the clone in a `repo` folder next to the script, and it writes to an absolute path (`OUT`) that you'll need to adjust. It needs Pillow. Regenerate `sprites/lpc/CREDITS.md` if you add parts from new definitions; each definition JSON has a `credits` list. Keep the title-screen credit line: the LPC licenses (CC-BY-SA 3.0 / GPL 3.0 / OGA-BY 3.0) require attribution.

## Testing

The scripts in `tests/` use Playwright with the preinstalled Chromium. Pass the path to `index.html`:

| Script | Checks |
|---|---|
| `solo_board_fight.js` | Start, boarding a door, a short fight, fps |
| `siege.js` | A fully boarded house under a wave-6 siege: boards break and zombies get in |
| `coop_bridge.js` | Two pages linked by a fake connection: same map, shared kills and XP, guest down and respawn |
| `customize.js` | Customize screen and the chosen look in game |
| `sprites_http.js` | Sheet loading. Serve the folder first: `python3 -m http.server 8765` in `dead-wave/` |

They write screenshots to the current folder and print `ERR` followed by any page errors (empty means clean).

## Publishing

- Work happened on branch `claude/lucid-dirac-5sn34a`, which has been fast-forwarded into `main` (most recently at `8ad12b1`). Pushing to `main` triggers both Pages workflows. They take about a minute.
- The Claude preview is a separate copy: the game with the `<!doctype>`, `<html>`, `<head>` and `<body>` wrapper lines stripped, republished with the Artifact tool to the same URL.

## Known limitations and ideas

- Real two-device co-op is untested (see above). Mid-game join is supported but also only bridge-tested.
- The procedural built-in art is still in the file as a fallback and for the single-file/preview builds. It can be removed if every build will ship with sheets.
- LPC survivors wear everyday RPG clothes. The LPC set has many more parts (jackets, vests, bandages, hats, hair) for more survival-themed outfits or layered customization. The sheet system already supports `layers`.
- The gun is drawn over the LPC "shoot" pose at a fixed hand offset (`gun` in the manifest), so it lines up best facing left or right.
- Possible next steps: real-play balance tuning, more weapons, a day/night timer, sound polish, saving a run in progress.
