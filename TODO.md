# Derivations

We should generate textures for as many new items/blocks as possible, using old available textures.

- [ ] Suspicious Gravel: This block is based on gravel but with some impact on it, it can be derived from the gravel texture
- [ ] Copper Ingot + Tools and armor set: Can be derived from Iron ingot and tools.
- [ ] Netherite Set: can be derived from Iron set
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
