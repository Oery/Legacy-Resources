package dev.oery.legacyresources.client.convert;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;

/** Fast matrix-conversion checks against the checked-in 1.8.9 vanilla model tree. */
public final class LegacyItemTransformVerifier {
	private LegacyItemTransformVerifier() {
	}

	public static void main(String[] args) {
		Path assets = Path.of(System.getProperty("lab.project", "."))
			.resolve("reference/1.8.9/assets/assets");
		Identifier torch = Identifier.fromNamespaceAndPath("minecraft", "models/item/torch.json");
		LegacyItemTransformConverter.Result result = LegacyItemTransformConverter.convert(torch, id -> read(assets, id));
		if (!result.succeeded()) throw new IllegalStateException("Vanilla torch conversion failed: " + result.failure());
		JsonObject display = result.display();
		assertScale(display, "firstperson_righthand", 0.68);
		assertScale(display, "thirdperson_righthand", 0.55);
		assertScale(display, "ground", 0.5);
		assertScale(display, "fixed", 1.0);
		verifyMissingVanillaBlockParent();
		verifyMixedEraDetection();

		Path itemModels = assets.resolve("minecraft/models/item");
		List<String> unexpected = new ArrayList<>();
		int checked = 0;
		try (var files = Files.walk(itemModels)) {
			for (Path file : files.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".json")).toList()) {
				checked++;
				String relative = assets.resolve("minecraft").relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/");
				Identifier id = Identifier.fromNamespaceAndPath("minecraft", relative);
				LegacyItemTransformConverter.Result converted = LegacyItemTransformConverter.convert(id, candidate -> read(assets, candidate));
				if (!converted.succeeded()
					&& !converted.failure().startsWith("unsupported legacy built-in parent builtin/entity")) {
					unexpected.add(id + ": " + converted.failure());
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException("Cannot enumerate legacy vanilla item models", e);
		}
		if (!unexpected.isEmpty()) {
			throw new IllegalStateException("Unexpected legacy vanilla item conversion failures:\n" + String.join("\n", unexpected));
		}
		System.out.println("Verified transform conversion for all " + checked + " legacy vanilla item models");
	}

	/** A custom wrapper is still classifiable when it relies on a vanilla block parent not in the pack. */
	private static void verifyMissingVanillaBlockParent() {
		Identifier root = Identifier.fromNamespaceAndPath("minecraft", "models/item/custom_block.json");
		JsonObject wrapper = JsonParser.parseString("""
			{
			  "parent": "block/custom_block",
			  "display": { "ground": { "scale": [ 1.5, 1.5, 1.5 ] } }
			}
			""").getAsJsonObject();
		LegacyItemTransformConverter.Result result = LegacyItemTransformConverter.convert(
			root, id -> id.equals(root) ? wrapper : null
		);
		if (!result.succeeded() || result.geometry() != LegacyItemTransformConverter.Geometry.CUBOID_3D) {
			throw new IllegalStateException("Missing vanilla block parent was not treated as 3-D geometry: " + result.failure());
		}
		assertScale(result.display(), "ground", 0.375);
	}

	private static void verifyMixedEraDetection() {
		Identifier modern = Identifier.fromNamespaceAndPath("minecraft", "models/item/copied_template.json");
		JsonObject modernModel = JsonParser.parseString("""
			{ "parent": "builtin/generated", "display": { "firstperson_righthand": { "scale": [ 0.68, 0.68, 0.68 ] } } }
			""").getAsJsonObject();
		LegacyItemTransformConverter.Result copied = LegacyItemTransformConverter.convert(
			modern, id -> id.equals(modern) ? modernModel : null
		);
		if (!copied.succeeded() || !copied.alreadyModern()) {
			throw new IllegalStateException("Already-modern display contexts were converted a second time");
		}

		Identifier child = Identifier.fromNamespaceAndPath("minecraft", "models/item/mixed_child.json");
		Identifier parent = Identifier.fromNamespaceAndPath("minecraft", "models/block/copied_parent.json");
		JsonObject legacyChild = JsonParser.parseString("""
			{ "parent": "block/copied_parent", "display": { "ground": { "scale": [ 1.5, 1.5, 1.5 ] } } }
			""").getAsJsonObject();
		JsonObject modernParent = JsonParser.parseString("""
			{ "elements": [], "display": { "on_shelf": { "rotation": [ 0, 180, 0 ] } } }
			""").getAsJsonObject();
		LegacyItemTransformConverter.Result mixed = LegacyItemTransformConverter.convert(child, id -> {
			if (id.equals(child)) return legacyChild;
			if (id.equals(parent)) return modernParent;
			return null;
		});
		if (!mixed.succeeded() || mixed.alreadyModern()) {
			throw new IllegalStateException("Modern geometry parent incorrectly erased a legacy child display");
		}
		assertScale(mixed.display(), "ground", 0.375);
	}

	private static JsonObject read(Path assets, Identifier id) {
		Path file = assets.resolve(id.getNamespace()).resolve(id.getPath());
		if (!Files.isRegularFile(file)) return null;
		try (Reader reader = Files.newBufferedReader(file)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	private static void assertScale(JsonObject display, String context, double expected) {
		double actual = display.getAsJsonObject(context).getAsJsonArray("scale").get(0).getAsDouble();
		if (Math.abs(actual - expected) > 1.0e-5) {
			throw new IllegalStateException(context + " scale is " + actual + ", expected " + expected);
		}
	}
}
