package dev.oery.legacyresources.client.convert;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.oery.legacyresources.LegacyResources;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.GsonHelper;

/**
 * Old (1.13 flattening) block/item texture stem name -> new stem name, and back.
 * Unknown stems fall back to identity, since most texture file names never changed.
 * <p>
 * The forward direction is a function; the inverse is not. The flattening split some files in two,
 * so one legacy name can be the source for several modern ones - {@code dye_powder_white} is both
 * {@code white_dye} and {@code bone_meal} - and the two directions need different answers about
 * that. Rewriting a reference inside a model has to pick one name ({@link #newBlockName}), since a
 * texture variable takes a single value; announcing a file has to emit all of them
 * ({@link #newBlockNames}), since the pack's one file really is the art for every sprite the
 * flattening made from it, and a name left unannounced silently renders as vanilla's.
 */
public final class TextureNameMaps {
	private static final Map<String, String> BLOCK_TO_OLD = load("block_textures.json");
	private static final Map<String, String> ITEM_TO_OLD = load("item_textures.json");
	private static final Map<String, List<String>> BLOCK_TO_NEW = invert(BLOCK_TO_OLD);
	private static final Map<String, List<String>> ITEM_TO_NEW = invert(ITEM_TO_OLD);

	private TextureNameMaps() {
	}

	public static String oldBlockName(String newStem) {
		return BLOCK_TO_OLD.getOrDefault(newStem, newStem);
	}

	public static String oldItemName(String newStem) {
		return ITEM_TO_OLD.getOrDefault(newStem, newStem);
	}

	/** The modern name to write when a single one is required; see {@link #newBlockNames}. */
	public static String newBlockName(String oldStem) {
		return BLOCK_TO_NEW.getOrDefault(oldStem, List.of(oldStem)).getFirst();
	}

	public static String newItemName(String oldStem) {
		return ITEM_TO_NEW.getOrDefault(oldStem, List.of(oldStem)).getFirst();
	}

	/** Every modern name this legacy file is the art for - never empty. */
	public static List<String> newBlockNames(String oldStem) {
		return BLOCK_TO_NEW.getOrDefault(oldStem, List.of(oldStem));
	}

	public static List<String> newItemNames(String oldStem) {
		return ITEM_TO_NEW.getOrDefault(oldStem, List.of(oldStem));
	}

	private static Map<String, String> load(String fileName) {
		String path = "assets/" + LegacyResources.MOD_ID + "/conversion/" + fileName;
		try (InputStream in = TextureNameMaps.class.getClassLoader().getResourceAsStream(path)) {
			if (in == null) {
				LegacyResources.LOGGER.warn("Missing bundled texture mapping {}", path);
				return Map.of();
			}
			JsonObject json = GsonHelper.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
			// Insertion-ordered, and kept that way rather than handed to Map.copyOf, whose iteration
			// order is unspecified: invert() reads this order to decide which of a legacy file's
			// modern names is the canonical one.
			Map<String, String> map = new LinkedHashMap<>();
			json.entrySet().forEach(entry -> map.put(entry.getKey(), entry.getValue().getAsString()));
			return Collections.unmodifiableMap(map);
		} catch (IOException | JsonParseException e) {
			LegacyResources.LOGGER.warn("Failed to load bundled texture mapping {}", path, e);
			return Map.of();
		}
	}

	/**
	 * Groups the map by its values, so a legacy name split in two by the flattening keeps both of its
	 * modern names. Ordered by the mapping file, which makes {@link #newBlockName}'s choice of a
	 * single name the first entry written there rather than a hash order that could change between
	 * runs - and lets the file say which name is canonical by listing it first.
	 */
	private static Map<String, List<String>> invert(Map<String, String> map) {
		Map<String, List<String>> inverted = new LinkedHashMap<>();
		map.forEach((newStem, oldStem) -> inverted.computeIfAbsent(oldStem, key -> new ArrayList<>()).add(newStem));
		Map<String, List<String>> copy = new LinkedHashMap<>();
		inverted.forEach((oldStem, newStems) -> copy.put(oldStem, List.copyOf(newStems)));
		return Map.copyOf(copy);
	}
}
