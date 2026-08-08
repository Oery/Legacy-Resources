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
- [ ] Colored Beds: can be derived from the og red bed
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
