# PROJECT CHANGELOG (reconstructed from git history and the conversation)

All dates 2026. Commits are on `Tjwavyasf/everwyn-watch`, branch `claude/sleepy-einstein-ipmhac` (merged to `main` at `f001b81`).

## 2026-11-05: Warehouse Showdown v1 — COMPLETED (`f1ff3b3`)
- The owner asked for a mobile game "sorta like Last Ditch Effort" (screenshot reference). Built an original 2D side-scrolling melee game in one HTML file (canvas 2D).
- 3-hit combo, heavy, dash, parry, Surge meter, 6 waves (grunts, gunners, brutes), Foreman boss, touch buttons, synthesized audio.
- Published as a Claude artifact: https://claude.ai/artifact/R1u2zCutxeV6Pj2BxhDsSV

## 2026-11-05: v1 preserved (`f17be37`) → v2 — COMPLETED (`3bbe0fb`)
- The owner asked for "3D pixel" free movement (Octopath-style reference), classes, weapons, upgrades, better designs, throwing and fists.
- Rebuilt in three.js r128: generated pixel sprites, lit 3D warehouse, bloom/tilt-shift, 4 classes, pick-up/throw weapons with durability, crates, food, 12 upgrades.
- Artifact: https://claude.ai/artifact/E5kTkGrW59CZdTYq3TWW4V

## 2026-11-05: v2 preserved (`05f567a`) → The Long Shift — COMPLETED (`0ed091b`)
- The owner asked for "a bit of Elden Ring": story, bosses, exploration. Built an original souls-like: 10 rooms, Dee NPC, lore notes, time clocks, stamina/roll/thermos, scrip drop on death, 4 bosses, map, autosave, ending.
- Artifact: https://claude.ai/artifact/UnEQoFU4dNL9V34zBaBC3t

## 2026-11-06: CLOCKED IN Chapter 1 "You're Late" — COMPLETED (`fa030a5`, `7e32bd2`)
- The owner gave the full Chapter 1 spec (written for Godot 4). The Godot project wasn't accessible (repo attach blocked); the owner said to build it "the same way". The Long Shift moved to `long-shift/`.
- Built the whole chapter in the existing engine: arrival, order desk, clock-in glitch, companion, carry system plus 4 delivery events, outbreak (3 hostiles), dock intro, Jose boss (states, 3 attacks, 2 phases, posture, five-ram forklift finish, forced dismount, HP gating, stuck reset), ending card, 4 checkpoints, defeat/retry, pause.
- Bugs fixed during development: duplicate `musicMode` declaration; name clash `scene` (renamed the helper to `cutscene`); Jose never moved during the forced dismount (`goalStep` not called); TeeJay slid after landing from the throw (zero velocity on landing); objective text lingering across rooms.
- Added `GAME_STATE.md`, `CHANGELOG.md`.
- Artifact: https://claude.ai/artifact/AJQtgHme1rXTaomeKwXdNd

## 2026-11-06: Public link — COMPLETED (`f001b81`, pushed to `main` with the owner's OK)
- The Pages workflow (`.github/workflows/leaderboard.yml`) copies `warehouse-showdown/index.html` to `/clocked-in/` and deploys even when the Minecraft server is offline (keeping the last `board.json`). The mod build (`build.yml`) ignores `warehouse-showdown/**`.
- Deploy run 42222714570: success. URL: https://tjwavyasf.github.io/everwyn-watch/clocked-in/

## 2026-11-06: Handoff package — this commit
- Added `warehouse-showdown/handoff/*.md`. No game code changed.

## Pending / uncertain
- Real-device testing: pending.
- Godot project (`Tjwavyasf/warehouse-show-down`?): contents unknown, not part of this history.
