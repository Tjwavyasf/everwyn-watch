# Changelog

## Player names
- Customize screen has a NAME box (12 chars, letters/numbers/space/._-). The name shows over your head, in the HUD, and to your co-op partner (instead of FRIEND/HOST).

## Working relay for co-op
- Replaced the dead free relay servers with a Metered TURN credential (free plan) so phones on different mobile networks can connect.

## Jump, double jump, slam; connection test
- JUMP button (V / B on keyboard). Press again in the air to double jump.
- Attack in the air = slam dive that hits everything around the landing spot (harder and wider after a double jump). Works in co-op.
- Co-op lobby and waiting room: TEST CONNECTION button reports whether the matchmaking server and relay servers are reachable.

## Levels, skill tree, classes, combo bonus
- XP from kills, deliveries (once each), checkpoints (once each); level-ups give 1 skill point.
- Pause > SKILLS: 13 skills in Survival / Combat / Movement / Work / Boss branches, reset button.
- Beating the boss unlocks boss skills and a permanent class (Brawler, Duelist, Hauler); a New Game after finishing keeps progress.
- Combo counter: consecutive hits give +3% damage per stack (cap raised by Combo Mastery); resets when hit or after 3s.
- Solo and co-op host only; the co-op guest and the break room use base stats.

## Box cutter + 3 new moves
- TeeJay now starts with the Box Cutter equipped (solo and co-op).
- Running attack: hold RUN and press ATTACK for a lunging slash.
- Backstep attack: dodge while standing still, then attack.
- Charged heavy: hold ATTACK to charge (up to 1.7x damage, drains stamina).

## Clocked In — controls and co-op update (2026-11-06)
- Added **RUN** (hold Shift / RUN button): sprint with stamina drain and an empty-stamina lockout.
- Reworked **dodge** (Elden-style): committed roll with a shorter invincible window, or a quick backstep when standing still. Dodge is Space or the DODGE button; Shift is now run.
- Added **PARRY** (L / Q / PARRY button) with a stagger and a RIPOSTE for big damage. Parrying Jose's Clock-In Rush now adds 20 posture. This is a new rule on the boss; HP gating and the five-ram finish are unchanged.
- Added **CUSTOMIZE** on the title screen (skin, hair and style, beard, shades, top, accent, pants, boots, build), saved on the device.
- Added **online co-op for 2 players** with a 5-letter room code (PeerJS): HOST CO-OP / JOIN CO-OP on the title, a lobby screen, invite link (`?join=CODE`), a friend who fights alongside TeeJay with their own look, partner health bar and name tags, down/revive, shared defeat and retry, disconnect handling.
- Touch layout: PARRY and RUN buttons added without overlapping USE (circle gaps checked on five viewports).
- Pause help text updated. Solo play is unchanged apart from the above; the full Chapter 1 regression passes.

## Clocked In — Chapter 1 "You're Late" (2026-11-06)
- New game in `index.html`: a complete Chapter 1 from the parking-lot arrival to Jose's defeat and the Mandatory Overtime cliffhanger.
- Added: arrival cutscene, order desk scene with a readable mystery work order, time clock glitch, coworker companion AI, reusable tile carry/placement system with four scripted delivery events, first outbreak fight (3 hostiles), loading dock intro with PA and shockwave.
- Added: Jose, the Floor Enforcer, with an explicit state machine, posture meter, three attacks, two phases, and the five-ram forklift finish (drivable forklift, ram detection, forced dismount, HP gating, stuck reset).
- Added: four checkpoints, defeat screen with Retry, pause menu (restart checkpoint, reset forklift, sound), chapter end card.
- Reused unchanged from the previous build: pixel-art sprite generator, renderer and post effects (bloom, depth blur), sparks/effects, actor system, touch joystick and buttons.
- Moved the previous game to `long-shift/` (Warehouse Showdown: The Long Shift). `v1/` and `v2/` are unchanged.

## Warehouse Showdown: The Long Shift (2026-11-05) — now in `long-shift/`
## Warehouse Showdown v2 (2026-11-05) — `v2/`
## Warehouse Showdown v1 (2026-11-05) — `v1/`

## Co-op waiting room (break room)
- When a friend joins, both players start in the Break Room (the same room as the story one) instead of the title screen.
- The two players can fight each other there. No one can be knocked out and health slowly comes back.
- The friend taps READY UP; then the host's START NEW GAME (or CONTINUE) button unlocks and moves both into the game.
- Co-op protocol version bumped to 2, so both players must have the same build (the host is told if not).
- Internal room id is `lounge`; it reuses the break room build.
- HOST CO-OP now drops the host straight into the break room; the room code (and a COPY INVITE LINK button) show on the panel while waiting for a friend.
