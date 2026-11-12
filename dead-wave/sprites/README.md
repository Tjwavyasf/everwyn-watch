# Dead Wave sprite sheets

The game draws characters from PNG sprite sheets listed in `manifest.json` in this folder. Any character that isn't listed (or whose file fails to load) uses the built-in placeholder art, so the game always runs.

- **Try the sample pack:** open the game with `?sprites=sample` at the end of the address. It loads `sample/manifest.json`, a working pack made from the placeholder art in exactly the layout below. Use it as a template.
- **Use your own art:** put the PNGs in this folder and fill in `manifest.json` (copy `sample/manifest.json` and change the file names and numbers).

Sheets are loaded from the game's web address, so test on GitHub Pages or a local web server, not by double-clicking `index.html`.

## Character names

| Name | What it is |
|---|---|
| `survivor` | The player and co-op teammates |
| `walker` | Common zombie (give it several `variants` for variety) |
| `runner` | Fast zombie |
| `spitter` | Ranged zombie |
| `bloater` | Exploding zombie |
| `brute` | Big zombie that smashes barricades |
| `boss` | The Abomination (every 5th wave) |

## Sheet layout

Every frame in a sheet is the same size. Frames for one animation go left to right in a row. Each animation has one row per direction, in this order unless you set `dirs`:

1. down (facing the camera)
2. left
3. right
4. up (facing away)

The default layout (the one the sample pack uses):

| Rows | Animation | Frames | Notes |
|---|---|---|---|
| 0–3 | `idle` | 2 | loops |
| 4–7 | `walk` | 4 | loops |
| 8–11 | `run` | 4 | loops (sprinting) |
| 12–15 | `attack` | 2 | plays once (shooting for survivors, swipes for zombies) |
| 16–19 | `hurt` | 2 | plays once |
| 20–23 | `roll` | 4 | survivors only, dodge roll |
| 24 | `death` | 6 | one row, plays once, last frame stays on the ground |

You don't have to use this layout. Each animation says where its rows are.

## manifest.json

```json
{
  "characters": {
    "survivor": {
      "frame": [32, 40],
      "anchor": [16, 38],
      "scale": 1,
      "dirs": ["down", "left", "right", "up"],
      "drawGun": true,
      "gun": [21, 27],
      "variants": [
        { "name": "Scout", "sheet": "survivor_scout.png" },
        { "name": "Medic", "sheet": "survivor_medic.png" }
      ],
      "layers": [
        { "slot": "hair", "name": "Hair", "options": [
          { "name": "Short", "sheet": "hair_short.png" },
          { "name": "None" }
        ]}
      ],
      "anims": {
        "idle":   { "row": 0,  "frames": 2, "fps": 3 },
        "walk":   { "row": 4,  "frames": 6, "fps": 10 },
        "run":    { "row": 8,  "frames": 6, "fps": 14 },
        "attack": { "row": 12, "frames": 3, "fps": 12, "loop": false },
        "hurt":   { "row": 16, "frames": 2, "fps": 10, "loop": false },
        "roll":   { "row": 20, "frames": 4, "fps": 14 },
        "death":  { "row": 24, "frames": 6, "fps": 10, "loop": false, "dirs": 1 }
      }
    },
    "walker": {
      "frame": [32, 40], "anchor": [16, 38],
      "variants": [ { "sheet": "walker_1.png" }, { "sheet": "walker_2.png" } ],
      "anims": {
        "idle":   { "row": 0,  "frames": 2, "fps": 3 },
        "walk":   { "row": 4,  "frames": 6, "fps": 7 },
        "attack": { "row": 12, "frames": 3, "fps": 10, "loop": false },
        "hurt":   { "row": 16, "frames": 2, "fps": 10, "loop": false },
        "death":  { "row": 24, "frames": 6, "fps": 10, "loop": false, "dirs": 1 }
      }
    }
  }
}
```

| Field | Meaning |
|---|---|
| `frame` | Width and height of one frame, in pixels. |
| `anchor` | The point in a frame that sits on the character's position: usually between the feet. |
| `scale` | Draw size multiplier. The world is about 16 pixels per tile and the current characters are about 36 pixels tall, so art around 32×40 per frame fits at `1`. |
| `dirs` | Row order for directions. Leave out `left` (or `right`) and the game mirrors the other one. |
| `sheet` | One sheet for the character. |
| `variants` | Several complete sheets. Survivors pick one in the Customize screen, and zombies pick one at random. |
| `layers` | Extra sheets drawn on top in order (hair, hats, gear). They must use the same layout. Each becomes a row in the Customize screen. An option with no `sheet` draws nothing. |
| `drawGun` | `true` draws the current gun over the survivor, aimed where the player aims. Set `false` if the art already shows a weapon. |
| `gun` | Where the gun is held, in frame pixels. |
| `anims.*.row` | First row of that animation. |
| `anims.*.col` | Optional starting column (if several animations share rows). |
| `anims.*.frames`, `fps` | Frame count and speed. |
| `anims.*.loop` | `false` plays once and holds the last frame. |
| `anims.*.dirs` | `1` means a single row for all directions (common for death). |

Missing animations fall back sensibly: `run` uses `walk`, `roll` uses `run`, and anything else uses `idle`.

## Tips for buying or commissioning art

- Ask for a top-down or three-quarter RPG view with 4 directions, transparent PNG, and no anti-aliasing.
- 32×40 to 48×56 pixels per frame fits the world best. Bigger art works with `scale` below 1, but pixels will look uneven, so prefer art drawn near the size it will be shown.
- If the pack's layout is different, you don't need to rearrange it. Point each animation's `row` and `col` at where it lives.
- For customization, ask for separate layers (body, outfit, hair, headwear) drawn on the same frame grid.
