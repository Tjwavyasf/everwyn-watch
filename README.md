# Everwyn Watch

A server-side Fabric 1.20.1 mod that runs a small web app you can open on your phone:

- **Map** – live player positions on a top-down map drawn from your actual world (vanilla map colors), Overworld / Nether / End, pinch to zoom, tap a player.
- **Players** – health, food, XP, origin (Origins mod), every scoreboard value (RPG levels, kills…), full inventory and armor.
- **Feed** – chat, joins/leaves, deaths, advancements, and a log of admin actions.
- **Admin** – message, heal, teleport (to a player or coordinates), change gamemode, kick, broadcast.
- **Waypoints** – hold a spot on the map (or tap "Waypoint here" on a player) to save one. Tap it to send it to players: Xaero's Minimap shows a chat message with an **[Add]** button that puts it on their Xaero's map.
- **Deaths** – each player's last death spot shows as a red X on the map and in their profile.

- **Care packages** – tap a waypoint (or "Care package" on a player), pick items from every item in the pack, and a chest drops there with a lightning strike.
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
