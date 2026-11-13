"""Builds the robot and monster enemy sheets for Dead Wave from Universal LPC Spritesheet Character Generator art.

Robots are LPC skeletons and plate armour re-shaded to metal ramps (chrome endoskeletons and armored automatons).
Monsters are LPC beastmen: goblins, orcs, boarmen, wolf-men, lizardmen, trolls, minotaurs and a winged boss.
Output: sprites/lpc/fac/*.png and a "factions" block in ../manifest.json. The game falls back to its code-drawn
enemies if these files are missing.

Usage (LPC generator cloned as in HANDOFF.md, LPC_REPO pointing at it):
    python3 build_factions.py fetch
    python3 build_factions.py
"""
import json, os, subprocess, sys, warnings
from PIL import Image
warnings.filterwarnings('ignore', category=DeprecationWarning)
HERE = os.path.dirname(os.path.abspath(__file__))
R = os.environ.get('LPC_REPO') or os.path.join(HERE, 'repo')
OUT = os.path.join(HERE, 'fac')
MANIFEST = os.path.join(HERE, '..', 'manifest.json')
DEFS = os.path.join(R, 'sheet_definitions')
FW = 64
PAL = {m: json.load(open(os.path.join(R, 'palette_definitions', m, f'{m}_ulpc.json'))) for m in ['body', 'hair', 'cloth', 'eye', 'metal']}
LAYOUT = [('walk', 0, 'walk', list(range(1, 9)), 4), ('run', 4, 'run', list(range(0, 8)), 4), ('idle', 8, 'idle', [0, 1], 4),
          ('attack', 12, 'slash', [0, 1, 2, 3, 4, 5], 4), ('death', 16, 'hurt', list(range(0, 6)), 1)]
ANIMS = ['walk', 'run', 'idle', 'slash', 'hurt']
STEEL = ['#14181f', '#2c3340', '#4f5a6c', '#7d899e', '#b4bfd2', '#eef4ff']
GUNM = ['#0e1014', '#20242c', '#383e4a', '#59606f', '#8890a2', '#c4cad8']
BRASS = ['#1e1406', '#4a3212', '#7e5a24', '#b8883c', '#e4bc6a', '#fff0b8']
CRIM = ['#14080a', '#3a1218', '#6a2028', '#a8343a', '#e06a5a', '#ffc0a0']

def d(path): return json.load(open(os.path.join(DEFS, path)))
def hx(h): h = h.lstrip('#'); return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))

def matinfo(df):
    rc = df.get('recolors') or {}
    if 'material' in rc: return [rc['material']] if rc['material'] in PAL else []
    return [v['material'] for k, v in rc.items() if k.startswith('color_') and isinstance(v, dict) and v.get('material') in PAL][:1]

def best_ramp(img, material):
    cnt = {}
    for p in img.getdata():
        if p[3]: cnt[p[:3]] = cnt.get(p[:3], 0) + 1
    best, score = None, 0
    for name, ramp in PAL[material].items():
        s = sum(cnt.get(hx(h), 0) for h in ramp)
        if s > score: best, score = name, s
    return best

def recolor(img, material, to):
    if not material or not to: return img
    frm = best_ramp(img, material)
    if not frm or frm == to or to not in PAL[material]: return img
    m = {hx(a): hx(b) for a, b in zip(PAL[material][frm], PAL[material][to])}
    px = img.load(); w, h = img.size
    for y in range(h):
        for x in range(w):
            p = px[x, y]
            if p[3] and p[:3] in m: px[x, y] = m[p[:3]] + (p[3],)
    return img

def tint(img, ramp):
    """Re-shade a layer onto a colour ramp by brightness (used to turn bone and skin into metal)."""
    ramp = [hx(c) for c in ramp]; px = img.load(); w, h = img.size; lums = []
    for y in range(h):
        for x in range(w):
            p = px[x, y]
            if p[3]: lums.append(p[0] * .3 + p[1] * .59 + p[2] * .11)
    if not lums: return img
    lo, hi = min(lums), max(lums); span = max(1, hi - lo)
    for y in range(h):
        for x in range(w):
            p = px[x, y]
            if p[3]:
                k = (p[0] * .3 + p[1] * .59 + p[2] * .11 - lo) / span
                px[x, y] = ramp[min(len(ramp) - 1, int(k * len(ramp)))] + (p[3],)
    return img

NEED = set()
def layers(defpath, body, anim, variant):
    df = d(defpath); out = []
    for k in sorted(k for k in df if k.startswith('layer_')):
        L = df[k]; sub = L.get(body) or L.get('male')
        if not sub: continue
        vs = df.get('variants')
        if vs:
            v = variant if variant in vs else vs[0]
            f = os.path.join(R, 'spritesheets', sub, anim, v.replace(' ', '_') + '.png')
            NEED.add(f'/spritesheets/{sub}{anim}/{v.replace(" ", "_")}.png')
        else:
            f = os.path.join(R, 'spritesheets', sub, anim + '.png'); NEED.add(f'/spritesheets/{sub}{anim}.png')
        out.append((L.get('zPos', 50), f if os.path.exists(f) else None, sub))
    return out

def build(name, body, parts):
    """parts: [(defpath, colour or variant, tint ramp or None)]"""
    sheet = Image.new('RGBA', (8 * FW, 17 * FW), (0, 0, 0, 0))
    for key, row0, anim, cols, nrows in LAYOUT:
        stack = []
        for defpath, colour, tr in parts:
            mats = matinfo(d(defpath))
            for z, f, sub in layers(defpath, body, anim, colour):
                use = cols
                if f is None:
                    if key == 'death': continue
                    alt = layers(defpath, body, 'walk', colour)
                    alt = [a for a in alt if a[0] == z and a[1]]
                    if not alt: continue
                    f = alt[0][1]; use = list(range(1, 9)) if key == 'run' else [0] * len(cols)
                im = Image.open(f).convert('RGBA')
                if mats and colour and not d(defpath).get('variants'): im = recolor(im, mats[0], colour)
                if tr: im = tint(im, tr)
                stack.append((z, im, use))
        stack.sort(key=lambda s: s[0])
        for z, im, use in stack:
            n = im.size[0] // FW
            for r in range(nrows):
                srow = r if im.size[1] >= (r + 1) * FW else 0
                for i, c in enumerate(use):
                    c = min(c, n - 1)
                    sheet.alpha_composite(im.crop((c * FW, srow * FW, (c + 1) * FW, (srow + 1) * FW)), (i * FW, (row0 + r) * FW))
    os.makedirs(OUT, exist_ok=True); sheet.save(os.path.join(OUT, name + '.png'), optimize=True)
    return 'lpc/fac/' + name + '.png'

SK = 'body/special/body_skeleton.json'; SKH = 'head/heads/undead/heads_skeleton.json'
PL = 'torso/armour/torso_armour_plate.json'; ARM = 'arms/arms_armour.json'; LEG = 'legs/legs_armour.json'; FT = 'feet/feet_armour.json'
B = 'body/body.json'; HW = 'headwear/helmets/helmets/'
ROBOTS = {
 'walker': [('robot_drone_1', 'male', [(SK, None, STEEL), (SKH, None, STEEL)]),
            ('robot_drone_2', 'male', [(SK, None, GUNM), (SKH, None, GUNM), (PL, 'iron', None)]),
            ('robot_drone_3', 'male', [(SK, None, BRASS), (SKH, None, BRASS), (LEG, 'brass', None)])],
 'runner': [('robot_hunter', 'male', [(SK, None, GUNM), (SKH, None, GUNM), (LEG, 'steel', None), (FT, 'steel', None)])],
 'spitter': [('robot_gunner', 'male', [(SK, None, STEEL), (PL, 'steel', None), (ARM, 'steel', None), (HW + 'hat_helmet_armet.json', 'steel', None)])],
 'bloater': [('robot_bomber', 'male', [(SK, None, BRASS), (SKH, None, BRASS), ('torso/backpack/backpack_jetpack.json', None, None)])],
 'brute': [('robot_tank', 'muscular', [(B, 'black', GUNM), (PL, 'iron', None), (ARM, 'iron', None), (LEG, 'iron', None), (FT, 'iron', None),
           ('arms/shoulders/shoulders_pauldrons.json', 'black', None), (HW + 'hat_helmet_greathelm.json', 'iron', None)])],
 'boss': [('robot_warmech', 'muscular', [(B, 'black', CRIM), (PL, 'gold', None), (ARM, 'gold', None), (LEG, 'gold', None), (FT, 'gold', None),
          ('arms/shoulders/shoulders_pauldrons.json', 'red', None), (HW + 'hat_helmet_horned.json', 'gold', None), ('torso/backpack/backpack_jetpack.json', None, None)])]}
HD = 'head/heads/'; PANTS = 'legs/pants/legs_pants.json'
MONSTERS = {
 'walker': [('mon_goblin', 'male', [(B, 'green', None), (HD + 'fantasy/heads_goblin.json', 'green', None), (PANTS, 'brown', None), ('torso/torso_bandages.json', None, None)]),
            ('mon_orc', 'male', [(B, 'dark_green', None), (HD + 'fantasy/heads_orc_male.json', 'dark_green', None), (PANTS, 'charcoal', None), ('feet/boots/feet_boots_basic.json', 'brown', None)]),
            ('mon_boarman', 'male', [(B, 'fur_brown', None), (HD + 'beast/heads_boarman.json', 'fur_brown', None), ('legs/shorts/legs_shorts.json', 'tan', None)])],
 'runner': [('mon_wolf', 'male', [(B, 'fur_grey', None), (HD + 'beast/heads_wolf_male.json', 'fur_grey', None), ('legs/shorts/legs_shorts.json', 'charcoal', None)])],
 'spitter': [('mon_lizard', 'male', [(B, 'bright_green', None), (HD + 'reptile/heads_lizard_male.json', 'bright_green', None), ('body/lizard/tail_lizard.json', 'bright_green', None), (PANTS, 'maroon', None)])],
 'bloater': [('mon_troll', 'pregnant', [(B, 'pale_green', None), (HD + 'fantasy/heads_troll.json', 'pale_green', None), ('legs/pants/legs_pregnantpants.json', None, None)])],
 'brute': [('mon_minotaur', 'muscular', [(B, 'fur_brown', None), (HD + 'beast/heads_minotaur.json', 'fur_brown', None), (PANTS, 'brown', None), ('arms/wrists/arms_bracers.json', 'iron', None)])],
 'boss': [('mon_warlord', 'muscular', [(B, 'fur_black', None), (HD + 'beast/heads_wartotaur.json', 'fur_black', None), ('body/lizard/wings_lizard.json', 'fur_black', None),
          (PANTS, 'black', None), ('arms/shoulders/shoulders_pauldrons.json', 'red', None), ('arms/wrists/arms_bracers.json', 'gold', None)])]}

def entry(files, n_atk=6, scale=1):
    e = {'frame': [64, 64], 'anchor': [32, 60], 'scale': scale, 'dirs': ['up', 'left', 'down', 'right'],
         'anims': {'walk': {'row': 0, 'frames': 8, 'fps': 10}, 'run': {'row': 4, 'frames': 8, 'fps': 14}, 'idle': {'row': 8, 'frames': 2, 'fps': 2},
                   'attack': {'row': 12, 'frames': n_atk, 'fps': 12, 'loop': False}, 'death': {'row': 16, 'frames': 6, 'fps': 10, 'loop': False, 'dirs': 1}}}
    e['variants'] = [{'name': n, 'sheet': s} for n, s in files]; return e

def all_parts():
    for fac in (ROBOTS, MONSTERS):
        for lst in fac.values():
            for name, body, parts in lst:
                for p, c, t in parts:
                    for a in ANIMS: layers(p, body, a, c)

if __name__ == '__main__':
    if sys.argv[1:] == ['fetch']:
        all_parts(); cur = subprocess.run(['git', '-C', R, 'sparse-checkout', 'list'], capture_output=True, text=True).stdout.split()
        pats = sorted(set(cur) | NEED | {'/sheet_definitions/', '/palette_definitions/'})
        subprocess.run(['git', '-C', R, 'sparse-checkout', 'set', '--no-cone', '--stdin'], input='\n'.join(pats), text=True, check=True); print(len(pats), 'patterns'); sys.exit()
    man = json.load(open(MANIFEST)); out = {}
    for fid, fac in (('robots', ROBOTS), ('monsters', MONSTERS)):
        out[fid] = {}
        for role, lst in fac.items():
            files = [(name, build(name, body, parts)) for name, body, parts in lst]
            out[fid][role] = entry(files, scale=1.5 if role == 'boss' else 1)
            print(fid, role, [n for n, _ in files])
    man['factions'] = out; json.dump(man, open(MANIFEST, 'w'), indent=1)
