# Warehouse Showdown: The Long Shift

A story-driven action RPG for phones, in an HD-2D pixel style. Depot 9 never closes, and nobody who clocked in has ever clocked out. You're the new hire. Explore the warehouse, beat the bosses who run it, and reach the Foreman's office.

Open `index.html` in a phone or desktop browser. No install or build step. It loads three.js r128 from cdnjs, so the first load needs a connection. Progress saves automatically in the browser.

Earlier versions are kept in [`v1/`](v1/index.html) (side-scroller) and [`v2/`](v2/index.html) (arcade waves with classes and upgrades).

## How it plays

- **Explore** ten connected rooms: the Break Room, Aisles 1–3 and 4–6, the Dock Yard, the Loading Dock, the Freezer Hall, Cold Storage, the Mezzanine, the Foreman's Office, and a records room hidden behind a wall of boxes. Doors show where they lead, there's a shortcut shutter to unlock from one side, and MAP shows what you've found.
- **Time clocks** are checkpoints. Punching in heals you, refills your thermos, brings the enemies back, and opens a menu to level up (Vigor, Grit, Might), fast travel between clocks, and change weapons.
- **Scrip** comes from enemies, crates and envelopes. When you're fired (killed), you drop all of it where you fell. Get back there and pick it up before you die again, or it's gone.
- **Stamina** limits attacks, rolls and parries. **Roll** through attacks for invincibility frames. **Heal** by drinking from your thermos (limited sips; Coffee Beans add more).
- **Parry** right before a hit lands to stagger the attacker, then hit them for a triple-damage **riposte**. Hitting an enemy that hasn't noticed you is a **sneak attack** for 2.5× damage. Enemies flash right before they attack.
- **Weapons** you find stay in your inventory: lead pipe, crowbar, sledgehammer, knife, box cutter, and a golden box cutter from the optional boss. Tap your weapon in the HUD (or press Q) to swap. Fists always work. Throwing knives go on the THROW button.
- **Story:** Dee, the old night guard in the Break Room, explains what happened and has more to say after each boss. Notes scattered around the depot fill in the rest.

## Bosses

| Boss | Where | Drops |
| --- | --- | --- |
| Big Tully, King of the Dock | Loading Dock | Dock Keycard |
| Quartermaster Vance, Keeper of Cold Storage | Cold Storage | Freezer Keycard |
| The Auditor (optional) | Records Room (hidden) | Golden Box Cutter |
| The Foreman, Master of the Night Shift | Office (needs both keycards) | The ending |

Each boss has a second phase at half health. The main bosses sit behind red mist; once you step through, there's no leaving until one of you goes down.

## Workers

| Class | Weapon | Skill |
| --- | --- | --- |
| Knox (Brawler) | Fists, four-hit combos | Ground Pound |
| Rin (Cutter) | Box Cutter, cheaper rolls | Surge: hits everything in view |
| Mara (Loader) | Sledgehammer, light hits don't interrupt | Forklift Spin |
| Vee (Slinger) | Knife, endless throwing knives | Knife Fan |

## Controls

| Action | Touch | Keyboard |
| --- | --- | --- |
| Move | Left-side stick | WASD / arrows |
| Attack (tap for combo, hold for heavy) | ATTACK | J (K = heavy) |
| Roll | ROLL | Space / Shift |
| Heal (thermos) | HEAL | R |
| Throw knife | THROW | F |
| Parry | PARRY | L |
| Skill (when the meter is full) | SKILL | U |
| Talk / read / open / rest / pick up | USE (appears when near something) | E |
| Swap weapon | Tap the weapon in the HUD | Q |
| Map / pause | buttons top right | Tab / Esc |
