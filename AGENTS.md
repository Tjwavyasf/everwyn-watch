# Instructions for AI coding assistants (ChatGPT/Codex, Claude, etc.)

This repo's main project is **Everwyn Watch**: a server-side Fabric mod for Minecraft 1.20.1 (Java 17, Gradle + fabric-loom) that serves a phone web app (`src/main/resources/assets/everwyn-watch/web/index.html`, vanilla HTML/CSS/JS, no build step).

**Read first:** `handoff/EVERWYN_WATCH_PROJECT_HANDOFF.md`, then `handoff/EVERWYN_WATCH_NEXT_STEPS.md`.

`warehouse-showdown/` (on `main`) is a **different project** (the "Clocked In" browser game). Do not mix its code with Everwyn Watch.

## Rules from the owner
- Do not rebuild or redesign the app, remove features, rename files without need, or replace the stack.
- Do not change saved data formats (config JSON, markers, tiles, heights files) without the owner's OK.
- Make only the changes the owner asks for; no unrelated features; no placeholder code over working code.
- Keep the pixel Minecraft look (Silkscreen font, existing CSS variables) and plain, friendly wording.
- Server-only mod: never load or generate chunks; keep work off the server thread or rate-limited.
- Work on a branch / pull request. Never push to `main`, merge, force-push, or delete branches without permission.
- Update the files in `handoff/` (especially the handoff status and changelog) whenever you change something.

## Checking changes
- Build: `gradle build --no-daemon` (Java 17, Gradle 8.10+; needs maven.fabricmc.net). GitHub Actions "Build mod jar" runs on every push.
- No automated tests exist. For the web app, at least syntax-check the `<script>` blocks of `index.html` with `node --check`.
- Never commit `config/everwyn-watch.json` or other server config: it holds the owner's password, helper passwords and webhooks.
