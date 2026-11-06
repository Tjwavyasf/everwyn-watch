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

- **Player (TeeJay):** 3-hit fist combo, hold for a heavy punch, stamina, HP, hit/knockdown reactions.
- **Movement (Elden-style):** hold RUN (Shift) to sprint (x1.55, drains stamina, locks out until 25 stamina after running dry). DODGE rolls when you push a direction (0.58 s, i-frames 0.05–0.30 s, 20 stamina) and is a short backstep when you stand still (0.40 s, i-frames 0.03–0.22 s, 14 stamina). Space is dodge; Shift no longer dodges.
- **Parry:** PARRY (L / Q) raises a guard for a 0.23 s active window (10 stamina). A hostile swing that lands inside it is turned aside: 1.7 s stagger and a RIPOSTE window where your next hit does at least 40 damage and knocks down. Whiffing leaves a 0.5 s recovery. **New Jose rule (needs your approval):** parrying Clock-In Rush adds 20 posture (`PARRY_POSTURE`) unless posture is locked; HP gating and the five-ram finish are untouched.
- **Customization:** title screen CUSTOMIZE edits skin, hair, hair style, beard, shades, top style/color, accent, pants, boots and build. Stored in `ci1_look`; rebakes `CHARS.teejay` so every sprite sheet picks it up.
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

## Online co-op (2 players)

- **Connecting:** title → HOST CO-OP shows a 5-letter code; the friend taps JOIN CO-OP and types it (or opens `index.html?join=CODE`). Uses PeerJS (loaded on demand from unpkg, jsDelivr as fallback, pinned to 1.5.4 with an integrity hash) and PeerJS's free public broker + relay. Host starts NEW GAME / CONTINUE as usual; a friend who connects mid-game is dropped in at the next room load.
- **Model:** the host owns the world (story, enemies, Jose, forklift, crates, saves). The friend is P2: they run their own character locally (instant controls, own stamina, roll, parry timing) and report their pose ~20x/s. The host decides every hit, parry, dodge and crate break, and tells the friend when they are hurt (`dmg`). The host sends the friend a snapshot ~20x/s (actors, Jose numbers, forklift, crates, boxes, shockwaves, telegraph lines, slabs) plus one-shot events (sound, sparks, damage numbers, barks, banners, dialog lines).
- **What the friend can do:** run, roll, backstep, parry (with +0.12 s latency allowance on the host), punch, kill hostiles, hit Jose, break crates. Hostiles and Jose target whichever player is nearest; Jose's Rush, Floor Breaker rings and Tile Storm slabs hit and can be parried or dodged by either player.
- **What only the host does:** pick up / place tile boxes, talk to NPCs, use the clock, drive the forklift, change rooms, save, retry. The friend sees dialogs (read-only) and follows the host through rooms. Friend has no pause-for-all; host pausing freezes the game for both.
- **Down / revive:** a downed player gets back up at half health after 8 s if the partner is alive. Both down → the host sees WRITTEN UP and RETRY, the friend sees it too; retry restores both at the checkpoint.
- **Safety:** all text from the other side is rendered as text (no HTML), numbers are range-checked, and the friend's saves are never touched (a guest never writes `ci1_save`).
- **Not synced:** the clock-screen text on the break-room wall, toppled-stack scatter (random per device), the printer paper, and decorative flicker.

## Saving

Saves happen only at the four checkpoints (localStorage `ci1_save`), never during a fight or a dismount. Retry restores full HP and stamina, plus the story step and delivery count stored with the checkpoint.

## Known limits / not done

- Tested in headless Chromium (desktop and phone viewports, keyboard and touch events). **Not tested on a physical phone.**
- A first playthrough is probably **10–15 minutes**, not the 20–30 in the spec. More content (optional side talk, collectibles, a longer outbreak) would be needed to reach that.
- Jose, TeeJay and all NPCs are generated placeholder pixel art until final reference art exists.
- Debug hooks live on `window.__ci` (skipTo, breakPosture, god, hurt, dbg, net). They're harmless, but remove them before release.
- **Co-op is tested only in headless Chromium**: two pages bridged in-page (with 0 and 120 ms round-trip), a full Chapter 1 playthrough with a connected friend, and two pages connected over real WebRTC through a **local** PeerJS broker. It has **not** been tested against PeerJS's public broker, across two real networks, or on phones. Some mobile carriers and strict networks block peer-to-peer; PeerJS's public relay is a free service with no guarantee.
- The host trusts the friend's reported position and pose (fine for friends, not for strangers).

### Co-op waiting room
On friend join the host enters room `lounge` (the break room, throwaway save state, real save untouched). PvP uses the normal attack code with `pvpHit`; hits never kill in this room and HP regenerates. Guest sends `{t:'ready'}`; host shows START buttons once ready. `loungeStart()` then runs the normal `beginPlay()`.
