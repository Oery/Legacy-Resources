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
- [ ] Copper Ingot + Tools and armor set: Can be derived from Iron ingot and tools.
- [ ] Netherite Set: can be derived from Iron set
      — armour and tools done, derived from **diamond** rather than iron: vanilla's diamond and
      netherite art are the same silhouette twice, so the transform is a pure palette remap and comes
      out within 1-5% of vanilla's own texture on the control. `NetheriteArmor` covers the four item
      icons plus the worn `humanoid`/`humanoid_baby`/`humanoid_leggings` layers; `NetheriteTools`
      covers sword/pickaxe/axe/shovel/hoe. Both share `NetheriteRecolor`.
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

# Issue
- [x] Some scaling is happening to block textures in some pack, causing all blocks to look blurry, including blocks using vanilla textures
