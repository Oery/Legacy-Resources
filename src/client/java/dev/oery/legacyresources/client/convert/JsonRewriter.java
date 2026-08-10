package dev.oery.legacyresources.client.convert;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Recursively rewrites legacy texture references inside a model or blockstate JSON tree:
 * {@code "blocks/<name>"} becomes {@code "block/<mapped-name>"} and {@code "items/<name>"}
 * becomes {@code "item/<mapped-name>"}, following the 1.13 "flattening" rename.
 */
final class JsonRewriter {
	private static final String OLD_BLOCK_PREFIX = "blocks/";
	private static final String OLD_ITEM_PREFIX = "items/";

	private JsonRewriter() {
	}

	static JsonElement rewrite(JsonElement element) {
		if (element.isJsonObject()) {
			JsonObject in = element.getAsJsonObject();
			JsonObject out = new JsonObject();
			for (Map.Entry<String, JsonElement> entry : in.entrySet()) {
				out.add(entry.getKey(), rewrite(entry.getValue()));
			}
			return out;
		} else if (element.isJsonArray()) {
			JsonArray in = element.getAsJsonArray();
			JsonArray out = new JsonArray();
			for (JsonElement child : in) {
				out.add(rewrite(child));
			}
			return out;
		} else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
			return new JsonPrimitive(rewriteReference(element.getAsString()));
		} else {
			return element;
		}
	}

	/** Rewrites a legacy bed model to the derived sheets matching one modern bed colour. */
	static JsonObject rewriteBedModel(JsonElement element, String color) {
		JsonObject model = rewrite(element).getAsJsonObject();
		JsonElement textures = model.get("textures");
		if (textures == null || !textures.isJsonObject()) {
			return model;
		}
		for (Map.Entry<String, JsonElement> texture : textures.getAsJsonObject().entrySet()) {
			if (!texture.getValue().isJsonPrimitive() || !texture.getValue().getAsJsonPrimitive().isString()) {
				continue;
			}
			String path = texture.getValue().getAsString();
			if (path.startsWith("block/bed_feet_") || path.equals("block/bed_head_top") || path.equals("block/bed_head_side")) {
				texture.setValue(new JsonPrimitive("block/" + color + "_legacy_" + path.substring("block/".length())));
			}
		}
		return model;
	}

	private static String rewriteReference(String value) {
		int colon = value.indexOf(':');
		String namespace = colon >= 0 ? value.substring(0, colon) : null;
		String path = colon >= 0 ? value.substring(colon + 1) : value;
		String rewritten = rewritePath(path);
		if (rewritten == null) {
			return value;
		}
		return namespace != null ? namespace + ":" + rewritten : rewritten;
	}

	private static @Nullable String rewritePath(String path) {
		if (path.startsWith(OLD_BLOCK_PREFIX)) {
			String stem = path.substring(OLD_BLOCK_PREFIX.length());
			return "block/" + TextureNameMaps.newBlockName(stem);
		}
		if (path.startsWith(OLD_ITEM_PREFIX)) {
			String stem = path.substring(OLD_ITEM_PREFIX.length());
			return "item/" + TextureNameMaps.newItemName(stem);
		}
		String modelPath = ResourceNameMaps.newBlockModelPath(path);
		return modelPath.equals(path) ? null : modelPath;
	}
}
