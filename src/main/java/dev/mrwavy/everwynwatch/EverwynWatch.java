package dev.mrwavy.everwynwatch;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.stat.Stats;
import net.minecraft.server.network.ServerPlayerEntity;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Everwyn Watch: a server-side mod that hosts a small web dashboard
 * (map, players, chat feed, admin tools) you can open on your phone.
 */
public class EverwynWatch implements DedicatedServerModInitializer {
    public static final String MOD_ID = "everwyn-watch";
    public static final Logger LOG = LoggerFactory.getLogger("EverwynWatch");

    private static WatchConfig CONFIG;
    private WatchConfig config;

    public static WatchConfig config() {
        return CONFIG;
    }
    private EventLog events;
    private Markers markers;
    private Challenges challenges;
    private DeathVault vault;
    private Snapshotter snapshotter;
    private TileRenderer tiles;
    private WebServer web;

    private int tickCounter = 0;
    private long tickStartNanos = 0;

    @Override
    public void onInitializeServer() {
        config = WatchConfig.load(FabricLoader.getInstance().getConfigDir().resolve("everwyn-watch.json"));
        CONFIG = config;
        events = new EventLog(config.eventBufferSize);
        markers = new Markers(FabricLoader.getInstance().getConfigDir().resolve("everwyn-watch-markers.json"));
        IconRenderer.init(FabricLoader.getInstance().getConfigDir());

        // Copy the inventory right before a player dies (before graves/drops take it)
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity p && vault != null) {
                try {
                    vault.snapshot(p);
                } catch (Exception e) {
                    LOG.error("Everwyn Watch couldn't back up {}'s inventory", p.getGameProfile().getName(), e);
                }
            }
            return true;
        });

        // Push notifications when people join / leave
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.getPlayer();
            String name = p.getGameProfile().getName();
            int online = server.getPlayerManager().getCurrentPlayerCount();
            boolean first = p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.LEAVE_GAME)) == 0;
            if (first) Notifier.send("firstJoin", "New player: " + name, name + " joined " + config.serverName + " for the first time! (" + online + " online)", "tada");
            else Notifier.send("join", name + " joined", name + " is on " + config.serverName + " (" + online + " online)", "video_game");
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            String name = handler.getPlayer().getGameProfile().getName();
            int online = Math.max(0, server.getPlayerManager().getCurrentPlayerCount() - 1);
            Notifier.send("leave", name + " left", name + " left " + config.serverName + " (" + online + " online)", "wave");
        });

        // Remember where each player last died
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity p) {
                markers.recordDeath(p.getGameProfile().getName(),
                        p.getWorld().getRegistryKey().getValue().toString(),
                        p.getX(), p.getY(), p.getZ(),
                        source.getDeathMessage(p).getString());
                if (challenges != null) challenges.onPlayerDeath(p);
                String msg = source.getDeathMessage(p).getString();
                if (vault != null) {
                    try {
                        vault.commit(p, msg);
                    } catch (Exception e) {
                        LOG.error("Everwyn Watch couldn't save {}'s death", p.getGameProfile().getName(), e);
                    }
                }
                Notifier.send("death", p.getGameProfile().getName() + " died", msg, "skull");
            }
        });

        // Clean up leftover wave mobs after a crash or restart
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (challenges != null) challenges.onEntityLoad(entity);
        });

        // Player chat
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) ->
                events.add("chat", sender.getGameProfile().getName(), message.getContent().getString()));

        // /say, /me and similar
        ServerMessageEvents.COMMAND_MESSAGE.register((message, source, params) ->
                events.add("chat", source.getName(), message.getContent().getString()));

        // Joins, leaves, deaths, advancements and other server broadcasts
        ServerMessageEvents.GAME_MESSAGE.register((server, message, overlay) -> {
            if (!overlay) events.addGameMessage(message);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            vault = new DeathVault(server, config);
            challenges = new Challenges(server, events, FabricLoader.getInstance().getConfigDir().resolve("everwyn-watch-challenges.json"));
            snapshotter = new Snapshotter(server, config, markers, challenges);
            tiles = new TileRenderer(server, config);
            snapshotter.refresh();
            try {
                web = new WebServer(server, config, snapshotter, events, tiles, markers, challenges, vault);
                web.start();
                LOG.info("Everwyn Watch dashboard running on port {}. Your login token is in config/everwyn-watch.json", config.port);
            } catch (IOException e) {
                LOG.error("Everwyn Watch could not start on port {}. Is the port open on your host?", config.port, e);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (web != null) web.stop();
            web = null;
            if (challenges != null) challenges.shutdown();
        });

        ServerTickEvents.START_SERVER_TICK.register(server -> tickStartNanos = System.nanoTime());

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (snapshotter == null) return;
            challenges.tick();
            snapshotter.recordTick((System.nanoTime() - tickStartNanos) / 1_000_000.0);
            if (++tickCounter >= Math.max(5, config.snapshotIntervalTicks)) {
                tickCounter = 0;
                snapshotter.refresh();
            }
        });
    }
}
