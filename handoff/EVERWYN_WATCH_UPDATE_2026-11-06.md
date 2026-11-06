# Everwyn Watch — Admin and Mob Protection Update (2026-11-06)

## Baseline
Created on local branch `update/everwyn-watch-mob-controls` from verified archived code commit `3167fd7bb155f494ad503e283f5ed00ff3d683a6`, which contains Tabletop 3D + Free Cam. Repo remote could be read publicly but not cloned live from this environment; rebase or compare before applying to any newer remote commits.

## Added in this update
- Owner/admin **Clear all hostile mobs** button under Server > Mob Cleanup, with confirmation. Does not load or generate chunks. Removes normal hostiles in loaded chunks from all dimensions. Protects named, persistent and Everwyn challenge mobs. The command is manually invoked and may cause a transient spike on heavily loaded servers.
- **Unwhitelist** button with confirmation in Server > Whitelist, using current server whitelist. Does not kick players who are already online.
- **Typed item quantities** and +/- stepper controls for quantity fields, retaining the established UI styles. Minecraft stack-size rules still apply.
- **Automatic mob protection**: new `MobGuard` service scans loaded entities every 200 ticks (~10 seconds at 20 TPS), with configurable eligible hostile limits per chunk (16) and per dimension (200), player exclusion radius (48 blocks), and max removal batch (25). Named/persistent/challenge mobs excluded.
- **TPS emergency mode** after 60s sustained below 14 TPS: eligible limits temporarily lowered to 75%; messages rate limited. Existing TPS alerts preserved. App Server tab exposes settings and counts (admin-only).

## Scope / limitations
- The population controller **removes excess mobs after spawning**; it does not intercept the spawning event. It cannot guarantee TPS or protect against other lag causes.
- No runtime state or config secrets were included. Config fields are additive, existing server config is preserved.
- The existing 3D map is preserved unchanged. No full block rendering or cave slice was added.
- Java/Fabric compilation and live server deployment are **not verified**; run GitHub Actions `Build mod jar` on the new branch or compile with Java 17/Gradle 8.10+.
- Nothing was pushed to GitHub or installed on a Minecraft server. Update is ready to review and build, **not an already compiled JAR**.

## Install after successful CI build
1. Back up your current server world, mod JAR and `config/everwyn-watch.json`.
2. Apply patch to `claude/funny-feynman-ifcll1` (or import the provided branch bundle), commit on a new branch, and push that branch only.
3. Confirm the `Build mod jar` GitHub Action is green; download `everwyn-watch-jar` artifact from the branch workflow run. `latest` release tracks main and is not appropriate here.
4. Stop the Bisect server. Replace **only** the previous Everwyn Watch JAR in `mods/` with the new built JAR; keep Fabric API. Restart server.
5. Because the phone app is bundled inside the same JAR, open the app and refresh (force-reload if cached) to see new Server tab controls.
6. Verify TPS, mob counts, permissions, 3D modes, protected bosses, item transfers, and whitelist behavior on a staging server before production.
