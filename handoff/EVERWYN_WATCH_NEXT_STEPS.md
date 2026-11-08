# Everwyn App — Next Steps (prioritized)

Only build what the owner asks for. Check the repo state first.

1. **Reconcile:** fetch all branches; newest code is `everwyn-app` (2.0.0). Report any differences from the handoff before editing.
2. **Owner testing of 2.0.0** on the live server: real blocks + mob models in 3D, follow camera/underground, inventory editor (moves, trinkets, offline edits), spawn/tame, schematic placer, crash reporter, Ledger. Fix what they report (ask for screenshots and the server log).
3. **Merge to main (only with permission)** so the GitHub "latest" release has 2.0.0. `everwyn-app` merges on top of main's separate `warehouse-showdown/` game; keep that untouched.
4. Ideas already discussed (not approved yet): more mob models / GeckoLib mobs; always-catch-every-death; block entity models; block lighting.
