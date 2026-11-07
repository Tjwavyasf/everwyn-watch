package dev.mrwavy.everwynwatch;

import net.minecraft.server.MinecraftServer;

/** Everything the web server, actions and schedules need, in one place. */
final class Services {
    MinecraftServer server;
    WatchConfig cfg;
    Snapshotter snapshotter;
    EventLog events;
    TileRenderer tiles;
    Markers markers;
    Challenges challenges;
    DeathVault vault;
    Accounts accounts;
    Trails trails;
    PerfMonitor perf;
    MobGuard mobGuard;
    Leaderboards boards;
    Scheduler scheduler;
    RestartController restart;
    LedgerBridge ledger;
    CrashReports crashes;
    final SchematicPlacer schematics = new SchematicPlacer();
}
