package dev.oery.anyresource.client.convert;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.oery.anyresource.AnyResource;
import dev.oery.anyresource.client.derive.Derivation;
import dev.oery.anyresource.client.derive.Derivations;
import dev.oery.anyresource.client.derive.Params;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.InclusiveRange;
import org.jspecify.annotations.Nullable;

/**
 * Wraps a legacy (pack_format 1-3, i.e. 1.6.1-1.12.2) {@link PackResources} so that the game
 * can load it as-is: block/item textures are re-homed from the pre-flattening
 * {@code textures/blocks}/{@code textures/items} folders (and old file names) to the modern
 * {@code textures/block}/{@code textures/item} layout, model/blockstate JSON gets its texture
 * references rewritten the same way, and pack_format is reported as current so the pack shows
 * up as compatible. See PLAN.md.
 */
public final class LegacyPackResources implements PackResources {
	private static final Gson GSON = new Gson();

	/** Prefix a {@link Derivation}'s texture paths are relative to. */
	private static final String TEXTURE_DIR = "textures/";
	private static final String NEW_BLOCK_TEXTURE_DIR = "textures/block/";
	private static final String OLD_BLOCK_TEXTURE_DIR = "textures/blocks/";
	private static final String NEW_ITEM_TEXTURE_DIR = "textures/item/";
	private static final String OLD_ITEM_TEXTURE_DIR = "textures/items/";
	private static final String NEW_EQUIPMENT_TEXTURE_DIR = "textures/entity/equipment/";
	private static final String OLD_ARMOR_TEXTURE_DIR = "textures/models/armor/";
	private static final String OVERLAY_SUFFIX = "_overlay";
	private static final Set<String> LEAVES_STEMS = Set.of(
		"oak_leaves", "spruce_leaves", "birch_leaves", "jungle_leaves", "acacia_leaves", "dark_oak_leaves"
	);
	private static final Set<String> LOG_STEMS = Set.of(
		"oak_log", "spruce_log", "birch_log", "jungle_log", "acacia_log", "dark_oak_log"
	);
	/**
	 * Block model stems that must never receive the generic {@link #computeBlockModel} fallback,
	 * because their real vanilla model is a thin/sculpted shape (built from a template parent, or a
	 * multi-face plane) rather than a {@code cube_all}, yet their stem happens to exactly match a
	 * texture stem that a legacy pack can now resolve. Once that texture resolves, the generic
	 * fallback below would otherwise treat its existence as license to synthesize a flat
	 * {@code cube_all} model, discarding the real shape (and, for the anvil, the per-damage-state
	 * {@code top} texture swap). These stems must defer to vanilla's own model/blockstate JSON; only
	 * the texture bytes need remapping:
	 * <ul>
	 *   <li>{@code anvil}/{@code chipped_anvil}/{@code damaged_anvil} - {@code template_anvil}-based
	 *   sculpted shape, separate {@code body}/{@code top} texture vars.</li>
	 *   <li>{@code redstone_dust_dot} - a paper-thin plane (the wire junction dot) with a tinted
	 *   layer plus an untinted overlay layer, not a cube.</li>
	 * </ul>
	 */
	private static final Set<String> NO_GENERIC_FALLBACK_MODEL_STEMS =
		Set.of("anvil", "chipped_anvil", "damaged_anvil", "redstone_dust_dot");
	private static final String HORIZONTAL_SUFFIX = "_horizontal";
	/**
	 * Torch-family blocks use vanilla's own thin billboard template models (never a full cube),
	 * keyed by a {@code "torch"} texture variable. Maps each block model stem to its vanilla
	 * template parent and to the texture stem it actually renders with (wall variants reuse the
	 * standing torch's texture; there's no separate {@code wall_torch.png}).
	 */
	private static final Map<String, String> TORCH_MODEL_TEMPLATES = Map.of(
		"torch", "template_torch",
		"wall_torch", "template_torch_wall",
		"redstone_torch", "template_redstone_torch",
		"redstone_torch_off", "template_torch_unlit",
		"redstone_wall_torch", "template_redstone_torch_wall",
		"redstone_wall_torch_off", "template_torch_wall_unlit"
	);
	private static final Map<String, String> TORCH_MODEL_TEXTURE_STEM = Map.of(
		"torch", "torch",
		"wall_torch", "torch",
		"redstone_torch", "redstone_torch",
		"redstone_torch_off", "redstone_torch_off",
		"redstone_wall_torch", "redstone_torch",
		"redstone_wall_torch_off", "redstone_torch_off"
	);
	/**
	 * Entity textures that later gained biome/variant splits (e.g. cows: temperate/warm/cold,
	 * mooshroom: red/brown) and moved off the single filename legacy packs know. Only the
	 * variant matching the pre-split classic look is aliased - the others are new additions
	 * with no legacy equivalent, so they're left to fall back to vanilla's own textures.
	 * <p>
	 * {@code sheep_wool.png} is a similar case, though not a biome/variant split: 1.13 pulled the
	 * fur layer's texture out of the old single {@code sheep_fur.png} into a dedicated file, with no
	 * geometry change at all - {@code SheepFurModel.createFurLayer()} bakes the exact same boxes/UV
	 * offsets/inflation as 1.8.9's {@code ModelSheep1}. The separate {@code sheep_wool_undercoat.png}
	 * added later (a second, uninflated copy of the plain body shape drawn just under the fur, for
	 * extra fluffiness) has no legacy equivalent shape+texture pairing, so it's left on vanilla's own
	 * texture - it is mostly hidden under the fur layer and still gets tinted to the sheep's wool
	 * color like everything else, so leaving it vanilla is a minor, not a broken, compromise.
	 * <p>
	 * {@code textures/entity/player/slim/steve.png} is the default player skin used whenever a
	 * skull/player render has no real profile to fetch a skin for - including
	 * {@link net.minecraft.client.resources.DefaultPlayerSkin#getDefaultTexture()} (index 6 of its
	 * {@code DEFAULT_SKINS} array, which - despite "Steve" being the classic wide-armed look - is
	 * {@code slim/steve}, not {@code wide/steve}: the array lists all 9 slim variants first, and
	 * Steve is 7th alphabetically within each group), which is what a player-head block with no
	 * stored owner renders with. 1.8.9 predates both the slim/wide model split and the multiple
	 * default-skin choices added later; its single default skin lived unnamespaced at
	 * {@code textures/entity/steve.png}.
	 */
	private static final Map<String, String> ENTITY_TEXTURE_ALIASES = Map.of(
		"textures/entity/cow/cow_temperate.png", "textures/entity/cow/cow.png",
		"textures/entity/cow/mooshroom_red.png", "textures/entity/cow/mooshroom.png",
		"textures/entity/sheep/sheep_wool.png", "textures/entity/sheep/sheep_fur.png",
		"textures/entity/player/slim/steve.png", "textures/entity/steve.png"
	);
	/**
	 * The single alias target above (also this mod's oldest supported format) whose art can
	 * predate even 1.6.1's own skin format: pre-1.8 skins were 64x32 (no distinct back-of-limb or
	 * hat/jacket/sleeve/pants overlay regions - those pixels didn't exist yet), while every player
	 * model since 1.8 samples a 64x64 layout. A legacy pack that still ships a 64x32
	 * {@code steve.png} (some do - it predates this mod's 1.6.1-1.12.2 pack_format floor and
	 * apparently still circulates) would otherwise render with those regions reading whatever
	 * happens to sit past the old image's actual bounds - vanilla itself only performs this same
	 * 64x32-to-64x64 upgrade for skins fetched over HTTP ({@code SkinTextureDownloader
	 * .processLegacySkin}, {@code reference/26.2}), never for a texture a resource pack serves
	 * directly, so this mod has to replicate it. {@link #upgradeLegacySkin} mirrors that method's
	 * exact sequence of mirrored-copy regions (verified against its decompiled source line for
	 * line) - just not its separate, unrelated alpha-stripping calls, which exist to sanitize
	 * skins submitted to Mojang's session service against a griefing exploit, not to fix the
	 * format, and would risk clobbering intentional transparency in a legacy pack's own art.
	 */
	private static final String STEVE_TEXTURE_PATH = "textures/entity/steve.png";
	/**
	 * Pre-1.13 Minecraft rendered the fishing bobber by cropping a fixed icon cell out of the
	 * shared particle sheet, rather than using its own texture file. The modern renderer always
	 * loads a dedicated {@link #FISHING_HOOK_TEXTURE_PATH} and never reads the particle sheet, so
	 * there's no path to alias - instead the icon is cropped from the legacy pack's own
	 * {@link #PARTICLE_ATLAS_PATH} at load time and served as a synthesized PNG. The icon's grid
	 * cell (column 1, row 2 of a 16x16 grid) was confirmed empirically: two independently drawn
	 * legacy packs both have a fishhook-shaped icon at that exact cell.
	 */
	/**
	 * 1.8.9 renders the compass needle by picking a frame directly out of a single animated
	 * strip texture ({@code textures/items/compass.png}, a square-frame vertical strip like
	 * {@code fire_layer_0.png} or {@code water_still.png}), sized to however many frames the
	 * pack's own art actually has - {@link net.minecraft.client.renderer.texture.TextureAtlasSprite}'s
	 * frame count, not a fixed constant (confirmed against {@code TextureCompass.updateCompass}
	 * in {@code reference/1.8.9}). Modern Minecraft dropped per-sprite frame animation for items
	 * entirely: the compass is now a data-driven {@code minecraft:range_dispatch} item model
	 * (see {@code items/compass.json}) hardcoded to exactly 32 buckets, each pointing at its own
	 * standalone sprite ({@code item/compass_00.png} .. {@code item/compass_31.png}). A legacy
	 * pack's single strip therefore needs splitting into 32 individual textures rather than
	 * renamed/aliased like every other texture in this mod - and since a pack's own frame count
	 * need not be 32 (e.g. a real HD pack observed shipping 64), each modern bucket resamples the
	 * nearest legacy frame proportionally rather than assuming a 1:1 frame mapping.
	 */
	private static final String LEGACY_COMPASS_TEXTURE_PATH = "textures/items/compass.png";
	private static final String COMPASS_FRAME_STEM_PREFIX = "compass_";
	private static final int COMPASS_MODERN_FRAME_COUNT = 32;
	private static final String FISHING_HOOK_TEXTURE_PATH = "textures/entity/fishing/fishing_hook.png";
	private static final String PARTICLE_ATLAS_PATH = "textures/particle/particles.png";
	private static final int PARTICLE_ATLAS_GRID = 16;
	private static final int FISHING_HOOK_ATLAS_COLUMN = 1;
	private static final int FISHING_HOOK_ATLAS_ROW = 2;
	private static final String MODEL_BLOCK_DIR = "models/block/";
	private static final String MODEL_ITEM_DIR = "models/item/";
	private static final String BLOCKSTATES_DIR = "blockstates/";
	/**
	 * Vanilla's {@code redstone_dust_dot.png} is a small (verified empirically: a 6x6 blob at
	 * pixels 5,5 to 11,11) accent centered on an otherwise fully transparent 16x16 canvas - the
	 * model draws it at full-tile UV, relying on the texture's own transparency to keep it small. In
	 * 1.8.9, that exact 5,5-11,11 region is one of several crops {@code redstone_none.json} (and
	 * other named connectivity models) take out of the shared, edge-to-edge {@code
	 * redstone_dust_cross.png}; there the cropping happens in the model's UV rect instead, because
	 * every connectivity combination got its own hand-authored model. Simply aliasing the whole
	 * legacy file - as every other texture in this mod does - would feed that busy, full-tile
	 * pattern into a slot vanilla expects to be a tiny center dot: it visually swamps the thin line
	 * segments around it, so every junction ends up reading as an oversized blob regardless of which
	 * directions are actually connected. So this one texture is synthesized instead of aliased:
	 * {@link #computeRedstoneDustDotTexture} crops just that matching center region out of the
	 * legacy {@code redstone_dust_cross.png}, onto a transparent canvas at the same position.
	 */
	private static final String REDSTONE_DUST_DOT_TEXTURE_PATH = "textures/block/redstone_dust_dot.png";
	private static final int REDSTONE_DUST_BASE_CANVAS_SIZE = 16;
	private static final int REDSTONE_DUST_DOT_CROP_MIN = 5;
	private static final int REDSTONE_DUST_DOT_CROP_MAX = 11;
	/**
	 * Vanilla's {@code redstone_dust_line0.png}/{@code line1.png} are thin vertical bands (verified
	 * empirically: content confined to a handful of columns around x=6-9, spanning the full height) -
	 * each half-tile side model samples the top or bottom half of that column to get its arm of the
	 * wire. 1.8.9's single {@code redstone_dust_line.png} is authored the other way around: a
	 * horizontal band across the middle rows, spanning the full width (confirmed against two real
	 * legacy packs). That's why {@code redstone_n.json} - 1.8.9's own north/south-connected model -
	 * applies an explicit {@code "rotation": 90} to this same texture when using it for a
	 * north-south segment; without that rotation the asset is oriented for an east-west line.
	 * Aliasing the file straight across (as most textures in this mod are) skips that compensation:
	 * the modern top/bottom-half UV crops end up slicing a horizontal band at the wrong axis,
	 * producing disjointed fragments instead of a coherent line - i.e. "wrong orientation".
	 * <p>
	 * That part's fixed by {@link #computeRedstoneDustLineTexture}, which transposes the legacy image
	 * (swaps x/y, the exact inverse of the model's own missing rotation). But simply serving the
	 * result back under vanilla's own {@code block/redstone_dust_line0}/{@code line1} sprite IDs -
	 * the way every other texture in this mod works - hit a second problem, empirically observed
	 * across several rebuild-and-retest rounds: exactly one of the two sprites would render with
	 * vanilla's own pixels instead of the legacy ones, and *which* one flipped between rounds with no
	 * obvious trigger - not tied to which sprite's own serving code changed, nor reproducible by a
	 * client restart alone without a rebuild. That inconsistency, on its own, was inconclusive. What
	 * tipped it into "stop fighting vanilla's sprite IDs" territory: {@code redstone_dust_up.json}
	 * shares no parent model with the side pieces at all (no model inheritance in common) and yet
	 * exhibited the exact same failure mode as the parent-sharing side1/side_alt1 models - the only
	 * thing {@code up}/{@code side1}/{@code side_alt1} share is texturing with {@code
	 * block/redstone_dust_line1}. So the fix serves the transposed textures under brand new sprite
	 * IDs in this mod's own namespace ({@link
	 * #REDSTONE_DUST_LINE_NS_TEXTURE}/{@link #REDSTONE_DUST_LINE_EW_TEXTURE}, never referenced by
	 * vanilla for anything) and point the five affected models at those instead of overriding
	 * vanilla's models in place - see {@link #computeBlockModel}'s handling of {@link
	 * #REDSTONE_DUST_NS_MODEL_STEMS}/{@link #REDSTONE_DUST_EW_MODEL_STEMS}.
	 */
	private static final String REDSTONE_DUST_LINE_NS_STEM = "legacy_redstone_dust_line_ns";
	private static final String REDSTONE_DUST_LINE_EW_STEM = "legacy_redstone_dust_line_ew";
	private static final Identifier REDSTONE_DUST_LINE_NS_TEXTURE = AnyResource.id(NEW_BLOCK_TEXTURE_DIR + REDSTONE_DUST_LINE_NS_STEM + ".png");
	private static final Identifier REDSTONE_DUST_LINE_EW_TEXTURE = AnyResource.id(NEW_BLOCK_TEXTURE_DIR + REDSTONE_DUST_LINE_EW_STEM + ".png");
	/** North/south ("side"/"side_alt") redstone dust arm models now pointed at {@link #REDSTONE_DUST_LINE_NS_TEXTURE}. */
	private static final Set<String> REDSTONE_DUST_NS_MODEL_STEMS = Set.of("redstone_dust_side0", "redstone_dust_side_alt0");
	/**
	 * West/east ("side"/"side_alt", rotated 270 at the blockstate level) redstone dust arm models,
	 * plus the vertical climbing-wire model (which vanilla also happens to texture with {@code
	 * line1}), now pointed at {@link #REDSTONE_DUST_LINE_EW_TEXTURE}.
	 */
	private static final Set<String> REDSTONE_DUST_EW_MODEL_STEMS = Set.of("redstone_dust_side1", "redstone_dust_side_alt1", "redstone_dust_up");
	private static final String CHEST_TEXTURE_DIR = "textures/entity/chest/";
	/**
	 * Chest materials that had a combined double-wide sheet in 1.8.9 ({@code <stem>_double.png})
	 * and now need synthesizing into separate {@code <stem>_left.png}/{@code <stem>_right.png}
	 * halves. Ender chests never had a double variant on either side, so they're excluded.
	 */
	private static final Set<String> CHEST_DOUBLE_STEMS = Set.of("normal", "trapped", "christmas");
	/**
	 * Every sprite name the modern chest atlas (a {@code minecraft:directory} source over
	 * {@code textures/entity/chest/}, see {@code assets/minecraft/atlases/chests.json}) can look
	 * up. Used to drive {@link #listResources} so the atlas discovers exactly this set instead of
	 * whatever raw filenames the legacy pack happens to ship (which include never-requested
	 * {@code _double} sheets and are missing the {@code _left}/{@code _right} halves the atlas
	 * actually needs) - an unexpected sprite entering that shared, tightly packed atlas is a
	 * plausible way for a legacy pack to end up with chest faces sampling the wrong texture region.
	 */
	private static final List<String> CHEST_TEXTURE_STEMS = List.of(
		"normal", "normal_left", "normal_right",
		"trapped", "trapped_left", "trapped_right",
		"christmas", "christmas_left", "christmas_right",
		"ender"
	);
	/**
	 * Height (in pixels) of a single-chest canvas at vanilla 1.8.9 resolution; a legacy pack's
	 * actual resolution multiplier is derived from how many times larger its {@code _double.png}
	 * is than this, since HD packs (32x, 64x, ...) scale every chest texture up proportionally
	 * rather than shipping fixed 64x64/128x64 sheets.
	 */
	private static final int CHEST_BASE_CANVAS_SIZE = 64;
	/**
	 * Root of modern Minecraft's per-sprite GUI textures. A legacy pack has nothing here - its
	 * whole HUD lives in two monolithic sheets - so everything under this prefix is synthesized by
	 * cropping them, see {@link LegacyGuiSprites} for the sprite-by-sprite coordinates and
	 * {@link #computeGuiSprite} for the cropping itself.
	 */
	private static final String GUI_SPRITE_DIR = "textures/gui/sprites/";
	/** {@link #GUI_SPRITE_DIR} without its trailing slash, i.e. as {@link #listResources} sees it. */
	private static final String GUI_SPRITE_ROOT = "textures/gui/sprites";
	private static final String METADATA_SUFFIX = ".mcmeta";

	private final PackResources delegate;
	private final Map<Identifier, byte[]> jsonCache = new ConcurrentHashMap<>();
	/**
	 * Decoded legacy source sheets, keyed by their path in the pack. A single HD {@code icons.png}
	 * can be 2048x2048 and feeds 70-odd separate sprites; the compass strip likewise feeds 32
	 * frames. Without this, each one re-decodes the whole PNG.
	 */
	private final Map<String, Optional<BufferedImage>> sheetCache = new ConcurrentHashMap<>();
	/**
	 * Derived textures ({@link Derivations}), keyed by derivation id, then by the texture path each
	 * one produces. Cached per derivation rather than per texture because one run produces the whole
	 * set at once - the netherite armour derivation reads seven sources to write seven textures, and
	 * the game asks for those seven separately.
	 */
	private final Map<String, Map<String, byte[]>> derivedCache = new ConcurrentHashMap<>();
	/**
	 * Derivations currently being computed on this thread, so one that (mis)declares its own output
	 * as a source cannot recurse forever. Not a {@link ConcurrentHashMap} guard: the cycle would be
	 * within a single {@link #getResource} call chain, i.e. on one thread.
	 */
	private final ThreadLocal<Set<String>> deriving = ThreadLocal.withInitial(HashSet::new);

	LegacyPackResources(PackResources delegate) {
		this.delegate = delegate;
	}

	@Override
	public @Nullable IoSupplier<InputStream> getRootResource(String... path) {
		return delegate.getRootResource(path);
	}

	@Override
	public @Nullable IoSupplier<InputStream> getResource(PackType type, Identifier location) {
		if (type != PackType.CLIENT_RESOURCES) {
			return delegate.getResource(type, location);
		}

		if (location.equals(REDSTONE_DUST_LINE_NS_TEXTURE)) {
			return resolveJson(location, () -> computeRedstoneDustLineTexture(false));
		}
		if (location.equals(REDSTONE_DUST_LINE_EW_TEXTURE)) {
			return resolveJson(location, () -> computeRedstoneDustLineTexture(true));
		}

		String path = location.getPath();
		if (path.equals(REDSTONE_DUST_DOT_TEXTURE_PATH)) {
			return resolveJson(location, () -> computeRedstoneDustDotTexture(location));
		}
		if (path.startsWith(NEW_ITEM_TEXTURE_DIR)) {
			Integer compassFrame = parseCompassFrameIndex(path.substring(NEW_ITEM_TEXTURE_DIR.length()));
			if (compassFrame != null) {
				return resolveJson(location, () -> computeCompassFrameTexture(compassFrame));
			}
		}
		if (path.equals(LegacyGuiSprites.LOGO_PATH) || path.equals(LegacyGuiSprites.EASTER_EGG_LOGO_PATH)) {
			boolean easterEgg = path.equals(LegacyGuiSprites.EASTER_EGG_LOGO_PATH);
			return resolveJson(location, () -> computeTitleLogo(easterEgg));
		}
		if (path.startsWith(GUI_SPRITE_DIR)) {
			String spriteName = path.substring(GUI_SPRITE_DIR.length());
			String metadata = LegacyGuiSprites.SPRITE_METADATA.get(stripMetadataSuffix(spriteName));
			// Nine-slice metadata has to be served by this pack too, not just the image - see
			// LegacyGuiSprites.SPRITE_METADATA - but only once the image itself resolves, or a pack
			// with no widgets.png would claim metadata for a sprite it isn't overriding.
			if (metadata != null && spriteName.endsWith(METADATA_SUFFIX)) {
				return guiSpriteResolves(location, stripMetadataSuffix(spriteName))
					? resolveJson(location, () -> metadata.getBytes(StandardCharsets.UTF_8))
					: null;
			}
			LegacyGuiSprites.SheetCrop crop = LegacyGuiSprites.SPRITES.get(spriteName);
			// Unmapped GUI sprites (those with no 1.8.9 equivalent) fall through untouched, so
			// they keep resolving to vanilla's own art.
			if (crop != null) {
				return resolveJson(location, () -> computeGuiSprite(spriteName, crop));
			}
		}
		// Derivation is the last resort in each of these branches, reached only once the pack has been
		// found to have nothing of its own that maps to the requested texture - a pack that ships the
		// file is never second-guessed.
		if (path.startsWith(NEW_BLOCK_TEXTURE_DIR) || path.startsWith(NEW_ITEM_TEXTURE_DIR)) {
			IoSupplier<InputStream> texture = resolveTexture(location, path);
			return texture != null ? texture : resolveDerivedTexture(path);
		}
		if (path.startsWith(NEW_EQUIPMENT_TEXTURE_DIR)) {
			IoSupplier<InputStream> texture = resolveEquipmentTexture(location, path);
			return texture != null ? texture : resolveDerivedTexture(path);
		}
		if (ENTITY_TEXTURE_ALIASES.containsKey(path)) {
			String aliasPath = ENTITY_TEXTURE_ALIASES.get(path);
			if (aliasPath.equals(STEVE_TEXTURE_PATH)) {
				return resolveJson(location, () -> computeSteveSkinTexture(location));
			}
			return delegate.getResource(PackType.CLIENT_RESOURCES, location.withPath(aliasPath));
		}
		if (path.equals(FISHING_HOOK_TEXTURE_PATH)) {
			return resolveJson(location, () -> computeFishingHookTexture(location));
		}
		if (path.startsWith(CHEST_TEXTURE_DIR) && path.endsWith(".png")) {
			String stem = path.substring(CHEST_TEXTURE_DIR.length(), path.length() - ".png".length());
			IoSupplier<InputStream> chestTexture = resolveChestTexture(location, stem);
			if (chestTexture != null) {
				return chestTexture;
			}
		}
		if (path.startsWith(MODEL_BLOCK_DIR) && path.endsWith(".json")) {
			String stem = path.substring(MODEL_BLOCK_DIR.length(), path.length() - ".json".length());
			return resolveJson(location, () -> computeBlockModel(location, stem));
		}
		if (path.startsWith(MODEL_ITEM_DIR) && path.endsWith(".json")) {
			String stem = path.substring(MODEL_ITEM_DIR.length(), path.length() - ".json".length());
			return resolveJson(location, () -> computeItemModel(location, stem));
		}
		if (path.startsWith(BLOCKSTATES_DIR) && path.endsWith(".json")) {
			String stem = path.substring(BLOCKSTATES_DIR.length(), path.length() - ".json".length());
			return resolveJson(location, () -> computeBlockstate(location, stem));
		}
		return delegate.getResource(type, location);
	}

	@Override
	public void listResources(PackType type, String namespace, String directory, PackResources.ResourceOutput output) {
		if (type != PackType.CLIENT_RESOURCES) {
			delegate.listResources(type, namespace, directory, output);
			return;
		}

		if (namespace.equals(AnyResource.MOD_ID) && isOrUnder(directory, "textures/block")) {
			// The atlas ("blocks") is populated by DirectoryLister enumerating textures/block/ via
			// listResources, not by walking model texture references - so these two computed,
			// invented-name sprites (they're not real files in the legacy pack, nothing would ever
			// list them on its own) must be explicitly announced here, or the atlas never learns
			// they exist and the models that reference them silently fail to resolve. Only announce
			// them when the pack actually has redstone dust art to derive them from, matching the
			// same gate computeBlockModel uses.
			if (redstoneDustLineSourceExists()) {
				IoSupplier<InputStream> ns = getResource(PackType.CLIENT_RESOURCES, REDSTONE_DUST_LINE_NS_TEXTURE);
				IoSupplier<InputStream> ew = getResource(PackType.CLIENT_RESOURCES, REDSTONE_DUST_LINE_EW_TEXTURE);
				if (ns != null) {
					output.accept(REDSTONE_DUST_LINE_NS_TEXTURE, ns);
				}
				if (ew != null) {
					output.accept(REDSTONE_DUST_LINE_EW_TEXTURE, ew);
				}
			}
			return;
		}

		if (isOrUnder(directory, "textures/block")) {
			String oldDirectory = "textures/blocks" + directory.substring("textures/block".length());
			delegate.listResources(type, namespace, oldDirectory, (oldId, supplier) -> {
				Identifier newId = translateListed(oldId, OLD_BLOCK_TEXTURE_DIR, NEW_BLOCK_TEXTURE_DIR, TextureNameMaps::newBlockName);
				if (newId == null) {
					return;
				}
				// Keep in sync with the getResource intercept: the dot is synthesized, not a raw
				// passthrough of the legacy file, so listing must fetch it the same way.
				if (newId.getPath().equals(REDSTONE_DUST_DOT_TEXTURE_PATH)) {
					IoSupplier<InputStream> resource = getResource(PackType.CLIENT_RESOURCES, newId);
					if (resource != null) {
						output.accept(newId, resource);
					}
					return;
				}
				if (!isValidLegacyTexture(oldId, supplier)) {
					return;
				}
				output.accept(newId, supplier);
			});
			announceDerivedTextures(namespace, "block/", output);
			return;
		}
		if (isOrUnder(directory, "textures/item")) {
			String oldDirectory = "textures/items" + directory.substring("textures/item".length());
			delegate.listResources(type, namespace, oldDirectory, (oldId, supplier) -> {
				Identifier newId = translateListed(oldId, OLD_ITEM_TEXTURE_DIR, NEW_ITEM_TEXTURE_DIR, TextureNameMaps::newItemName);
				if (newId != null && isValidLegacyTexture(oldId, supplier)) {
					output.accept(newId, supplier);
				}
			});
			// item/compass_00..31 are standalone sprites with no legacy-pack file of their own
			// (see LEGACY_COMPASS_TEXTURE_PATH's javadoc) - like the redstone dust textures above,
			// nothing would ever list them unless explicitly announced here.
			if (namespace.equals("minecraft") && compassSourceExists()) {
				for (int frame = 0; frame < COMPASS_MODERN_FRAME_COUNT; frame++) {
					Identifier id = Identifier.fromNamespaceAndPath(namespace, NEW_ITEM_TEXTURE_DIR + compassFrameStem(frame) + ".png");
					IoSupplier<InputStream> resource = getResource(PackType.CLIENT_RESOURCES, id);
					if (resource != null) {
						output.accept(id, resource);
					}
				}
			}
			announceDerivedTextures(namespace, "item/", output);
			return;
		}
		if (namespace.equals("minecraft") && directoryCovers(directory, "models/block") && redstoneDustLineSourceExists()) {
			// Model discovery (ModelDiscovery, built from a Map<Identifier, UnbakedModel> assembled
			// ahead of time) works the same way atlas sprite discovery does: by what gets listed, not
			// by asking getResource() per identifier on demand. These 5 model files are computed, not
			// real files in the legacy pack, so - just like the two computed textures above - they
			// must be explicitly announced here or model discovery never learns they exist and quietly
			// keeps using vanilla's own (getResource() would answer correctly if ever asked, but nothing
			// asks). This was the actual cause of "sprite exists in the atlas but the game still shows
			// vanilla": the texture was discoverable, the model override that would have referenced it
			// never was.
			//
			// Critically, the real query directory here is "models" (ModelManager's MODEL_LISTER
			// scans that whole tree in one FileToIdConverter, not "models/block" specifically) - a
			// plain isOrUnder(directory, "models/block") check requires directory to equal or be
			// nested under "models/block", which "models" itself never satisfies. directoryCovers
			// checks the relationship the other way around: does the queried directory contain
			// "models/block"? A harness call that hardcodes "models/block" as the test directory
			// would pass either way, which is exactly how this slipped through verification before.
			for (String stem : REDSTONE_DUST_NS_MODEL_STEMS) {
				announceComputedModel(namespace, stem, output);
			}
			for (String stem : REDSTONE_DUST_EW_MODEL_STEMS) {
				announceComputedModel(namespace, stem, output);
			}
		}
		if (isOrUnder(directory, "models/block") || isOrUnder(directory, "models/item") || isOrUnder(directory, "blockstates")) {
			delegate.listResources(type, namespace, directory, (id, supplier) -> {
				// Keep in sync with the redstone_wire skip in computeBlockstate: the pack's own
				// old-scheme blockstate must never surface, from listing either.
				if (id.getPath().equals(BLOCKSTATES_DIR + "redstone_wire.json")) {
					return;
				}
				output.accept(id, rewriteJsonSupplier(id, supplier));
			});
			return;
		}
		if (isOrUnder(directory, "textures/entity/chest")) {
			for (String stem : CHEST_TEXTURE_STEMS) {
				Identifier id = Identifier.fromNamespaceAndPath(namespace, CHEST_TEXTURE_DIR + stem + ".png");
				IoSupplier<InputStream> resource = getResource(PackType.CLIENT_RESOURCES, id);
				if (resource != null) {
					output.accept(id, resource);
				}
			}
			return;
		}
		if (namespace.equals("minecraft")
			&& (isOrUnder(directory, GUI_SPRITE_ROOT) || directoryCovers(directory, GUI_SPRITE_ROOT))) {
			// This is the branch that actually makes HUD conversion work. The GUI atlas is built by
			// DirectoryLister enumerating textures/gui/sprites (see assets/minecraft/atlases/gui.json),
			// never by asking getResource() per identifier - and these sprites are computed, with no
			// file of their own in the legacy pack, so nothing would ever list them unless announced
			// here. Same trap as the computed redstone dust models above.
			//
			// The real query is for GUI_SPRITE_ROOT exactly, but handle both directions: isOrUnder
			// covers a narrower query (e.g. "textures/gui/sprites/hud"), directoryCovers a wider one
			// (e.g. "textures").
			String prefix = isOrUnder(directory, GUI_SPRITE_ROOT) ? directory + "/" : GUI_SPRITE_DIR;
			LegacyGuiSprites.SPRITES.forEach((spriteName, crop) -> {
				String path = GUI_SPRITE_DIR + spriteName;
				if (!path.startsWith(prefix)) {
					return;
				}
				Identifier id = Identifier.fromNamespaceAndPath(namespace, path);
				IoSupplier<InputStream> resource = getResource(PackType.CLIENT_RESOURCES, id);
				if (resource == null) {
					return;
				}
				output.accept(id, resource);
				// The nine-slice metadata is picked up from the listing too, not fetched per sprite
				// (FallbackResourceManager.listResources collects .mcmeta entries alongside the
				// images), so announcing the image alone would still lose the scaling.
				if (!LegacyGuiSprites.SPRITE_METADATA.containsKey(spriteName)) {
					return;
				}
				Identifier metadataId = id.withPath(path + METADATA_SUFFIX);
				IoSupplier<InputStream> metadata = getResource(PackType.CLIENT_RESOURCES, metadataId);
				if (metadata != null) {
					output.accept(metadataId, metadata);
				}
			});
			// Deliberately no early return: a legacy pack has nothing of its own under this path
			// today, but swallowing the query outright would silently drop anything it did ship.
		}
		delegate.listResources(type, namespace, directory, output);
	}

	private void announceComputedModel(String namespace, String stem, PackResources.ResourceOutput output) {
		Identifier id = Identifier.fromNamespaceAndPath(namespace, MODEL_BLOCK_DIR + stem + ".json");
		IoSupplier<InputStream> resource = getResource(PackType.CLIENT_RESOURCES, id);
		if (resource != null) {
			output.accept(id, resource);
		}
	}

	@Override
	public Set<String> getNamespaces(PackType type) {
		Set<String> namespaces = delegate.getNamespaces(type);
		if (type != PackType.CLIENT_RESOURCES || namespaces.contains(AnyResource.MOD_ID)) {
			return namespaces;
		}
		// The legacy pack only ever advertises "minecraft" (1.8.9 packs never have their own
		// namespace), but redstone dust's synthesized textures/models are served under this mod's
		// own namespace (see REDSTONE_DUST_LINE_NS_TEXTURE) - without adding it here, the resource
		// manager never asks this pack for "any-resource:..." at all, so those references silently
		// fail to resolve and the affected models fall back to vanilla's own (unconverted) ones.
		Set<String> withModNamespace = new HashSet<>(namespaces);
		withModNamespace.add(AnyResource.MOD_ID);
		return withModNamespace;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> @Nullable T getMetadataSection(MetadataSectionType<T> metadataSerializer) throws IOException {
		T value = delegate.getMetadataSection(metadataSerializer);
		if (value instanceof PackMetadataSection section
			&& (metadataSerializer == PackMetadataSection.CLIENT_TYPE || metadataSerializer == PackMetadataSection.FALLBACK_TYPE)) {
			PackFormat current = SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES);
			return (T) new PackMetadataSection(section.description(), new InclusiveRange<>(current));
		}
		return value;
	}

	@Override
	public PackLocationInfo location() {
		return delegate.location();
	}

	@Override
	public void close() {
		delegate.close();
	}

	private @Nullable IoSupplier<InputStream> resolveTexture(Identifier location, String path) {
		String oldPath = translateTexturePath(path);
		if (oldPath == null) {
			return null;
		}
		return delegate.getResource(PackType.CLIENT_RESOURCES, location.withPath(oldPath));
	}

	/**
	 * Synthesizes a texture the pack has no art for out of art it does have - see {@link Derivations}.
	 * <p>
	 * Modern Minecraft has a decade of blocks and items that no pre-flattening pack could have
	 * covered, and for many of them a close relative is right there in the pack: suspicious gravel is
	 * gravel that has been dug into, netherite armour is diamond armour in a different metal. Left
	 * alone these fall back to vanilla's own art and sit in the world as obvious foreign objects among
	 * hundreds of restyled neighbours, which is worse than an approximation drawn from the pack's own
	 * palette.
	 * <p>
	 * The constants each derivation runs on are tuned in the derivation lab ({@code ./gradlew runLab},
	 * see {@code src/lab}), which drives exactly this code path against every legacy pack at once.
	 *
	 * @param path full modern texture path, e.g. {@code textures/block/suspicious_gravel_0.png}
	 */
	private @Nullable IoSupplier<InputStream> resolveDerivedTexture(String path) {
		if (!path.endsWith(".png")) {
			return null;
		}
		byte[] bytes = derivedTexture(path.substring(TEXTURE_DIR.length(), path.length() - ".png".length()));
		return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
	}

	/** @param texturePath a derivation-facing path, e.g. {@code block/suspicious_gravel_0} */
	private byte @Nullable [] derivedTexture(String texturePath) {
		Derivation derivation = Derivations.byOutput(texturePath);
		return derivation == null ? null : runDerivation(derivation).get(texturePath);
	}

	/**
	 * Every texture {@code derivation} produces for this pack, computed once and cached.
	 * <p>
	 * Deliberately not {@code computeIfAbsent}: the computation calls {@link #getResource} to fetch
	 * its sources, which can re-enter the caches, and a recursive update of a
	 * {@link ConcurrentHashMap} from inside its own mapping function is undefined at best.
	 */
	private Map<String, byte[]> runDerivation(Derivation derivation) {
		Map<String, byte[]> cached = derivedCache.get(derivation.id());
		if (cached != null) {
			return cached;
		}
		Set<String> active = deriving.get();
		if (!active.add(derivation.id())) {
			return Map.of();
		}
		try {
			Map<String, byte[]> computed = computeDerivation(derivation);
			derivedCache.put(derivation.id(), computed);
			return computed;
		} finally {
			active.remove(derivation.id());
		}
	}

	private Map<String, byte[]> computeDerivation(Derivation derivation) {
		Map<String, BufferedImage> sources = new LinkedHashMap<>();
		for (String source : derivation.sources()) {
			BufferedImage image = readDerivationSource(source);
			if (image != null) {
				sources.put(source, image);
			}
		}
		if (sources.isEmpty()) {
			return Map.of();
		}
		Map<String, byte[]> encoded = new LinkedHashMap<>();
		try {
			Map<String, BufferedImage> derived = derivation.derive(sources, Params.defaults(derivation.params()));
			for (Map.Entry<String, BufferedImage> entry : derived.entrySet()) {
				ByteArrayOutputStream bytes = new ByteArrayOutputStream();
				ImageIO.write(entry.getValue(), "png", bytes);
				encoded.put(entry.getKey(), bytes.toByteArray());
			}
		} catch (IOException | RuntimeException e) {
			// One misbehaving derivation must not take the pack down with it: log it and let every
			// texture it would have produced fall back to vanilla's.
			AnyResource.LOGGER.warn("Derivation {} failed for pack {}", derivation.id(), location().id(), e);
			return Map.of();
		}
		return Map.copyOf(encoded);
	}

	/**
	 * Fetches a derivation input through {@link #getResource} rather than off the delegate, so an
	 * input can itself be something the conversion synthesizes - the compass frames, for one, exist
	 * in no pack and are cut from a legacy strip by {@link #computeCompassFrameTexture}.
	 */
	private @Nullable BufferedImage readDerivationSource(String texturePath) {
		Identifier id = Identifier.fromNamespaceAndPath("minecraft", TEXTURE_DIR + texturePath + ".png");
		IoSupplier<InputStream> supplier = getResource(PackType.CLIENT_RESOURCES, id);
		if (supplier == null) {
			return null;
		}
		try (InputStream in = supplier.get()) {
			return ImageIO.read(in);
		} catch (IOException e) {
			AnyResource.LOGGER.warn("Failed to read derivation source {} from pack {}", id, location().id(), e);
			return null;
		}
	}

	/**
	 * Announces derived textures under {@code directoryPrefix} to the atlas.
	 * <p>
	 * Same trap as the computed redstone dust textures and GUI sprites above: atlas sprite discovery
	 * enumerates directories through {@link #listResources} and never asks {@link #getResource} per
	 * identifier, so a texture that exists only because it was computed has to be named here or the
	 * atlas never learns of it and the models referencing it quietly keep vanilla's art.
	 * <p>
	 * The {@code entity/equipment} outputs need no equivalent - there is no equipment atlas
	 * ({@code assets/minecraft/atlases} has no entry for it), so those are loaded by path.
	 */
	private void announceDerivedTextures(String namespace, String directoryPrefix, PackResources.ResourceOutput output) {
		if (!namespace.equals("minecraft")) {
			return;
		}
		for (Derivation derivation : Derivations.ALL) {
			for (String texturePath : derivation.outputs()) {
				if (!texturePath.startsWith(directoryPrefix)) {
					continue;
				}
				Identifier id = Identifier.fromNamespaceAndPath(namespace, TEXTURE_DIR + texturePath + ".png");
				// The pack's own art wins, and the delegate listing has already announced it.
				if (resolveTexture(id, id.getPath()) != null) {
					continue;
				}
				byte[] bytes = derivedTexture(texturePath);
				if (bytes != null) {
					output.accept(id, () -> new ByteArrayInputStream(bytes));
				}
			}
		}
	}

	private @Nullable IoSupplier<InputStream> resolveEquipmentTexture(Identifier location, String path) {
		String oldPath = translateEquipmentTexturePath(path);
		if (oldPath == null) {
			return null;
		}
		return delegate.getResource(PackType.CLIENT_RESOURCES, location.withPath(oldPath));
	}

	/**
	 * See {@link #STEVE_TEXTURE_PATH}: serves the legacy pack's {@code steve.png} unchanged if it's
	 * already 64x64-family, or upgraded via {@link #upgradeLegacySkin} if it's the older 64x32-family
	 * (width exactly double the height - the same check {@code SkinTextureDownloader
	 * .processLegacySkin} makes) shape.
	 */
	private byte @Nullable [] computeSteveSkinTexture(Identifier location) {
		IoSupplier<InputStream> supplier = delegate.getResource(PackType.CLIENT_RESOURCES, location.withPath(STEVE_TEXTURE_PATH));
		if (supplier == null) {
			return null;
		}
		try (InputStream in = supplier.get()) {
			BufferedImage source = ImageIO.read(in);
			if (source == null) {
				return null;
			}
			BufferedImage out = source.getWidth() == source.getHeight() * 2 ? upgradeLegacySkin(source) : source;
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			ImageIO.write(out, "png", bytes);
			return bytes.toByteArray();
		} catch (IOException e) {
			AnyResource.LOGGER.warn("Failed to convert legacy steve skin texture in legacy pack {}", location().id(), e);
			return null;
		}
	}

	/**
	 * Replicates {@code SkinTextureDownloader.processLegacySkin}'s 64x32-to-64x64 upgrade
	 * (mirrored-copy region for region, scaled to the source's own resolution) - see
	 * {@link #STEVE_TEXTURE_PATH}'s javadoc for why this mod can't just call that method directly.
	 */
	private static BufferedImage upgradeLegacySkin(BufferedImage source) {
		int scale = source.getWidth() / 64;
		int size = source.getWidth();
		BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.drawImage(source, 0, 0, null);
		g.dispose();
		copyMirroredX(out, 4, 16, 16, 32, 4, 4, scale);
		copyMirroredX(out, 8, 16, 16, 32, 4, 4, scale);
		copyMirroredX(out, 0, 20, 24, 32, 4, 12, scale);
		copyMirroredX(out, 4, 20, 16, 32, 4, 12, scale);
		copyMirroredX(out, 8, 20, 8, 32, 4, 12, scale);
		copyMirroredX(out, 12, 20, 16, 32, 4, 12, scale);
		copyMirroredX(out, 44, 16, -8, 32, 4, 4, scale);
		copyMirroredX(out, 48, 16, -8, 32, 4, 4, scale);
		copyMirroredX(out, 40, 20, 0, 32, 4, 12, scale);
		copyMirroredX(out, 44, 20, -8, 32, 4, 12, scale);
		copyMirroredX(out, 48, 20, -16, 32, 4, 12, scale);
		copyMirroredX(out, 52, 20, -8, 32, 4, 12, scale);
		return out;
	}

	/**
	 * Copies a {@code sizeX}x{@code sizeY} (in vanilla 1.8.9 64x32-skin units, scaled by {@code
	 * scale} for HD packs) block starting at {@code (startX, startY)} to a position offset by
	 * {@code (offsetX, offsetY)} from that same start, mirrored horizontally - matching {@code
	 * NativeImage.copyRect(startX, startY, offsetX, offsetY, sizeX, sizeY, true, false)}.
	 */
	private static void copyMirroredX(BufferedImage image, int startX, int startY, int offsetX, int offsetY, int sizeX, int sizeY, int scale) {
		int sx = startX * scale;
		int sy = startY * scale;
		int w = sizeX * scale;
		int h = sizeY * scale;
		int dx = sx + offsetX * scale;
		int dy = sy + offsetY * scale;
		int[] block = image.getRGB(sx, sy, w, h, null, 0, w);
		int[] mirrored = new int[block.length];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				mirrored[y * w + (w - 1 - x)] = block[y * w + x];
			}
		}
		image.setRGB(dx, dy, w, h, mirrored, 0, w);
	}

	/**
	 * Decodes one of the legacy pack's source sheets once and keeps it, since a single sheet backs
	 * dozens of synthesized sprites. Returns {@code null} when the pack doesn't ship the file (or
	 * ships something undecodable), which is also how callers learn to leave vanilla's art alone.
	 */
	private @Nullable BufferedImage loadSheet(String legacyPath) {
		return sheetCache.computeIfAbsent(legacyPath, path -> {
			IoSupplier<InputStream> supplier = delegate.getResource(
				PackType.CLIENT_RESOURCES, Identifier.fromNamespaceAndPath("minecraft", path)
			);
			if (supplier == null) {
				return Optional.empty();
			}
			try (InputStream in = supplier.get()) {
				return Optional.ofNullable(ImageIO.read(in));
			} catch (IOException e) {
				AnyResource.LOGGER.warn("Failed to read {} in legacy pack {}", path, location().id(), e);
				return Optional.empty();
			}
		}).orElse(null);
	}

	/**
	 * Crops one modern GUI sprite out of the legacy pack's {@code icons.png}/{@code widgets.png},
	 * scaling {@link LegacyGuiSprites}'s 256px-base coordinates by the pack's own resolution
	 * multiplier (HD packs ship 512/1024/2048px sheets, all of which were observed in the wild).
	 * A sheet that isn't a square multiple of the base size, or that is too small to hold the cell,
	 * isn't something 1.8.9 could have rendered from either, so those bail out to vanilla's sprite
	 * rather than guessing.
	 */
	private byte @Nullable [] computeGuiSprite(String spriteName, LegacyGuiSprites.SheetCrop crop) {
		BufferedImage sheet = loadSheet(crop.sheet().legacyPath());
		if (sheet == null
			|| sheet.getWidth() != sheet.getHeight()
			|| sheet.getWidth() % LegacyGuiSprites.SHEET_BASE_SIZE != 0) {
			return null;
		}
		int scale = sheet.getWidth() / LegacyGuiSprites.SHEET_BASE_SIZE;
		int height = crop.h() * scale;
		int width = crop.totalWidth() * scale;
		try {
			BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			// Deliberately a raw pixel copy rather than a Graphics2D blit like the chest/redstone
			// paths above: those rearrange faces and need drawImage's transforms, whereas this is a
			// plain crop, and drawImage's default SRC_OVER composite round-trips every partially
			// transparent pixel through premultiplied alpha - which shifts colour channels by a
			// step or two. That is invisible on mostly-opaque art but not here: the hotbar and the
			// boss bar, the two largest sprites in the table, are semi-transparent almost edge to
			// edge, and this crop has to hand back the pack's own bytes untouched.
			if (!copyCell(sheet, crop, scale, out, 0)) {
				return null;
			}
			if (crop.second() != null && !copyCell(sheet, crop.second(), scale, out, crop.w() * scale)) {
				return null;
			}
			if (spriteName.equals(LegacyGuiSprites.HOTBAR_SELECTION) && isRowBlank(out, height - scale, scale)) {
				// See LegacyGuiSprites.HOTBAR_SELECTION: repeat the row above into the bottom row
				// the pack never authored, so the selection box keeps a bottom edge.
				int[] above = out.getRGB(0, height - 2 * scale, width, scale, null, 0, width);
				out.setRGB(0, height - scale, width, scale, above, 0, width);
			}
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			ImageIO.write(out, "png", bytes);
			return bytes.toByteArray();
		} catch (IOException e) {
			AnyResource.LOGGER.warn("Failed to crop GUI sprite {} in legacy pack {}", spriteName, location().id(), e);
			return null;
		}
	}

	private static String stripMetadataSuffix(String spriteName) {
		return spriteName.endsWith(METADATA_SUFFIX)
			? spriteName.substring(0, spriteName.length() - METADATA_SUFFIX.length())
			: spriteName;
	}

	/** Whether this pack actually supplies {@code spriteName}, i.e. has the sheet it is cut from. */
	private boolean guiSpriteResolves(Identifier location, String spriteName) {
		LegacyGuiSprites.SheetCrop crop = LegacyGuiSprites.SPRITES.get(spriteName);
		if (crop == null) {
			return false;
		}
		Identifier spriteLocation = location.withPath(GUI_SPRITE_DIR + spriteName);
		return resolveJson(spriteLocation, () -> computeGuiSprite(spriteName, crop)) != null;
	}

	/**
	 * Reassembles the legacy title screen wordmark into the layout modern reads - see
	 * {@link LegacyGuiSprites#LOGO_PATH} for why the file needs rebuilding rather than passing
	 * through, and where the piece coordinates come from.
	 */
	private byte @Nullable [] computeTitleLogo(boolean easterEgg) {
		BufferedImage sheet = loadSheet(LegacyGuiSprites.LOGO_PATH);
		if (sheet == null
			|| sheet.getWidth() != sheet.getHeight()
			|| sheet.getWidth() % LegacyGuiSprites.SHEET_BASE_SIZE != 0) {
			return null;
		}
		int scale = sheet.getWidth() / LegacyGuiSprites.SHEET_BASE_SIZE;
		var pieces = easterEgg ? LegacyGuiSprites.EASTER_EGG_LOGO_PIECES : LegacyGuiSprites.LOGO_PIECES;
		try {
			// Lay the pieces out side by side exactly as 1.8.9's own draw calls do, then keep only
			// the columns that hold artwork.
			int stripWidth = LegacyGuiSprites.LOGO_LEGACY_WIDTH * scale;
			int stripHeight = LegacyGuiSprites.LOGO_HEIGHT * scale;
			BufferedImage strip = new BufferedImage(stripWidth, stripHeight, BufferedImage.TYPE_INT_ARGB);
			Graphics2D sg = strip.createGraphics();
			for (LegacyGuiSprites.LogoPiece piece : pieces) {
				int width = Math.min(piece.w() * scale, stripWidth - piece.dx() * scale);
				if (width <= 0) {
					continue;
				}
				blit(sg, sheet, piece.u() * scale, piece.v() * scale, width, stripHeight, piece.dx() * scale, 0);
			}
			sg.dispose();

			BufferedImage out = new BufferedImage(
				LegacyGuiSprites.LOGO_CANVAS_WIDTH * scale, LegacyGuiSprites.LOGO_CANVAS_HEIGHT * scale,
				BufferedImage.TYPE_INT_ARGB
			);
			Graphics2D g = out.createGraphics();
			// The one place in this class that genuinely resamples: 274 columns of artwork have to
			// display in a 256-wide slot whatever we do, so squeeze rather than crop. Nearest
			// neighbour keeps the wordmark's hard pixel edges - the logo is chunky enough that the
			// dropped columns don't read as distortion, confirmed against real packs.
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			g.drawImage(strip, 0, 0, LegacyGuiSprites.LOGO_CANVAS_WIDTH * scale, stripHeight, null);
			g.dispose();
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			ImageIO.write(out, "png", bytes);
			return bytes.toByteArray();
		} catch (IOException e) {
			AnyResource.LOGGER.warn("Failed to rebuild title logo in legacy pack {}", location().id(), e);
			return null;
		}
	}

	/**
	 * Copies one {@link LegacyGuiSprites.SheetCrop} cell into {@code out} at horizontal offset
	 * {@code dx}, or returns {@code false} if the cell doesn't fit inside the pack's sheet - a
	 * region 1.8.9 could not have drawn from either, so the sprite is left to vanilla instead of
	 * being filled with whatever happens to sit at the edge.
	 */
	private static boolean copyCell(
		BufferedImage sheet, LegacyGuiSprites.SheetCrop crop, int scale, BufferedImage out, int dx
	) {
		int x = crop.u() * scale;
		int y = crop.v() * scale;
		int width = crop.w() * scale;
		int height = crop.h() * scale;
		if (x + width > sheet.getWidth() || y + height > sheet.getHeight()) {
			return false;
		}
		out.setRGB(dx, 0, width, height, sheet.getRGB(x, y, width, height, null, 0, width), 0, width);
		return true;
	}

	/** Whether the {@code height}-tall band of {@code image} starting at {@code y} is fully transparent. */
	private static boolean isRowBlank(BufferedImage image, int y, int height) {
		for (int row = y; row < y + height; row++) {
			for (int x = 0; x < image.getWidth(); x++) {
				if ((image.getRGB(x, row) >>> 24) != 0) {
					return false;
				}
			}
		}
		return true;
	}

	private byte @Nullable [] computeFishingHookTexture(Identifier location) {
		IoSupplier<InputStream> atlasSupplier = delegate.getResource(
			PackType.CLIENT_RESOURCES, location.withPath(PARTICLE_ATLAS_PATH)
		);
		if (atlasSupplier == null) {
			return null;
		}
		try (InputStream in = atlasSupplier.get()) {
			BufferedImage atlas = ImageIO.read(in);
			if (atlas == null || atlas.getWidth() % PARTICLE_ATLAS_GRID != 0 || atlas.getHeight() % PARTICLE_ATLAS_GRID != 0) {
				return null;
			}
			int cellWidth = atlas.getWidth() / PARTICLE_ATLAS_GRID;
			int cellHeight = atlas.getHeight() / PARTICLE_ATLAS_GRID;
			BufferedImage icon = atlas.getSubimage(
				FISHING_HOOK_ATLAS_COLUMN * cellWidth, FISHING_HOOK_ATLAS_ROW * cellHeight, cellWidth, cellHeight
			);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(icon, "png", out);
			return out.toByteArray();
		} catch (IOException e) {
			AnyResource.LOGGER.warn("Failed to crop fishing hook icon from legacy particle atlas in pack {}", location().id(), e);
			return null;
		}
	}

	/**
	 * Both {@code TileEntityChestRenderer} and {@code TileEntityEnderChestRenderer} in 1.8.9 draw
	 * the chest model under {@code GlStateManager.scale(1.0f, -1.0f, -1.0f)} - a Y and Z axis flip,
	 * equivalent to a 180-degree rotation around the X axis. Legacy chest art was painted assuming
	 * that flip happens at render time. Modern's {@code ChestRenderer.modelTransformation} only
	 * rotates around Y to orient the chest by facing - no Y/Z flip at all. Verified empirically
	 * (per-face pixel-diff search against the real vanilla textures, not just derived from the
	 * geometry) rather than trusted from the rotation math alone, since a first attempt at the
	 * geometric derivation got the per-face transform wrong: swapping which slot each face's
	 * content occupies is necessary but not sufficient - west/east need a 180-degree rotation in
	 * place, the down/up swap additionally needs a vertical flip, and the front/back swap
	 * additionally needs a 180-degree rotation, each confirmed as an exact (zero pixel-difference)
	 * match against 26.2's own vanilla chest textures.
	 */
	private @Nullable IoSupplier<InputStream> resolveChestTexture(Identifier location, String stem) {
		if (stem.endsWith("_left") || stem.endsWith("_right")) {
			boolean isLeft = stem.endsWith("_left");
			String baseStem = stem.substring(0, stem.length() - (isLeft ? "_left" : "_right").length());
			if (!CHEST_DOUBLE_STEMS.contains(baseStem)) {
				return null;
			}
			return resolveJson(location, () -> computeChestHalfTexture(location, baseStem, isLeft));
		}
		return resolveJson(location, () -> computeChestTexture(location, stem));
	}

	/**
	 * Un-flips a legacy single (or ender) chest texture in place: same file, same canvas size,
	 * with each box's DOWN/UP and FRONT/BACK regions swapped per {@link #resolveChestTexture}.
	 */
	private byte @Nullable [] computeChestTexture(Identifier location, String stem) {
		IoSupplier<InputStream> supplier = delegate.getResource(
			PackType.CLIENT_RESOURCES, location.withPath(CHEST_TEXTURE_DIR + stem + ".png")
		);
		if (supplier == null) {
			return null;
		}
		try (InputStream in = supplier.get()) {
			BufferedImage source = ImageIO.read(in);
			if (source == null || source.getWidth() != source.getHeight() || source.getWidth() % CHEST_BASE_CANVAS_SIZE != 0) {
				return null;
			}
			int scale = source.getWidth() / CHEST_BASE_CANVAS_SIZE;
			BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = out.createGraphics();
			blitChestBoxUnflip(g, source, scale, 0, 0, 14, 5, 14); // lid
			blitChestBoxUnflip(g, source, scale, 0, 19, 14, 10, 14); // body
			blitChestBoxUnflip(g, source, scale, 0, 0, 2, 4, 1); // lock
			g.dispose();
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			ImageIO.write(out, "png", bytes);
			return bytes.toByteArray();
		} catch (IOException e) {
			AnyResource.LOGGER.warn("Failed to un-flip {} chest texture in legacy pack {}", stem, location().id(), e);
			return null;
		}
	}

	/**
	 * Synthesizes a modern {@code <stem>_left.png}/{@code <stem>_right.png} chest-half texture
	 * from a legacy pack's combined {@code <stem>_double.png} sheet. 1.8.9 rendered the whole
	 * double chest as one box twice the width of a single chest (30 units vs 14); modern splits it
	 * into two independent boxes, each 15 units wide on its own square canvas. Because both the
	 * canvas size and the box width changed, this can't be a single rectangular crop - each face
	 * whose size depends on box width (top/bottom/front/back) needs to be sliced at its own
	 * midpoint into its two halves, on top of the DOWN/UP and FRONT/BACK swap from
	 * {@link #resolveChestTexture}.
	 */
	private byte @Nullable [] computeChestHalfTexture(Identifier location, String baseStem, boolean isLeft) {
		IoSupplier<InputStream> doubleSupplier = delegate.getResource(
			PackType.CLIENT_RESOURCES, location.withPath(CHEST_TEXTURE_DIR + baseStem + "_double.png")
		);
		if (doubleSupplier == null) {
			return null;
		}
		try (InputStream in = doubleSupplier.get()) {
			BufferedImage source = ImageIO.read(in);
			if (source == null || source.getWidth() != source.getHeight() * 2 || source.getHeight() % CHEST_BASE_CANVAS_SIZE != 0) {
				return null;
			}
			int scale = source.getHeight() / CHEST_BASE_CANVAS_SIZE;
			int canvasSize = CHEST_BASE_CANVAS_SIZE * scale;
			BufferedImage out = new BufferedImage(canvasSize, canvasSize, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = out.createGraphics();
			blitChestBoxHalf(g, source, isLeft, scale, 0, 0, 30, 5, 14, 0, 0, 15); // lid
			blitChestBoxHalf(g, source, isLeft, scale, 0, 19, 30, 10, 14, 0, 19, 15); // body
			blitChestBoxHalf(g, source, isLeft, scale, 0, 0, 2, 4, 1, 0, 0, 1); // lock
			g.dispose();
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			ImageIO.write(out, "png", bytes);
			return bytes.toByteArray();
		} catch (IOException e) {
			AnyResource.LOGGER.warn("Failed to synthesize {} chest half texture in legacy pack {}", baseStem, location().id(), e);
			return null;
		}
	}

	/**
	 * Un-flips one box's worth of a legacy chest sheet (the lid, the body, or the tiny lock nub -
	 * each unwrapped via the classic Minecraft box-UV formula) onto the same canvas position:
	 * west/east rotate 180 degrees in place; the down/up and front/back regions swap slots, with
	 * down/up additionally flipped vertically and front/back additionally rotated 180 degrees.
	 */
	private static void blitChestBoxUnflip(Graphics2D g, BufferedImage source, int scale, int tx, int ty, int w, int h, int d) {
		tx *= scale;
		ty *= scale;
		w *= scale;
		h *= scale;
		d *= scale;
		int u1 = tx + d;
		int u2 = u1 + w;
		int u3 = u2 + d;
		int v1 = ty + d;
		blitRot180(g, source, tx, v1, d, h, tx, v1); // west
		blitRot180(g, source, u2, v1, d, h, u2, v1); // east
		blitFlipV(g, source, u2, ty, w, d, u1, ty); // dest-down <- source-up
		blitFlipV(g, source, u1, ty, w, d, u2, ty); // dest-up <- source-down
		blitRot180(g, source, u3, v1, w, h, u1, v1); // dest-front <- source-back
		blitRot180(g, source, u1, v1, w, h, u3, v1); // dest-back <- source-front
	}

	/**
	 * Copies one box's worth of a legacy double-chest sheet (the lid, the body, or the tiny lock
	 * nub - each unwrapped via the classic Minecraft box-UV formula, see {@link #computeChestHalfTexture})
	 * onto a single chest-half canvas, applying the same Y/Z-flip correction as
	 * {@link #blitChestBoxUnflip} together with the width split. {@code legacyW} is the full
	 * double-box width (twice {@code modernW}, the modern half-box width); faces whose size scales
	 * with box width (down/up/front/back) are sliced at the midpoint so each half gets only its own
	 * half of the artwork. Which half-slice feeds which destination, and in which orientation, was
	 * verified empirically per {@link #resolveChestTexture} (an exact pixel match against 26.2's
	 * own {@code normal_left.png}/{@code normal_right.png}) rather than assumed - the rotation
	 * applied to a face also reverses which half of its source slice ends up on which side, so the
	 * slice offset for down/up/back is the *opposite* of what a naive "left gets the first half"
	 * rule would give for front. The two side faces are the true left/right edges of the whole
	 * double chest, so only one of them has source art for a given half - the interior seam (never
	 * visible in the legacy double chest) is left blank on the other side. All box coordinates are
	 * expressed at vanilla 1.8.9 resolution (16px/block) and scaled up by {@code scale} for HD packs.
	 */
	private static void blitChestBoxHalf(
		Graphics2D g, BufferedImage source, boolean isLeft, int scale,
		int legacyTx, int legacyTy, int legacyW, int legacyH, int legacyD,
		int modernTx, int modernTy, int modernW
	) {
		legacyTx *= scale;
		legacyTy *= scale;
		legacyW *= scale;
		legacyH *= scale;
		legacyD *= scale;
		modernTx *= scale;
		modernTy *= scale;
		modernW *= scale;
		int legacyU1 = legacyTx + legacyD;
		int legacyU2 = legacyU1 + legacyW;
		int legacyU3 = legacyU2 + legacyD;
		int legacyV1 = legacyTy + legacyD;
		int modernU1 = modernTx + legacyD;
		int modernU2 = modernU1 + modernW;
		int modernU3 = modernU2 + legacyD;
		int modernV1 = modernTy + legacyD;
		// The left half's model culls its WEST face (interior seam) and renders EAST; the right
		// half culls EAST and renders WEST - see ChestModel.createDoubleBodyLeftLayer/-RightLayer's
		// visibleFaces. Fill whichever side is actually rendered; the culled side is left blank.
		if (isLeft) {
			blitRot180(g, source, legacyU2, legacyV1, legacyD, legacyH, modernU2, modernV1);
		} else {
			blitRot180(g, source, legacyTx, legacyV1, legacyD, legacyH, modernTx, modernV1);
		}
		int downUpOffset = isLeft ? modernW : 0;
		int frontOffset = isLeft ? 0 : modernW;
		int backOffset = isLeft ? modernW : 0;
		blitFlipV(g, source, legacyU2 + downUpOffset, legacyTy, modernW, legacyD, modernU1, modernTy); // dest-down <- source-up
		blitFlipV(g, source, legacyU1 + downUpOffset, legacyTy, modernW, legacyD, modernU2, modernTy); // dest-up <- source-down
		blitRot180(g, source, legacyU3 + frontOffset, legacyV1, modernW, legacyH, modernU1, modernV1); // dest-front <- source-back
		blitRot180(g, source, legacyU1 + backOffset, legacyV1, modernW, legacyH, modernU3, modernV1); // dest-back <- source-front
	}

	private static void blit(Graphics2D g, BufferedImage source, int sx, int sy, int w, int h, int dx, int dy) {
		g.drawImage(source, dx, dy, dx + w, dy + h, sx, sy, sx + w, sy + h, null);
	}

	private static void blitFlipV(Graphics2D g, BufferedImage source, int sx, int sy, int w, int h, int dx, int dy) {
		g.drawImage(source, dx, dy + h, dx + w, dy, sx, sy, sx + w, sy + h, null);
	}

	private static void blitRot180(Graphics2D g, BufferedImage source, int sx, int sy, int w, int h, int dx, int dy) {
		g.drawImage(source, dx + w, dy + h, dx, dy, sx, sy, sx + w, sy + h, null);
	}

	private @Nullable IoSupplier<InputStream> resolveJson(Identifier location, Supplier<byte[]> compute) {
		byte[] cached = jsonCache.computeIfAbsent(location, loc -> compute.get());
		return cached == null ? null : () -> new ByteArrayInputStream(cached);
	}

	/**
	 * See {@link #REDSTONE_DUST_DOT_TEXTURE_PATH}: crops the legacy pack's {@code
	 * redstone_dust_cross.png} down to the 5,5-11,11 region vanilla's own {@code
	 * redstone_dust_dot.png} occupies, onto an otherwise transparent canvas of the same size, rather
	 * than aliasing the whole (much busier, full-tile) legacy file.
	 */
	private byte @Nullable [] computeRedstoneDustDotTexture(Identifier location) {
		IoSupplier<InputStream> supplier = delegate.getResource(
			PackType.CLIENT_RESOURCES, location.withPath(OLD_BLOCK_TEXTURE_DIR + "redstone_dust_cross.png")
		);
		if (supplier == null) {
			return null;
		}
		try (InputStream in = supplier.get()) {
			BufferedImage source = ImageIO.read(in);
			if (source == null || source.getWidth() != source.getHeight() || source.getWidth() % REDSTONE_DUST_BASE_CANVAS_SIZE != 0) {
				return null;
			}
			int scale = source.getWidth() / REDSTONE_DUST_BASE_CANVAS_SIZE;
			BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = out.createGraphics();
			int cropMin = REDSTONE_DUST_DOT_CROP_MIN * scale;
			int cropSize = (REDSTONE_DUST_DOT_CROP_MAX - REDSTONE_DUST_DOT_CROP_MIN) * scale;
			blit(g, source, cropMin, cropMin, cropSize, cropSize, cropMin, cropMin);
			g.dispose();
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			ImageIO.write(out, "png", bytes);
			return bytes.toByteArray();
		} catch (IOException e) {
			AnyResource.LOGGER.warn("Failed to crop redstone dust dot texture in legacy pack {}", location().id(), e);
			return null;
		}
	}

	/**
	 * See {@link #REDSTONE_DUST_LINE_NS_TEXTURE}: transposes (swaps x/y) the legacy pack's
	 * horizontally-banded {@code redstone_dust_line.png} into the vertically-banded orientation
	 * vanilla's own line textures use, then - only for the east/west texture ({@code mirror}) -
	 * flips it horizontally on top of that, so the two served sprites aren't byte-identical.
	 * {@code location} plays no part in picking the source file (unlike every other {@code compute*}
	 * method here) because both callers now serve brand new, mod-namespaced sprite IDs that have no
	 * legacy-pack equivalent path to translate from; the legacy source is always
	 * {@code minecraft:textures/blocks/redstone_dust_line.png}.
	 */
	private boolean redstoneDustLineSourceExists() {
		return redstoneDustLineSource() != null;
	}

	private @Nullable IoSupplier<InputStream> redstoneDustLineSource() {
		return delegate.getResource(
			PackType.CLIENT_RESOURCES, Identifier.fromNamespaceAndPath("minecraft", OLD_BLOCK_TEXTURE_DIR + "redstone_dust_line.png")
		);
	}

	private byte @Nullable [] computeRedstoneDustLineTexture(boolean mirror) {
		IoSupplier<InputStream> supplier = redstoneDustLineSource();
		if (supplier == null) {
			return null;
		}
		try (InputStream in = supplier.get()) {
			BufferedImage source = ImageIO.read(in);
			if (source == null || source.getWidth() != source.getHeight()) {
				return null;
			}
			int size = source.getWidth();
			BufferedImage transposed = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
			Graphics2D tg = transposed.createGraphics();
			tg.drawImage(source, new AffineTransform(0, 1, 1, 0, 0, 0), null);
			tg.dispose();
			BufferedImage out = transposed;
			if (mirror) {
				out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
				Graphics2D g = out.createGraphics();
				g.drawImage(transposed, size, 0, 0, size, 0, 0, size, size, null);
				g.dispose();
			}
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			ImageIO.write(out, "png", bytes);
			return bytes.toByteArray();
		} catch (IOException e) {
			AnyResource.LOGGER.warn("Failed to transpose redstone dust line texture in legacy pack {}", location().id(), e);
			return null;
		}
	}

	private byte @Nullable [] computeBlockModel(Identifier location, String stem) {
		byte[] rewritten = tryRewriteJson(location);
		if (rewritten != null) {
			return rewritten;
		}
		String namespace = location.getNamespace();
		if (NO_GENERIC_FALLBACK_MODEL_STEMS.contains(stem)) {
			return null;
		}
		if (REDSTONE_DUST_NS_MODEL_STEMS.contains(stem) || REDSTONE_DUST_EW_MODEL_STEMS.contains(stem)) {
			// Only take over these models when the pack actually customizes redstone dust - packs
			// that don't touch it at all must keep vanilla's own working wire, not a texture-less one.
			if (!redstoneDustLineSourceExists()) {
				return null;
			}
			if (stem.equals("redstone_dust_up")) {
				return FallbackModelGenerator.redstoneDustUpModel(AnyResource.MOD_ID, REDSTONE_DUST_LINE_EW_STEM);
			}
			boolean ns = REDSTONE_DUST_NS_MODEL_STEMS.contains(stem);
			String parent = stem.startsWith("redstone_dust_side_alt") ? "redstone_dust_side_alt" : "redstone_dust_side";
			String textureStem = ns ? REDSTONE_DUST_LINE_NS_STEM : REDSTONE_DUST_LINE_EW_STEM;
			return FallbackModelGenerator.redstoneDustSideModel(parent, AnyResource.MOD_ID, textureStem);
		}
		if (TORCH_MODEL_TEMPLATES.containsKey(stem)) {
			String textureStem = TORCH_MODEL_TEXTURE_STEM.get(stem);
			return textureResolves(namespace, NEW_BLOCK_TEXTURE_DIR, textureStem)
				? FallbackModelGenerator.torchModel(namespace, TORCH_MODEL_TEMPLATES.get(stem), textureStem)
				: null;
		}
		if (LOG_STEMS.contains(stem) && textureResolves(namespace, NEW_BLOCK_TEXTURE_DIR, stem)) {
			return FallbackModelGenerator.pillarModel(namespace, stem, stem + "_top", false);
		}
		if (stem.endsWith(HORIZONTAL_SUFFIX)) {
			String base = stem.substring(0, stem.length() - HORIZONTAL_SUFFIX.length());
			if (LOG_STEMS.contains(base) && textureResolves(namespace, NEW_BLOCK_TEXTURE_DIR, base)) {
				return FallbackModelGenerator.pillarModel(namespace, base, base + "_top", true);
			}
		}
		if (textureResolves(namespace, NEW_BLOCK_TEXTURE_DIR, stem)) {
			return LEAVES_STEMS.contains(stem)
				? FallbackModelGenerator.leavesModel(namespace, stem)
				: FallbackModelGenerator.cubeAllModel(namespace, stem);
		}
		return null;
	}

	private byte @Nullable [] computeItemModel(Identifier location, String stem) {
		byte[] rewritten = tryRewriteJson(location);
		if (rewritten != null) {
			return rewritten;
		}
		String namespace = location.getNamespace();
		if (textureResolves(namespace, NEW_ITEM_TEXTURE_DIR, stem)) {
			return FallbackModelGenerator.generatedItemModel(namespace, "item/" + stem);
		}
		if (textureResolves(namespace, NEW_BLOCK_TEXTURE_DIR, stem)) {
			return FallbackModelGenerator.generatedItemModel(namespace, "block/" + stem);
		}
		return null;
	}

	/**
	 * 1.8.9's own {@code blockstates/redstone_wire.json} enumerates one named model per
	 * connectivity combination ({@code redstone_n}, {@code redstone_ne}, ...,
	 * {@code redstone_unusueuw} - ~35 in total, see {@code models/block/redstone_n.json} etc. in
	 * that version), a scheme modern Minecraft dropped entirely in favour of a
	 * {@code multipart}/{@code redstone_dust_dot}+{@code redstone_dust_side0/1} system; none of
	 * those old named models exist in modern vanilla assets anymore. A legacy pack that ships its
	 * own {@code blockstates/redstone_wire.json} (common even for texture-focused packs, since many
	 * are built by copying the vanilla asset tree) would otherwise have that old-scheme file passed
	 * straight through by {@link #tryRewriteJson} - {@link JsonRewriter} only rewrites
	 * {@code "blocks/"}/{@code "items/"} texture-path prefixes, not bare model names like
	 * {@code "redstone_n"} - leaving most connectivity states pointing at models that no longer
	 * exist anywhere, i.e. missing-texture/checkerboard. There's no way to honor that old scheme
	 * against modern's asset layout, so always defer to vanilla's own (current) blockstate and
	 * models here; only the {@code redstone_dust_dot}/{@code redstone_dust_line0}/
	 * {@code redstone_dust_line1} textures get remapped, via {@link #resolveTexture}.
	 */
	private byte @Nullable [] computeBlockstate(Identifier location, String stem) {
		if (stem.equals("redstone_wire")) {
			return null;
		}
		byte[] rewritten = tryRewriteJson(location);
		if (rewritten != null) {
			return rewritten;
		}
		String namespace = location.getNamespace();
		if (stem.equals("redstone_torch") || stem.equals("redstone_wall_torch")) {
			String unlitStem = stem + "_off";
			if (!blockModelResolves(namespace, stem) || !blockModelResolves(namespace, unlitStem)) {
				return null;
			}
			return stem.equals("redstone_wall_torch")
				? FallbackModelGenerator.wallLitUnlitBlockstate(namespace, stem, unlitStem)
				: FallbackModelGenerator.litUnlitBlockstate(namespace, stem, unlitStem);
		}
		if (!blockModelResolves(namespace, stem)) {
			return null;
		}
		if (stem.equals("wall_torch")) {
			return FallbackModelGenerator.wallTorchBlockstate(namespace, stem);
		}
		return LOG_STEMS.contains(stem)
			? FallbackModelGenerator.pillarBlockstate(namespace, stem)
			: FallbackModelGenerator.singleVariantBlockstate(namespace, stem);
	}

	private boolean blockModelResolves(String namespace, String stem) {
		Identifier modelLocation = Identifier.fromNamespaceAndPath(namespace, MODEL_BLOCK_DIR + stem + ".json");
		return resolveJson(modelLocation, () -> computeBlockModel(modelLocation, stem)) != null;
	}

	private byte @Nullable [] tryRewriteJson(Identifier location) {
		IoSupplier<InputStream> direct = delegate.getResource(PackType.CLIENT_RESOURCES, location);
		if (direct == null) {
			return null;
		}
		try (InputStream in = direct.get()) {
			JsonElement parsed = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			return GSON.toJson(JsonRewriter.rewrite(parsed)).getBytes(StandardCharsets.UTF_8);
		} catch (IOException | JsonParseException e) {
			AnyResource.LOGGER.warn("Failed to convert {} in legacy pack {}", location, location().id(), e);
			return null;
		}
	}

	private boolean textureResolves(String namespace, String newDirectory, String stem) {
		String oldPath = translateTexturePath(newDirectory + stem + ".png");
		return oldPath != null && delegate.getResource(PackType.CLIENT_RESOURCES, Identifier.fromNamespaceAndPath(namespace, oldPath)) != null;
	}

	private IoSupplier<InputStream> rewriteJsonSupplier(Identifier id, IoSupplier<InputStream> original) {
		return () -> {
			try (InputStream in = original.get()) {
				JsonElement parsed = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
				return new ByteArrayInputStream(GSON.toJson(JsonRewriter.rewrite(parsed)).getBytes(StandardCharsets.UTF_8));
			} catch (JsonParseException e) {
				throw new IOException("Failed to convert " + id + " in legacy pack " + location().id(), e);
			}
		};
	}

	private boolean compassSourceExists() {
		return delegate.getResource(
			PackType.CLIENT_RESOURCES, Identifier.fromNamespaceAndPath("minecraft", LEGACY_COMPASS_TEXTURE_PATH)
		) != null;
	}

	private static String compassFrameStem(int frame) {
		return COMPASS_FRAME_STEM_PREFIX + (frame < 10 ? "0" + frame : Integer.toString(frame));
	}

	/** Returns the frame index for {@code item/compass_NN.png}, or {@code null} if the stem doesn't match. */
	private static @Nullable Integer parseCompassFrameIndex(String stemWithExtension) {
		if (!stemWithExtension.startsWith(COMPASS_FRAME_STEM_PREFIX) || !stemWithExtension.endsWith(".png")) {
			return null;
		}
		String digits = stemWithExtension.substring(
			COMPASS_FRAME_STEM_PREFIX.length(), stemWithExtension.length() - ".png".length()
		);
		if (digits.length() != 2 || !Character.isDigit(digits.charAt(0)) || !Character.isDigit(digits.charAt(1))) {
			return null;
		}
		return Integer.parseInt(digits);
	}

	/**
	 * See {@link #LEGACY_COMPASS_TEXTURE_PATH}: crops the single legacy needle frame matching
	 * {@code modernFrame} out of the pack's own animated strip, resampling proportionally by the
	 * pack's actual frame count (its height divided by its own square frame width) rather than
	 * assuming the strip has exactly 32 frames like modern's own fixed bucket count.
	 */
	private byte @Nullable [] computeCompassFrameTexture(int modernFrame) {
		BufferedImage source = loadSheet(LEGACY_COMPASS_TEXTURE_PATH);
		if (source == null || source.getWidth() <= 0 || source.getHeight() % source.getWidth() != 0) {
			return null;
		}
		int frameSize = source.getWidth();
		int legacyFrameCount = source.getHeight() / frameSize;
		if (legacyFrameCount <= 0) {
			return null;
		}
		try {
			int legacyFrame = modernFrame * legacyFrameCount / COMPASS_MODERN_FRAME_COUNT;
			BufferedImage frame = source.getSubimage(0, legacyFrame * frameSize, frameSize, frameSize);
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			ImageIO.write(frame, "png", bytes);
			return bytes.toByteArray();
		} catch (IOException e) {
			AnyResource.LOGGER.warn("Failed to synthesize compass frame {} in legacy pack {}", modernFrame, location().id(), e);
			return null;
		}
	}

	/**
	 * The shared "blocks"/"items" atlases are populated by {@code DirectoryLister} enumerating
	 * whatever this method lists - not by walking model texture references - so a stray legacy
	 * file with no real modern purpose still gets swept in under {@link TextureNameMaps}'s
	 * identity fallback. A single such orphan, if wildly non-square (e.g. a leftover, unused
	 * {@code water.png} sitting alongside the properly named {@code water_still.png}/
	 * {@code water_flow.png}), forces the shared atlas dramatically larger than intended, which
	 * degrades every sprite packed into it - including unrelated vanilla-textured blocks - not
	 * just the offending file itself. A block/item texture is only ever legitimately non-square
	 * as a vertical animation strip with matching frame metadata, so anything non-square lacking
	 * that backing is rejected here rather than announced to the atlas.
	 */
	private boolean isValidLegacyTexture(Identifier oldId, IoSupplier<InputStream> supplier) {
		if (!oldId.getPath().endsWith(".png")) {
			return true;
		}
		try (InputStream in = supplier.get()) {
			BufferedImage image = ImageIO.read(in);
			if (image == null) {
				return false;
			}
			int width = image.getWidth();
			int height = image.getHeight();
			if (width == height) {
				return true;
			}
			if (width <= 0 || height % width != 0) {
				return false;
			}
			return delegate.getResource(PackType.CLIENT_RESOURCES, oldId.withPath(oldId.getPath() + ".mcmeta")) != null;
		} catch (IOException e) {
			AnyResource.LOGGER.warn("Failed to read legacy texture {} in pack {}", oldId, location().id(), e);
			return false;
		}
	}

	private static boolean isOrUnder(String directory, String prefix) {
		return directory.equals(prefix) || directory.startsWith(prefix + "/");
	}

	/** The inverse relationship to {@link #isOrUnder}: does the queried {@code directory} contain {@code target}? */
	private static boolean directoryCovers(String directory, String target) {
		return directory.equals(target) || target.startsWith(directory + "/");
	}

	private static @Nullable Identifier translateListed(Identifier oldId, String oldDir, String newDir, UnaryOperator<String> nameMap) {
		String path = oldId.getPath();
		if (!path.startsWith(oldDir)) {
			return null;
		}
		String rest = path.substring(oldDir.length());
		String stem;
		String suffix;
		if (rest.endsWith(".png.mcmeta")) {
			suffix = ".png.mcmeta";
			stem = rest.substring(0, rest.length() - suffix.length());
		} else if (rest.endsWith(".png")) {
			suffix = ".png";
			stem = rest.substring(0, rest.length() - suffix.length());
		} else {
			return null;
		}
		return oldId.withPath(newDir + nameMap.apply(stem) + suffix);
	}

	private static @Nullable String translateTexturePath(String path) {
		if (path.startsWith(NEW_BLOCK_TEXTURE_DIR)) {
			return translate(path, NEW_BLOCK_TEXTURE_DIR, OLD_BLOCK_TEXTURE_DIR, TextureNameMaps::oldBlockName);
		}
		if (path.startsWith(NEW_ITEM_TEXTURE_DIR)) {
			return translate(path, NEW_ITEM_TEXTURE_DIR, OLD_ITEM_TEXTURE_DIR, TextureNameMaps::oldItemName);
		}
		return null;
	}

	/**
	 * Modern armor rendering loads {@code textures/entity/equipment/<layer-type>/<material>.png}
	 * (referenced from a data-driven {@code equipment/<material>.json}, which legacy packs never
	 * define but vanilla still supplies for the stock materials). Pre-1.13 packs instead ship
	 * {@code textures/models/armor/<material>_layer_1.png} (helmet/chestplate/boots, also used
	 * for the baby variant) and {@code _layer_2.png} (leggings), with dyeable leather adding an
	 * {@code _overlay} suffixed file. Material stems (leather, chainmail, iron, gold, diamond)
	 * are unchanged across versions, so only the directory/suffix scheme needs translating.
	 */
	private static @Nullable String translateEquipmentTexturePath(String path) {
		String rest = path.substring(NEW_EQUIPMENT_TEXTURE_DIR.length());
		int slash = rest.indexOf('/');
		if (slash < 0 || !rest.endsWith(".png")) {
			return null;
		}
		String layerType = rest.substring(0, slash);
		String stem = rest.substring(slash + 1, rest.length() - ".png".length());
		String layerSuffix = switch (layerType) {
			case "humanoid", "humanoid_baby" -> "_layer_1";
			case "humanoid_leggings" -> "_layer_2";
			default -> null;
		};
		if (layerSuffix == null) {
			return null;
		}
		boolean overlay = stem.endsWith(OVERLAY_SUFFIX);
		String material = overlay ? stem.substring(0, stem.length() - OVERLAY_SUFFIX.length()) : stem;
		String oldStem = material + layerSuffix + (overlay ? OVERLAY_SUFFIX : "");
		return OLD_ARMOR_TEXTURE_DIR + oldStem + ".png";
	}

	private static @Nullable String translate(String path, String newDir, String oldDir, UnaryOperator<String> nameMap) {
		String rest = path.substring(newDir.length());
		String stem;
		String suffix;
		if (rest.endsWith(".png.mcmeta")) {
			suffix = ".png.mcmeta";
			stem = rest.substring(0, rest.length() - suffix.length());
		} else if (rest.endsWith(".png")) {
			suffix = ".png";
			stem = rest.substring(0, rest.length() - suffix.length());
		} else {
			return null;
		}
		return oldDir + nameMap.apply(stem) + suffix;
	}
}
