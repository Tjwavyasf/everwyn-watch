# Everwyn App — Next Steps (prioritized)

Only build what the owner asks for. Check the repo state first.

1. **Reconcile:** fetch all branches; newest code is `everwyn-app` (2.0.0). Report any differences from the handoff before editing.
2. **Owner testing of 2.0.0** on the live server: real blocks + mob models in 3D, follow camera/underground, inventory editor (moves, trinkets, offline edits), spawn/tame, schematic placer, crash reporter, Ledger. Fix what they report (ask for screenshots and the server log).
3. **Merge to main (only with permission)** so the GitHub "latest" release has 2.0.0. `everwyn-app` merges on top of main's separate `warehouse-showdown/` game; keep that untouched.
4. Ideas already discussed (not approved yet): more mob models / GeckoLib mobs; always-catch-every-death; block entity models; block lighting.

## Bedrock players (researched 2026-11-08, not built)
Owner wants Bedrock players to join with no mods and see modded textures. Findings:
- **Geyser + Floodgate** let Bedrock players join Java servers, but current Geyser only supports the newest Java version (1.21.x), not 1.20.1. Old Geyser builds that matched 1.20.1 don't accept today's Bedrock app versions. ViaVersion-style setups (newer Geyser in front of a 1.20.1 server) are possible in theory but untested with this pack.
- **Modded content is the real blocker.** Plain Geyser shows modded blocks/items/mobs wrong or not at all, and Fabric API's registry sync can kick non-Fabric clients (Geyser players count) from servers with modded registries. A workaround mod, RegSyncFix, skips that check but doesn't make modded blocks show up.
- **GeyserMC Hydraulic** (Fabric/NeoForge, with Geyser) is the project that converts modded content for Bedrock, but it's in very early development, not for production, and built for current Minecraft versions, not 1.20.1.
- Realistic options: (1) keep the pack Java-only; Bedrock/phone users can still watch through the Everwyn App; (2) a separate vanilla(-ish) server or world on current Minecraft with Geyser + Floodgate for crossplay; (3) later, move the pack to a Minecraft version Hydraulic supports and test it, accepting that some mods won't convert.
- If Geyser/Floodgate is ever added: Floodgate gives Bedrock names a prefix (default "."), and several app actions only accept [A-Za-z0-9_] names (unwhitelist, spawn/tame owner, rollback source). Those checks would need to allow the prefix.
