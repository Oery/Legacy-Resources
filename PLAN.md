**Goal:** Create a mod that lets players use legacy 1.8.9 resource packs in modern Minecraft (the version pinned by `gradle.properties`, currently 26.2) by converting textures, models, and other assets on the fly.

**Assumptions:**  
- The mod will intercept resource loading, not create permanent files.  
- Textures will be adapted; advanced features like sounds or fonts are optional.

---

### Checklist / Implementation Plan

#### 1. Research & mapping data
- [x] **Folder renaming**  
  Old → New  
  `assets/minecraft/textures/blocks/` → `assets/minecraft/textures/block/`  
  `assets/minecraft/textures/items/` → `assets/minecraft/textures/item/`  
  Implemented in `LegacyPackResources` (path prefix rewriting in `getResource`/`listResources`).
- [x] **File name mapping** (extensive list)  
  Curated JSON tables (`block_textures.json`, ~140 entries; `item_textures.json`, ~90 entries) cover the common 1.13 flattening renames (wool/terracotta/glass colors, wood species, doors, food items, tools/armor prefix swaps, dyes, discs, etc). Unmapped names fall back to identity, which covers the majority of texture files (validated against a real pack below). Not exhaustive — easy to extend by adding entries to the JSON files.
- [x] **Model texture references**  
  Any string in a model's `"textures"` object starting with `"blocks/"` becomes `"block/"` and `"items/"` becomes `"item/"`, via `JsonRewriter`, using the same mapping tables.

#### 2. Detect legacy packs
- [x] Every pack the game opens (resourcepacks folder, built-ins, server packs) goes through `Pack.readMetaAndCreate`, which we intercept via `PackMixin` — no separate directory scan needed.
- [x] Read `pack.mcmeta` via the vanilla `PackMetadataSection` codec; `LegacyPackDetector` treats `pack_format` **1, 2, or 3** as legacy.
- [x] `LegacyResourcesSupplier` wraps the pack's `PackResources` in `LegacyPackResources` when legacy, otherwise passes it through untouched.

#### 3. Register the wrapper as an extra resource pack
- [x] No separate `ResourcePackProvider` registration needed — since the wrapper is installed at the `Pack.readMetaAndCreate` choke point (via Mixin), every legacy pack already present in the resourcepacks folder is upgraded transparently, keeping its original name/description in the pack list.
- [x] `pack_format` is reported as the current version's (`SharedConstants.getCurrentVersion().packVersion(...)`) via an overridden `getMetadataSection`, so the game accepts the pack as compatible without a "[Legacy]" rename.

#### 4. Implement path‑aware resource interception
Override key methods in your `PackResources` wrapper:

- `InputStream getResource(PackType type, ResourceLocation location)`  
  - If the path contains `textures/blocks/` or `textures/items/`:  
    1. Translate folder to `textures/block/` / `textures/item/`.  
    2. Map the file name using your mapping table.  
    3. Read the raw PNG from the original pack and return it unchanged (the image itself doesn’t need modification).  
  - If it’s a **model** (`models/block/…`, `models/item/…`) or **blockstate** (`blockstates/…`):  
    1. Read the original JSON.  
    2. Parse it (Gson or similar).  
    3. Recursively replace any string starting with `"blocks/"` → `"block/"` and `"items/"` → `"item/"`.  
    4. Serialise back and return a `ByteArrayInputStream` of the corrected JSON.  
  - [x] **Not enough on its own, and revised:** string rewriting leaves the two format changes that actually break these files. A legacy blockstate names its models *bare* (`"model": "cobblestone"`, resolved relative to `models/block/`) and spells "every state" as `"normal"`/`"all"`; modern reads the first as `minecraft:cobblestone` and throws on the second, so the block ends up with no model at all — a pink/black cube. `BlockstateConverter` therefore rewrites the file properly (qualifying every model reference, translating the selector keys, validating selectors, rotations and weights against the block's real `StateDefinition`) and does it **all-or-nothing**: any file it cannot convert completely — unknown block, property that no longer exists, model that exists in neither the pack nor modern vanilla, a state left unclaimed or claimed twice — is refused, so vanilla's own blockstate stays in play and still picks up the pack's textures. Models are held to the same standard: one whose `parent` or whose textures no longer resolve is refused rather than served, because modern answers a dangling reference with the missing model/sprite instead of falling back to the file being overridden. "Does modern vanilla still ship this?" is answered from the running client's own built-in pack, via `ModernVanillaAssets`.
  - For all other resources (sounds, texts, font glyphs, etc.) pass through without modification (or gracefully ignore if not needed now).

- `void listResources(...)`  
  - [x] Ensure the game sees the transformed paths so pack overlays work correctly. `LegacyPackResources.listResources` wraps the original list and applies the same folder + filename mapping.
  - [x] Models and blockstates are announced by routing each listed file through `getResource`, so listing and per-file lookup cannot disagree. This is not a detail: both trees only ever reach the game *through* listing (`ModelManager` and `BlockStateModelLoader` each scan `models`/`blockstates` in one pass and never ask for an individual file), so a conversion or a refusal that only happens in `getResource` happens for nobody.

- [x] **Fallback model generator** (highly recommended)  
  Many 1.8 packs supply only textures, no models. `FallbackModelGenerator` auto-creates a `cube_all` model (block), `generated` model (item), or single-variant blockstate when the legacy pack has no model/blockstate JSON but the mapped texture exists. Confirmed necessary and working against a real texture-only 1.8.9-style pack (see Testing plan).

#### 5. Optional – language file support
- [ ] **Skipped.** Converting `.lang` → `.json` syntax alone is easy, but making it useful requires remapping hundreds of old flat keys (`tile.*.name`, `item.*.name`) to modern namespaced keys (`block.minecraft.*`, `item.minecraft.*`), which needs its own large curated mapping table. Given this was explicitly called out as a stretch goal and the core texture/model conversion covers the bulk of visual compatibility, this was left unimplemented. Custom item/block names from legacy packs will simply not apply; vanilla language stays in effect.

#### 6. Performance & caching
- [x] Converted model/blockstate JSON is cached in `LegacyPackResources.jsonCache`, a `Map<Identifier, byte[]>` (`ConcurrentHashMap`), computed once per resource location.
- [x] The cache is per-`LegacyPackResources` instance, i.e. implicitly keyed by pack (a fresh wrapper/cache is created each time the pack is opened, e.g. on `F3+T`).

#### 7. Testing plan
- [x] Tested with a real 1.8.9-style pack (`!        §cBedwars §8[§f32x§8].zip`, `pack_format: 1`, texture-only — no blockstates, almost no block/item models). Wrote a throwaway harness that loads the pack's real `PackResources` (via vanilla `FilePackResources`, bypassing the Mixin) and drives it through `LegacyPackDetector`/`LegacyPackResources` directly. Confirmed: pack detected as legacy; reported `pack_format` overridden to current; unmapped textures (majority of the pack) pass through by identity (e.g. `oak_log.png`, `wooden_sword.png`); renamed textures resolve via the mapping table (e.g. `white_wool.png` → `wool_colored_white.png`); fabricated `cube_all` block model, single-variant blockstate, and `generated` item model are produced correctly when the pack has none.
- [ ] Verify blocks, items, armour, GUI elements render correctly **in-game** — not performed; this sandboxed environment has no display and no logged-in Minecraft account to run `runClient`. The harness above verifies the underlying resource-resolution logic but not actual rendering. Recommend a manual `runClient` + F3+T smoke test.
- [ ] Test `F3 + T` reload in-game — not performed for the same reason. The wrapper holds no global state across reloads (a fresh `LegacyPackResources`/cache is created each time `Pack.readMetaAndCreate` runs), so it should reload cleanly, but this needs a manual check.
- [x] Check that missing models fall back to the generated ones — confirmed via the harness above.
- [ ] Test with other modded resources — not performed; no other resource-pack-modifying mods were available to test interaction with.
- [x] Ensure the mod does nothing if no legacy packs are present — `PackMixin` only wraps the supplier; `LegacyResourcesSupplier` only wraps `PackResources` in `LegacyPackResources` when `LegacyPackDetector.isLegacy` returns true (checked against real `pack_format` 1-3 packs and, implicitly, every vanilla/modern pack that has been loading fine throughout this testing, which all report modern pack formats and pass through untouched).
