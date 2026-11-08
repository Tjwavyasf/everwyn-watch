# Everwyn App (formerly Everwyn Watch)

The mod id, config file names and data folders are still `everwyn-watch` so existing servers keep their settings.

A server-side Fabric 1.20.1 mod that runs a small web app you can open on your phone:

- **Map** – live player positions on a top-down map drawn from your actual world (vanilla map colors), Overworld / Nether / End, pinch to zoom, tap a player.
- **3D map** – tap **Tabletop 3D** above the map for a tilted, rotatable diorama of the real terrain (drag to move, pinch to zoom, twist to turn, Tilt for top-down), or **Free cam** to fly around it (left thumb moves, drag to look, ▲▼ up/down; WASD + mouse on a computer). Players, waypoints, deaths and spawn show up in 3D too; hold on the ground for the usual waypoint/care-package menu. The 3D engine (three.js) loads from the internet the first time you open it.
- **Real blocks in 3D** – in Tabletop 3D and Free cam, the land near the camera is drawn block by block with Minecraft's own models and textures (modded blocks too), with biome-colored grass, leaves and water. Players show as their real skins and move live: walking, jumping, crouching, swimming, flying. Mobs show as simple shapes in their spawn-egg colors. Slide **See inside** to slice the world at any height and look into builds and caves. The **Real/Simple** button switches back to the lighter terrain.
- **Follow camera and underground** – tap a player's name in 3D to follow them: **Behind / Front / Side / Top / Free**, drag to circle around them, pinch or −/+ to get close or far. It follows them into caves and mines: underground the app draws the blocks around them (rock in front of the camera turns see-through). Free cam can fly underground too.
- **Real mob models** – zombies, husks, drowned, skeletons, strays, wither skeletons, creepers, cows, mooshrooms, pigs, sheep (with wool) and chickens show with their real models and textures in 3D and walk/turn their heads. Other mobs are simple shapes in their spawn-egg colors.
- **Fantasy look** – forest-green and gold theme with vines; the inventory looks like a Minecraft inventory with each player's own skin.
- **RPG armor levels** – Server tab → RPG armor levels: builds a straight weakest-to-strongest Defense ladder for the RPG Level mod from every armor on the server (leather lowest, Metallurgium highest, even steps or by strength), shows a preview, and on Apply backs up `config/rpglevel/restrictions`, takes the old armor equip locks out of every restriction file and writes the ladder into `restrictions/minecraft/defense.json`. Restart afterwards.
- **Achievements** – a player's panel (or offline profile) → **Achievements**: every advancement tab (Story, Nether, The End, Adventure, Husbandry, modded tabs) with icons, done/not done, when, and progress counts.
- **Send to me** – in the inventory editor, hold someone's item → **Send to me** puts it in your own in-game inventory (asks your Minecraft name once). **My inventory** tab shows yours next to theirs.
- **Inventory editor** – a player's panel → **Open inventory**: a Minecraft-style screen with armor, offhand, **trinket slots** (Trinkets mod), main inventory and hotbar. Tap an item, then tap where it goes. The top half switches between their **ender chest**, **another player** (move stolen items straight back), **one of their deaths** (copy items back; the backup stays) or a **chest/container** (map: hold a spot → Open the chest/container here). Shulker boxes, bundles and backpacks show what's inside. Works for offline players too (not trinkets). Death backups now include trinkets and give them back into their slots.
- **Spawn mobs and pets** – hold a spot on the map → **Spawn a mob here**, or a player → **Spawn a mob / pet**. Search every mob in the pack, pick how many, a name tag, baby, and for wolves, cats, horses, parrots (and modded tameables) **Tame it** for any player. Tap a wild tameable mob on the map → **Tame it for…**, or a pet → **Give it to someone else**.
- **Schematic placer** – hold a spot on the map → **Place schematic here**, pick a `.nbt` structure file (structure block / Create format) and a rotation. **Undo last schematic** puts back what was there. The area must be loaded.
- **Crash reports from players' games** – add the small **Everwyn Crash Reporter** mod (download it from the app: Server tab › Crash reports › Set up for players) and its config file to your modpack. When a player's game crashes, or won't start because of incompatible mods, the report comes to the app with the root cause and the mods involved, plus a phone/Discord alert. The server's own crash reports show up there too. Source: `crash-reporter/`.
- **Players** – health, food, XP, origin (Origins mod), every scoreboard value (RPG levels, kills…), full inventory and armor.
- **Feed** – chat, joins/leaves, deaths, advancements, and a log of admin actions.
- **Admin** – message, heal, teleport (to a player or coordinates), change gamemode, kick, broadcast.
- **Waypoints** – hold a spot on the map (or tap "Waypoint here" on a player) to save one. Tap it to send it to players: Xaero's Minimap shows a chat message with an **[Add]** button that puts it on their Xaero's map.
- **Deaths** – each player's last death spot shows as a red X on the map and in their profile.

- **Care packages** – tap a waypoint (or "Care package" on a player), pick items from every item in the pack, and a chest drops there with a lightning strike.
- **"mob wave go"** – any player can type `mob wave go` in chat to start a random challenge around them: 5–10 waves of random hostile mobs (vanilla and modded) that get stronger each wave (Lv 1–5: name tag, armor, Speed/Strength/Resistance), with random loot that's better for longer challenges. One at a time per player, 10-minute cooldown (`chatMobWaveCooldownMin`), turn off with `"chatMobWaves": false` in config/everwyn-watch.json.
- **Mob wave challenges** – tap a waypoint → "Mob wave challenge here". Pick up to 5 waves of hostile mobs (vanilla or modded), a time limit and the loot. It starts when a player walks within 16 blocks. Beat every wave → loot chest appears. A player dies, time runs out, or everyone leaves → "Challenge failed" and the mobs vanish.

- **Real item icons** – the server downloads Minecraft's own textures from Mojang once (about 25 MB) and draws every item and block icon, modded ones included.
- **Player skins** – everyone shows up as their own face on the map and in lists.
- **More admin tools** – feed, give items, clear effects, send to spawn, ban (timed or forever) and unban, kill, smite, clear inventory, and time/weather from the Worlds list.
- **Death vault** – every death's full inventory is backed up the moment it happens, before YIGD makes a grave. Open the Deaths tab, pick a death, and give everything back into the same slots, even if the grave is long gone. If a YIGD grave is still there, the app warns you so players don't get double items.
- **Push notifications** – tap the bell, install the free ntfy app, paste your topic, and get alerts when people join (plus optional leave, death and challenge alerts).

- **Grief investigation (Ledger)** – with the Ledger mod installed, hold a spot on the map → "Who changed this area?" to see who broke, placed or took what, filter by player and time, and undo it.
- **Movement trails** – see where any player has been over the last 1, 6 or 24 hours as a line on the map.
- **Helper logins** – give helpers their own password (helper, admin or viewer). Every action is written to the activity log with who did it.
- **Lag monitor** – TPS, memory and entity charts, a lag finder that shows the busiest chunks (with buttons to clear dropped items or hostile mobs), and alerts when the server lags, starts, stops or crashes.
- **Leaderboards** – playtime, kills, deaths, blocks mined, distance, plus every scoreboard (RPG levels), all time or this week. Optional public page at `/board` to share.
- **Scheduled events** – automatic mob waves, care package drops, chat announcements and restart warnings on set days and times. (For the actual restart, add a matching Restart schedule in Bisect's Schedules tab.)
- **Offline players** – look up anyone who's ever joined: their inventory, ender chest, stats and where they logged out.
- **Discord** – paste a channel webhook in the bell menu to post chat, joins, deaths, challenges and server status to Discord.

Waypoints and deaths are saved in `config/everwyn-watch-markers.json`; placed challenges in `config/everwyn-watch-challenges.json`. Saved map images live in your world folder under `everwyn-watch/tiles`.

If players tap [Add] on a shared waypoint and nothing happens, open the bell menu in the app and flip "Use the newer Xaero format", then send it again.

Server-only: players don't need it installed. It never loads or generates chunks; the map fills in as people explore.

## 1. Get the jar

The easiest way (works from a phone):

1. Make a free GitHub repo and upload this whole folder to it.
2. Open the repo's **Actions** tab. The "Build mod jar" run starts on its own (or press **Run workflow**).
3. When it's green, open the run and download **everwyn-watch-jar**. Inside is `everwyn-watch-1.0.0.jar`.

On a PC with Java 17 and Gradle 8.10+: run `gradle build` and take the jar from `build/libs/`.

## 2. Install on your host

1. Upload the jar to the server's `mods` folder (Fabric API must be there too, which your pack already has).
2. In your host's panel, add an extra port (Apex/Bisect/etc. call it "Additional ports" or "Port allocation"). Note the number.
3. Start the server once, then stop it. Open `config/everwyn-watch.json`:
   - set `"port"` to the port your host gave you
   - copy the `"token"` – that's your app password
4. Start the server again.

## 3. Open the app

Go to `http://YOUR-SERVER-IP:PORT` on your phone, paste the token, then **Share → Add to Home Screen** so it opens like an app.

## Config (`config/everwyn-watch.json`)

| Setting | What it does |
|---|---|
| `port` | Port the dashboard uses |
| `token` | App password. Change it to log everyone out |
| `serverName` | Name shown at the top of the app |
| `showInventory` | Show inventories in the app |
| `allowAdminActions` | Turn the admin buttons on/off |
| `maxTileRendersPerSecond` | Lower this if the map ever costs TPS |

## Safety notes

- Anyone with the token can kick and teleport people, so keep it private. Wrong guesses get blocked after 10 tries.
- The connection is plain `http`, so avoid logging in on public Wi-Fi.
- Let your players know the server shows their location and inventory to admins.
