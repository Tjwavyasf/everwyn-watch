# Dead Wave

A top-down zombie survival shooter for phones, in an HD-2D pixel style. The town fell at dusk. Scavenge guns and ammo, board up a house, and outlast wave after wave as the dead get tougher and faster.

Open `index.html` in any phone or desktop browser. No install or build step.

## Controls

| Phone | Keyboard / mouse | Action |
|---|---|---|
| Drag on the left half | WASD | Move |
| Drag on the right half (hold still to auto-aim) | Mouse + left click | Aim and shoot |
| Tap your gun | R | Reload |
| Swap button | Q | Swap between pistol and your other gun |
| Grenade button | G | Throw a grenade |
| ROLL | Space | Dodge roll (brief invincibility) |
| SHOVE | F or right click | Push zombies off you and stun them |
| RUN | Hold Shift | Sprint |
| Orange button when it appears | E | Board up a door or window, or swap for a gun on the ground |

Roll, shove and sprint use stamina (yellow bar).

## Online co-op (up to 4 players)

Co-op uses the same connection setup as Clocked In: PeerJS's public matchmaking server, a 5-letter room code, and the same STUN/TURN relay servers so phones on mobile data can still connect.

1. One player taps **Host co-op** and shares the code (or taps **Copy invite link**; the link opens straight into the join screen).
2. Friends tap **Join co-op** and type the code. They can join before the game starts or in the middle of a run.
3. The host taps **Start game** (or PLAY).

The host's game runs the zombies, waves, loot and barricades. Every player moves and shoots on their own phone, so controls feel instant. XP, coins and wave rewards are shared, and each player picks their own perks between waves. If you go down, you come back with half health when your team clears the wave. The run ends when everyone is down.

Co-op only works when the game is opened from its own web address, such as the GitHub Pages link. Previews that block WebRTC, like the Claude artifact viewer, can only play solo.

## How it plays

- **Town:** a new town every run with roads, about 50 houses, a store, a park and a parking lot, plus wrecked and burning cars, streetlights and a minimap. You see through a flashlight in the dark.
- **Guns and ammo:** the pistol has unlimited reserve ammo. The SMG, shotgun, assault rifle and hunting rifle have limited ammo; when one runs dry you drop back to the pistol. Zombies drop guns, ammo boxes, planks, med kits and grenades, and houses restock loot each wave.
- **Barricades:** walk into a house and the roof fades away. Stand by a door or window and board it up (up to 3 boards, 1 plank each). Zombies pathfind to you, hammer the boards down and climb in through windows. Brutes break boards fast, and the boss smashes them in one hit.
- **Waves:** you get 25 seconds to prepare, and 20 seconds between waves. Every 5th wave brings the Abomination.
- **Perks:** collect gems to level up and choose perks such as Hollow Points, Fast Hands, Ammo Belt, Carpenter, Strong Arm and Marathon.
- **Armory:** coins carry over between runs. Spend them on permanent upgrades and to unlock a starting gun.

Progress is saved in the browser.
