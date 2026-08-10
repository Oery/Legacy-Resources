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
 * Explicit flattening aliases for named JSON resources.
 *
 * <p>These deliberately do not reuse {@link TextureNameMaps}' data. A texture's name often gives
 * useful evidence for a resource rename, but model and blockstate names describe geometry and
 * blocks rather than sprites: {@code trapdoor -> oak_trapdoor}, for example, becomes
 * {@code wooden_trapdoor_bottom -> oak_trapdoor_bottom} for a finished model and
 * {@code trapdoor_bottom -> template_trapdoor_bottom} for its parent.
 */
final class ResourceNameMaps {
	private static final Names BLOCKSTATES = load("blockstates.json");
	private static final Names BLOCK_MODELS = load("block_models.json");

	private ResourceNameMaps() {
	}

	static String oldBlockstateName(String newStem) {
		return BLOCKSTATES.oldName(newStem);
	}

	static List<String> newBlockstateNames(String oldStem) {
		return BLOCKSTATES.newNames(oldStem);
	}

	static String oldBlockModelName(String newStem) {
		return BLOCK_MODELS.oldName(newStem);
	}

	static List<String> newBlockModelNames(String oldStem) {
		return BLOCK_MODELS.newNames(oldStem);
	}

	/**
	 * Every identifier a legacy model must keep: its modern aliases, plus its original identifier
	 * when it has one. Legacy pack blockstates can still refer to the original while modern vanilla
	 * blockstates select the aliases.
	 */
	static List<String> allBlockModelNames(String oldStem) {
		List<String> mapped = newBlockModelNames(oldStem);
		if (mapped.size() == 1 && mapped.getFirst().equals(oldStem)) {
			return mapped;
		}
		List<String> all = new ArrayList<>(mapped);
		all.add(oldStem);
		return List.copyOf(all);
	}

	/** Rewrites an already-qualified {@code block/<stem>} model reference. */
	static String newBlockModelPath(String path) {
		if (!path.startsWith("block/")) {
			return path;
		}
		return "block/" + BLOCK_MODELS.newName(path.substring("block/".length()));
	}

	private static Names load(String fileName) {
		String path = "assets/" + LegacyResources.MOD_ID + "/conversion/" + fileName;
		try (InputStream in = ResourceNameMaps.class.getClassLoader().getResourceAsStream(path)) {
			if (in == null) {
				LegacyResources.LOGGER.warn("Missing bundled resource mapping {}", path);
				return new Names(Map.of());
			}
			JsonObject json = GsonHelper.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
			Map<String, String> names = new LinkedHashMap<>();
			json.entrySet().forEach(entry -> names.put(entry.getKey(), entry.getValue().getAsString()));
			return new Names(names);
		} catch (IOException | JsonParseException e) {
			LegacyResources.LOGGER.warn("Failed to load bundled resource mapping {}", path, e);
			return new Names(Map.of());
		}
	}

	private record Names(Map<String, String> toOld, Map<String, List<String>> toNew) {
		Names(Map<String, String> toOld) {
			this(Collections.unmodifiableMap(new LinkedHashMap<>(toOld)), invert(toOld));
		}

		String oldName(String newStem) {
			return toOld.getOrDefault(newStem, newStem);
		}

		String newName(String oldStem) {
			return newNames(oldStem).getFirst();
		}

		List<String> newNames(String oldStem) {
			return toNew.getOrDefault(oldStem, List.of(oldStem));
		}

		private static Map<String, List<String>> invert(Map<String, String> map) {
			Map<String, List<String>> inverted = new LinkedHashMap<>();
			map.forEach((newStem, oldStem) -> inverted.computeIfAbsent(oldStem, key -> new ArrayList<>()).add(newStem));
			Map<String, List<String>> copy = new LinkedHashMap<>();
			inverted.forEach((oldStem, newStems) -> copy.put(oldStem, List.copyOf(newStems)));
			return Map.copyOf(copy);
		}
	}
}
