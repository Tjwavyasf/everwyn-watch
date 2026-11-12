"""Builds Dead Wave character sheets from the Universal LPC Spritesheet Character Generator art.
Each character is a recipe of LPC parts (sheet definition + colour). Parts are recoloured with the official palettes,
stacked by zPos, and packed into the Dead Wave sheet layout described in dead-wave/sprites/README.md."""
import json, os, subprocess, sys
from PIL import Image
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'repo')
OUT = '/home/user/everwyn-watch/dead-wave/sprites/lpc'
FW = 64
DEFS = os.path.join(R, 'sheet_definitions')
PAL = {m: json.load(open(os.path.join(R, 'palette_definitions', m, f'{m}_ulpc.json'))) for m in ['body', 'hair', 'cloth', 'eye']}
BASE = {'body': 'light', 'hair': 'orange', 'cloth': 'white', 'eye': 'blue'}
# Dead Wave layout: name -> (first row, LPC animation, columns to take, rows)
LAYOUT = [('walk', 0, 'walk', list(range(1, 9)), 4), ('run', 4, 'run', list(range(0, 8)), 4), ('idle', 8, 'idle', [0, 1], 4),
          ('attack', 12, None, None, 4), ('death', 16, 'hurt', list(range(0, 6)), 1)]

def d(path):
    return json.load(open(os.path.join(DEFS, path)))

def hx(h):
    h = h.lstrip('#'); return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))

def recolor(img, material, frm, to):
    if not to or to == frm: return img
    src, dst = PAL[material][frm], PAL[material][to]
    m = {hx(a): hx(b) for a, b in zip(src, dst)}
    px = img.load(); w, h = img.size
    for y in range(h):
        for x in range(w):
            p = px[x, y]
            if p[3] and p[:3] in m: px[x, y] = m[p[:3]] + (p[3],)
    return img

NEED = set()
def part_files(defpath, body, anim, colour):
    """Return [(zPos, PIL image or None, material, base, colour)] for one part and animation."""
    df = d(defpath); res = []
    for k in sorted(k for k in df if k.startswith('layer_')):
        L = df[k]; sub = L.get(body) or L.get('male')
        if not sub: continue
        variants = df.get('variants'); rc = df.get('recolors') or {}
        if 'material' in rc: material, base = rc['material'], (rc.get('base') or '').split('.')[-1] or BASE[rc['material']]
        elif 'color_1' in rc: material, base = rc['color_1']['material'], BASE[rc['color_1']['material']]
        else: material, base = None, None
        if variants:
            v = colour if colour in variants else variants[0]
            f = os.path.join('spritesheets', sub, anim, v.replace(' ', '_') + '.png'); c2 = None
        else:
            f = os.path.join('spritesheets', sub, anim + '.png'); c2 = colour
        NEED.add(os.path.join('spritesheets', sub))
        full = os.path.join(R, f)
        res.append((L.get('zPos', 50), full if os.path.exists(full) else None, material, base, c2))
    return res

def load(full, material, base, colour):
    im = Image.open(full).convert('RGBA')
    return recolor(im, material, base, colour) if material and colour else im

def frames_of(img, row, cols):
    out = []
    n = img.size[0] // FW
    for c in cols:
        c = min(c, n - 1)
        out.append(img.crop((c * FW, row * FW, (c + 1) * FW, (row + 1) * FW)))
    return out

def build(name, body, parts, attack):
    """parts: [(defpath, colour)], attack: ('shoot', [cols]) or ('slash', [cols])"""
    W = 8 * FW; H = 17 * FW
    sheet = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    lay = LAYOUT[:3] + [('attack', 12, attack[0], attack[1], 4)] + LAYOUT[4:]
    for key, row0, anim, cols, nrows in lay:
        stack = []
        for defpath, colour in parts:
            for z, full, mat, base, c2 in part_files(defpath, body, anim, colour):
                stack.append((z, full, mat, base, c2, defpath, colour))
        stack.sort(key=lambda s: s[0])
        for z, full, mat, base, c2, defpath, colour in stack:
            use_cols = cols
            if full is None:  # part has no art for this animation: borrow its walk frames
                alt = [p for p in part_files(defpath, body, 'walk', colour) if p[0] == z]
                if not alt or alt[0][1] is None: continue
                full, mat, base, c2 = alt[0][1], alt[0][2], alt[0][3], alt[0][4]
                use_cols = list(range(1, 9)) if key == 'run' else [0] * len(cols)
                if key == 'death': continue
            im = load(full, mat, base, c2)
            for r in range(nrows):
                srow = r if im.size[1] >= (r + 1) * FW else 0
                for i, fr in enumerate(frames_of(im, srow, use_cols)):
                    sheet.alpha_composite(fr, (i * FW, (row0 + r) * FW))
    os.makedirs(OUT, exist_ok=True)
    sheet.save(os.path.join(OUT, name + '.png'), optimize=True)
    return name + '.png'

# ---------------- recipes ----------------
H = lambda p: 'hair/' + p
SURVIVORS = [
 ('Alex', 'male', [('body/body.json', 'light'), ('head/heads/human/heads_human_male.json', 'light'), ('legs/pants/legs_pants.json', 'charcoal'), ('feet/boots/feet_boots_basic.json', 'brown'),
   ('torso/shirts/shortsleeve/torso_clothes_shortsleeve.json', 'gray'), ('torso/jacket/torso_jacket_collared.json', 'leather'), (H('short/hair_messy1.json'), 'dark_brown'), ('torso/backpack/backpack.json', 'leather')]),
 ('Maya', 'female', [('body/body.json', 'olive'), ('head/heads/human/heads_human_female.json', 'olive'), ('legs/pants/legs_pants.json', 'navy'), ('feet/boots/feet_boots_basic.json', 'black'),
   ('torso/shirts/shortsleeve/torso_clothes_shortsleeve.json', 'maroon'), (H('long/hair_long.json'), 'black'), ('torso/backpack/backpack.json', 'forest')]),
 ('Rook', 'male', [('body/body.json', 'brown'), ('head/heads/human/heads_human_male.json', 'brown'), ('legs/pants/legs_pants.json', 'tan'), ('feet/boots/feet_boots_basic.json', 'black'),
   ('torso/shirts/longsleeve/torso_clothes_longsleeve.json', 'forest'), (H('short/hair_plain.json'), 'black'), ('torso/backpack/backpack.json', 'gray')]),
 ('Jade', 'female', [('body/body.json', 'light'), ('head/heads/human/heads_human_female.json', 'light'), ('legs/pants/legs_pants.json', 'charcoal'), ('feet/boots/feet_boots_basic.json', 'brown'),
   ('torso/shirts/shortsleeve/torso_clothes_shortsleeve.json', 'teal'), (H('bob/hair_bob.json'), 'redhead'), ('torso/backpack/backpack.json', 'tan')]),
 ('Diesel', 'male', [('body/body.json', 'black'), ('head/heads/human/heads_human_male.json', 'black'), ('legs/pants/legs_pants.json', 'blue'), ('feet/boots/feet_boots_basic.json', 'black'),
   ('torso/shirts/sleeveless/torso_clothes_sleeveless.json', 'charcoal'), (H('short/hair_bedhead.json'), 'black'), ('torso/backpack/backpack.json', 'black')]),
 ('Wren', 'female', [('body/body.json', 'bronze'), ('head/heads/human/heads_human_female.json', 'bronze'), ('legs/pants/legs_pants.json', 'black'), ('feet/boots/feet_boots_basic.json', 'tan'),
   ('torso/shirts/longsleeve/torso_clothes_longsleeve.json', 'sky'), (H('short/hair_messy1.json'), 'blonde'), ('torso/backpack/backpack.json', 'navy')]),
 ('Cole', 'male', [('body/body.json', 'amber'), ('head/heads/human/heads_human_male.json', 'amber'), ('legs/pants/legs_pants.json', 'forest'), ('feet/boots/feet_boots_basic.json', 'brown'),
   ('torso/shirts/shortsleeve/torso_clothes_shortsleeve.json', 'white'), ('torso/jacket/torso_jacket_collared.json', 'forest'), (H('short/hair_parted.json'), 'ginger'), ('torso/backpack/backpack.json', 'walnut')]),
 ('Ivy', 'female', [('body/body.json', 'taupe'), ('head/heads/human/heads_human_female.json', 'taupe'), ('legs/pants/legs_pants.json', 'gray'), ('feet/boots/feet_boots_basic.json', 'black'),
   ('torso/shirts/longsleeve/torso_clothes_longsleeve.json', 'purple'), (H('long/hair_long.json'), 'platinum'), ('torso/backpack/backpack.json', 'purple')]),
]
ZB = ('body/special/body_zombie.json', 'zombie'); ZH = ('head/heads/undead/heads_zombie.json', None)
WALKERS = [
 ('walker_1', 'male', [ZB, ZH, ('legs/pants/legs_pants.json', 'navy'), ('torso/shirts/shortsleeve/torso_clothes_shortsleeve.json', 'gray'), (H('short/hair_plain.json'), 'dark_brown')]),
 ('walker_2', 'male', [ZB, ZH, ('legs/pants/legs_pants.json', 'charcoal'), ('torso/shirts/longsleeve/torso_clothes_longsleeve.json', 'maroon'), ('feet/boots/feet_boots_basic.json', 'brown')]),
 ('walker_3', 'male', [ZB, ZH, ('legs/pants/legs_pants.json', 'tan'), ('torso/shirts/sleeveless/torso_clothes_sleeveless.json', 'white'), (H('short/hair_messy1.json'), 'black')]),
 ('walker_4', 'male', [ZB, ZH, ('legs/pants/legs_pants.json', 'blue'), ('torso/shirts/shortsleeve/torso_clothes_shortsleeve.json', 'forest'), (H('long/hair_long.json'), 'chestnut')]),
 ('walker_5', 'male', [ZB, ZH, ('legs/pants/legs_pants.json', 'black'), ('torso/shirts/longsleeve/torso_clothes_longsleeve.json', 'slate'), ('feet/boots/feet_boots_basic.json', 'black'), (H('short/hair_bedhead.json'), 'ash')]),
 ('walker_6', 'male', [ZB, ZH, ('legs/pants/legs_pants.json', 'brown'), ('torso/shirts/shortsleeve/torso_clothes_shortsleeve.json', 'yellow')]),
]
OTHERS = [
 ('runner', 'male', [('body/body.json', 'zombie_green'), ('head/heads/undead/heads_zombie.json', 'zombie_green'), ('legs/pants/legs_pants.json', 'black'), ('torso/shirts/shortsleeve/torso_clothes_shortsleeve.json', 'red')]),
 ('spitter', 'male', [('body/body.json', 'pale_green'), ('head/heads/undead/heads_zombie.json', 'pale_green'), ('legs/pants/legs_pants.json', 'brown'), ('torso/shirts/longsleeve/torso_clothes_longsleeve.json', 'purple')]),
 ('bloater', 'pregnant', [('body/body.json', 'zombie'), ('head/heads/undead/heads_zombie.json', None), ('legs/pants/legs_pants.json', 'gray')]),
 ('brute', 'muscular', [('body/body.json', 'zombie'), ('head/heads/undead/heads_zombie.json', None), ('legs/pants/legs_pants.json', 'brown'), ('feet/boots/feet_boots_basic.json', 'black')]),
 ('boss', 'muscular', [('body/body.json', 'zombie_green'), ('head/heads/undead/heads_zombie.json', 'zombie_green'), ('legs/pants/legs_pants.json', 'black'), ('feet/boots/feet_boots_basic.json', 'black')]),
]

def all_parts():
    for _, body, parts in SURVIVORS + WALKERS + OTHERS:
        for p, c in parts:
            for anim in ['walk', 'run', 'idle', 'shoot', 'slash', 'hurt']: part_files(p, body, anim, c)

if __name__ == '__main__':
    if sys.argv[1:] == ['fetch']:
        all_parts()
        dirs = sorted(NEED); print(len(dirs), 'dirs'); subprocess.run(['git', '-C', R, 'sparse-checkout', 'add'] + dirs, check=True); sys.exit()
    SHOOT = ('shoot', [4, 5, 6, 7]); SLASH = ('slash', [0, 1, 2, 3, 4, 5])
    man = {'characters': {}}
    def entry(files, extra):
        e = {'frame': [64, 64], 'anchor': [32, 60], 'scale': 1, 'dirs': ['up', 'left', 'down', 'right'],
             'anims': {'walk': {'row': 0, 'frames': 8, 'fps': 10}, 'run': {'row': 4, 'frames': 8, 'fps': 14}, 'idle': {'row': 8, 'frames': 2, 'fps': 2},
                       'attack': {'row': 12, 'frames': len(extra.pop('atk')), 'fps': 12, 'loop': False}, 'death': {'row': 16, 'frames': 6, 'fps': 10, 'loop': False, 'dirs': 1}}}
        e.update(files); e.update(extra); return e
    sv = [{'name': n, 'sheet': 'lpc/' + build('survivor_' + n.lower(), b, p, SHOOT)} for n, b, p in SURVIVORS]
    man['characters']['survivor'] = entry({'variants': sv}, {'atk': SHOOT[1], 'drawGun': True, 'gun': [42, 42]})
    wv = [{'name': n, 'sheet': 'lpc/' + build(n, b, p, SLASH)} for n, b, p in WALKERS]
    man['characters']['walker'] = entry({'variants': wv}, {'atk': SLASH[1]})
    for n, b, p in OTHERS:
        man['characters'][n] = entry({'sheet': 'lpc/' + build(n, b, p, SLASH)}, {'atk': SLASH[1], **({'scale': 1.5, 'anchor': [32, 60]} if n == 'boss' else {})})
    json.dump(man, open('/home/user/everwyn-watch/dead-wave/sprites/manifest.json', 'w'), indent=1)
    print('built', len(sv) + len(wv) + len(OTHERS), 'sheets')
