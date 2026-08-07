package dev.oery.anyresource.client.convert;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.oery.anyresource.AnyResource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.GsonHelper;

/**
 * Old (1.13 flattening) block/item texture stem name -> new stem name, and back.
 * Unknown stems fall back to identity, since most texture file names never changed.
 */
public final class TextureNameMaps {
	private static final Map<String, String> BLOCK_TO_OLD = load("block_textures.json");
	private static final Map<String, String> ITEM_TO_OLD = load("item_textures.json");
	private static final Map<String, String> BLOCK_TO_NEW = invert(BLOCK_TO_OLD);
	private static final Map<String, String> ITEM_TO_NEW = invert(ITEM_TO_OLD);

	private TextureNameMaps() {
	}

	public static String oldBlockName(String newStem) {
		return BLOCK_TO_OLD.getOrDefault(newStem, newStem);
	}

	public static String oldItemName(String newStem) {
		return ITEM_TO_OLD.getOrDefault(newStem, newStem);
	}

	public static String newBlockName(String oldStem) {
		return BLOCK_TO_NEW.getOrDefault(oldStem, oldStem);
	}

	public static String newItemName(String oldStem) {
		return ITEM_TO_NEW.getOrDefault(oldStem, oldStem);
	}

	private static Map<String, String> load(String fileName) {
		String path = "assets/" + AnyResource.MOD_ID + "/conversion/" + fileName;
		try (InputStream in = TextureNameMaps.class.getClassLoader().getResourceAsStream(path)) {
			if (in == null) {
				AnyResource.LOGGER.warn("Missing bundled texture mapping {}", path);
				return Map.of();
			}
			JsonObject json = GsonHelper.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
			Map<String, String> map = new HashMap<>();
			json.entrySet().forEach(entry -> map.put(entry.getKey(), entry.getValue().getAsString()));
			return Map.copyOf(map);
		} catch (IOException | JsonParseException e) {
			AnyResource.LOGGER.warn("Failed to load bundled texture mapping {}", path, e);
			return Map.of();
		}
	}

	private static Map<String, String> invert(Map<String, String> map) {
		Map<String, String> inverted = new HashMap<>();
		map.forEach((newStem, oldStem) -> inverted.putIfAbsent(oldStem, newStem));
		return Map.copyOf(inverted);
	}
}
