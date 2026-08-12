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
	private static final String DISPLAY_KEY = "display";

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

	static JsonObject blockItemWrapper(String parent) {
		JsonObject model = new JsonObject();
		model.addProperty("parent", parent);
		model.add(DISPLAY_KEY, modernBlockDisplay());
		return model;
	}

	private static JsonObject modernBlockDisplay() {
		JsonObject display = new JsonObject();
		display.add("gui", transform(30, 225, 0, 0, 0, 0, .625));
		display.add("ground", transform(0, 0, 0, 0, 3, 0, .25));
		display.add("fixed", transform(0, 0, 0, 0, 0, 0, .5));
		display.add("on_shelf", transform(0, 180, 0, 0, 0, 0, 1));
		display.add("thirdperson_righthand", transform(75, 45, 0, 0, 2.5, 0, .375));
		display.add("firstperson_righthand", transform(0, 45, 0, 0, 0, 0, .4));
		display.add("firstperson_lefthand", transform(0, 225, 0, 0, 0, 0, .4));
		return display;
	}

	private static JsonObject transform(double rx, double ry, double rz, double tx, double ty, double tz, double scale) {
		return transform(rx, ry, rz, tx, ty, tz, scale, scale, scale);
	}

	private static JsonObject transform(double rx, double ry, double rz, double tx, double ty, double tz,
		double sx, double sy, double sz) {
		JsonObject transform = new JsonObject();
		transform.add("rotation", vector(rx, ry, rz));
		transform.add("translation", vector(tx, ty, tz));
		transform.add("scale", vector(sx, sy, sz));
		return transform;
	}

	private static JsonArray vector(double x, double y, double z) {
		JsonArray vector = new JsonArray();
		vector.add(x);
		vector.add(y);
		vector.add(z);
		return vector;
	}

	/** Repoints a vanilla block-item definition at the converted legacy item wrapper. */
	static JsonElement routeBlockItemDefinition(JsonElement definition, String itemModel) {
		JsonElement copy = definition.deepCopy();
		if (!copy.isJsonObject()) return copy;
		JsonObject root = copy.getAsJsonObject();
		JsonElement model = root.get("model");
		if (model != null && model.isJsonObject()) {
			JsonObject selected = model.getAsJsonObject();
			JsonElement type = selected.get("type");
			JsonElement target = selected.get("model");
			if (type != null && type.isJsonPrimitive() && type.getAsString().equals("minecraft:model")
				&& target != null && target.isJsonPrimitive() && target.getAsString().contains(":block/")) {
				selected.addProperty("model", itemModel);
			}
		}
		return copy;
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
