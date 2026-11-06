# PROJECT NEXT STEPS (prioritized)

Nothing is in progress. Each item needs the owner's go-ahead; don't start new features unasked.

## P0: Verify on real hardware (owner action + fixes)
1. The owner opens https://tjwavyasf.github.io/everwyn-watch/clocked-in/ on a phone in landscape and plays Chapter 1.
2. Collect: frame rate or stutter, touch issues (stick plus a button at the same time), audio on iOS (starts after the first tap), notch or safe-area overlap, any softlock.
3. Fix only what's reported. If performance is low, the cheapest levers are in `resize()` (`pr` pixel-ratio cap 1.6, so try 1.0–1.25), the blur passes in `renderFrame()`, `EMBERS`/`SPARKS` counts, and the number of point lights in `LPOOL` (7).

## P1: Tune Chapter 1 with the owner's feedback
- Difficulty knobs: `J` attack timings in `startJoseAtk` (wind / recovery), damage values in `updateJoseAtk`/`updateQuakes`/`updateShots`, posture values in `MOVES[*].post`, forklift window `J.win = 12`, hostile `hp/dmg` in `spawnHostile`.
- Forklift feel: `driveForklift` (turn rate 2.9, top speed 8.5, reverse 4).

## P2: Reach the 20–30 minute target (only if the owner wants it)
- Ideas that fit without changing systems: optional talkable NPCs and extra lore pickups at the order desk and break room; a longer or optional floor route; a third outbreak wave; collectibles counted on the end card. Ask before adding.

## P3: Release hygiene
- Gate or remove `window.__ci` debug hooks before calling it a release (e.g. only when `location.hash === '#debug'`).
- Optional: a small landing page at the Pages root linking the games (currently the root is the Minecraft leaderboard; don't break it).

## P4: Final art
- When the owner provides references for TeeJay, Jose, the coworker and the NPCs, update their entries in `CHARS` (palette, hair style, bulk, scale) or add new hair/top styles in `drawChar`. Keep the sheet system.

## P5: Chapter 2 "Mandatory Overtime"
- Needs the owner's script. Suggested structure (PLANNED, not approved): keep the chapter in the same file or split chapters into `chapter2/`. Reuse `ROOMS`/`BUILD`/`SETUP`, the `cutscene()` helpers, checkpoints, and the boss state-machine pattern from Jose.
- Before starting a new version: move the current `index.html` into its own folder (as was done for `v1/`, `v2/` and `long-shift/`), but **ask the owner first**, because the public `/clocked-in/` link serves `warehouse-showdown/index.html`.

## Open question for the owner
- Does the Godot 4 project (TeeJay, training dummy, test scenes) still matter? If yes, it needs to be attached or uploaded; nothing from it is in this backup.
