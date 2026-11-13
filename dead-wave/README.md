# Dead Wave

A top-down zombie survival shooter for phones, in an HD-2D pixel style. The town fell at dusk. Scavenge guns and ammo, board up a house, and outlast wave after wave as the dead get tougher and faster.

Open `index.html` in any phone or desktop browser. No install or build step.

## Your survivor

Tap **Customize your survivor** on the title screen to dress your character. The tabs are:

- **Body:** 12 ready-made presets, build, skin tone and eye color
- **Hair:** about 85 hair styles, 26 hair colors, 13 beards and mustaches, eyebrows
- **Top:** 33 shirts, 17 jackets, vests and armor pieces, arm gear, capes
- **Bottom:** 18 pants, shorts and skirts, 13 kinds of shoes, belts
- **Head:** 42 hats, hoods and helmets, glasses and face masks, scarves and ties
- **Gear:** backpacks and packs, bandages and wounds

Most items have their own color row, usually with 20 or more colors. **Randomize** builds a random outfit. Your look is saved on your device, and teammates see it in co-op.

## Character art (PNG sprite sheets)

Characters can be drawn from PNG sprite sheets with 4-direction idle, walk, run, attack, hurt, roll and death animations. Put the sheets in `sprites/` and describe them in `sprites/manifest.json`. See [sprites/README.md](sprites/README.md) for the layout. The game ships with characters built from the Liberated Pixel Cup (LPC) art: 8 survivors to choose from, 6 walker zombies, and a runner, spitter, bloater, brute and boss. Credits and licenses are in [sprites/lpc/CREDITS.md](sprites/lpc/CREDITS.md). If the sheets can't load (for example when opening the file directly), the game falls back to its built-in art.

## Controls

The game works in portrait and landscape. Tap **Controls** on the title screen or in the pause menu to change auto-aim (Off, Assist, Full), switch to left-handed, fix the stick in place, turn push-to-sprint off, or resize the buttons.

| Phone | Keyboard / mouse | Controller | Action |
|---|---|---|---|
| Move stick (left side) | WASD | Left stick | Move |
| Push the stick all the way | Hold Shift | Hold LT | Sprint |
| Hold FIRE | Mouse + left click | RT | Shoot. On phone and controller it locks onto the nearest zombie (red brackets) |
| Drag FIRE | Mouse | Right stick | Aim by hand |
| RELOAD, or tap your gun | R | X | Reload |
| ROLL | Space | A | Dodge roll (brief invincibility) |
| SHOVE | F or right click | B | Push zombies off you and stun them |
| Swap button | Q | Y | Swap between pistol and your other gun |
| Grenade button | G | LB | Throw a grenade |
| Orange button when it appears | E | RB | Board up a door or window, or swap for a gun on the ground |
| Pause button | Esc | Start | Pause |

Roll, shove and sprint use stamina (yellow bar).

## Online co-op (up to 4 players)

Co-op uses the same connection setup as Clocked In: PeerJS's public matchmaking server, a 5-letter room code, and the same STUN/TURN relay servers so phones on mobile data can still connect.

1. One player taps **Host co-op** and shares the code (or taps **Copy invite link**; the link opens straight into the join screen).
2. Friends tap **Join co-op** and type the code. They can join before the game starts or in the middle of a run.
3. The host taps **Start game** (or PLAY).

The host's game runs the zombies, waves, loot and barricades. Every player moves and shoots on their own phone, so controls feel instant. XP, coins and wave rewards are shared, and each player picks their own perks between waves. If you go down, you lose everything but your pistol: your other gun and its ammo, your planks and your grenades drop where you fell, so you or a teammate can grab them back. You come back with half health when your team clears the wave. The run ends when everyone is down.

Any player can pause, and it pauses the game for everyone. Anyone can resume it.

Co-op only works when the game is opened from its own web address, such as the GitHub Pages link. Previews that block WebRTC, like the Claude artifact viewer, can only play solo.

## Modes

Pick a mode on the title screen with the arrows next to **Mode**. In co-op, the host's mode is used.

- **Survival:** the original. Board up and outlast wave after wave, with the Abomination every 5th wave.
- **Objectives:** a story run with one of three scenarios: *Last Ride* (get an old pickup running), *Mayday* (fix a radio and call in a helicopter) or *Cold Chain* (get medicine to the evac truck). Each is a chain of tasks spread over the town: find items, switch things on by standing at them, and hold a spot while zombies swarm. It ends with an extraction you have to hold to escape. A gold arrow and minimap markers show where to go. Zombies roam all the time and get tougher as you progress. Downed players come back after 40 seconds or when the team finishes a task.
- **Horde:** no breaks. One endless stream that keeps getting bigger and faster, a supply drop every 2 minutes, and the Abomination at every 5th danger level. Downed players come back after 30 seconds if anyone is still standing. Your best time is saved.

## How it plays

- **Town:** a new town every run with roads, about 50 houses, a store, a park and a parking lot, plus wrecked and burning cars, streetlights and a minimap. You see through a flashlight in the dark.
- **Guns and ammo:** the pistol has unlimited reserve ammo. The SMG, shotgun, assault rifle and hunting rifle have limited ammo; when one runs dry you drop back to the pistol. Zombies drop guns, ammo boxes, planks, med kits and grenades, and houses restock loot each wave.
- **Barricades:** walk into a house and the roof fades away. Stand by a door or window and board it up (up to 3 boards, 1 plank each). Zombies pathfind to you, hammer the boards down and climb in through windows. Brutes break boards fast, and the boss smashes them in one hit.
- **Waves:** you get 25 seconds to prepare, and 20 seconds between waves. Every 5th wave brings the Abomination.
- **Perks:** collect gems to level up and choose perks such as Hollow Points, Fast Hands, Ammo Belt, Carpenter, Strong Arm and Marathon.
- **Armory:** coins carry over between runs. Spend them on permanent upgrades and to unlock a starting gun.

Progress is saved in the browser.
