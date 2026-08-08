package dev.oery.anyresource.client.convert;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.oery.anyresource.AnyResource;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
	 * The anvil's three damage states are sculpted (non-cube) shapes built from vanilla's own
	 * {@code template_anvil} parent, with only the {@code top} texture var swapped per state - never
	 * a {@code cube_all}. Once {@code anvil}/{@code anvil_top}/{@code chipped_anvil_top}/
	 * {@code damaged_anvil_top} resolve to legacy texture files, the generic
	 * {@link #computeBlockModel} fallback below would otherwise treat the stem-matching texture as
	 * license to synthesize a flat {@code cube_all} model, discarding the sculpted shape and the
	 * per-state top texture. These stems must defer to vanilla's own model/blockstate JSON; only the
	 * texture bytes need remapping.
	 */
	private static final Set<String> ANVIL_MODEL_STEMS = Set.of("anvil", "chipped_anvil", "damaged_anvil");
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
	 */
	private static final Map<String, String> ENTITY_TEXTURE_ALIASES = Map.of(
		"textures/entity/cow/cow_temperate.png", "textures/entity/cow/cow.png",
		"textures/entity/cow/mooshroom_red.png", "textures/entity/cow/mooshroom.png"
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

		String path = location.getPath();
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

		if (isOrUnder(directory, "textures/block")) {
			String oldDirectory = "textures/blocks" + directory.substring("textures/block".length());
			delegate.listResources(type, namespace, oldDirectory, (oldId, supplier) -> {
				Identifier newId = translateListed(oldId, OLD_BLOCK_TEXTURE_DIR, NEW_BLOCK_TEXTURE_DIR, TextureNameMaps::newBlockName);
				if (newId != null) {
					output.accept(newId, supplier);
				}
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
		if (isOrUnder(directory, "models/block") || isOrUnder(directory, "models/item") || isOrUnder(directory, "blockstates")) {
			delegate.listResources(type, namespace, directory, (id, supplier) -> output.accept(id, rewriteJsonSupplier(id, supplier)));
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

	@Override
	public Set<String> getNamespaces(PackType type) {
		return delegate.getNamespaces(type);
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

	private byte @Nullable [] computeBlockModel(Identifier location, String stem) {
		byte[] rewritten = tryRewriteJson(location);
		if (rewritten != null) {
			return rewritten;
		}
		String namespace = location.getNamespace();
		if (ANVIL_MODEL_STEMS.contains(stem)) {
			return null;
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

	private byte @Nullable [] computeBlockstate(Identifier location, String stem) {
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
