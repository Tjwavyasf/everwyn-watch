# Paste this into ChatGPT (or any AI assistant) to continue

---

I'm continuing development of **Everwyn App** (formerly Everwyn Watch): a server-side Fabric 1.20.1 Minecraft mod (Java 17) with a built-in phone web app (`src/main/resources/assets/everwyn-watch/web/index.html`, vanilla JS + three.js, no build step), plus a small client mod for my modpack (`crash-reporter/`).

GitHub repo (source of truth): **https://github.com/Tjwavyasf/everwyn-watch**
Newest code: branch **`everwyn-app`** (version 2.0.0). `main` is old and doesn't have these features yet.

Read first, in this order: `AGENTS.md`, `handoff/EVERWYN_WATCH_PROJECT_HANDOFF.md` (the "READ THIS FIRST" section), `handoff/EVERWYN_WATCH_NEXT_STEPS.md`, `handoff/EVERWYN_WATCH_CHANGELOG.md`, `handoff/EVERWYN_WATCH_SETUP.md`.

Build: Java 17, **Gradle 8.10** (`gradle build --no-daemon`), or push and use the GitHub Actions "Build mod jar" artifacts (server jar + crash reporter jar).

Rules:
- Don't rebuild or redesign the app, remove features, rename files, or change the stack. Keep the Everwyn fantasy look (forest green, gold, vines) and the Minecraft-style inventory.
- Don't rename the mod id or config/data names (`everwyn-watch`).
- Keep `warehouse-showdown/` (Clocked In game on main) separate.
- Work on a branch; don't push to main, merge, force-push or delete branches without asking me.
- Never commit server config (it has passwords, webhooks, keys).
- Update the `handoff/` docs and changelog when you change something.
- Things can't be tested without a Minecraft server: make sure it compiles, and tell me what to test.

Wait for my next instruction.
