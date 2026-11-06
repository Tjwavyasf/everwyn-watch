# CLOCKED IN — Game State

**Current build:** Chapter 1 "You're Late", playable start to finish (`index.html`).
**Engine:** single-file HTML5 + three.js r128 (HD-2D pixel sprites in a lit 3D scene), built the same way as earlier Warehouse Showdown versions. This is not a Godot project.

## Chapter 1 flow

| # | Scene | Room | Done when | Checkpoint |
|---|---|---|---|---|
| 1 | Arriving late (car pulls in, TeeJay's line) | Parking Lot | Walk into the entrance | — |
| 2 | Order desk panic, printer spits out an unexplained work order (readable) | Order Desk | Dialogue ends | — |
| 3 | Coworker talk, time clock flashes SHIFT ALREADY IN PROGRESS | Break Room | Clock in | **Clocked in** |
| 4 | Four tile deliveries, A → B, with escalating events per delivery | Warehouse Floor | 4/4 delivered, alarm | **Tile assignment done** |
| 5 | First outbreak: 1 hostile, then 2 more; companion distracts | Warehouse Floor | All hostiles down | **First outbreak survived** |
| 6 | Doors closing, PA, emergency lights, shockwave, Jose drops in | Loading Dock | Intro starts | **Loading dock** |
| — | Jose boss fight (5 forklift rams) | Loading Dock | Fifth ram | — |
| — | Ending: PA "MANDATORY OVERTIME", doors seal, end card | Loading Dock | — | marks save complete |

## Systems

- **Player (TeeJay):** 3-hit fist combo, hold for a heavy punch, dodge roll with invincibility frames, stamina, HP, hit/knockdown reactions.
- **Carry:** pick up / put down / place tile boxes. While carrying: 60% speed, attack and dodge disabled, collision kept, `TILE DELIVERIES n/4` shown. Boxes dropped outside Section B can be picked up again; taking damage drops the box.
- **Companion (Coworker):** leads to exits, follows, distracts hostiles during the outbreak (pulls aggro for ~2 s, never takes damage), hides and calls out tips during the boss.
- **Hostile employee:** pursuit, one telegraphed swing (wind-up flash), recovery window, stagger, knockdown, defeated state (stays down).
- **Script runner:** `cutscene()` + `wait()` + `talk()` + `walkTo()` with an epoch guard, so a room change or retry can never leave a stale cutscene running or controls locked.

## Jose boss state machine

States: `INTRO → PHASE_1 ⇄ STAGGERED → FORKLIFT_READY → FORKLIFT_IMPACT → FORCED_DISMOUNT → PHASE_1/PHASE_2 … → FINAL_DEFEAT`

- **Posture:** player hits fill it (+60% during Jose's recovery windows). It decays when you stop hitting. Full posture → STAGGERED (0.9 s) → FORKLIFT_READY (12 s window, drains at half speed while driving).
- **Forklift:** only enterable in FORKLIFT_READY. A ram needs a real front-of-forklift collision at speed. `ramUsed` limits each window to one ram. Hitting Jose outside a window shows RESISTANT and bounces off.
- **HP gating:** caps after rams 1–5 are 800 / 600 / 450 / 250 / 0. Melee can never take him below the next cap + 30, so only the fifth ram can finish him.
- **Phase 2:** triggers once, during the dismount after ram 3 (HP ≤ 50% or rams ≥ 3), and unlocks Tile Storm plus a Rush → Floor Breaker combo with faster recovery.
- **Attacks:** Floor Breaker (raised-fist wind-up, expanding ring, damage only where the ring passes), Clock-In Rush (red line telegraph, committed dash, dizzy recovery if he misses or hits a wall), Tile Storm (three telegraphed slabs that shatter on barriers). Only one attack runs at a time, and posture break clears active waves and slabs.
- **Forced dismount:** cutscene where Jose throws TeeJay to a safe spot. The forklift resets to whichever home spot is farther from Jose, posture resets, and he gets a brief posture lock. If the window expires while you're driving, the same dismount happens with no ram.
- **Fallbacks:** RESET button (touch) or R key when the forklift is stuck; Pause → Reset Forklift; Pause → Restart Checkpoint.
- **Retry:** reloads the Loading dock checkpoint, which rebuilds Jose (full HP, 0 rams, 0 posture) and puts the forklift at home.

## Saving

Saves happen only at the four checkpoints (localStorage `ci1_save`), never during a fight or a dismount. Retry restores full HP and stamina, plus the story step and delivery count stored with the checkpoint.

## Known limits / not done

- Tested in headless Chromium (desktop and phone viewports, keyboard and touch events). **Not tested on a physical phone.**
- A first playthrough is probably **10–15 minutes**, not the 20–30 in the spec. More content (optional side talk, collectibles, a longer outbreak) would be needed to reach that.
- Jose, TeeJay and all NPCs are generated placeholder pixel art until final reference art exists.
- Debug hooks live on `window.__ci` (skipTo, breakPosture, god). They're harmless, but remove them before release.
