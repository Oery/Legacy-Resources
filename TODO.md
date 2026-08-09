# Derivations

We should generate textures for as many new items/blocks as possible, using old available textures.

Each one is a `Derivation` in `src/client/java/.../derive/`, declaring its source textures, its output
textures, and its tunable constants. `LegacyPackResources` consults `Derivations` as a last resort,
only once the pack has been found to have no art of its own that maps to the requested texture, and
announces the results from `listResources` so atlas discovery finds them.

**Tune it with `./gradlew runLab`** (http://localhost:8642): the
derivation lab renders it across all ~70 legacy packs in `~/.minecraft/resourcepacks` at once, next to
the modern texture being recreated, with a live slider per constant and an error score against vanilla
1.8.9 as a control. Editing a derivation and refreshing the page picks up the change - no restart.
Registering a new derivation in `Derivations.ALL` is all it takes to appear there.

`GET /api/verify?d=<id>` is the check worth running before an in-game test: it confirms, per pack, that
the real `LegacyPackResources` both *serves* each output and *announces* it. The second half is the one
that fails silently - an unannounced sprite renders as vanilla's art with nothing in the log.

- [x] Suspicious Gravel: This block is based on gravel but with some impact on it, it can be derived from the gravel texture
      — `SuspiciousGravel`: all four brushing stages, from the pack's own gravel plus its
      `destroy_stage_*` overlays, blurred. Serves and lists on 58 of 69 packs; the rest lack a source
      and keep vanilla's. Needs an in-game look.
- [x] Copper Ingot + Tools and armor set: Can be derived from Iron ingot and tools.
      — the equipment is done, from iron: `CopperArmor` covers the four item icons plus the worn
      `humanoid`/`humanoid_baby`/`humanoid_leggings` layers, `CopperTools` covers
      sword/pickaxe/axe/shovel/hoe. Vanilla drew copper by repainting iron and nothing else — the two
      agree on the alpha of *every* pixel of all nine icons and all three layers, a cleaner match than
      diamond and netherite manage — so the same relative-ramp remap applies, now shared as
      `MetalRecolor`. Control lands at **0.50% for the tools and 2.20% for the armour**; the armour's
      residual is almost all the chestplate at 5.0%, which is the floor, since 1.8.9's chestplate and
      26.2's disagree on 14 of 256 alpha pixels — that art was redrawn between eras and the tools' was
      not. 57 of 69 packs serve and announce every output, none unlisted; the rest lack iron art and
      keep vanilla's.
      The one place copper parts company with netherite is the **handle, which is not recoloured** —
      vanilla's copper tools carry the iron tool's handle tones across byte for byte, and 1.8.9 painted
      those same four tones a decade earlier. Telling handle from head is asked of the pack twice, and
      either answer is decisive:
      1. **the tiers.** Every pack in the corpus that ships more than one metal does what vanilla does —
         one silhouette, the head repainted per metal, the handle left alone — so the pack has already
         said which pixels are metal: the ones it changed between its iron tool and its gold, diamond or
         stone one. Asked **per pixel**, not per colour. Per colour was the first attempt and it gets a
         fifth of the corpus wrong: a tone a pack uses on both the handle and the blade cannot be
         classified as one or the other, so 13 of 70 packs bled copper into the handle and Occult, which
         outlines its head in the same black it draws its handle with, lost its handle entirely.
         Compared on colour with alpha dropped — alpha is antialiasing coverage, not material. Tiers are
         pooled, since Occult and majesta restyle their *gold* handle but not their diamond one; safe
         because two tiers independently agree on 99% of pixels (median), and where they disagree it is
         one tier keeping what the other repainted rather than noise.
      2. **the stick**, for handles drawn as wood: they lean the same way in colour as the pack's own
         `items/stick.png`, measured as a chroma direction so shading does not matter.
      Neither alone is enough. The stick misses the 31 PvP packs that draw the handle as flat black ink,
      which has no colour direction at all — those had their handles turned copper and their contrast
      flattened until the tier test went in — and it misses pax10, whose handle is wood but whose stick
      is drawn nothing like it (8% against the tiers' 84%). The tiers miss PureBDcraft, whose 128x handle
      is softly re-shaded per tier and so never byte-identical. On vanilla the two agree exactly, 38.3%
      each, which is precisely its handle; the corpus runs on a median of 43%, and where it goes higher
      the pack really does keep that much — Blue 128x repaints barely a tenth of its tool between iron
      and gold. Scored against every pack's own tier evidence, **no pack bleeds a single pixel** and none
      is left without a mask. The tier test also keeps the black outline a pack draws *around* the head,
      since that ink does not change with the metal either; that is the pack's own styling and is what
      stops the tool reading as a foreign object beside its neighbours.
      The armour needs none of this: checked against the packs' own gold armour, they recolour it
      wholesale, outline included, which is exactly what `CopperArmor` does.
      `keep_hue` is 0 for both sets, unlike netherite's 0.4: it costs nothing on the control either
      way, but at 0.1 a pack with blue-grey iron comes out pink and at 0.25 magenta. Copper is a colour
      you can name, so it keeps none of the pack's own — the call `Beds` makes, for the same reason.
      Still to do: ingot, nugget, horse armour (its icon would work today; the worn
      `entity/equipment/horse_body` layer needs the same translation extension netherite's entry
      notes), spear and nautilus armour (no legacy relative to derive from).
- [x] Netherite Set: can be derived from Iron set
      — armour and tools done, derived from **diamond** rather than iron: vanilla's diamond and
      netherite art are the same silhouette twice, so the transform is a pure palette remap and comes
      out within 1-5% of vanilla's own texture on the control. `NetheriteArmor` covers the four item
      icons plus the worn `humanoid`/`humanoid_baby`/`humanoid_leggings` layers; `NetheriteTools`
      covers sword/pickaxe/axe/shovel/hoe. Both share `MetalRecolor` with the copper sets.
      Still to do: ingot, scrap, horse armour (needs the equipment translation extended to
      `horse_body`), and the smithing template (no legacy equivalent to derive from).
- [ ] Suspicious Stew: can be derived from soup
- [x] Colored Beds: can be derived from the og red bed
      — `Beds`: all sixteen dyes plus the shared `bed_head_north` and `bed_down`, 114 textures from the
      six legacy bed files and the pack's oak planks. Two halves. The geometry is exact — both eras read
      the mattress from the same texture rows, so a modern face is a legacy face mirrored or rotated,
      and only the leg strip is repacked. The colour is exact too: vanilla's sixteen beds are one bed
      palette-swapped over seven cloth tones, and the side and top palettes turn out to be one ramp
      (every dye's brightest side tone *is* its darkest top tone), so `RAMPS` is read straight out of
      `reference/26.2`. Red keeps the pack's own pixels untouched — unless the pack's bed is not red, and
      ten of the corpus are not: those get red built off vanilla's red ramp like any other dye, rather
      than being handed fifteen correct beds and one that lies about its colour. The corpus splits on
      this with a huge gap — 53 packs at +0.996 or above against vanilla red, ten at -0.43 or below,
      nothing at all between — so it is not a close call.
      Telling cloth from pillow is the part that took the tuning. Saturation does not do it — PureBDcraft
      draws a pale blue blanket at 0.12 saturation against a cream pillow at 0.02 — but *hue* does, and
      it need not be guessed: a bed's **foot** has no pillow on it in either era, so the cloth's colour
      direction is measured there and the head faces are classified against it, the same way the frame
      timber is measured off the legs. Measured per surface, since a pack can style the top and the sides
      apart (neptune has a teal side, a green foot top, and a head top it never restyled off vanilla).
      The frame is told from the blanket the same way, off the legs, but *comparatively* — oak and a red
      blanket agree to 0.83, so no absolute threshold splits them, while asking which of the pack's own
      two references a pixel is nearer splits them every time. Which rows hold frame is not assumed:
      vanilla starts it at row 11, Eum3 at row 10 under the pillow, Deep Sky runs the mattress past 13.
      The leg tile is scrubbed of mattress before it is stamped: the 3-wide slot it is lifted from is only
      wood in a pack whose bedpost is exactly 3 wide, and Draill's is 2, so one stray column of blanket
      would be replicated into three stripes down the legs of every bed. 14 of 63 packs had dye reaching
      the leg strip before this; none do now.
      Control lands at 3.8% mean, and the sides at 2.6% are at or under red's own floor, so what is left
      is 1.13 having redrawn the bed top rather than anything the recolour does. Of 69 legacy packs, 55
      get all 114 and `/api/verify` reports none unannounced; 3 hit the decline rule (a bed whose cloth
      cannot be told from its pillow keeps red and lets the other fifteen fall back to vanilla), the rest
      lack sources. At the shipped defaults no pack in the corpus dyes any of its own timber, and none
      misses more than 5% of its cloth or dyes more than 5% of its pillow.
      `neutral_floor` and `cloth_agreement` are both invisible to the control and set entirely by the
      corpus — see their notes in `params()` before moving them. `cloth_agreement` in particular wants to
      stay loose: the pillow is already held off by `neutral_floor` (no pack in the corpus tints one past
      it) and the frame by the timber comparison, so tightening it buys nothing and starts slicing
      blankets that are not one hue — nebula's runs a gradient cyan to violet across a single face.
      Needs an in-game look.
- [ ] Path Blocks: can be derived from grass blocks
- [ ] Concrete powders block: can be derived from sand
- [ ] Iron Nuggets / Copper Nuggets: can be derived from gold nuggets
- [ ] Breeze Rods: can be derived from Blaze Rods
- [ ] Recovery Compass: can be derived from Compass
- [ ] Soul fire / soul torch: can be derived from fire / torch
- [ ] Pillagers: can probably be derived from villagers

# Not Working

- [x] Fire
- [x] Bricks
- [x] Anvil
- [x] Sheep Wool
- [x] Tall Grass (Double Tall Grass works though)
- [x] Player Heads (block)
- [x] Redstone powder (block)
- [x] Books / Enchanted Books / Book and quill
- [x] Bone meal (i think it became a dye instead)
- [x] Fire Charges
- [x] Compass
- [x] Smooth stone slab texture
- [x] Some packs have 1.7 textures for steve which are the wrong format, they should be converted to a 1.8 skin format first
- [x] HUD (hearts, armor, food, air, XP/jump/boss bars, crosshair, hotbar, tab-list ping bars) — `icons.png`/`widgets.png` split into `gui/sprites/**`, see `reference/atlas-mappings.md` section 1
- [x] Menu buttons and sliders — `widgets.png` split into `gui/sprites/widget/**`; needs the nine-slice `.mcmeta` served too, see section 1b
- [x] Title screen logo — legacy stores it as two stacked halves, modern as one strip; same filename, so it has to be reassembled
- [x] Creative Inventory Tabs
- [x] Wooden Doors
      — two wrong entries in `block_textures.json`, both the same mistake: 1.13 renamed the default wood
      from `wood` to `oak`, and these were written from the *modern* name pattern instead of being
      checked against a real 1.8.9 file. `oak_door_bottom`/`oak_door_top` pointed at `door_oak_lower`
      /`door_oak_upper`, which 1.8.9 never had — the files are `door_wood_lower`/`door_wood_upper` —
      and `oak_trapdoor` had no entry at all, so it fell through to identity and looked for
      `blocks/oak_trapdoor.png` where 1.8.9 has plain `trapdoor.png`. Oak doors resolved on 2 of 69
      packs and oak trapdoors on 1, while every other wood type converted fine. Now 69 and 68. It broke
      the announcing half too: with no entry, `newBlockName("door_wood_lower")` returned itself, which
      the atlas does not know.
      Checked the whole map for the same class of error afterwards — of 225 block entries and 106 item
      entries these were the only ones pointing at a 1.8.9 file that does not exist.
      Still to do: the five other wood trapdoors (spruce, birch, jungle, acacia, dark oak) and the
      modern wood doors (mangrove, cherry, bamboo, crimson, warped, pale oak) have no 1.8.9 counterpart
      at all — 1.8.9 had exactly one wooden trapdoor. Pointing them all at the pack's single `trapdoor`
      would make the inverse map ambiguous and give a birch trapdoor oak art, so it is a judgement call
      rather than a fix; deriving them from the pack's own planks is the better answer.

# Issue
- [x] Some scaling is happening to block textures in some pack, causing all blocks to look blurry, including blocks using vanilla textures
