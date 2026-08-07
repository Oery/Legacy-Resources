package dev.oery.anyresource.client.convert;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.oery.anyresource.AnyResource;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
	private static final String HORIZONTAL_SUFFIX = "_horizontal";
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
		Identifier modelLocation = Identifier.fromNamespaceAndPath(namespace, MODEL_BLOCK_DIR + stem + ".json");
		if (resolveJson(modelLocation, () -> computeBlockModel(modelLocation, stem)) == null) {
			return null;
		}
		return LOG_STEMS.contains(stem)
			? FallbackModelGenerator.pillarBlockstate(namespace, stem)
			: FallbackModelGenerator.singleVariantBlockstate(namespace, stem);
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
