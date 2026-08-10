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
      `RampRecolor`. Control lands at **0.50% for the tools and 2.20% for the armour**; the armour's
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
      covers sword/pickaxe/axe/shovel/hoe. Both share `RampRecolor` with the copper sets.
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
      Needs an in-game look. The legacy custom-model path now works, but the bed derivation itself
      should be revisited: its cloth mask still bleeds slightly and the derived side colour does not
      yet match the top as closely as it should.
- [x] Cherry wood (initial): `CherryWood` derives cherry bark, log end and planks from the pack's oak
      counterparts. The source art supplies grain and resolution; the three target bands are measured
      from modern vanilla. Stripped wood and the rest of the cherry set remain separate work.
- [x] Cherry leaves (initial): `CherryLeaves` repaints the pack's oak canopy to pale pink while retaining
      its own cutout and branch pattern. Cherry's blossom-cluster silhouette is new art, so this needs an
      in-game judgement call rather than a pixel-match claim.
- [x] Pale oak (initial): `PaleOak` derives the log, end, planks and leaves from oak/birch sources. Its
      bark is a material approximation over oak grain; its distinctive cracked silhouette needs separate
      work if the initial result does not read well in-game.
      Log tops infer their bark perimeter from the pack's own edge and centre colours, so rounded HD
      end grain (such as PureBDcraft's) keeps its real boundary while matching the side bark palette.
- [x] Stripped logs (initial): `StrippedLogs` derives stripped oak, spruce, birch, jungle, acacia, dark
      oak, cherry and pale oak logs. Legacy has no stripped art, so the pack's log grain is repainted to
      modern exposed-wood bands; the top uses the source-aware bark mask. Mangrove, bamboo and nether
      woods remain unserved because they have no compatible legacy source.
- [x] Mangrove (initial): `Mangrove` derives its log, end, planks and leaves from jungle; `StrippedLogs`
      also now derives stripped mangrove logs. Roots, propagules and the rest of the mangrove family need
      separate source and geometry decisions.
- [x] Path Blocks: can be derived from grass blocks
      — `DirtPath`, both faces. Vanilla's own pair turned out to say exactly how the block is built.
      `dirt_path_side` is `dirt` with the grass block's fringe painted over it: its crust falls on
      precisely the pixels `grass_block_side_overlay` marks, shifted **down one pixel**, 240 of 240
      agreeing, and 193 of the 195 pixels outside that mask are `dirt` itself at the same coordinate.
      Row 0 is transparent, which `models/block/dirt_path.json` explains — the block is 15/16 tall and
      its side UV starts at `v=1`, so that row is never sampled. `dirt_path_top` is dirt's palette
      flattened and warmed: mean luminance x1.21, spread down to a standard deviation of 9.2 from
      22.9, hue +14.7 degrees, saturation x1.11. The seven packs that drew their own path for 1.9-1.12
      agree independently — 14 to 21 degrees of hue, a 1.15x to 1.24x lift — and one of them draws in
      greyscale throughout, which is why saturation is a multiplier on the pack's own rather than a
      target to reach.
      What the top's *pattern* is made of is the only thing vanilla leaves open, since its path top is
      noise unrelated to anything else. It comes from `grass_block_top`, so the path is not a
      recoloured dirt block; flattening the spread to `spread` is what stops it reading as grass
      instead, taking a pack's blades down to a mottle. Both faces are levelled against their own
      source's mean and deviation, which is what lets grass (17.7) and dirt (22.9) land on one palette
      without either being special-cased, so the top and the side's crust agree.
      `crust_rows` is a clamp, not a description, and it is the one thing holding the side together:
      corpus overlays are nothing like uniform — vanilla's fringe ends at row 3, 13 packs run to row
      5, 11 to rows 6-7, and 4 tint the whole side, which unclamped would repaint every pixel and
      leave a block that is solid crust. It ships at 8 on how it reads in game, not on the control,
      which wants 4: at 8 the pack's own fringe is what shapes the crust nearly everywhere, and only
      the 16 packs that would have swallowed the face are held back, at half of it. The control is
      indifferent either way — vanilla's own overlay stops at row 3, so the clamp never binds on it.
      A pack whose overlay is missing, odd-sized or drawn entirely transparent (5 of 83) gets a flat
      band instead of being declined; a trodden edge is straight anyway, and the fringe only ever
      ragged it.
      Control matches to about a count on every measure — top 123.82/9.28/40.6°/0.557 against
      vanilla's 123.31/9.13/40.7°/0.559 — and below the crust the side is byte-identical to vanilla's,
      since there it *is* `dirt.png`. What is left is that the derived top is a finer field than the
      control: vanilla's is 4 shades against grass top's 66. `levels` posterises it back and ships off,
      because that is right for 16x art and wrong for a 128x pack's gradients; it is the first thing to
      sweep. Serves and lists on 61 of 69 packs (60 both faces, 1 the side alone), the other 8 having
      no dirt at all, and no pack has more than half its side repainted. `block_textures.json` also
      now maps `dirt_path_*` to `grass_path_*`, so the 7 packs that shipped real 1.9-1.12 path art use
      their own instead of a derived one.
- [x] Concrete powders block: can be derived from sand
      — `ConcretePowder`, and `Concrete` for the hardened blocks, both off the pack's own sand. The two
      are the same sixteen colours at two contrasts, so the measurements live in one table
      (`ConcreteColor`) and each block reads its own column. Nothing of vanilla's *pattern* is copied,
      and there is nothing there to copy: the powders' noise correlates with vanilla's sand at |r| <
      0.13 across all sixteen. Only the band is taken — hue, saturation, mean and spread — and the
      pack's sand is re-levelled onto it by `Palette`, now shared with `DirtPath`. Control lands at
      **2.00% for the powders and 0.51% for the hardened blocks**; across the corpus every derived
      colour sits within a few luminance counts of vanilla's own (white powder: vanilla 227.1, corpus
      median 228.0, range 222-246). Serves and lists all 32 on 59 of 69 packs, none partial, the other
      10 having no usable sand.
      Two constants are judgement rather than measurement. `follow_source` (0.35) is how far the band
      tracks the pack's own sand brightness instead of vanilla's absolute value — 0 makes every pack's
      white concrete equally white, 1 makes a dark pack's concrete dark, and the default splits it.
      And `spread_scale` on the hardened block defaults to 2.5 rather than 1: vanilla's concrete is
      flat to within a deviation of 1, so reproducing it exactly would be sixteen fills in vanilla's
      own colours — a derivation that derives nothing. Above 1 the pack's grain shows through, which
      is the whole point of deriving it. Both want an in-game look.
- [ ] Iron Nuggets / Copper Nuggets: can be derived from gold nuggets
- [ ] Breeze Rods: can be derived from Blaze Rods
- [ ] Recovery Compass: can be derived from Compass
- [ ] Soul fire / soul torch: can be derived from fire / torch
- [ ] Pillagers: can probably be derived from villagers
- [ ] Trap doors: from their respective wood (very experimental)
- [x] White Dye: should be derived from another dye (magenta for example)
      — `WhiteDye` from the pack's magenta, and with it `BlackDye`, `BlueDye` and `BrownDye`, which had
      the same defect: 1.8.9 had no separate item for any of these four, so `dye_powder_white` *is*
      bone meal, `dye_powder_black` the ink sac, `dye_powder_blue` lapis and `dye_powder_brown` cocoa
      beans. `item_textures.json` was handing that art to both members of each pair, so a legacy pack
      rendered white dye as bone shards and black dye as an ink sac. The four mappings are gone (which
      is what lets the derivation run at all — `getResource` reaches a derivation only once the pack
      has nothing of its own), and `ink_sac`, `lapis_lazuli` and `cocoa_beans` are now mapped, having
      been absent entirely and falling back to vanilla's art while the pack's own went unused.
      All four are `RampRecolor` (renamed from `MetalRecolor`, since it is a luminance ramp and these
      are not metals) via `DyeRecolor`, one source to one output, `keep_hue` at 0 — a white dye that
      keeps the pack's hue is not white, and with the hue gone the choice of source dye is only a
      choice of which pile is drawn most typically. `auto_level` does the work here and runs at 1.0
      for white, black and brown: it pins each dye on vanilla's own mean, and the corpus lands within
      half a count of it (black: vanilla 46.5, corpus 47.0-47.3).
      **Only the powder is recoloured, not what it sits on.** PureBDcraft draws each dye as a heap on a
      folded sheet of paper, and repainting the whole icon turned the paper white or black along with
      the powder. The pack answers which pixels are which the same way it answers handle-versus-head in
      `CopperTools`: the powder is what it *changed* between its twelve dyes, the sheet is what it did
      not, asked per pixel and pooled over the other eleven. A tolerance rather than exact equality,
      because BDcraft's paper catches a faint cast from the powder above it — on an equality test two
      thirds of the sheet still reads as dye and a tan wedge comes out tinted. The split is not
      delicate: its paper moves at most 5 counts between dyes and its powder 120 or more, so
      `dye_change` sits in an empty gap ~60 wide and anywhere in it classifies the same 53% of the
      icon, pile plus the corner swatch. Inert everywhere else — 58 of the 60 packs with a comparable
      dye set keep every pixel and the 59th keeps 98.8%, since they draw no backdrop to exclude.
      Control reads **20.75% white, 13.41% black, 12.32% blue, 14.92% brown**, which needs its floor
      quoted beside it: the same dye drawn in both eras scores 11.6% to 20.0% against itself (magenta,
      the source, is 18.15%), because the item was redrawn between 1.8.9 and 26.2 and no recolour can
      close a silhouette. On the pixel metric black dye even scores *worse* than the ink sac it
      replaces, 13.41% against 13.59% — an ink sac is a dark blob and black dye is dark, and the
      metric cannot see that one of them is the wrong item. Judge these by eye. Serves and lists on 52
      of 69 packs, none partial.
- [x] Suspicious Sand: same as suspicious gravel but with sand
      — `SuspiciousSand`, sharing `SuspiciousBlock` with the gravel it was split out of. It wants
      gravel's exact numbers, which is worth recording because the raw figures suggest otherwise:
      vanilla's last stage takes 13.2 luminance off sand against only 8.3 off gravel, but sand is the
      brighter field and as a proportion of each block's own mean those are 6.40% and 6.44%, the same
      darkening twice. The overlay is a multiply and so is proportional in the same way, landing both
      blocks at 13.3% of their own mean. Control lands at **5.46%** (gravel's is 7.56%), though the
      control barely constrains this one — vanilla perturbs individual pixels rather than overlaying
      anything, and the metric is happiest when the derivation does nothing at all. Serves and lists
      all four stages on 59 of 69 packs, none partial.
      Worth noting for whenever gravel gets its in-game look: both blocks darken about twice as hard
      as vanilla's own art does (13.3% against 6.4%). That is gravel's tuned value carried across
      deliberately, not a slip, but if the hollow reads too heavy in game it is one number for both.
- [x] Honey Blocks: from slime blocks — `HoneyBlock` repaints the pack's slime mottle into the three
      vanilla honey face bands, preserving its relative transparency. Serves all three faces or none;
      standalone validation found 60 usable slime textures in the 83-pack zip corpus.
- [ ] Frosted Ice: from packed ice or regular ice

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
- [ ] Clouds — **needs reworking, the in-game result is bad**
      The scale half below is settled and correct; the *appearance* is not, and a second attempt
      should probably start by questioning whether a per-cell binary mask can represent these sheets
      at all. What has been tried and rejected, so it is not tried again:
      - cut at `alpha < 10` (modern's own): a soft-painted pack's halo turns solid, 35% of cells
        against vanilla's 27.6%, in continents rather than puffs — a sky roofed with slabs;
      - cut at half opacity: erases such a pack's clouds outright;
      - cut at the sheet's own ink (`sum(alpha)/255`, currently shipped): numerically principled and
        leaves hard-edged sheets untouched, but on PureBDcraft it lands at 8.6% of cells and reads as
        thin and wispy — closer to right than the first attempt, still not good.
      Worth weighing next time: that modern's clouds are *geometry*, not a texture, may simply mean
      an HD painterly sheet has no faithful rendition — in which case **declining** to convert a
      non-256 sheet (so vanilla's own clouds render, unmistakably not the pack's art but not wrong
      either) may beat any threshold. Judge it in game against 1.8.9 side by side rather than on
      coverage numbers, which is what led the first two attempts astray.
      — served correctly all along (the path never changed, so they pass straight through), but the
      two eras read the sheet at different scales, so anything other than 256x256 came out the wrong
      size. 1.8.9's `renderCloudsFancy` steps its texture coordinate by a hardcoded `1/256` per
      12-block cell: the whole file is stretched over 256 cells whatever its resolution, so an HD
      pack's clouds covered the same 3072 blocks vanilla's did and the extra pixels only ever added
      sub-cell detail. 26.2's `CloudRenderer.prepare` builds one cell per **pixel** and tiles at
      `width * 12` blocks, so the same file renders at `width / 256` scale — 4 of the 21 packs that
      ship one were affected, worst of them PureBDcraft at 2048px: clouds eight times too large over
      a 24,576-block period, off a 4.2M-entry cell array rebuilt on every reload.
      `computeCloudsTexture` resamples anything that isn't 256x256 onto that grid, **by coverage,
      not by colour**: `encodeFace` writes three bytes of position and direction per face and takes
      the colour from a uniform, so the pixel's own colour never reaches the GPU and the only thing
      a cell decides is present-or-absent. A cell is drawn when at least half the pixels it covers
      are cloud, which is the silhouette 1.8.9 showed at its own 256-cell granularity. The 13 packs
      already at 256x256 are passed through byte-for-byte rather than round-tripped through ImageIO,
      and the 4 whose "clouds" is a single transparent pixel — the pre-1.13 way of turning clouds
      off — upsample to an empty sheet and still render nothing, which is why the resample has to
      handle a source smaller than the grid rather than only shrinking.
      **Which pixels count as cloud is the whole difficulty**, and getting it wrong is what the first
      attempt did: it cut at `isCellEmpty`'s own `alpha < 10`, which is right for vanilla-descended
      art (binary, cloud at 255 over empty at 1 — and note 1.8.9's empty sky is alpha 1, under
      modern's threshold by coincidence rather than design, so the common sheet was never at risk)
      and badly wrong for a soft-painted one. PureBDcraft is the extreme: **no** pixel in its sheet is
      fully opaque, 4.6% reaches 128, and 21% sits between 10 and 63 — a halo 1.8.9 drew at under a
      tenth opacity, which modern cannot draw at all, since a cell is either a solid 12-block box or
      nothing. Cutting at "visible" turned that halo solid: 35% of all cells against vanilla's 27.6%,
      in sprawling continents rather than puffs, which in game is a sky roofed over with slabs.
      Cutting at half-opacity instead erases such a pack's clouds outright.
      The measure that serves both is **ink** — `sum(alpha)/255`, how many pixels' worth of opaque
      cloud the sheet holds. `cloudAlphaThreshold` picks, per sheet, the alpha whose mask holds that
      much, which on a soft sheet recovers about the mask the paint was spread from (blur conserves
      ink) and on a hard-edged one lands at the top of the ramp and changes nothing. PureBDcraft
      35.2% → 8.6% of cells, Default Low Fire 37.7% → 10.3%, Nightshade 33.4% → 14.6%, while 30.zip
      — 21.9% of its pixels genuinely opaque — keeps 27.1%. No constant of its own, and no per-pack
      tuning, which a threshold picked by eye on four sheets would not have survived.
      Sodium was a red herring worth recording: it is installed in the test instance and does replace
      cloud rendering, but 0.9.2 only overrides `buildMesh` and reuses vanilla's `TextureData`, so
      vanilla's `prepare` is still what reads the sheet and the alpha rule above is still the one
      that matters.
      Needs an in-game look.
- [x] Noteblocks
      — 1.8.9's file is `blocks/noteblock.png`, one word; modern asks for `block/note_block.png`.
      No entry in `block_textures.json`, so it fell through to identity, looked for
      `blocks/note_block.png` and found it in 2 of 70 packs (both of which happen to ship
      post-flattening names). 51 packs had note block art that went nowhere. Jukebox, checked at the
      same time, was fine — its two file names never changed, so identity carries it on all 52 packs
      that ship the art, and `record_*` -> `music_disc_*` was already mapped.
      The audit afterwards is the useful part: rather than checking the entries that exist (which is
      what the oak door and quartz pillar fixes did, and neither would have caught this), list the
      **1.8.9 files nothing can reach** — not a value in the map, and not a name modern still uses.
      29 of 373 block files came back, and once the ones consumed by a derivation or a synthesized
      texture are set aside, every one of the rest was this same bug:
      `cobweb`/`web`, `lily_pad`/`waterlily`, `sugar_cane`/`reeds` (the *item* was mapped, the block
      was not), `nether_portal`/`portal`, `spawner`/`mob_spawner`, `slime_block`/`slime`,
      `wet_sponge`/`sponge_wet`, `farmland_moist`/`farmland_wet`, `nether_bricks`/`nether_brick`,
      `tripwire`/`trip_wire`, `tripwire_hook`/`trip_wire_source`, `cocoa_stage0-2`/`cocoa_stage_0-2`
      (the crops group has the same rename for wheat, carrots, potatoes and nether wart — cocoa was
      simply missed), and `end_portal_frame_top`/`side`/`eye` from `endframe_*`. Each is served and
      announced on 49-61 packs now, from 1-4 before. `.mcmeta` follows the same map, so the animated
      portal and tripwire sheets bring their own frame timing across too.
      Two legacy files are left unreachable on purpose: `command_block` (1.9 split one texture into
      back/front/side, so pointing all three at it is a judgement call like the trapdoors, not a
      rename) and `itemframe_background` (no modern block texture at all).
      One asymmetry surfaced while measuring, and it predates these entries: `listResources` renames
      only the names it recognizes, so a pack shipping a post-flattening name in a pre-flattening
      tree (`textures/blocks/farmland_moist.png` — 5 in the corpus, and 3 ship `cobweb.png`) had it
      announced under that name while `getResource` resolved only the mapped one and answered
      nothing. `resolveTexture` now falls back to the modern name, which cannot be wrong — the file
      is called exactly what was asked for — and makes served match announced on every entry above.
      Measured by driving the real `LegacyPackResources` over all 69 packs plus vanilla 1.8.9:
      blockstates leaving states unmodelled stay at 0 across the corpus, and unannounced sprite
      references in served models go 757 -> 713.
      Needs an in-game look.

# Issue
- [x] Some scaling is happening to block textures in some pack, causing all blocks to look blurry, including blocks using vanilla textures
- [x] Quartz pillar: the top face converted but the side face stayed vanilla
      — the map was keyed `quartz_pillar`, which is what the side texture was called between 1.13 and
      its rename to `quartz_pillar_side`. Modern asks for `quartz_pillar_side`, found no entry, fell
      through to identity and looked for `blocks/quartz_pillar_side.png`, which **no** pack ships; 67
      ship `quartz_block_lines`. The top's entry was right all along, hence one face working and not the
      other. Same shape as the oak door bug but the opposite cause: there the *legacy* name was wrong,
      here the *modern* one went stale.
      Both mapping tables are now clean in both directions — every legacy target exists in
      `reference/1.8.9`, every modern key exists in `reference/26.2` — bar the two below.
- [x] PureBDcraft: many blocks render as pink/black cubes, and its glass pane and door item icons too
      — the packs this hits are the ones that ship models and blockstates of their own rather than
      textures alone (22 of 69 in the corpus, PureBDcraft among them; a texture-only pack was never
      affected because it leans on vanilla's models, which do resolve). Two independent causes, either
      one fatal on its own:
      1. **Models were served raw.** The rewrite branch in `listResources` tested
         `isOrUnder(directory, "models/block")`, but `ModelManager` asks for the whole `models` tree in
         one pass — and `"models"` is neither equal to nor under `"models/block"`, so every pack model
         fell through to the unconverted delegate listing. Its texture references stayed
         `blocks/cobblestone`, a sprite the block atlas (which enumerates `textures/block`) does not
         contain: 390 missing sprite references in PureBDcraft alone, 1816 in vanilla 1.8.9's own tree.
         Both trees only ever reach the game *through* listing, so the fix routes each listed file
         through `getResource` — one path, one set of decisions.
      2. **Blockstates were only string-rewritten.** A legacy blockstate names its models bare
         (`"model": "cobblestone"`, relative to `models/block/`) and spells "every state" as `"normal"`;
         modern reads the first as `minecraft:cobblestone` and throws on the second. 31 of PureBDcraft's
         42 blockstates left *every* state of their block unmodelled — stone, cobblestone, dirt, sand,
         gravel, ice, sandstone, glass panes, iron doors, torches, ladders, levers. `BlockstateConverter`
         now converts them properly and refuses, per file, anything it cannot convert completely, so
         vanilla's own blockstate stays in play instead of a half-working one. Same call the
         `redstone_wire` skip already made, generalized.
      Models get the same all-or-nothing treatment, since modern answers a dangling reference with the
      missing model rather than with the file being overridden: 19 of PureBDcraft's item models extend
      parents the flattening deleted (`block/glass_pane_ns`, `item/iron_door_item`) and are now left to
      vanilla, which is where those pink icons came from.
      One more collision came out of the same audit, and it is the reason a **torch** was pink in the
      three packs that copy 1.8's asset tree wholesale: the two eras split their models at different
      points. 1.8's `models/block/torch.json` is bare geometry over an unbound `#torch`, bound by
      `normal_torch.json` extending it; modern made `block/torch` the finished model and
      `block/template_torch` the geometry. Served under that name, vanilla's blockstate gets a model whose
      every face is textureless. A parentless model that leaves a variable unbound is therefore refused —
      but only where modern ships a model of the same name, since a legacy-only template name is
      unreachable except through the pack's own models, which do bind it. That is 38 files in bluefault
      and Darkpack, 65 in the largest pack, 1 in three others, and none in PureBDcraft (whose torch model
      binds its own textures, which is why its torches were fine while its stone was not).
      A file the pack authored now settles the question by itself: converted if it converts, otherwise
      vanilla's, never a synthesized stand-in. Letting synthesis catch a refused blockstate is how a lever
      came back as a full cube (its 1.8 `facing=up_z` selectors cannot convert, but a `block/lever`
      texture resolves, so `singleVariantBlockstate` happily built one) - the fallback generators are for
      what a pack *doesn't* ship.
      Measured by driving the real conversion and then the game's own loader (`BlockStateModelDispatcher`
      parse + `instantiate`) over every pack: blockstate files leaving states unmodelled go 31 → 0 for
      PureBDcraft, 251 → 0 for vanilla 1.8.9's own tree, 264 → 0 for the largest pack in the corpus, and
      0 after across all 69, with no missing sprite references left in any served model.
      Still imprecise: `spriteResolves` (and `textureResolves` before it) asks whether a texture *exists*,
      not whether `listResources` announces it under that exact name. A reference to an already-flattened
      path carrying a pre-flattening stem - `block/stonebrick`, where the atlas only ever gets
      `block/stone_bricks` - therefore passes. Nothing in the corpus is reachable through one today, but
      the exact test is the announced-name round-trip, not file existence.
- [x] PureBDcraft: farmland, sandstone and snow render as missing-model cubes
      — the same imprecision as the note above, but on the *model* half, where the corpus does reach it.
      `modelResolves` asked whether a model could be **served**; what decides whether the game sees one is
      whether it is **announced**, and models only ever reach the game through `listResources`, which
      announces the pack's own files and nothing else. So a model this class would synthesize on demand,
      for a name the pack ships no file under, counted as resolvable while being unreachable.
      PureBDcraft ships `blockstates/farmland.json` but no models of its own, naming 1.8's `farmland_dry`;
      since it does ship `textures/blocks/farmland_dry.png`, `computeBlockModel` was glad to invent a
      `cube_all` under that name, so the blockstate was accepted, announced, and then pointed at a model
      nothing announces. Same for `sandstone` -> `sandstone_normal` and `snow` -> `snow`.
      Every one of these is a model 1.13 renamed, so refusing the blockstate is also right on the merits -
      vanilla's own over the pack's sprites is the block the pack was drawing. `snow` makes the point
      twice over: the flattening moved the *name* to the layer block and the full block became
      `snow_block`, so the pack's `blockstates/snow.json` describes a different block from the one modern
      asks that file for, and honouring it would give snow layers a full cube even if the model resolved.
      Blockstate and model *file* names are still not mapped through the flattening (only textures are),
      which is why the pack's own `snow_layer.json` remains unreachable; that mapping is the larger job,
      and refusing is the correct behaviour until it exists.
      The metric that catches this is new, and neither previous one could see it: "every state is
      modelled" only validates a blockstate's selectors, and the sprite check only looked at models the
      pack ships. Collecting every model reference out of each announced blockstate and asking whether
      that name is announced (by the pack) or shipped (by 26.2) finds **38 dangling references across 2
      packs** before, 0 after — 3 blockstates in PureBDcraft, and 35 models in Queue 32x whose parents the
      flattening deleted (`block/half_slab_oak`, `block/stonebrick_normal`, `block/carpet_*`). The
      tightening costs exactly those 38 files corpus-wide (announced blockstates 874 -> 871, models
      9144 -> 9109) and nothing else, and every one of them was a missing-model cube in game.
      Needs an in-game look.
- [x] `bone_meal` is served but never announced
      — 1.8.9 had one `dye_powder_white` where modern has both `bone_meal` and `white_dye`, so both
      entries point at it. The forward direction is right and deliberate; `TextureNameMaps.invert` was
      one-to-one, so `listResources` announced one of the two and the other silently rendered as
      vanilla's. The inverse now groups by legacy name and `listResources` emits every modern name a
      listed file is the art for. Both go to 54 and 53 packs of 70, served and announced alike.
      Measured before fixing, the note above turned out to have the wrong one: it was **`white_dye`**
      that was announced by nothing (0 of 70), not `bone_meal` (54). Which of the pair lost was decided
      by `HashMap` iteration order under `putIfAbsent` — it could have been either, and could have
      changed between runs or JDKs. So `load` now keeps the mapping file's own order (a
      `LinkedHashMap`, not `Map.copyOf`, whose iteration order is explicitly unspecified) and the
      canonical name — the one `newBlockName` hands to a model rewrite, where only one name can be
      written — is whichever the file lists first. That is a decision the file can now make rather
      than the JDK.
      Only `dye_powder_white` is claimed by two modern names today, in either map, so nothing else
      changes; the plural lookup exists for the next split, and the singular one is still what model
      rewriting uses, since a texture variable takes a single value.
- [x] `item_textures.json` maps `crossbow` -> `crossbow_standby`, which exists in neither era
      — dropped. 1.8.9 has no crossbow, and 26.2's item texture is `crossbow_standby` with no plain
      `crossbow.png`, so the entry pointed a name modern never asks for at a file no legacy pack has.
      Dead in both directions before and after: 0 packs of 70 serve or announce either name.
- [ ] Torch in first hand has bad orientation (bdcraft)
- [ ] doors in the inventory has bad orientation (bd craft model)
- [ ] doors have 3d model when held but not when placed (bd craft)
- [ ] Cauldron with snow inside shows vanilla texture
- [ ] Trapdoor custom model on bdcraft doesnt look right
- [ ] cocoa bean has a bad uv mapping (at least on bdcraft)
- [x] Command block textures: 1.8.9's single `command_block` texture is now mapped and announced for
      all regular, chain, repeating, and conditional command-block faces.
- [ ] Derive subtype-specific command block textures from the legacy command block: chain, repeating,
      and conditional command blocks currently share the original block's art, but could gain
      pack-consistent face details rather than using a direct compatibility mapping.
- [ ] beds model dont work correctly (bdcraft)
- [ ] the concrete derived from concrete powder looks too similar to the powder (bdcraft)
