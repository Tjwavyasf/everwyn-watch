# Dead Wave: handoff

A top-down zombie survival shooter for phones and desktop. You play in a procedurally generated town, scavenge guns and ammo, board up houses, and survive escalating waves, solo or in online co-op for up to 4 players.

- **Live (co-op works here):** https://tjwavyasf.github.io/everwyn-watch/dead-wave/
- **Claude preview (solo only, built-in art only):** https://claude.ai/artifact/29fTEky1asaW2nnWx522WK
- **Player guide:** [README.md](README.md) · **Sprite guide:** [sprites/README.md](sprites/README.md) · **Art credits:** [sprites/lpc/CREDITS.md](sprites/lpc/CREDITS.md)

## Where things stand

Everything below is built, tested in a headless browser, and live on `main`:

- Town map, twin-stick shooting, magazines and limited ammo, gun/ammo/plank/med/grenade drops, house loot that restocks each wave.
- Boardable doors and windows (up to 3 boards). Zombies pathfind to players with a flow field, break boards and climb in.
- Controls: move stick plus a FIRE button with auto-aim (drag FIRE to aim by hand), RELOAD/ROLL/SHOVE around it, a gamepad, and a Controls screen (auto-aim level, left-handed, fixed stick, push-to-sprint, button size, saved in `meta.ctl`). Works in portrait and landscape. The gamepad code is untested on real hardware.
- Shove, dodge roll, sprint with stamina; 5 zombie types plus a boss every 5th wave.
- Game modes (the "game modes" section of `index.html`): Survival (waves), Objectives (3 scenarios in `SCEN`, each a chain of collect / switch / hold steps ending in an extraction hold) and Horde (endless ramp, supply drops). The host runs the director (`objUpdate` / `hordeUpdate`) and sends its state to guests as `ob` messages and in `init`. The chosen mode is saved in `meta.mode`.
- Weapons: 11 guns in `GUNDEF` (`randGun` weights their drops); the launcher's rounds explode where they stop (host side). Attachments in `ATT` (5 slots, `gunStats` folds them into a gun's numbers, saved on each gun slot as `att` and carried by gun drops). First aid kits (`P.aid`, `useAid`) heal or revive a downed mate (`revive` message to the host). Firing without a suppressor adds `G.noise`, which raises the zombie cap in Objectives and Horde.
- Infection mode (the "infection mode" section of `index.html`): up to 10 players (rooms take 9 guests when the host's mode is Infection; `MATE_NAMES` has 10 colors). The host runs the round (`infUpdate`: intro scene, 30 s run, 5 min hunt, carrier at 2:30, touch to infect, `infShove` stuns, meetings with votes) and sends it in `inf` messages. Roles are s/i/x. Infected players are drawn with the zombie skin tone (`infLook`). Solo is a practice round against AI infected (zombies flagged `inf`).
- Enemy factions (the "enemy factions" section): all three use LPC sheets. Robots and monsters are built by `sprites/lpc/build_factions.py` into `sprites/lpc/fac/` and listed under `factions` in the manifest (loaded into `SHEETS.fac`). The code-drawn versions (`genChar` / `robo` / `bugDraw`) are only a fallback when the sheets can't load, such as when the file is opened from disk. `FACX` holds names, boss names, blood/acid colors and stat tweaks. Chosen with `meta.fac`, sent to guests in `init`.
- Maps (the "maps" section, before `const CHT`): `THEMES` restyle the same generator (ground textures, house palettes, tree lists and counts, lamps and neon colors, clutter, fires, night tint, rain, fireflies). The forest also swaps 55% of the house lots for woods and campfires. `setMap(id)` runs before `genTown`, and guests get the map in `init`, so layouts match.
- Sci-fi guns: `laser`, `arc` (`arcChain`, sent as a `zap` fx), `rail`, `plasma` (explosive). `randGun` triples their weight against robots and monsters.
- Lobby (the "multiplayer lobby" section): `G.gm='lobby'` via `NEXT_GM`. Gun racks refill (`lobbyRack`), a few weak enemies spawn, there is no loot or XP, and you respawn in 3 s without losing gear. The host's in-game `#lobbyBar` has Invite, Match settings (`#matchScr`) and START MATCH. `#lobbyAgain` on the end screen returns everyone to the lobby.
- Card screen (the "lobby / mode select (card screen)" section, `#modeScr`): a stone-hall backdrop (`hallArt`), avatar and name banner, and mode/enemy/map cards with art drawn in code (`CARDART`, `enemyArt`, `MAPART`). Contexts: `solo` (PLAY), `host` (picks are broadcast as `cfg`; START MATCH / yard / invite) and `guest` (read-only, follows `cfg`). PLAY on the title, a ready host room, a waiting guest and the yard's "Pick the match" button all open it.
- Art slots (the "drop-in art" section): `art/lobby_bg.png` and `art/cards/*.png` replace the code-drawn card art and backdrop (`loadArt`, `artOr`). The shipped files are staged renders from the game made by `tools/render_art.js`, which uses capture-only flags `G.cap` (no reticle, labels or numbers, lighter night) and `G.hideP`. See `art/README.md`.
- Story intros (the "story intros (cutscenes)" section): `startCutscene` runs after `resetRun` (and in `guestStart` when `init.fresh`). The world is frozen while `CUT.on` (the loop calls `cutTick` instead of `update`). Shots pan `cam`, temporary actors are zombies flagged `cut` (removed at the end) plus survivor stand-ins (`CUT.people`). Lore is in `LORE`. The host's end or skip sends `cut` so guests close theirs. Automated browsers skip intros unless the URL has `?story=1`. The setting is `meta.ctl.story`.
- Home screen (the "home screen" section, `#title.home`): `homeEnter` builds a camp in an open clearing of a fixed forest town (seed 9137) with a campfire and a truck with red lights. `homeRefresh` lines the squad up (`squadList`: host REMOTES, or the host's `cfg.pl` with looks for guests) and draws them in `render` via `homeDraw`, with name tags and lights in `renderFX`. The zoom is set per orientation. Weekly challenges (`CH_POOL`, `chWeek`, `chAdd`, `chAfterRun`) are stored in `meta.ch`. DEPLOY = `play()`. `#playBtn` and `#hSetChip` open the card picker.
- XP perks between level-ups, coins and a permanent Armory (saved in `localStorage` under `deadwave.v1`).
- Online co-op for up to 4 players using the same PeerJS setup as Clocked In.
- Character art from the Liberated Pixel Cup (LPC) set: 6 walker variants, runner, spitter, bloater, brute, boss, with 4-direction animations.
- Layered survivor wardrobe: about 280 LPC items across 15 slots (hair, beard, shirt, jacket, pants, shoes, hats, face, neck, arms, belt, back, cape, wounds), recolored in the browser, plus 12 presets. Built by `sprites/lpc/build_parts.py` into `sprites/lpc/parts/` (381 PNGs, about 6 MB, loaded only when a look uses them). Code is the "wardrobe" section of `index.html`.

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

**Co-op.** Host-authoritative. The host runs zombies, waves, loot and boards, and sends snapshots (zombies, players, changed openings, grenades, acid, hit numbers). Guests run their own movement and shooting locally and send poses, shots, pickups, boarding, shoves and grenades. XP and coins are shared; perks are per player, and level-up cards wait until between waves in co-op. Going down drops everything but the pistol (other gun with its ammo, planks, grenades) where the player fell (`takeGear` / `spillGear`; a guest sends its gear in the `dead` message and the host spawns the drops). Pausing is shared: `togglePause` sends `pz` to the host, which applies it with `setPause` and relays it to everyone, and late joiners get it in `init`. Players respawn at half HP when the wave is cleared, and the run ends when everyone is down. The PeerJS room ID is `dw1-` plus a 5-letter code. ICE/TURN servers are copied from Clocked In (`peerOpts()`). Co-op only works from an http(s) address; previews that block WebRTC can't do it.

**Sprites.** `sprites/manifest.json` lists characters (`survivor`, `walker`, `runner`, `spitter`, `bloater`, `brute`, `boss`) with frame size, anchor, direction order, animations and either `sheet`, `variants` or `layers`. See `sprites/README.md`. Sheets are fetched over HTTP, so opening `index.html` from disk, or in the Claude preview, uses the built-in art instead.

**Rebuilding the LPC sheets.** `sprites/lpc/build_lpc.py` assembles them from the LPC generator repo. To change outfits or add survivors, edit the `SURVIVORS` / `WALKERS` / `OTHERS` recipes and run:

```
git clone --depth 1 --filter=blob:none --sparse https://github.com/LiberatedPixelCup/Universal-LPC-Spritesheet-Character-Generator.git repo
git -C repo sparse-checkout set sheet_definitions palette_definitions
python3 build_lpc.py fetch   # pulls just the art the recipes use
python3 build_lpc.py         # writes sprites/lpc/*.png and sprites/manifest.json
```

The wardrobe is built the same way with `build_parts.py fetch` and then `build_parts.py`. Set `LPC_REPO` to point at the clone, and edit `CATALOG` / `PRESETS` to add items. It rewrites only the survivor entry of the manifest. `build_lpc.py` writes the old fixed survivors over that entry, so if you run it, run `build_parts.py` afterwards.

`build_lpc.py` expects the clone in a `repo` folder next to the script, and it writes to an absolute path (`OUT`) that you'll need to adjust. It needs Pillow. Regenerate `sprites/lpc/CREDITS.md` if you add parts from new definitions; each definition JSON has a `credits` list. Keep the title-screen credit line: the LPC licenses (CC-BY-SA 3.0 / GPL 3.0 / OGA-BY 3.0) require attribution.

## Testing

The scripts in `tests/` use Playwright with the preinstalled Chromium. Pass the path to `index.html`:

| Script | Checks |
|---|---|
| `solo_board_fight.js` | Start, boarding a door, a short fight, fps |
| `siege.js` | A fully boarded house under a wave-6 siege: boards break and zombies get in |
| `coop_bridge.js` | Two pages linked by a fake connection: same map, shared kills and XP, guest down and respawn |
| `coop_pause_gear.js` | Same fake connection: a guest goes down and drops their gear, the guest pauses for everyone, the host resumes |
| `modes.js` | Plays an Objectives run to the end by teleporting to each target, then checks Horde (ramp, supply drop, end screen). Takes a few minutes |
| `coop_modes.js` | Fake connection: the guest sees the host's objective, and a guest pickup counts for the team |
| `gear.js` | Every gun deals damage, attachments fit and swap from the ground, a first aid kit heals |
| `coop_gear.js` | Fake connection: a downed guest's gun keeps its attachments, the host revives the guest with a first aid kit, the guest picks the gun back up |
| `infection_solo.js` | Infection practice round: opening scene, run, hunt, push stun, getting caught |
| `infection_coop.js` | Infection with host + 4 guests over fake connections: patient zero, one secret carrier, push stun, infecting, a meeting that quarantines the carrier, end screens |
| `factions_maps.js` | Each map with each faction (all six enemy types in view), boss names, and the four sci-fi guns' damage |
| `lobby.js` | Host + guest in the lobby: racks, lobby bar, a death without gear loss, match settings, start, back to the lobby |
| `wardrobe_http.js` | The wardrobe screen with the real sprite files (pass the `dead-wave` folder, not `index.html`) |
| `controls_touch.js` | Multi-touch stick + FIRE in portrait and landscape, Controls screen, left-handed layout (pass the `dead-wave` folder) |
| `cards.js` | Card screen in portrait and landscape: pick cards, start (pass the `dead-wave` folder) |
| `cards_coop.js` | Card lobby over a fake connection: the guest follows the host's picks, can't change them, and joins the match |
| `cutscene.js` | Survival intro shot by shot (world frozen, then running), and a skipped Robots/Cyberpunk intro (pass the `dead-wave` folder) |
| `cutscene_coop.js` | Co-op intro: both players get it, and the host's skip starts the match for both |
| `home.js` | Home screen in landscape and portrait, a squad of 3 standing together over fake connections, DEPLOY starting the match (pass the `dead-wave` folder) |
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
