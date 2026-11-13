# Dead Wave art slots

Images in this folder replace the art the game draws on its own. Drop in a PNG with the same name and it's used the next time the page loads. Delete a file to go back to the built-in art. These files only load when the game is served from a web address (GitHub Pages, a local server), not when you open `index.html` straight from disk.

| File | Where it shows | Suggested size |
|---|---|---|
| `lobby_bg.png` | Backdrop of the card screen (PLAY / co-op lobby). It is darkened slightly so the cards stay readable | 1280×720 or larger, landscape |
| `cards/mode_survival.png`, `mode_objectives.png`, `mode_horde.png`, `mode_infection.png` | The four big mode cards | 3:4 portrait, for example 300×400 or 600×800 |
| `cards/fac_zombies.png`, `fac_robots.png`, `fac_monsters.png` | Enemy cards | Square, 300×300 or larger |
| `cards/map_suburb.png`, `map_city.png`, `map_cyber.png`, `map_forest.png` | Map cards | Square, 300×300 or larger |

Pictures are cropped to fill the card (centered), so keep the important part in the middle. Pixel art stays crisp at any size because the game scales it without smoothing.

## Where the current pictures came from

They are screenshots of staged scenes from the game itself, made by `tools/render_art.js`:

```
node tools/render_art.js /path/to/dead-wave
```

The script needs Playwright with Chromium. It overwrites every file listed above, so keep your own replacements somewhere else if you re-run it.

## Enemy and character sprites

Characters use PNG sprite sheets described in `sprites/manifest.json` (see `sprites/README.md`). The robot and monster sheets are built from LPC art by `sprites/lpc/build_factions.py` and listed under `"factions"` in the manifest. You can point any entry at your own sheets with the same layout.
