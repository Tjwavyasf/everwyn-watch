# Warehouse Showdown

A mobile beat 'em up set on a warehouse night shift, in an HD-2D style: pixel-art fighters in a lit 3D warehouse with bloom, depth-of-field blur and drifting embers. Walk anywhere on the floor, fight through five shifts, then beat The Foreman in Overtime.

Open `index.html` in a phone or desktop browser. No install or build step. It loads three.js r128 from cdnjs, so the first load needs a connection.

The original side-scrolling version is kept in [`v1/`](v1/index.html).

## Crew

| Class | Starts with | Plays like | Special |
| --- | --- | --- | --- |
| Knox (Brawler) | Fists | Four-hit combos, strongest punches, lots of HP | Ground Pound: leap and slam, floors everyone nearby |
| Rin (Cutter) | Box Cutter | Fast slashes, double jump | Surge: one slash hits every enemy on screen |
| Mara (Loader) | Sledgehammer | Slow, huge swings; light hits don't interrupt her | Forklift Spin: spin and move while shredding anything close |
| Vee (Slinger) | Knife | Fastest; THROW sends knives from an endless supply | Knife Fan: ten knives in a fan, both ways |

## Weapons, throwing and fists

- Crates break open into food or weapons: lead pipe, crowbar, knife, sledgehammer. Enemies drop theirs too.
- **THROW** picks up whatever you're standing on. With nothing nearby, it throws your weapon, which knocks enemies down and lands on the floor for you to pick back up. Without a weapon you fight with your fists.
- Picked-up weapons break after a number of hits (pips next to the weapon name). Your class weapon never breaks.

## Upgrades

After each shift you get a pick of three upgrades: more HP, damage, speed, attack speed, special charge, healing on KOs, throw damage, weapon durability, an extra life, a wider parry, damage reduction or an extra jump.

## Controls

| Action | Touch | Keyboard |
| --- | --- | --- |
| Move (all directions) | Left-side stick | WASD / arrows |
| Attack (tap for combo, hold for heavy) | ATTACK | J (K = heavy) |
| Jump | JUMP | Space |
| Dash (dodges through attacks; attack mid-dash to lunge) | DASH | Shift |
| Grab / eat / throw | THROW | E |
| Parry (right before a hit lands; also reflects bullets) | PARRY | L |
| Special (when the meter is full) | SPECIAL | U |
| Pause / mute | buttons top right | P / M |

## Tips

- Enemies flash red right before they attack. That's your cue to parry or dash.
- Gunners only shoot in a straight line, so step up or down out of their aim.
- The Foreman's slam sends a shockwave ring across the floor. Jump or dash through it. He's open for a moment after a charge or a slam.
- Below 25% HP you go into Last Stand: more damage and faster special charge.
