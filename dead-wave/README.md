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

## Online co-op (up to 4 players, 10 in Infection)

Co-op uses the same connection setup as Clocked In: PeerJS's public matchmaking server, a 5-letter room code, and the same STUN/TURN relay servers so phones on mobile data can still connect.

1. One player taps **Host co-op** and shares the code (or taps **Copy invite link**; the link opens straight into the join screen).
2. Friends tap **Join co-op** and type the code. They can join before the game starts or in the middle of a run.
3. The host taps **Start game** (or PLAY).

The host's game runs the zombies, waves, loot and barricades. Every player moves and shoots on their own phone, so controls feel instant. XP, coins and wave rewards are shared, and each player picks their own perks between waves. If you go down, you lose everything but your pistol: your other gun and its ammo, your planks and your grenades drop where you fell, so you or a teammate can grab them back. You come back with half health when your team clears the wave. The run ends when everyone is down.

Any player can pause, and it pauses the game for everyone. Anyone can resume it.

Co-op only works when the game is opened from its own web address, such as the GitHub Pages link. Previews that block WebRTC, like the Claude artifact viewer, can only play solo.

## Enemies and maps

Pick them on the title screen (or in the lobby's **Match settings**). They work with every mode.

- **Enemies:**
  - **Zombies:** walkers, runners, spitters, bloaters and brutes. Boss: the Abomination.
  - **Robots:** drones, hunters, gunners that shoot lasers, bombers and treaded tanks. Boss: the Warmech. A bit tougher and slower.
  - **Monsters:** giant bugs: crawlers, skitters, acid spitters, bile sacs and armored chargers. Boss: the Brood Queen. A bit weaker and faster.
- **Maps:**
  - **Suburbs:** the original town.
  - **Abandoned City:** grey ruins, burning wrecks, dead trees and very few working lights.
  - **Cyberpunk City:** dark towers, neon streetlights and rain.
  - **Forest:** dirt trails through thick woods, a few cabins and campfires.
- **Sci-fi guns:** the Laser Rifle, Arc Gun (lightning jumps to 3 more targets), Railgun (goes through 12 enemies) and Plasma Cannon (explodes). They drop three times as often against robots and monsters, and you can unlock them in the Armory.

## Lobby

When you host co-op, tap **ENTER LOBBY**. Everyone who joins drops into a warm-up yard: racks with every gun (they refill), attachments, a first aid kit and a few enemies to shoot. You respawn after 3 seconds and keep your gear. The host's bar at the top has **Invite**, **Match settings** (mode, enemies, map) and **START MATCH**. After a match, the host can tap **Back to the lobby** to bring everyone back. Leave the room from the pause menu.

## Modes

Pick a mode on the title screen with the arrows next to **Mode**. In co-op, the host's mode is used.

- **Survival:** the original. Board up and outlast wave after wave, with the Abomination every 5th wave.
- **Objectives:** a story run with one of three scenarios: *Last Ride* (get an old pickup running), *Mayday* (fix a radio and call in a helicopter) or *Cold Chain* (get medicine to the evac truck). Each is a chain of tasks spread over the town: find items, switch things on by standing at them, and hold a spot while zombies swarm. It ends with an extraction you have to hold to escape. A gold arrow and minimap markers show where to go. Zombies roam all the time and get tougher as you progress. Downed players come back after 40 seconds or when the team finishes a task.
- **Horde:** no breaks. One endless stream that keeps getting bigger and faster, a supply drop every 2 minutes, and the Abomination at every 5th danger level. Downed players come back after 30 seconds if anyone is still standing. Your best time is saved.

### Infection (up to 10 players)

Host a co-op room with the mode set to **Infection** and up to 9 friends can join.

1. Everyone starts together in one house. In a short scene, one random player (patient zero) says they don't feel good and collapses.
2. Everyone else gets **30 seconds** to run.
3. Patient zero turns. Infected players can't be killed, and one touch turns a survivor. Infected players move a bit faster but can't sprint.
4. Survivors have no guns, but **PUSH** (the big button, F or right-click) knocks an infected player back and stuns them for **4 seconds**. Dodge rolls keep you from being touched.
5. Survive **5 minutes** and the survivors win. If everyone is caught, the infected win.

With 4 or more players, one survivor is secretly the **carrier**: only they know, and they turn at 2:30. Each survivor can call one **emergency meeting** per round. The game stops and survivors vote to quarantine someone (or skip). Whoever gets the most votes is out of the round, and everyone learns whether they were the carrier.

Playing Infection solo is a practice round against AI infected. Your minimap only shows you, and names float over every player.

## How it plays

- **Town:** a new town every run with roads, about 50 houses, a store, a park and a parking lot, plus wrecked and burning cars, streetlights and a minimap. You see through a flashlight in the dark.
- **Guns and ammo:** the pistol has unlimited reserve ammo. The other 10 guns have limited ammo: SMG, revolver, shotgun, auto shotgun, assault rifle, marksman rifle, hunting rifle, machine gun, crossbow and grenade launcher. When one runs dry you drop back to the pistol. Zombies drop guns, ammo boxes, planks, med kits, first aid kits, attachments and grenades, and houses restock loot.
- **Attachments:** each gun has five slots: optic (red dot, scope), muzzle (suppressor, compensator, shotgun choke), magazine (extended, drum, speed loader), underbarrel (laser sight, grip) and ammo (hollow rounds, AP rounds). Walk over one and it goes on your gun if the slot is free; if not, the orange button offers a swap. Attachments stay on a gun when you drop or swap it, and the icons show under your ammo count. A suppressor also means fewer extra zombies come for you in Objectives and Horde.
- **First aid kits:** carry up to 3 (H, the red kit button, or D-pad down on a controller). Using one takes 2.5 seconds and heals 60% of your health. Next to a downed teammate it revives them on the spot.
- **Barricades:** walk into a house and the roof fades away. Stand by a door or window and board it up (up to 3 boards, 1 plank each). Zombies pathfind to you, hammer the boards down and climb in through windows. Brutes break boards fast, and the boss smashes them in one hit.
- **Waves:** you get 25 seconds to prepare, and 20 seconds between waves. Every 5th wave brings the Abomination.
- **Perks:** collect gems to level up and choose perks such as Hollow Points, Fast Hands, Ammo Belt, Carpenter, Strong Arm and Marathon.
- **Armory:** coins carry over between runs. Spend them on permanent upgrades and to unlock a starting gun.

Progress is saved in the browser.
