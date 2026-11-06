# Everwyn Watch — Next Steps (prioritized)

Only items already discussed or needed to finish existing work. Nothing here is approved for implementation unless the user says so.

1. **Reconcile the repository first (every new session).** Fetch, compare `claude/funny-feynman-ifcll1` (3167fd7) and `main` (f001b81 at handoff) and any newer commits against this handoff. Report differences before editing.
2. **Get the 3D map into the release (needs user permission).** The `latest` download is built from main and lacks 3D. Option: merge `claude/funny-feynman-ifcll1` into main (a test merge was clean; main's extra commits are Warehouse Showdown + workflow edits that must be kept). Ask before merging or pushing to main.
3. **Test 3D on the real server and a real phone.** Check: terrain loads, heights files appear next to tile PNGs, TPS stays fine, touch controls feel right, iPhone Safari loads three.js.
4. **Answer the open question "Can I see every block?"** — user to pick one: (1) real textures on top blocks, (2) full real blocks near the camera (recommended, likely what the user wants), (3) option 2 + cave/height slice slider. Do nothing until the user chooses.
5. **Small known gaps (only if the user asks):** mobs in 3D; README mention of modpack installer, voice listening and mobs on map; cleanup of the stale `cftest` branch (deleting branches needs permission).
