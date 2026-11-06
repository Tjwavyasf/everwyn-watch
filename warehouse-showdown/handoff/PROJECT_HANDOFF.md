# PROJECT HANDOFF: CLOCKED IN (formerly Warehouse Showdown)

Written 2026-11-06 at the end of the first development conversation. Labels used: **CONFIRMED** (stated by the owner), **IMPLEMENTED** (in the code and verified by running it), **PLANNED** (asked for, not built), **INCOMPLETE** (partly built), **UNVERIFIED** (claimed or assumed, not checked).

> The source of truth is the actual files in `warehouse-showdown/` (or the backup zip), not this document. If anything here disagrees with the code, the code wins.

---

## 1. Project overview

| | |
|---|---|
| Name | **CLOCKED IN**. Chapter 1 is "You're Late". Earlier builds were called *Warehouse Showdown*. |
| Owner | GitHub user `Tjwavyasf` |
| Genre | Landscape mobile pixel-art action RPG / beat 'em up with story chapters |
| Vision (CONFIRMED) | TeeJay, a warehouse worker, arrives late on the day an unknown force takes over the warehouse. Coworkers become hostile, supervisors become bosses. Each chapter ends on a cliffhanger. Tone: grounded, funny dialogue in TeeJay's voice, eerie mystery that is **not revealed yet**. |
| Art direction | HD-2D: generated pixel-art sprites (billboards) in a lit 3D warehouse with bloom, depth-of-field blur, embers, emergency lighting. All characters are **temporary art** until the owner provides references. |
| Tech | One self-contained HTML file per game (HTML + CSS + JS) using **three.js r128 from cdnjs**. No build step. |
| Where it lives | Repo `Tjwavyasf/everwyn-watch` (public), folder `warehouse-showdown/`. The rest of that repo is an **unrelated Minecraft server mod** ("Everwyn Watch"). Don't touch it. |
| Current file | `warehouse-showdown/index.html` = CLOCKED IN Chapter 1 |
| Public link | https://tjwavyasf.github.io/everwyn-watch/clocked-in/ (GitHub Pages; deploy run 42222714570 succeeded 2026-11-06 13:07 UTC. The page itself was not opened from the dev container, which cannot reach github.io.) |
| Branches | Dev branch `claude/sleepy-einstein-ipmhac`; `main` = commit `f001b81` (contains everything up to publishing). |

### Godot note (IMPORTANT)
The owner's Chapter 1 spec was written for a **Godot 4 / GDScript** project ("preserve the existing warehouse benchmark, TeeJay character, combat modules, character data, camera, mobile HUD, touch settings, training dummy, test scenes"). **That Godot project was never accessible in this conversation.** It may be in the private repo `Tjwavyasf/warehouse-show-down`; attaching it was blocked by a permission check. The owner then said: *"Make it the same way you've been making it, this gonna be v3"*. So Chapter 1 was built in the HTML/three.js engine. **Status of the Godot project: UNVERIFIED / not backed up.** Ask the owner before switching engines.

### Version naming (avoid confusion)
- `v1/`: side-scroller. `v2/`: HD-2D arcade with classes. `long-shift/`: "Warehouse Showdown: The Long Shift" (souls-like; its commit message calls it "v3").
- The owner calls **CLOCKED IN** "v3". It lives at the root `index.html`.

---

## 2. Current status

**Chapter 1 is IMPLEMENTED and playable end to end** (verified 2026-11-06 by an automated headless-Chromium playthrough of the committed file: every story step, all 4 deliveries, outbreak waves, 5 forklift rams with HP 1000→800→600→450→250→0, phase 2 triggered once at ram 3, ending card shown, 0 JS errors).

Not verified: real phone hardware, real human play feel and difficulty, full-length playtime.

---

## 3. Feature inventory

### IMPLEMENTED (Chapter 1, `index.html`)
- **Title screen**: Continue (if a save exists and isn't finished) / New Game. A live 3D parking lot renders behind it.
- **Scene 1, Parking Lot**: car drive-in cutscene, line "Man, ain't no way I'm late again.", objective *Enter the building*, door on the back wall.
- **Scene 2, Order Desk**: 5 desks, ringing phones (blinking + SFX), 2 NPCs rushing on patrol paths, exact 5-line dialogue from the spec, printer prints a **readable mystery work order** (no reveal). Objective *Go to the break room*.
- **Scene 3, Break Room**: coworker dialogue (3 lines from the spec). Time clock interaction shows **SHIFT ALREADY IN PROGRESS** (glitch SFX, lights dim), then "08:47 · CLOCKED IN". TeeJay: "…The heck was that?". **Checkpoint "Clocked in"**. The coworker becomes the companion and leads to the exit.
- **Scene 4, Tile Assignment**: 4 boxes, Section A to Section B (marked floor zones, 4 slots). Carry system: pick up / put down anywhere / place in zone; 60% speed; attack and dodge disabled ("HANDS FULL"); collision kept; `TILE DELIVERIES n/4` HUD; taking damage drops the box. Delivery events:
  1. chatter + TeeJay complains
  2. argument (shout bubbles)
  3. terrified worker runs past, crash, a stack topples, lights flicker
  4. workers fight, boxes fall, alarm, "BRO, I JUST GOT HERE!"

  Then **checkpoint "Tile assignment done"**.
- **Scene 5, First Outbreak**: a hostile employee (purple glowing eyes) with pursuit, one telegraphed swing (wind-up glow), recovery window, stagger, knockdown, defeated state. After it's down, a second wave of 2. The companion distracts (pulls aggro for 2.2 s, never damaged, barks). Closing dialogue from the spec, then **checkpoint "First outbreak survived"**. Objective *Reach the loading dock*.
- **Scene 6, Loading Dock**: shutters lower, PA "All employees… remain… on shift.", emergency beacons and alarm, TeeJay auto-walks, shockwave, Jose lands. **Checkpoint "Loading dock"**, then the intro dialogue from the spec, slam + shockwave, title card **JOSE — THE FLOOR ENFORCER**.
- **Jose boss** (details in §5): explicit states, 3 attacks, 2 phases, posture meter, five-ram forklift finish, forced dismount, HP gating, stuck-forklift fallback.
- **Ending**: final ram cutscene ("MAN, I'M TRYING TO GO HOME!", Jose crashes through a tile stack, glow fades, sprite swaps to `josefree`, "What… happened?" / "Bro, you tell ME!", Jose collapses alive). TeeJay and the companion walk to the doors, PA "ATTENTION ALL EMPLOYEES. MANAGEMENT HAS DECLARED MANDATORY OVERTIME.", doors seal, "Nah, I ain't getting paid enough for this.", fade, **CHAPTER 1 COMPLETE / TO BE CONTINUED / NEXT: MANDATORY OVERTIME** with time and death stats.
- **Defeat**: "WRITTEN UP" screen (desaturated), Retry (reloads the latest checkpoint, full HP and stamina, story step and delivery count restored) or Quit to title.
- **Pause**: resume, sound toggle, restart at checkpoint, reset forklift (boss only), quit to title. Auto-pauses on blur or app switch.
- **Controls**: floating left joystick; ATTACK (tap = 3-hit combo, hold = heavy), DODGE (roll with i-frames), contextual USE / DROP / EXIT button, RESET (only when the forklift is stuck). Keyboard: WASD/arrows, J, K, Space/Shift, E/Enter, R, Esc/P. Multi-touch uses pointer IDs (stick and buttons at the same time).
- **HUD**: TeeJay HP (with lag bar) + stamina; objective banner; delivery counter; boss HP + posture/window bar + 5 ram pips; context prompt; speech bubbles; damage numbers; area titles; PA text; toasts.
- **Save**: localStorage key `ci1_save` (checkpoint snapshot + deaths + time + done flag); `ci1_muted`.

### INCOMPLETE / not met from the Chapter 1 spec
- **Length**: the spec says 20–30 minutes; the estimated real first playthrough is **~10–15 min** (UNVERIFIED, no human playtest).
- **"Prepare a mobile-compatible export"**: N/A in the Godot sense. It's a web page; mobile hardware is untested.
- **"Preserve existing Godot systems"**: not applicable (see the Godot note).
- **Tile destruction**: breakable boxes use particles plus a few animated meshes (deliberately light), not physics.

### Earlier games (IMPLEMENTED, preserved, not under active development)
- `v1/index.html`: 2D side-scroller *Warehouse Showdown* (canvas 2D), 6 waves + Foreman boss.
- `v2/index.html`: HD-2D arcade version: 4 classes (Knox, Rin, Mara, Vee), weapons/throwing/durability, crates, 12 upgrades, 6 shifts.
- `long-shift/index.html`: *The Long Shift*: 10 connected rooms, Dee NPC, time clocks (rest / level up / fast travel), stamina, roll, thermos, scrip drop on death, 4 bosses (Big Tully, Vance, Auditor, Foreman), map, autosave (`whs3_save`).

### PLANNED / discussed, not built
- Chapter 2 "Mandatory Overtime" (only the title is known; no script yet).
- Final character art from the owner's references (Jose and TeeJay sprites are temporary).
- Ideas offered to the owner, never approved: side quests, armor/charms, NG+.

---

## 4. Architecture of `index.html` (CLOCKED IN)

One IIFE in strict mode. Line numbers are approximate (2390 lines total).

| Lines | Section | Notes |
|---|---|---|
| 1–205 | HTML + CSS | design tokens on `:root`; DOM for HUD, controls, screens. **Single dark theme by design.** |
| 210–307 | utilities, audio | `clamp/lerp/approach/sign/pick`, `store/load` (prefix `ci1_`), WebAudio synth `SFX`, `tone/noise`, `music()` (explore drone / boss loop, driven by `musicMode`) |
| 309–555 | pixel-art generator | `Px` rasterizer (SDF capsules/disks with rotation), `group/ball/ik`, `drawWeapon`, **`CHARS`** (all character designs), `drawChar`, poses `PZ/ARMS/SWING/BLUNT/FISTS/ATK`, `animSet(cat)` |
| 556–620 | three.js setup | renderer, scene, camera, post-processing (scene RT → bloom blur + tilt blur → composite shader with vignette / low-HP / flash / saturation) |
| 621–698 | textures | procedural canvas textures (`FLOORS`, `WALLS`, `texBox`, `textTex()` for signs…) |
| 699–731 | sprite sheets | `getSheet(charKey, weaponKey)` builds and caches an atlas; `icon(key)` |
| 732–814 | persistent scene | ambient/moon light, **7 pooled point lights (`LPOOL`)**, embers, sparks pool, swipe/star/ring FX pools, `lightAt()` CPU light tint for sprites |
| 815–853 | `Actor` class | billboard sprite + blob shadow, `show(anim, frame)` UV swap, `sync()` lighting/flash/glow |
| 855–910 | Chapter 1 data | extra SFX, textures, `drawWeapon` wrapper (adds `slab`), `WEAPONS`, `MOVES`, `CP_NAMES`, **`ROOMS`**, `THEMES` |
| 911–936 | state + script helpers | globals; `wait()`, **`cutscene(async ok => …)`** with `epoch` guard, `talk()`, `walkTo()`, `setObj()`, `setDeliv()` |
| 937–1054 | input + overlays | keyboard map, floating stick, buttons (`BTNS`), dialog (typewriter), notes, pause |
| 1055–1088 | flow | `toTitle`, `beginPlay`, **`loadCheckpoint`**, **`checkpoint(id)`**, `retry`, `fade` |
| 1089–1281 | room building | helpers (`box`, `addObst`, `lightSlot`, `lamp`, `shelfRow`, `tileStack`, `topple`, `car`, `zonePlane`, `beacon`), `buildBase`, `buildExit`, **`BUILD[room]`** |
| 1282–1472 | rooms: enter + setups | `clearRoom` (increments `epoch`, resets everything), `enterRoom`, NPCs, companion, barks, **`SETUP[room]`** (story logic per room keyed on `S.step`), `doClockIn` |
| 1473–1526 | tile carrying | `spawnBox`, `pickUp`, `putDown`, `deliveryEvent(n)` |
| 1527–1717 | outbreak + AI | hostiles, `outbreakCheck` (waves via `S.wave`), companion modes (`stand`, `lead`, `follow`, `assist`, `hide`, `none`), NPC modes (`stand`, `patrol`, `flee`, `fight`), `collide`, crates, coffee pickup |
| 1718–1844 | player | `startAttack`, `startRoll`, `attackHits`, `updatePlayer` (states `idle/walk/attack/roll/hurt/down/getup/flung/dead`, plus `riding` and `carry` flags), `damagePlayer`, `showDefeat`, interaction, `checkExit` |
| 1845–2127 | **Jose** | `makeJose`, `bossIntro`, `joseHit`, `postureBreak`, attacks (`startJoseAtk`, `updateJoseAtk`), quakes and telegraphs, `updateShots`, **`updateJose`** (state machine), `ramImpact`, `safeSpot`, `forcedDismount`, `finalDefeat`, `ending` |
| 2128–2217 | **forklift** | `buildForklift`, `resetForklift`, `syncForklift`, `enterForklift`, `exitForklift`, `driveForklift` (steering, collision, ram check, stuck detection) |
| 2218–2366 | animation, camera, env, HUD, loop | fixed 60 Hz `tick`, hitstop, slow-mo; `frame()` renders |
| 2367–end | boot + debug | `window.__ci` debug hooks (see §8) |

### Story state (`S`)
`S = { step, delivered, wave, deaths, time, done, cp: { id, room, x, z, step, delivered } }`

| step | meaning |
|---|---|
| 0 | parking arrival cutscene |
| 1 | enter the building |
| 2 | (unused gap) |
| 3 | order desk done → break room |
| 4 | talked to coworker → clock in |
| 5 | clocked in → follow coworker |
| 6 | tile deliveries (S.delivered 0–4) |
| 7 | outbreak (S.wave 1→3) |
| 8 | go to loading dock |
| 9 | boss fight |
| 10 | chapter complete |

Exits open when `S.step >= ROOMS[room].exit.req` (parking 1, orderdesk 3, breakroom 5, floor 8).

---

## 5. Jose boss: exact rules (do not change without the owner's approval)

- **States**: `INTRO, PHASE_1, STAGGERED, FORKLIFT_READY, FORKLIFT_IMPACT, FORCED_DISMOUNT, PHASE_2, FINAL_DEFEAT` (`J.state`).
- **Posture**: hits add `MOVES[*].post` (×1.6 during his recovery windows). It decays 8/s after 2.5 s without a hit. Max 100 (125 in phase 2). Full posture → STAGGERED 0.9 s → FORKLIFT_READY with a 12 s window (drains at half speed while driving).
- **Forklift**: enterable only in FORKLIFT_READY (`!J.ramUsed`). A ram needs the forklift front within reach of Jose at speed > 2.2. `J.ramUsed` makes it one ram per window. Outside a window the forklift bounces off with "RESISTANT".
- **HP gating**: `RAM_CAP = [800, 600, 450, 250, 0]`. Melee floor = next cap + 30. The 5th ram sets HP 0, so normal attacks can never kill him.
- **Phase 2**: set once (`J.p2`) during the dismount when HP ≤ 50% or rams ≥ 3 (in practice after ram 3). It unlocks Tile Storm, a Rush→Floor Breaker combo and faster recovery.
- **Attacks**: Floor Breaker (raised-fist wind-up 0.95 s / 0.75 s, ring 7.5 u/s to radius 9, 18 dmg + knockdown, recovery opening). Clock-In Rush (red line telegraph 0.8 s / 0.6 s, committed dash up to 10 u, 24 dmg + knockdown, dizzy if he misses or hits a wall). Tile Storm (3 slabs, each with its own line telegraph, 14 dmg, they break on barriers). One attack at a time; a posture break clears waves, slabs and telegraphs.
- **Forced dismount** (a cutscene, Jose can't attack): Jose walks to the forklift and throws TeeJay to the safest spot (`safeSpot`). The forklift resets to whichever home spot (`FL.homes`) is farther from Jose. Posture → 0, a 2 s posture lock, resist on. A window that expires while driving also triggers a dismount (no ram).
- **Fallbacks**: RESET button / R when the forklift is stuck for 1.2 s; Pause → Reset Forklift; the Retry checkpoint rebuilds the whole encounter (`SETUP.bay` with step 9).
- **No saving during the fight.** Saves happen only at the four `checkpoint()` calls.

---

## 6. Decisions and requirements that must not change (CONFIRMED by the owner)
1. **Keep the engine and art style**: single HTML file, three.js r128, generated HD-2D pixel art. The owner said to build "the same way you've been making it".
2. The **approved opening** (late arrival) and the **unknown-force mystery are canonical**. Never explain the mystery yet.
3. Dialogue lines in the spec are exact; keep them word for word.
4. Jose is a **fictionalized** supervisor, not a real person.
5. The five-ram forklift loop, HP gating, one ram per window and the explicit states are required.
6. Checkpoints only at the 4 spec points; never save mid-dismount.
7. Landscape-first mobile; touch controls must keep working with simultaneous touches.
8. Don't rebuild working systems; make changes incrementally.
9. Preserve earlier versions (`v1/`, `v2/`, `long-shift/`). Each new version first moves the old file into its own folder.
10. Never claim mobile compatibility until it has been tested on a real device.
11. Don't push to `main` without the owner's explicit OK (the owner approved it once, for publishing).
12. Don't recreate copyrighted games; earlier work avoided copying Last Ditch Effort and Elden Ring names and content.

---

## 7. Known issues and limitations
| Issue | Status |
|---|---|
| Never tested on a physical phone (performance, notch, Safari audio) | UNVERIFIED |
| Playtime below the 20–30 min target | INCOMPLETE |
| Difficulty not tuned by a human (tests used god mode and teleports) | UNVERIFIED |
| Forklift steering feel not human-tested (the touch stick did land a ram in testing) | UNVERIFIED |
| Requires an internet connection for three.js (cdnjs) and Google Fonts; there's an offline fallback message if three.js fails | by design |
| `window.__ci` debug hooks ship in the public build (god mode, skipTo…) | known; remove or gate before a "real" release |
| Damage to the player is ignored during cutscenes (`cine > 0`) | by design |
| Portrait works (top band + controls below) but landscape is intended | by design |
| `S.step` value 2 is unused | harmless |
| Engine code is copied from `long-shift/index.html` lines 258–603 and 703–999 with small patches; the two files are now independent | note |
| Claude artifact copies are separate snapshots and don't auto-update | note |

---

## 8. Debug hooks (`window.__ci`, browser console)
`state` (snapshot), `newGame()`, `advance()` (dialog), `tp(x,z)`, `god()`, `hurt(n)`, `boxes()`, `enemyPos()`, `breakPosture()`, `jose()`, `fl()`, `skipTo('clockin'|'tiles'|'outbreak'|'arena')`.
Older games: `window.__whs` (v1/v2/long-shift).

---

## 9. Exact task at handoff
- **Last completed task:** published CLOCKED IN Chapter 1 publicly on GitHub Pages (`f001b81` on `main`), then answered how updates reach players.
- **Unfinished task:** none in code. This handoff package is the current task.
- **Next action:** see `PROJECT_NEXT_STEPS.md`. First, the owner plays on a real phone and reports problems; then extend Chapter 1 or start Chapter 2 once the owner provides a script.

## 10. How to continue
1. Get the code: clone `Tjwavyasf/everwyn-watch` (or unzip the backup, or `git clone everwyn-watch.bundle`). Work in `warehouse-showdown/`.
2. Read `GAME_STATE.md` and this file, then the relevant section of `index.html`.
3. Make focused edits to `index.html`; test with Playwright (see `PROJECT_SETUP.md`); update `GAME_STATE.md` and `CHANGELOG.md`.
4. Publish: commit, then (with the owner's OK) push to `main`. The Pages workflow copies `warehouse-showdown/index.html` to `/clocked-in/`.
