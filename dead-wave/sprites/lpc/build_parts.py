"""Builds the layered survivor wardrobe for Dead Wave from the Universal LPC Spritesheet Character Generator art.

Every clothing item, hair style and accessory becomes its own transparent sheet in sprites/lpc/parts/, drawn in one
base palette. The game recolours it in the browser, so each item comes in every colour of its palette without
shipping a file per colour. The result is written into the "survivor" entry of sprites/manifest.json as "parts".

Usage (from this folder, with the LPC generator cloned into ./repo as described in HANDOFF.md):
    python3 build_parts.py fetch    # sparse-checks-out just the art the catalog below uses
    python3 build_parts.py          # writes parts/*.png and updates ../manifest.json
"""
import hashlib, json, os, subprocess, sys, warnings
warnings.filterwarnings('ignore', category=DeprecationWarning)
from collections import Counter
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
R = os.environ.get('LPC_REPO') or os.path.join(HERE, 'repo')
OUT = os.path.join(HERE, 'parts')
MANIFEST = os.path.join(HERE, '..', 'manifest.json')
DEFS = os.path.join(R, 'sheet_definitions')
FW = 64
ANIMS = ['walk', 'run', 'idle', 'shoot', 'hurt']
# Dead Wave layout (see build_lpc.py): key, first row, LPC animation, columns, rows
LAYOUT = [('walk', 0, 'walk', list(range(1, 9)), 4), ('run', 4, 'run', list(range(0, 8)), 4), ('idle', 8, 'idle', [0, 1], 4),
          ('attack', 12, 'shoot', [4, 5, 6, 7], 4), ('death', 16, 'hurt', list(range(0, 6)), 1)]
BUILDS = [('male', 'Masculine'), ('female', 'Feminine')]
MATS = ['body', 'hair', 'cloth', 'eye', 'metal']
PAL = {m: json.load(open(os.path.join(R, 'palette_definitions', m, f'{m}_ulpc.json'))) for m in MATS}
SKIN_NAMES = ['light', 'amber', 'olive', 'taupe', 'bronze', 'brown', 'black', 'pale_green', 'zombie', 'blue', 'lavender']

def d(path): return json.load(open(os.path.join(DEFS, path)))
def hx(h): h = h.lstrip('#'); return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))
def title(s): return s.replace('_', ' ').replace('-', ' ').strip().capitalize()

# ---------------- catalog ----------------
# Each slot: (id, label, default option index, [options]). An option is (label, definition path) or (label, None) for nothing.
# Hair, facial hair and eyebrows take the shared hair colour; everything else gets its own colour row.
T, L, F, HW, HR = 'torso/', 'legs/', 'feet/', 'headwear/', 'hair/'
SH = T + 'shirts/'
CATALOG = [
 ('hair', 'Hair', 1, [('None', None)] + [(title(n.split('/')[-1][5:-5] if n.split('/')[-1].startswith('hair_') else n.split('/')[-1][:-5]), HR + n) for n in [
   'short/hair_plain.json', 'short/hair_messy1.json', 'short/hair_messy2.json', 'short/hair_messy3.json', 'short/hair_bedhead.json', 'short/hair_parted.json',
   'short/hair_parted2.json', 'short/hair_parted3.json', 'short/hair_parted_side_bangs.json', 'short/hair_parted_side_bangs2.json', 'short/hair_bangs.json',
   'short/hair_bangsshort.json', 'short/hair_cowlick.json', 'short/hair_cowlick_tall.json', 'short/hair_curtains.json', 'short/hair_idol.json', 'short/hair_mop.json',
   'short/hair_page.json', 'short/hair_page2.json', 'short/hair_pixie.json', 'short/hair_single.json', 'short/hair_swoop.json', 'short/hair_swoop_side.json',
   'short/hair_unkempt.json', 'bald/hair_buzzcut.json', 'bald/hair_high_and_tight.json', 'bald/hair_balding.json', 'bald/hair_shorthawk.json', 'bald/hair_longhawk.json',
   'spiky/hair_spiked.json', 'spiky/hair_spiked2.json', 'spiky/hair_halfmessy.json', 'spiky/hair_spiked_porcupine.json', 'spiky/hair_spiked_liberty.json',
   'spiky/hair_spiked_liberty2.json', 'spiky/hair_spiked_beehive.json', 'curly/hair_curly_short.json', 'curly/hair_curly_short2.json', 'curly/hair_curly_long.json',
   'curly/hair_curls_large.json', 'curly/hair_curls_large_xlong.json', 'curly/hair_jewfro.json', 'afro/hair_afro.json', 'afro/hair_natural.json', 'afro/hair_cornrows.json',
   'afro/hair_dreadlocks_short.json', 'afro/hair_dreadlocks_long.json', 'afro/hair_flat_top_fade.json', 'afro/hair_flat_top_straight.json', 'afro/hair_twists_fade.json',
   'afro/hair_twists_straight.json', 'bob/hair_bob.json', 'bob/hair_bob_side_part.json', 'bob/hair_lob.json', 'bob/hair_relm_short.json', 'long/hair_long.json',
   'long/hair_long_straight.json', 'long/hair_long_messy.json', 'long/hair_long_messy2.json', 'long/hair_long_center_part.json', 'long/hair_loose.json', 'long/hair_wavy.json',
   'long/hair_bangslong.json', 'long/hair_bangslong2.json', 'long/hair_curtains_long.json', 'braids/hair_ponytail.json', 'braids/hair_ponytail2.json',
   'braids/hair_high_ponytail.json', 'braids/hair_braid.json', 'braids/hair_braid2.json', 'braids/hair_half_up.json', 'braids/hair_bangs_bun.json', 'braids/hair_long_tied.json',
   'braids/hair_shoulderl.json', 'braids/hair_shoulderr.json', 'braids/hair_topknot_short.json', 'braids/hair_topknot_long.json', 'braids/hair_relm_ponytail.json',
   'pigtails/hair_pigtails.json', 'pigtails/hair_pigtails_bangs.json', 'pigtails/hair_bunches.json', 'xlong/hair_xlong.json', 'xlong/hair_xlong_wavy.json',
   'xlong/hair_long_band.json', 'xlong/hair_princess.json', 'xlong/hair_sara.json']]),
 ('beard', 'Facial hair', 0, [('None', None), ('Stubble', HR + 'beards/beards_5oclock_shadow.json'), ('Trimmed beard', HR + 'beards/beards_trimmed.json'),
   ('Medium beard', HR + 'beards/beards_medium.json'), ('Full beard', HR + 'beards/beards_beard.json'), ('Winter beard', HR + 'beards/beards_winter.json'),
   ('Mustache', HR + 'mustaches/beards_mustache.json'), ('Big mustache', HR + 'mustaches/beards_bigstache.json'), ('Chevron', HR + 'mustaches/beards_chevron.json'),
   ('French', HR + 'mustaches/beards_french.json'), ('Handlebar', HR + 'mustaches/beards_handlebar.json'), ('Horseshoe', HR + 'mustaches/beards_horseshoe.json'),
   ('Lampshade', HR + 'mustaches/beards_lampshade.json'), ('Walrus', HR + 'mustaches/beards_walrus.json')]),
 ('brows', 'Eyebrows', 1, [('None', None), ('Thin', 'head/eyebrows/eyebrows_thin.json'), ('Thick', 'head/eyebrows/eyebrows_thick.json')]),
 ('shirt', 'Shirt', 1, [('None', None), ('T-shirt', SH + 'shortsleeve/torso_clothes_tshirt.json'), ('V-neck tee', SH + 'shortsleeve/torso_clothes_tshirt_vneck.json'),
   ('Scoop tee', SH + 'shortsleeve/torso_clothes_tshirt_scoop.json'), ('Buttoned tee', SH + 'shortsleeve/torso_clothes_tshirt_buttoned.json'),
   ('Short sleeve', SH + 'shortsleeve/torso_clothes_shortsleeve.json'), ('Polo', SH + 'shortsleeve/torso_clothes_shortsleeve_polo.json'),
   ('Short cardigan', SH + 'shortsleeve/torso_clothes_shortsleeve_cardigan.json'), ('Long sleeve', SH + 'longsleeve/torso_clothes_longsleeve.json'),
   ('Long sleeve 2', SH + 'longsleeve/torso_clothes_longsleeve2.json'), ('Button-up', SH + 'longsleeve/torso_clothes_longsleeve2_buttoned.json'),
   ('Long polo', SH + 'longsleeve/torso_clothes_longsleeve2_polo.json'), ('Cardigan', SH + 'longsleeve/torso_clothes_longsleeve2_cardigan.json'),
   ('Long V-neck', SH + 'longsleeve/torso_clothes_longsleeve2_vneck.json'), ('Long scoop', SH + 'longsleeve/torso_clothes_longsleeve_scoop.json'),
   ('Cuffed sleeves', SH + 'longsleeve/torso_clothes_longsleeves_cuffed.json'), ('Laced shirt', SH + 'longsleeve/torso_clothes_longsleeve_laced.json'),
   ('Dress shirt', SH + 'longsleeve/torso_clothes_longsleeve_formal.json'), ('Striped dress shirt', SH + 'longsleeve/torso_clothes_longsleeve_formal_striped.json'),
   ('Sleeveless', SH + 'sleeveless/torso_clothes_sleeveless1.json'), ('Sleeveless 2', SH + 'sleeveless/torso_clothes_sleeveless2.json'),
   ('Sleeveless button', SH + 'sleeveless/torso_clothes_sleeveless2_buttoned.json'), ('Sleeveless V-neck', SH + 'sleeveless/torso_clothes_sleeveless2_vneck.json'),
   ('Sleeveless polo', SH + 'sleeveless/torso_clothes_sleeveless2_polo.json'), ('Sleeveless scoop', SH + 'sleeveless/torso_clothes_sleeveless2_scoop.json'),
   ('Muscle shirt', SH + 'sleeveless/torso_clothes_sleeveless.json'), ('Laced vest top', SH + 'sleeveless/torso_clothes_sleeveless_laced.json'),
   ('Striped tank', SH + 'sleeveless/torso_clothes_sleeveless_striped.json'), ('Tank top', SH + 'sleeveless/torso_clothes_sleeveless_tanktop.json'),
   ('Blouse', SH + 'torso_clothes_blouse.json'), ('Long blouse', SH + 'torso_clothes_blouse_longsleeve.json'), ('Tunic', SH + 'torso_clothes_tunic.json'),
   ('Corset', SH + 'torso_clothes_corset.json'), ('Robe', SH + 'torso_clothes_robe.json')]),
 ('jacket', 'Jacket', 0, [('None', None), ('Jacket', T + 'jacket/torso_jacket_collared.json'), ('Trench coat', T + 'jacket/torso_jacket_trench.json'),
   ('Frock coat', T + 'jacket/torso_jacket_frock.json'), ('Cloak coat', T + 'jacket/torso_jacket_iverness.json'), ('Vest', T + 'vest/torso_clothes_vest.json'),
   ('Open vest', T + 'vest/torso_clothes_vest_open.json'), ('Tabard', T + 'jacket/torso_jacket_tabard.json'), ('Leather armor', T + 'armour/torso_armour_leather.json'),
   ('Chainmail', T + 'torso_chainmail.json'), ('Plate armor', T + 'armour/torso_armour_plate.json'), ('Legion armor', T + 'armour/torso_armour_legion.json'),
   ('Overalls', T + 'aprons/torso_aprons_overalls.json'), ('Suspenders', T + 'aprons/torso_aprons_suspenders.json'), ('Apron', T + 'aprons/torso_aprons_apron.json'),
   ('Full apron', T + 'aprons/torso_aprons_apron_full.json'), ('Half apron', T + 'aprons/torso_aprons_apron_half.json'), ('Santa coat', T + 'jacket/torso_jacket_santa.json')]),
 ('legs', 'Pants', 1, [('Pants', L + 'pants/legs_pants.json'), ('Pants 2', L + 'pants/legs_pants2.json'), ('Cuffed pants', L + 'pants/legs_cuffed.json'),
   ('Slacks', L + 'pants/legs_formal.json'), ('Pinstripes', L + 'pants/legs_formal_striped.json'), ('Baggy pants', L + 'pants/legs_pantaloons.json'),
   ('Shorts', L + 'shorts/legs_shorts.json'), ('Short shorts', L + 'shorts/legs_shorts_short.json'), ('Leggings', L + 'leggings/legs_leggings.json'),
   ('Leggings 2', L + 'leggings/legs_leggings2.json'), ('Tights', L + 'leggings/legs_hose.json'), ('Skirt', L + 'skirts/legs_skirts_plain.json'),
   ('Slit skirt', L + 'skirts/legs_skirts_slit.json'), ('Pleated skirt', L + 'skirts/legs_skirts_legion.json'), ('Pencil skirt', L + 'skirts/legs_skirt_straight.json'),
   ('Long skirt', L + 'skirts/legs_skirt_belle.json'), ('Overskirt', L + 'skirts/legs_skirt_overskirt.json'), ('Leg armor', L + 'legs_armour.json')]),
 ('feet', 'Shoes', 1, [('Barefoot', None), ('Boots', F + 'boots/feet_boots_basic.json'), ('Folded boots', F + 'boots/feet_boots_fold.json'),
   ('Rimmed boots', F + 'boots/feet_boots_rim.json'), ('Tall boots', F + 'boots/feet_boots_revised.json'), ('Shoes', F + 'shoes/feet_shoes_basic.json'),
   ('Sneakers', F + 'shoes/feet_shoes_revised.json'), ('Ghillies', F + 'shoes/feet_shoes_ghillies.json'), ('Flats', F + 'shoes/feet_shoes_sara.json'),
   ('Sandals', F + 'feet_sandals.json'), ('Slippers', F + 'feet_slippers.json'), ('Ankle socks', F + 'socks/feet_socks_ankle.json'),
   ('High socks', F + 'socks/feet_socks_high.json'), ('Armored boots', F + 'feet_armour.json')]),
 ('hat', 'Headwear', 0, [('None', None), ('Bandana', HW + 'coverings/bandana/hat_bandana.json'), ('Bandana 2', HW + 'coverings/bandana/hat_bandana2.json'),
   ('Pirate bandana', HW + 'coverings/bandana/hat_bandana_pirate.json'), ('Skull bandana', HW + 'coverings/bandana/hat_bandana_pirate_skull.json'),
   ('Headband', HW + 'coverings/headbands/hat_headband_thick.json'), ('Tied headband', HW + 'coverings/headbands/hat_headband_tied.json'),
   ('Kerchief', HW + 'coverings/headbands/hat_headband_kerchief.json'), ('Hair tie', HW + 'coverings/headbands/hat_headband_hairtie.json'),
   ('Hood', HW + 'coverings/hoods/hat_hood_cloth.json'), ('Sack hood', HW + 'coverings/hoods/hat_hood_sack_cloth.json'), ('Head wrap', HW + 'coverings/hoods/hat_hood_hijab.json'),
   ('Leather cap', HW + 'hats/caps/hat_cap_leather.json'), ('Bonnie cap', HW + 'hats/caps/hat_cap_bonnie.json'), ('Tilted cap', HW + 'hats/caps/hat_cap_bonnie_tilt.json'),
   ('Cavalier hat', HW + 'hats/caps/hat_cap_cavalier.json'), ('Bowler', HW + 'hats/formal/hat_formal_bowler.json'), ('Top hat', HW + 'hats/formal/hat_formal_tophat.json'),
   ('Crown', HW + 'hats/formal/hat_formal_crown.json'), ('Tiara', HW + 'hats/formal/hat_formal_tiara.json'), ('Wizard hat', HW + 'hats/magic/hat_magic_wizard.json'),
   ('Beanie', HW + 'hats/holiday/hat_holiday_christmas.json'), ('Elf hat', HW + 'hats/holiday/hat_holiday_elf.json'), ('Santa hat', HW + 'hats/holiday/hat_holiday_santa.json'),
   ('Kettle helmet', HW + 'helmets/helmets/hat_helmet_kettle.json'), ('Nasal helmet', HW + 'helmets/helmets/hat_helmet_nasal.json'),
   ('Norman helmet', HW + 'helmets/helmets/hat_helmet_norman.json'), ('Barbarian helmet', HW + 'helmets/helmets/hat_helmet_barbarian.json'),
   ('Viking helmet', HW + 'helmets/helmets/hat_helmet_barbarian_viking.json'), ('Horned helmet', HW + 'helmets/helmets/hat_helmet_horned.json'),
   ('Mail coif', HW + 'helmets/helmets/hat_helmet_mail.json'), ('Flat-top helmet', HW + 'helmets/helmets/hat_helmet_flattop.json'),
   ('Legion helmet', HW + 'helmets/helmets/hat_helmet_legion.json'), ('Morion', HW + 'helmets/helmets/hat_helmet_morion.json'),
   ('Pointed helmet', HW + 'helmets/helmets/hat_helmet_pointed.json'), ('Spangenhelm', HW + 'helmets/helmets/hat_helmet_spangenhelm.json'),
   ('Bascinet', HW + 'helmets/helmets/hat_helmet_bascinet.json'), ('Barbuta', HW + 'helmets/helmets/hat_helmet_barbuta.json'),
   ('Sugarloaf', HW + 'helmets/helmets/hat_helmet_sugarloaf.json'), ('Close helm', HW + 'helmets/helmets/hat_helmet_close.json'),
   ('Armet', HW + 'helmets/helmets/hat_helmet_armet.json'), ('Great helm', HW + 'helmets/helmets/hat_helmet_greathelm.json'), ('Xeon helmet', HW + 'helmets/helmets/hat_helmet_xeon.json')]),
 ('face', 'Face', 0, [('None', None), ('Face mask', HW + 'accessories/facial_mask_plain.json'), ('Glasses', HW + 'accessories/glasses/facial_glasses.json'),
   ('Round glasses', HW + 'accessories/glasses/facial_glasses_round.json'), ('Nerd glasses', HW + 'accessories/glasses/facial_glasses_nerd.json'),
   ('Half-moons', HW + 'accessories/glasses/facial_glasses_halfmoon.json'), ('Cat-eye glasses', HW + 'accessories/glasses/facial_glasses_secretary.json'),
   ('Shades', HW + 'accessories/glasses/facial_glasses_shades.json'), ('Sunglasses', HW + 'accessories/glasses/facial_glasses_sunglasses.json'),
   ('Eyepatch (left)', HW + 'accessories/eyepatch/facial_eyepatch_left.json'), ('Eyepatch (right)', HW + 'accessories/eyepatch/facial_eyepatch_right.json')]),
 ('neck', 'Neck', 0, [('None', None), ('Scarf', HW + 'neck/neck_scarf.json'), ('Necktie', HW + 'neck/neck_necktie.json'), ('Bow tie', HW + 'neck/neck_bowtie.json'),
   ('Big bow tie', HW + 'neck/neck_bowtie2.json'), ('Necklace', HW + 'neck/neck_necklace.json')]),
 ('arms', 'Arms', 0, [('None', None), ('Gloves', 'arms/arms_gloves.json'), ('Bracers', 'arms/wrists/arms_bracers.json'), ('Shoulder pads', 'arms/shoulders/shoulders_pauldrons.json'),
   ('Epaulets', 'arms/shoulders/shoulders_epaulets.json'), ('Arm armor', 'arms/arms_armour.json')]),
 ('belt', 'Belt', 0, [('None', None), ('Leather belt', T + 'waist/belt_leather.json'), ('Double belt', T + 'waist/belt_double.json'), ('Loose belt', T + 'waist/belt_loose.json'),
   ('Wide belt', T + 'waist/belt_belly.json'), ('Plain belt', T + 'waist/belt_leather2.json'), ('Sash', T + 'waist/belt_sash.json'), ('Narrow sash', T + 'waist/belt_sash_narrow.json'),
   ('Rope belt', T + 'waist/belt_robe.json')]),
 ('back', 'Back', 1, [('None', None), ('Backpack', T + 'backpack/backpack.json'), ('Square pack', T + 'backpack/backpack_squarepack.json'), ('Pack straps', T + 'backpack/backpack_straps.json'),
   ('Basket', T + 'backpack/backpack_basket.json'), ('Quiver', T + 'backpack/quiver.json'), ('Jetpack', T + 'backpack/backpack_jetpack.json')]),
 ('cape', 'Cape', 0, [('None', None), ('Cape', T + 'cape/cape_solid.json'), ('Tattered cape', T + 'cape/cape_tattered.json')]),
 ('extra', 'Wounds', 0, [('None', None), ('Bandages', T + 'torso_bandages.json'), ('Arm wound', 'body/wounds/wound_arm.json'), ('Rib wound', 'body/wounds/wound_ribs.json'),
   ('Eye scar (left)', 'body/wounds/wound_eye_left.json'), ('Eye scar (right)', 'body/wounds/wound_eye_right.json'), ('Bloody mouth', 'body/wounds/wound_mouth.json'),
   ('Hook hand', 'body/prostheses/prosthesis_hook.json'), ('Peg leg', 'body/prostheses/prosthesis_peg_leg.json')]),
]
SHARED_HAIR = {'hair', 'beard', 'brows'}
NO_COLOR = {'extra'}
# The old fixed survivors become starting presets: slot -> (option label, colour name)
PRESETS = [
 ('Alex', 'male', 'light', 'blue', 'dark_brown', {'hair': 'Messy1', 'shirt': ('Short sleeve', 'gray'), 'jacket': ('Jacket', 'leather'), 'legs': ('Pants', 'charcoal'), 'feet': ('Boots', 'brown'), 'back': ('Backpack', 'leather')}),
 ('Maya', 'female', 'olive', 'brown', 'black', {'hair': 'Long', 'shirt': ('Short sleeve', 'maroon'), 'legs': ('Pants', 'navy'), 'feet': ('Boots', 'black'), 'back': ('Backpack', 'forest')}),
 ('Rook', 'male', 'brown', 'brown', 'black', {'hair': 'Plain', 'shirt': ('Long sleeve', 'forest'), 'legs': ('Pants', 'tan'), 'feet': ('Boots', 'black'), 'back': ('Backpack', 'gray'), 'beard': 'Stubble'}),
 ('Jade', 'female', 'light', 'green', 'redhead', {'hair': 'Bob', 'shirt': ('Short sleeve', 'teal'), 'legs': ('Pants', 'charcoal'), 'feet': ('Boots', 'brown'), 'back': ('Backpack', 'tan')}),
 ('Diesel', 'male', 'black', 'brown', 'black', {'hair': 'Bedhead', 'shirt': ('Muscle shirt', 'charcoal'), 'legs': ('Pants', 'blue'), 'feet': ('Boots', 'black'), 'back': ('Backpack', 'black'), 'hat': ('Bandana', 'red')}),
 ('Wren', 'female', 'bronze', 'gray', 'blonde', {'hair': 'Messy1', 'shirt': ('Long sleeve', 'sky'), 'legs': ('Pants', 'black'), 'feet': ('Boots', 'tan'), 'back': ('Backpack', 'navy')}),
 ('Cole', 'male', 'amber', 'green', 'ginger', {'hair': 'Parted', 'shirt': ('Short sleeve', 'white'), 'jacket': ('Jacket', 'forest'), 'legs': ('Pants', 'forest'), 'feet': ('Boots', 'brown'), 'back': ('Backpack', 'walnut'), 'beard': 'Full beard'}),
 ('Ivy', 'female', 'taupe', 'purple', 'platinum', {'hair': 'Long', 'shirt': ('Long sleeve', 'purple'), 'legs': ('Pants', 'gray'), 'feet': ('Boots', 'black'), 'back': ('Backpack', 'purple')}),
 ('Medic', 'female', 'amber', 'brown', 'dark_brown', {'hair': 'Ponytail', 'shirt': ('Button-up', 'white'), 'legs': ('Slacks', 'white'), 'feet': ('Shoes', 'white'), 'face': ('Face mask', None), 'back': ('Square pack', 'red')}),
 ('Hunter', 'male', 'light', 'green', 'chestnut', {'hair': 'Unkempt', 'beard': 'Winter beard', 'shirt': ('Long sleeve', 'red'), 'jacket': ('Vest', None), 'legs': ('Pants', 'brown'), 'feet': ('Tall boots', 'brown'), 'hat': ('Leather cap', None), 'back': ('Quiver', None)}),
 ('Knight', 'male', 'olive', 'blue', 'black', {'hair': 'Plain', 'shirt': ('Long sleeve', 'navy'), 'jacket': ('Plate armor', 'steel'), 'legs': ('Leg armor', 'steel'), 'feet': ('Armored boots', 'steel'), 'arms': ('Arm armor', 'steel'), 'hat': ('Great helm', 'steel'), 'cape': ('Cape', 'red')}),
 ('Punk', 'female', 'light', 'purple', 'pink', {'hair': 'Spiked liberty', 'shirt': ('Tank top', None), 'legs': ('Leggings', 'black'), 'feet': ('Tall boots', 'black'), 'face': ('Sunglasses', None), 'arms': ('Bracers', 'iron'), 'extra': 'Bandages'}),
]

# ---------------- reading LPC parts ----------------
def matinfo(df):
    """[(material, slot)] for the recolourable materials of a definition; slot 1 is the main colour."""
    rc = df.get('recolors') or {}
    if 'material' in rc: return [(rc['material'], 1)] if rc['material'] in PAL else []
    out = []
    for k, v in rc.items():
        if k.startswith('color_') and isinstance(v, dict) and v.get('material') in PAL: out.append((v['material'], int(k[6:])))
    return out

def sublayers(df, build):
    out = []
    for k in sorted(k for k in df if k.startswith('layer_')):
        Lr = df[k]; sub = Lr.get(build)
        if not sub: continue
        out.append((Lr.get('zPos', 50), sub))
    return out

def available(df, build): return bool(sublayers(df, build))

def anim_file(sub, anim, variant):
    f = os.path.join(R, 'spritesheets', sub, anim, variant.replace(' ', '_') + '.png') if variant else os.path.join(R, 'spritesheets', sub, anim + '.png')
    return f if os.path.exists(f) else None

def assemble(sub, variant):
    """One Dead Wave sheet (8x17 frames) for a single LPC layer."""
    sheet = Image.new('RGBA', (8 * FW, 17 * FW), (0, 0, 0, 0)); any_art = False
    for key, row0, anim, cols, nrows in LAYOUT:
        f = anim_file(sub, anim, variant); use = cols
        if f is None:
            if key == 'death': continue
            f = anim_file(sub, 'walk', variant)
            if f is None: continue
            use = list(range(1, 9)) if key == 'run' else [0] * len(cols)
        im = Image.open(f).convert('RGBA'); n = im.size[0] // FW; any_art = True
        for r in range(nrows):
            srow = r if im.size[1] >= (r + 1) * FW else 0
            for i, c in enumerate(use):
                c = min(c, n - 1)
                sheet.alpha_composite(im.crop((c * FW, srow * FW, (c + 1) * FW, (srow + 1) * FW)), (i * FW, (row0 + r) * FW))
    return sheet if any_art else None

def colours(img):
    return Counter(p[:3] for p in img.getdata() if p[3])

def best_ramp(cnt, material):
    """Which named ramp of a palette the art is drawn in (most matching pixels)."""
    best, score = None, 0
    for name, ramp in PAL[material].items():
        s = sum(cnt.get(hx(h), 0) for h in ramp)
        if s > score: best, score = name, s
    return best, score

FILES = {}
def save(img):
    data = img.tobytes(); h = hashlib.sha1(data + bytes(img.size[0])).hexdigest()[:10]
    if h not in FILES:
        os.makedirs(OUT, exist_ok=True); img.save(os.path.join(OUT, h + '.png'), optimize=True); FILES[h] = True
    return 'lpc/parts/' + h + '.png'

# ---------------- palettes ----------------
PALETTES = {}
def ulpc_palette(material, names=None):
    key = material
    if key not in PALETTES:
        names = names or list(PAL[material])
        PALETTES[key] = {'names': [title(n) for n in names], 'ids': names, 'ramps': [[h.upper().lstrip('#') for h in PAL[material][n]] for n in names]}
    return key

def variant_palette(key, sub, variants):
    """Palette for a part that ships one PNG per colour: map the first variant's colours onto every other variant, pixel by pixel."""
    if key in PALETTES: return key
    base_f = anim_file(sub, 'walk', variants[0]); base = Image.open(base_f).convert('RGBA')
    bpx = list(base.getdata()); order = [c for c, _ in colours(base).most_common(48)]
    order.sort(key=lambda c: c[0] * 299 + c[1] * 587 + c[2] * 114)
    ids, names, ramps = [], [], []
    for v in variants:
        f = anim_file(sub, 'walk', v)
        if not f: continue
        im = Image.open(f).convert('RGBA')
        if im.size != base.size: continue
        votes = {}
        for a, b in zip(bpx, im.getdata()):
            if a[3] and b[3]: votes.setdefault(a[:3], Counter())[b[:3]] += 1
        ramp = []
        for c in order:
            t = votes.get(c).most_common(1)[0][0] if votes.get(c) else c
            ramp.append('%02X%02X%02X' % t)
        if ramp in ramps: continue
        ids.append(v); names.append(title(v)); ramps.append(ramp)
    PALETTES[key] = {'names': names, 'ids': ids, 'base': ['%02X%02X%02X' % c for c in order], 'ramps': ramps}
    return key

# ---------------- building ----------------
def part_entry(path, slot_id):
    """Build one option for every body type. Returns {'files': {build: [[z, file, recolours]]}, 'pal': name, 'def': colour index}."""
    df = d(path); variants = df.get('variants'); mats = matinfo(df); files = {}; pal = None; dflt = 0
    for build, _ in BUILDS:
        subs = sublayers(df, build)
        if not subs: continue
        lst = []
        for z, sub in subs:
            if variants:
                img = assemble(sub, variants[0])
                if img is None: continue
                rc = []
                if slot_id not in NO_COLOR and len(variants) > 1:
                    pal = variant_palette('v:' + os.path.basename(path)[:-5], sub, variants); rc = [[pal, -1, 'c']]
                lst.append([z, save(img), rc])
            else:
                img = assemble(sub, None)
                if img is None: continue
                cnt = colours(img); rc = []
                for material, slot in mats:
                    base, score = best_ramp(cnt, material)
                    if not score: continue
                    if material == 'body': src = 'k'
                    elif material == 'eye': src = 'e'
                    elif slot_id in SHARED_HAIR and material == 'hair': src = 'h'
                    elif slot == 1 and slot_id not in NO_COLOR: src = 'c'
                    else: continue  # secondary colours keep the art's own colour
                    pk = ulpc_palette(material); bi = PALETTES[pk]['ids'].index(base)
                    rc.append([pk, bi, src])
                    if src == 'c': pal = pk; dflt = bi
                lst.append([z, save(img), rc])
        if lst: files[build] = lst
    return {'files': files, 'pal': pal, 'def': dflt}

def build_all():
    ulpc_palette('body', SKIN_NAMES); ulpc_palette('hair'); ulpc_palette('eye')
    slots = []
    for sid, label, dflt, opts in CATALOG:
        out = []
        for name, path in opts:
            if path is None: out.append({'name': name}); continue
            e = part_entry(path, sid)
            if not e['files']: print('  skipped (no art):', path); continue
            o = {'name': name, 'files': e['files']}
            if e['pal'] and sid not in SHARED_HAIR: o['pal'] = e['pal']; o['def'] = e['def']
            out.append(o)
        # "None" shirt only for the masculine build
        if sid == 'shirt': out[0]['builds'] = ['male']
        slots.append({'id': sid, 'name': label, 'def': dflt, 'options': out}); print(f'{label}: {len(out)} options')
    body = {}
    for build, _ in BUILDS:
        lst = []
        for path in ['body/body.json', f'head/heads/human/heads_human_{build}.json', 'head/nose/head_nose_straight.json']:
            df = d(path)
            for z, sub in sublayers(df, build):
                img = assemble(sub, None); cnt = colours(img); rc = []
                for material, _ in matinfo(df):
                    base, score = best_ramp(cnt, material)
                    if score: pk = ulpc_palette(material); rc.append([pk, PALETTES[pk]['ids'].index(base), 'k' if material == 'body' else 'e'])
                lst.append([z, save(img), rc])
        body[build] = lst
    presets = []
    for name, build, skin, eye, hair, sel in PRESETS:
        p = {'name': name, 'b': [b for b, _ in BUILDS].index(build), 'k': PALETTES['body']['ids'].index(skin), 'e': PALETTES['eye']['ids'].index(eye),
             'h': PALETTES['hair']['ids'].index(hair), 'sel': {}}
        for s in slots:
            want = sel.get(s['id']); o_name, c_name = (want if isinstance(want, tuple) else (want, None))
            if o_name is None:
                idx = 0 if s['options'][0].get('files') is None else s['def']
                if s['id'] in ('legs', 'feet', 'brows'): idx = s['def']
                p['sel'][s['id']] = [idx, s['options'][idx].get('def', 0)]; continue
            idx = next(i for i, o in enumerate(s['options']) if o['name'] == o_name)
            o = s['options'][idx]; ci = o.get('def', 0)
            if c_name and o.get('pal'): ci = PALETTES[o['pal']]['ids'].index(c_name)
            p['sel'][s['id']] = [idx, ci]
        presets.append(p)
    pals = {k: {kk: vv for kk, vv in v.items() if kk != 'ids'} for k, v in PALETTES.items()}
    return {'builds': [n for _, n in BUILDS], 'buildIds': [b for b, _ in BUILDS], 'skin': 'body', 'eye': 'eye', 'hair': 'hair',
            'palettes': pals, 'body': body, 'slots': slots, 'presets': presets}

def fetch():
    pats = set()
    for _, _, _, opts in CATALOG:
        for _, path in opts:
            if not path: continue
            df = d(path); vs = df.get('variants')
            for build, _ in BUILDS:
                for z, sub in sublayers(df, build):
                    for a in ANIMS:
                        if vs: pats.add(f'/spritesheets/{sub}{a}/{vs[0].replace(" ", "_")}.png')
                        else: pats.add(f'/spritesheets/{sub}{a}.png')
                    if vs: pats.add(f'/spritesheets/{sub}walk/')
    for build, _ in BUILDS:
        for path in ['body/body.json', f'head/heads/human/heads_human_{build}.json', 'head/nose/head_nose_straight.json']:
            for z, sub in sublayers(d(path), build):
                for a in ANIMS: pats.add(f'/spritesheets/{sub}{a}.png')
    pats |= {'/sheet_definitions/', '/palette_definitions/'}
    p = subprocess.run(['git', '-C', R, 'sparse-checkout', 'set', '--no-cone', '--stdin'], input='\n'.join(sorted(pats)), text=True)
    print(len(pats), 'patterns', p.returncode)

if __name__ == '__main__':
    if sys.argv[1:] == ['fetch']: fetch(); sys.exit()
    parts = build_all()
    man = json.load(open(MANIFEST)); sv = man['characters']['survivor']
    sv.pop('variants', None); sv.pop('sheet', None); sv.pop('layers', None); sv['parts'] = parts
    json.dump(man, open(MANIFEST, 'w'), indent=1)
    print('files:', len(FILES), 'palettes:', len(PALETTES))
