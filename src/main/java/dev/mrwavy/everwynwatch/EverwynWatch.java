package dev.mrwavy.everwynwatch;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Everwyn Watch: a server-side mod that hosts a small web dashboard
 * (map, players, chat feed, admin tools) you can open on your phone.
 */
public class EverwynWatch implements DedicatedServerModInitializer {
    public static final String MOD_ID = "everwyn-watch";
    public static final Logger LOG = LoggerFactory.getLogger("EverwynWatch");

    private static WatchConfig CONFIG;

    public static WatchConfig config() {
        return CONFIG;
    }

    private final Services s = new Services();
    private WebServer web;
    private int tickCounter = 0;
    private long tickStartNanos = 0;

    @Override
    public void onInitializeServer() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path cacheDir = configDir.resolve("everwyn-watch-cache");
        WatchConfig config = WatchConfig.load(configDir.resolve("everwyn-watch.json"));
        CONFIG = config;
        s.cfg = config;
        s.events = new EventLog(config.eventBufferSize);
        s.markers = new Markers(configDir.resolve("everwyn-watch-markers.json"));
        s.accounts = new Accounts(config, configDir.resolve("everwyn-watch-audit.json"));
        s.trails = new Trails(cacheDir.resolve("trails.json"));
        s.perf = new PerfMonitor(config);
        s.mobGuard = new MobGuard(config);
        s.crashes = new CrashReports(configDir.resolve("everwyn-watch-crashes"));
        IconRenderer.init(configDir);

        // Copy the inventory right before a player dies (before graves/drops take it).
        // Runs in an early phase so other mods' "before death" handlers can't empty or skip it first.
        Identifier beforeOthers = new Identifier("everwyn-watch", "copy_inventory_first");
        ServerLivingEntityEvents.ALLOW_DEATH.addPhaseOrdering(beforeOthers, Event.DEFAULT_PHASE);
        ServerLivingEntityEvents.ALLOW_DEATH.register(beforeOthers, (entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity p && s.vault != null) {
                try {
                    s.vault.snapshot(p);
                } catch (Exception e) {
                    LOG.error("Everwyn Watch couldn't back up {}'s inventory", p.getGameProfile().getName(), e);
                }
            }
            return true;
        });

        // Join / leave alerts
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.getPlayer();
            String name = p.getGameProfile().getName();
            int online = server.getPlayerManager().getCurrentPlayerCount();
            boolean first = p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.LEAVE_GAME)) == 0;
            if (first) Notifier.send("firstJoin", "New player: " + name, name + " joined " + config.serverName + " for the first time! (" + online + " online)", "tada");
            else Notifier.send("join", name + " joined", name + " is on " + config.serverName + " (" + online + " online)", "video_game");
            Discord.send("join", (first ? ":tada: **" + Discord.clean(name) + "** joined for the first time!" : ":green_circle: **" + Discord.clean(name) + "** joined")
                    + " (" + online + " online)", null, null);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            String name = handler.getPlayer().getGameProfile().getName();
            int online = Math.max(0, server.getPlayerManager().getCurrentPlayerCount() - 1);
            Notifier.send("leave", name + " left", name + " left " + config.serverName + " (" + online + " online)", "wave");
            Discord.send("join", ":red_circle: **" + Discord.clean(name) + "** left (" + online + " online)", null, null);
        });

        // Deaths
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayerEntity p)) return;
            String name = p.getGameProfile().getName();
            String msg;
            try {
                msg = source.getDeathMessage(p).getString();
            } catch (Exception e) {
                msg = name + " died";
            }
            final String message = msg;
            // Save the items first and keep every step separate, so one problem can't lose the death
            if (s.vault != null) safe("death vault (" + name + ")", () -> s.vault.commit(p, message));
            safe("death marker", () -> s.markers.recordDeath(name, p.getWorld().getRegistryKey().getValue().toString(), p.getX(), p.getY(), p.getZ(), message));
            if (s.challenges != null) safe("challenge death", () -> s.challenges.onPlayerDeath(p));
            Notifier.send("death", p.getGameProfile().getName() + " died", msg, "skull");
            Discord.send("death", ":skull: " + Discord.clean(msg), null, null);
        });

        // Clean up leftover wave mobs after a crash or restart
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (s.challenges != null) s.challenges.onEntityLoad(entity);
        });

        // Chat
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String text = message.getContent().getString();
            s.events.add("chat", sender.getGameProfile().getName(), text);
            // "mob wave go": a random mob wave challenge around the player
            String cmd = text.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z ]", "").replaceAll("\\s+", " ").trim();
            if (config.chatMobWaves && s.challenges != null && (cmd.equals("mob wave go") || cmd.equals("mobwave go"))) {
                sender.getServer().execute(() -> {
                    try {
                        String msg = s.challenges.randomWave(sender);
                        s.events.add("event", sender.getGameProfile().getName(), msg);
                    } catch (IllegalArgumentException e) {
                        sender.sendMessage(Text.literal("[Everwyn] ").formatted(Formatting.GOLD).append(Text.literal(e.getMessage()).formatted(Formatting.YELLOW)));
                    } catch (Exception e) {
                        LOG.error("Everwyn mob wave go failed", e);
                    }
                });
            }
            Discord.send("chat", Discord.clean(text), sender.getGameProfile().getName(), sender.getUuidAsString());
        });
        ServerMessageEvents.COMMAND_MESSAGE.register((message, source, params) ->
                s.events.add("chat", source.getName(), message.getContent().getString()));
        ServerMessageEvents.GAME_MESSAGE.register((server, message, overlay) -> {
            if (!overlay) s.events.addGameMessage(message);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            s.server = server;
            VoiceBridge.server = server;
            s.vault = new DeathVault(server, config);
            Thread crashImport = new Thread(s.crashes::importServerReports, "EverwynWatch-CrashImport");
            crashImport.setDaemon(true);
            crashImport.start();
            s.challenges = new Challenges(server, s.events, configDir.resolve("everwyn-watch-challenges.json"));
            s.ledger = new LedgerBridge(server);
            s.boards = new Leaderboards(server, cacheDir.resolve("stats-history.json"));
            s.snapshotter = new Snapshotter(server, config, s.markers, s.challenges);
            s.scheduler = new Scheduler(s, configDir.resolve("everwyn-watch-schedule.json"));
            s.restart = new RestartController(s);
            s.snapshotter.services = s;
            s.tiles = new TileRenderer(server, config);
            s.snapshotter.refresh();
            try {
                web = new WebServer(s);
                web.start();
                LOG.info("Everwyn Watch dashboard running on port {}. Your login token is in config/everwyn-watch.json", config.port);
            } catch (IOException e) {
                LOG.error("Everwyn Watch could not start on port {}. Is the port open on your host?", config.port, e);
            }

            // Crash detection: this file exists only while the server is running
            Path flag = cacheDir.resolve("running.flag");
            boolean crashed = Files.exists(flag);
            try {
                Files.createDirectories(cacheDir);
                Files.writeString(flag, Long.toString(System.currentTimeMillis()));
            } catch (Exception ignored) {
                // can't tell next time
            }
            if (crashed) {
                Notifier.send("server", config.serverName + " restarted after a crash", "The server didn't shut down cleanly last time. It's back up now.", "rotating_light");
                Discord.send("server", ":rotating_light: The server crashed and is back online.", null, null);
            } else {
                Notifier.send("server", config.serverName + " is online", "The server finished starting.", "white_check_mark");
                Discord.send("server", ":white_check_mark: The server is online.", null, null);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (web != null) web.stop();
            web = null;
            if (s.challenges != null) s.challenges.shutdown();
            s.trails.save();
            Notifier.send("server", config.serverName + " is shutting down", "The server is stopping (restart or shutdown).", "stop_sign");
            Discord.send("server", ":octagonal_sign: The server is shutting down.", null, null);
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            try {
                Files.deleteIfExists(cacheDir.resolve("running.flag"));
            } catch (Exception ignored) {
                // next start will think it crashed
            }
        });

        ServerTickEvents.START_SERVER_TICK.register(server -> tickStartNanos = System.nanoTime());

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (s.snapshotter == null) return;
            s.snapshotter.recordTick((System.nanoTime() - tickStartNanos) / 1_000_000.0);
            safe("challenges", s.challenges::tick);
            if (s.vault != null) safe("death backup", s.vault::tick);
            safe("trails", () -> s.trails.tick(server));
            safe("perf", () -> s.perf.tick(server, s.snapshotter.mspt(), s.snapshotter.tps()));
            safe("mobGuard", () -> s.mobGuard.tick(server, s.snapshotter.tps()));
            safe("leaderboards", s.boards::tick);
            safe("schedules", s.scheduler::tick);
            safe("restart", s.restart::tick);
            safe("voice", () -> VoiceBridge.tick(server));
            if (++tickCounter >= Math.max(5, config.snapshotIntervalTicks)) {
                tickCounter = 0;
                safe("snapshot", s.snapshotter::refresh);
            }
        });
    }

    /** A bug in one feature should never crash the server. */
    private static void safe(String what, Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            LOG.error("Everwyn Watch {} error", what, e);
        }
    }
}
