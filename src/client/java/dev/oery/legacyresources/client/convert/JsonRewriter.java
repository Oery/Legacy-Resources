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
		return null;
	}
}
