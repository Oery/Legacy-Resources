package dev.oery.legacyresources.lab;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.util.datafix.fixes.BlockStateData;

/**
 * Derives resource aliases from vanilla's actual pre- and post-flattening blockstate files.
 *
 * <p>The flattening table supplies the authoritative block rename. For each legacy variant, the
 * matching modern variant for the same properties supplies the model alias. A result is emitted
 * only where exactly one legacy model supplies a modern model; structural redesigns and ambiguous
 * joins are reported instead of guessed.
 */
public final class ModelNameMapGenerator {
	private static final Gson GSON = new Gson();
	private static final String BLOCKSTATES = "blockstates";
	private static final String MODEL = "model";

	private ModelNameMapGenerator() {
	}

	public static void main(String[] args) throws IOException {
		Path project = Path.of(System.getProperty("lab.project", "."));
		Path oldStates = project.resolve("reference/1.8.9/assets/assets/minecraft/blockstates");
		Path newStates = project.resolve("reference/26.2/assets/assets/minecraft/blockstates");
		Result result = derive(oldStates, newStates);
		if (List.of(args).contains("--report")) {
			printReport(result);
			return;
		}
		if (List.of(args).contains("--check")) {
			verifyBundled(project, result.models());
			System.out.println("Verified " + result.models().size() + " vanilla-derived block-model aliases");
			return;
		}
		System.out.println("# Safe blockstate aliases");
		print(result.blockstates());
		System.out.println("# Safe block-model aliases");
		print(result.models());
		System.out.println("# Not inferred: " + result.unmatchedStates().size() + " blockstates, " + result.ambiguousModels().size() + " ambiguous model targets");
	}

	private static void verifyBundled(Path project, Map<String, String> inferred) throws IOException {
		Path bundled = project.resolve("src/client/resources/assets/legacy-resources/conversion/block_models.json");
		JsonObject mappings = read(bundled);
		List<String> missing = inferred.entrySet().stream()
			.filter(entry -> !mappings.has(entry.getKey()) || !mappings.get(entry.getKey()).getAsString().equals(entry.getValue()))
			.map(entry -> entry.getKey() + " -> " + entry.getValue()).toList();
		if (!missing.isEmpty()) {
			throw new IllegalStateException("Bundled block model map is missing " + missing.size() + " vanilla-derived aliases: " + missing);
		}
	}

	private static void printReport(Result result) {
		System.out.println("# Vanilla model-alias derivation exceptions\n");
		System.out.println("Generated from `reference/1.8.9` and `reference/26.2` by `./gradlew generateModelNameMaps`. "
			+ "These entries are deliberately excluded from `block_models.json`: they need a dedicated conversion or an explicit reviewed alias.\n");
		System.out.println("## Legacy blockstates with no modern blockstate file\n");
		for (Map.Entry<String, String> entry : result.unmatchedStates().entrySet()) {
			System.out.println("- `" + entry.getKey() + "` → `" + entry.getValue() + "`");
		}
		System.out.println("\n## Modern model names with conflicting legacy sources\n");
		for (Map.Entry<String, Set<String>> entry : result.ambiguousModels().entrySet()) {
			System.out.println("- `" + entry.getKey() + "` ← " + entry.getValue().stream().map(value -> "`" + value + "`").toList());
		}
	}

	static Result derive(Path oldStates, Path newStates) throws IOException {
		Map<String, String> blockstates = new TreeMap<>();
		Map<String, Set<String>> modelSources = new TreeMap<>();
		Map<String, String> unmatched = new TreeMap<>();
		try (var files = Files.list(oldStates)) {
			for (Path oldFile : files.filter(path -> path.getFileName().toString().endsWith(".json")).sorted().toList()) {
				String oldStem = stem(oldFile);
				String modernName = BlockStateData.upgradeBlock("minecraft:" + oldStem);
				String modernStem = modernName.substring(modernName.indexOf(':') + 1);
				Path modernFile = newStates.resolve(modernStem + ".json");
				if (!Files.isRegularFile(modernFile)) {
					unmatched.put(oldStem, modernStem);
					continue;
				}
				if (!oldStem.equals(modernStem)) {
					blockstates.put(modernStem, oldStem);
				}
				joinModels(read(oldFile), read(modernFile), modelSources);
			}
		}
		Map<String, String> models = new TreeMap<>();
		Map<String, Set<String>> ambiguous = new TreeMap<>();
		for (Map.Entry<String, Set<String>> entry : modelSources.entrySet()) {
			String source = entry.getValue().iterator().next();
			if (entry.getValue().size() == 1 && !entry.getKey().equals(source)) {
				models.put(entry.getKey(), source);
			} else if (entry.getValue().size() > 1) {
				ambiguous.put(entry.getKey(), Set.copyOf(entry.getValue()));
			}
		}
		return new Result(
			Collections.unmodifiableMap(blockstates), Collections.unmodifiableMap(models),
			Collections.unmodifiableMap(unmatched), Collections.unmodifiableMap(ambiguous)
		);
	}

	private static void joinModels(JsonObject oldRoot, JsonObject modernRoot, Map<String, Set<String>> sources) {
		JsonObject oldVariants = variants(oldRoot);
		JsonObject modernVariants = variants(modernRoot);
		if (oldVariants == null || modernVariants == null) {
			return;
		}
		for (Map.Entry<String, JsonElement> oldVariant : oldVariants.entrySet()) {
			Map<String, String> oldState = selector(oldVariant.getKey());
			if (oldState == null) {
				continue;
			}
			for (String oldModel : models(oldVariant.getValue())) {
				for (Map.Entry<String, JsonElement> modernVariant : modernVariants.entrySet()) {
					Map<String, String> modernState = selector(modernVariant.getKey());
					if (modernState != null && matches(oldState, modernState)) {
						for (String modernModel : models(modernVariant.getValue())) {
							sources.computeIfAbsent(modernModel, ignored -> new TreeSet<>()).add(oldModel);
						}
					}
				}
			}
		}
	}

	private static JsonObject variants(JsonObject root) {
		JsonElement variants = root.get("variants");
		return variants != null && variants.isJsonObject() ? variants.getAsJsonObject() : null;
	}

	private static Map<String, String> selector(String value) {
		if (value.isEmpty() || value.equals("normal") || value.equals("all")) {
			return Map.of();
		}
		Map<String, String> result = new LinkedHashMap<>();
		for (String term : value.split(",")) {
			int equals = term.indexOf('=');
			if (equals <= 0 || term.indexOf('|') >= 0) {
				return null;
			}
			result.put(term.substring(0, equals), term.substring(equals + 1));
		}
		return result;
	}

	/** Whether every modern condition is specified with the same value by this legacy variant. */
	private static boolean matches(Map<String, String> oldState, Map<String, String> modernState) {
		return modernState.entrySet().stream().allMatch(entry -> Objects.equals(oldState.get(entry.getKey()), entry.getValue()));
	}

	private static List<String> models(JsonElement element) {
		if (element.isJsonArray()) {
			List<String> result = new ArrayList<>();
			element.getAsJsonArray().forEach(part -> result.addAll(models(part)));
			return result;
		}
		if (!element.isJsonObject()) {
			return List.of();
		}
		JsonElement model = element.getAsJsonObject().get(MODEL);
		if (model == null || !model.isJsonPrimitive() || !model.getAsJsonPrimitive().isString()) {
			return List.of();
		}
		String path = model.getAsString();
		int colon = path.indexOf(':');
		path = colon < 0 ? path : path.substring(colon + 1);
		return path.startsWith("block/") ? List.of(path.substring("block/".length())) : List.of(path);
	}

	private static JsonObject read(Path file) throws IOException {
		try (Reader reader = Files.newBufferedReader(file)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		}
	}

	private static String stem(Path file) {
		String name = file.getFileName().toString();
		return name.substring(0, name.length() - ".json".length());
	}

	private static void print(Map<String, String> map) {
		JsonObject json = new JsonObject();
		map.forEach(json::addProperty);
		System.out.println(GSON.toJson(json));
	}

	record Result(
		Map<String, String> blockstates, Map<String, String> models, Map<String, String> unmatchedStates,
		Map<String, Set<String>> ambiguousModels
	) {
	}
}
