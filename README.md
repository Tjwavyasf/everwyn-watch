# Everwyn Watch

A server-side Fabric 1.20.1 mod that runs a small web app you can open on your phone:

- **Map** – live player positions on a top-down map drawn from your actual world (vanilla map colors), Overworld / Nether / End, pinch to zoom, tap a player.
- **Players** – health, food, XP, origin (Origins mod), every scoreboard value (RPG levels, kills…), full inventory and armor.
- **Feed** – chat, joins/leaves, deaths, advancements, and a log of admin actions.
- **Admin** – message, heal, teleport (to a player or coordinates), change gamemode, kick, broadcast.
- **Waypoints** – hold a spot on the map (or tap "Waypoint here" on a player) to save one. Tap it to send it to players: Xaero's Minimap shows a chat message with an **[Add]** button that puts it on their Xaero's map.
- **Deaths** – each player's last death spot shows as a red X on the map and in their profile.

Waypoints and deaths are saved in `config/everwyn-watch-markers.json`.

If a shared waypoint lands in the wrong world on players' Xaero's maps, set `"xaeroUnderscoreFormat": false` in the config and restart.

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
