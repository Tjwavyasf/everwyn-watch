# Paste this into the new Claude conversation

---

I'm continuing development of my game **CLOCKED IN** (formerly *Warehouse Showdown*). I've attached `PROJECT_FULL_BACKUP.zip` and the handoff docs. The code also lives in my GitHub repo **`Tjwavyasf/everwyn-watch`**, folder **`warehouse-showdown/`** (branch `main`, or `claude/sleepy-einstein-ipmhac`).

**Before doing anything:**
1. Read `warehouse-showdown/handoff/PROJECT_HANDOFF.md`, `warehouse-showdown/GAME_STATE.md`, and `PROJECT_NEXT_STEPS.md`.
2. Treat the actual files as the source of truth, not summaries. The current game is `warehouse-showdown/index.html` (one self-contained HTML file, three.js r128, generated HD-2D pixel art). Older versions are in `v1/`, `v2/` and `long-shift/`; keep them unchanged.
3. Confirm back to me in a few lines what state the project is in, before changing code.

**Rules:**
- Don't restart, redesign, simplify or rewrite. Keep the engine, art style, architecture, story, exact dialogue lines and the Jose five-ram forklift rules.
- Change only what my request needs, incrementally, and tell me which parts of `index.html` you changed and why.
- The unknown-force mystery stays unexplained.
- Don't mistake planned features for implemented ones (see the labels in the handoff).
- Test changes (Playwright, see `PROJECT_SETUP.md`) and report failures honestly. Don't claim mobile compatibility without a real-device test.
- Update `GAME_STATE.md` and `CHANGELOG.md` after changes.
- Don't push to `main` without my OK. Pushing to `main` updates the public link https://tjwavyasf.github.io/everwyn-watch/clocked-in/ for everyone.
- The rest of the `everwyn-watch` repo is my Minecraft server mod. Don't touch it.
- There was a Godot 4 version idea; that project wasn't available. Ask me before switching engines.

**What I want to do next:** [write your request here, e.g. "I tested on my phone and here's what happened…" or "start Chapter 2: Mandatory Overtime, here's the script…"]

---
