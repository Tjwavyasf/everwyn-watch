# Paste this into ChatGPT (or any AI assistant) to continue

---

I'm continuing development of **Everwyn Watch**, a server-side Fabric 1.20.1 Minecraft mod (Java 17) with a built-in phone web app (`src/main/resources/assets/everwyn-watch/web/index.html`, vanilla JS, no build step).

GitHub repo (source of truth): **https://github.com/Tjwavyasf/everwyn-watch**
Work on branch: **`claude/funny-feynman-ifcll1`** (it has the newest Everwyn Watch code, including the 3D map; the branch name is just where the last session worked).

First, read these files in the repo, in this order:
1. `AGENTS.md` (rules)
2. `handoff/EVERWYN_WATCH_PROJECT_HANDOFF.md` (what the app is, architecture, status)
3. `handoff/EVERWYN_WATCH_NEXT_STEPS.md`
4. `handoff/EVERWYN_WATCH_SETUP.md` and `handoff/EVERWYN_WATCH_CHANGELOG.md` as needed

State at handoff (2026-11-06):
- Last code commit: `3167fd7bb155f494ad503e283f5ed00ff3d683a6` — Tabletop 3D + Free cam map views, mod version 1.9.0. GitHub Actions build passed.
- `main` has 8 extra commits for a **separate** game, Warehouse Showdown / "Clocked In", in `warehouse-showdown/`. The 3D map is NOT merged into `main`, so the "latest" release jar has no 3D yet. A test merge was clean.
- Open question from me, not answered yet: "Can I see every block?" Options offered: (1) real textures on top blocks, (2) full real blocks near the camera, (3) option 2 + cave slider. Nothing built.

Before editing anything:
1. Look at the latest state of the repo (all branches) and tell me if anything differs from the handoff.
2. Never mix Everwyn Watch with `warehouse-showdown/` (Clocked In).

Rules:
- DO NOT rebuild Everwyn Watch or redesign its interface without my permission.
- DO NOT remove features, rename files unnecessarily, replace the stack (Fabric / Java 17 / vanilla JS), or change saved data formats without asking.
- DO NOT add unrelated features or replace working code with placeholders.
- Keep the pixel Minecraft look and plain, simple wording in the app.
- Make only the changes I ask for. Keep the `handoff/` docs updated when you change things.
- Make changes on a branch or as a pull request. Do not push to `main`, merge, force-push, reset, or delete branches unless I say so.
- The code can't be fully tested without a Minecraft server: after changes, check that the GitHub Actions "Build mod jar" run passes.

Wait for my next instruction.
