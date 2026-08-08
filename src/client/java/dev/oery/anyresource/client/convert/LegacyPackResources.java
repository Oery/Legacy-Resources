package dev.oery.anyresource.client.convert;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.oery.anyresource.AnyResource;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
	 * Pre-1.13 Minecraft rendered the fishing bobber by cropping a fixed icon cell out of the
	 * shared particle sheet, rather than using its own texture file. The modern renderer always
	 * loads a dedicated {@link #FISHING_HOOK_TEXTURE_PATH} and never reads the particle sheet, so
	 * there's no path to alias - instead the icon is cropped from the legacy pack's own
	 * {@link #PARTICLE_ATLAS_PATH} at load time and served as a synthesized PNG. The icon's grid
	 * cell (column 1, row 2 of a 16x16 grid) was confirmed empirically: two independently drawn
	 * legacy packs both have a fishhook-shaped icon at that exact cell.
	 */
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

	private final PackResources delegate;
	private final Map<Identifier, byte[]> jsonCache = new ConcurrentHashMap<>();

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
		if (path.startsWith(NEW_BLOCK_TEXTURE_DIR) || path.startsWith(NEW_ITEM_TEXTURE_DIR)) {
			return resolveTexture(location, path);
		}
		if (path.startsWith(NEW_EQUIPMENT_TEXTURE_DIR)) {
			return resolveEquipmentTexture(location, path);
		}
		if (ENTITY_TEXTURE_ALIASES.containsKey(path)) {
			return delegate.getResource(PackType.CLIENT_RESOURCES, location.withPath(ENTITY_TEXTURE_ALIASES.get(path)));
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
				output.accept(newId, supplier);
			});
			return;
		}
		if (isOrUnder(directory, "textures/item")) {
			String oldDirectory = "textures/items" + directory.substring("textures/item".length());
			delegate.listResources(type, namespace, oldDirectory, (oldId, supplier) -> {
				Identifier newId = translateListed(oldId, OLD_ITEM_TEXTURE_DIR, NEW_ITEM_TEXTURE_DIR, TextureNameMaps::newItemName);
				if (newId != null) {
					output.accept(newId, supplier);
				}
			});
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

	private @Nullable IoSupplier<InputStream> resolveEquipmentTexture(Identifier location, String path) {
		String oldPath = translateEquipmentTexturePath(path);
		if (oldPath == null) {
			return null;
		}
		return delegate.getResource(PackType.CLIENT_RESOURCES, location.withPath(oldPath));
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
