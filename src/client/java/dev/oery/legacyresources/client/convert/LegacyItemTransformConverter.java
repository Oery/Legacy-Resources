package dev.oery.legacyresources.client.convert;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Converts 1.8.9 item-display transforms as complete renderer matrices.
 *
 * <p>The JSON triples themselves did not keep the same meaning. 1.8 applied several scale and
 * rotation operations in the GUI, entity-item and held-item renderers; current Minecraft moved
 * those operations into model display transforms. Converting just the Euler order therefore cannot
 * preserve a legacy pack's authored pose. This class resolves the old parent inheritance first and
 * then folds the old renderer operations into each current display context.
 */
final class LegacyItemTransformConverter {
	private static final float DEG_TO_RAD = (float) Math.PI / 180.0f;
	private static final float RAD_TO_DEG = 180.0f / (float) Math.PI;
	private static final float MATRIX_EPSILON = 2.0e-4f;
	private static final float UNIFORM_EPSILON = 1.0e-5f;
	private static final int MAX_PARENT_DEPTH = 128;

	private static final Bridge FIRST_PERSON = bridge(
		new Matrix4f().rotateY(45.0f * DEG_TO_RAD).scale(0.8f), 0.5f
	);
	private static final Bridge THIRD_PERSON = thirdPersonBridge();
	private static final Bridge GUI_3D = guiBridge(true);
	private static final Bridge GUI_FLAT = guiBridge(false);
	private static final Bridge GROUND_3D = bridge(new Matrix4f().scale(0.5f), 0.5f);
	private static final Bridge GROUND_FLAT = bridge(new Matrix4f(), 0.5f);
	private static final Bridge FIXED_3D = bridge(new Matrix4f(), 0.5f);
	private static final Bridge FIXED_FLAT = bridge(
		new Matrix4f().rotateY(180.0f * DEG_TO_RAD).scale(2.0f), 0.5f
	);
	private static final Bridge HEAD = bridge(new Matrix4f().scale(2.0f), 0.5f);

	private LegacyItemTransformConverter() {
	}

	/** Loader identifiers are full asset paths such as {@code models/block/torch_item.json}. */
	static Result convert(Identifier root, Function<Identifier, @Nullable JsonObject> loader) {
		EnumMap<Context, JsonObject> inherited = new EnumMap<>(Context.class);
		Set<Identifier> visited = new HashSet<>();
		Identifier current = root;
		boolean unresolvedBlockParentIsGeometry = false;
		boolean legacyDisplaySeen = false;
		Geometry geometry = null;
		for (int depth = 0; depth < MAX_PARENT_DEPTH; depth++) {
			if (!visited.add(current)) {
				return Result.failure("cyclic legacy parent chain at " + current);
			}
			JsonObject model = loader.apply(current);
			if (model == null) {
				// Packs commonly ship only models/item/foo.json and inherit the corresponding
				// vanilla block model. Legacy block models did not supply item-display transforms,
				// so the missing parent still tells us everything needed here: this is 3-D geometry
				// and every effective display value has already been collected from its children.
				if (unresolvedBlockParentIsGeometry) {
					geometry = Geometry.CUBOID_3D;
					break;
				}
				return Result.failure("unresolved legacy model " + current);
			}
			JsonElement display = model.get("display");
			if (display != null) {
				if (!display.isJsonObject()) return Result.failure("display is not an object in " + current);
				JsonObject contexts = display.getAsJsonObject();
				boolean modernContexts = contexts.has("thirdperson_righthand") || contexts.has("thirdperson_lefthand")
					|| contexts.has("firstperson_righthand") || contexts.has("firstperson_lefthand")
					|| contexts.has("on_shelf");
				if (modernContexts) {
					// Some format-1 packs bundle model trees copied from much newer versions. Their
					// transforms already use current context semantics and must not go through the
					// legacy renderer bridge a second time.
					if (!legacyDisplaySeen) return Result.modernResult();
					// A legacy child can inherit geometry from a copied modern template. Its own
					// legacy display remains authoritative; current-only parent contexts did not
					// exist in the renderer whose result we are preserving.
				} else {
					legacyDisplaySeen = true;
					for (Context context : Context.values()) {
						JsonElement value = contexts.get(context.legacyName);
						if (value != null && !inherited.containsKey(context)) {
							if (!value.isJsonObject()) {
								return Result.failure(context.legacyName + " is not an object in " + current);
							}
							inherited.put(context, value.getAsJsonObject());
						}
					}
				}
			}

			JsonElement elements = model.get("elements");
			JsonElement parent = model.get("parent");
			if (elements != null) {
				if (!elements.isJsonArray()) return Result.failure("elements is not an array in " + current);
				geometry = Geometry.CUBOID_3D;
			}
			if (parent == null) {
				if (geometry != null) break;
				return Result.failure("legacy model has neither geometry nor a valid parent: " + current);
			}
			if (!parent.isJsonPrimitive() || !parent.getAsJsonPrimitive().isString()) {
				return Result.failure("legacy model has neither geometry nor a valid parent: " + current);
			}
			String parentName = parent.getAsString();
			if (parentName.equals("builtin/generated") || parentName.equals("builtin/compass")
				|| parentName.equals("builtin/clock")) {
				geometry = Geometry.FLAT_GENERATED;
				break;
			}
			if (parentName.startsWith("builtin/")) {
				return Result.failure("unsupported legacy built-in parent " + parentName);
			}
			Identifier parentId = Identifier.tryParse(parentName);
			if (parentId == null) return Result.failure("invalid legacy parent " + parentName);
			unresolvedBlockParentIsGeometry = parentId.getPath().startsWith("block/");
			current = Identifier.fromNamespaceAndPath(parentId.getNamespace(), "models/" + parentId.getPath() + ".json");
		}
		if (geometry == null) return Result.failure("legacy parent chain exceeds " + MAX_PARENT_DEPTH + " models");

		JsonObject output = new JsonObject();
		for (Context context : Context.values()) {
			LegacyTransform old;
			try {
				old = parse(inherited.get(context));
			} catch (IllegalArgumentException e) {
				return Result.failure("invalid " + context.legacyName + " transform: " + e.getMessage());
			}
			Bridge selected = switch (context) {
				case THIRD_PERSON -> THIRD_PERSON;
				case FIRST_PERSON -> FIRST_PERSON;
				case HEAD -> HEAD;
				case GUI -> geometry == Geometry.CUBOID_3D ? GUI_3D : GUI_FLAT;
				case GROUND -> geometry == Geometry.CUBOID_3D ? GROUND_3D : GROUND_FLAT;
				case FIXED -> geometry == Geometry.CUBOID_3D ? FIXED_3D : FIXED_FLAT;
			};
			Converted converted = selected.apply(old);
			String invalid = converted.invalidReason();
			if (invalid != null) return Result.failure(context.legacyName + ": " + invalid);
			output.add(context.modernName, converted.toJson());
		}
		return Result.success(output, geometry);
	}

	private static LegacyTransform parse(@Nullable JsonObject value) {
		Vector3f rotation = vector(value, "rotation", new Vector3f());
		Vector3f translation = vector(value, "translation", new Vector3f()).mul(0.0625f);
		translation.set(clamp(translation.x, -1.5f, 1.5f), clamp(translation.y, -1.5f, 1.5f),
			clamp(translation.z, -1.5f, 1.5f));
		Vector3f scale = vector(value, "scale", new Vector3f(1.0f));
		scale.set(clamp(scale.x, -4.0f, 4.0f), clamp(scale.y, -4.0f, 4.0f), clamp(scale.z, -4.0f, 4.0f));
		if (!rotation.isFinite() || !translation.isFinite() || !scale.isFinite()) {
			throw new IllegalArgumentException("contains a non-finite number");
		}
		Quaternionf quaternion = new Quaternionf().rotationY(rotation.y * DEG_TO_RAD)
			.rotateX(rotation.x * DEG_TO_RAD).rotateZ(rotation.z * DEG_TO_RAD);
		return new LegacyTransform(translation, quaternion, scale);
	}

	private static Vector3f vector(@Nullable JsonObject object, String key, Vector3f fallback) {
		if (object == null || !object.has(key)) return new Vector3f(fallback);
		JsonElement element = object.get(key);
		if (!element.isJsonArray() || element.getAsJsonArray().size() != 3) {
			throw new IllegalArgumentException(key + " must contain exactly three numbers");
		}
		JsonArray values = element.getAsJsonArray();
		try {
			return new Vector3f(values.get(0).getAsFloat(), values.get(1).getAsFloat(), values.get(2).getAsFloat());
		} catch (RuntimeException e) {
			throw new IllegalArgumentException(key + " contains a non-number", e);
		}
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}

	private static Bridge thirdPersonBridge() {
		Matrix4f modernBase = new Matrix4f().rotateX(-90.0f * DEG_TO_RAD).rotateY(180.0f * DEG_TO_RAD)
			.translate(1.0f / 16.0f, 2.0f / 16.0f, -10.0f / 16.0f);
		Matrix4f legacyBase = new Matrix4f().translate(-1.0f / 16.0f, 7.0f / 16.0f, 1.0f / 16.0f);
		return bridge(new Matrix4f(modernBase).invert().mul(legacyBase).scale(2.0f), 0.5f);
	}

	private static Bridge guiBridge(boolean threeDimensional) {
		Matrix4f modernBase = new Matrix4f().scale(16.0f, -16.0f, 16.0f);
		Matrix4f legacyBase = new Matrix4f().scale(1.0f, 1.0f, -1.0f).scale(0.5f);
		if (threeDimensional) {
			legacyBase.scale(40.0f).rotateX(210.0f * DEG_TO_RAD).rotateY(-135.0f * DEG_TO_RAD);
		} else {
			legacyBase.scale(64.0f).rotateX(180.0f * DEG_TO_RAD);
		}
		return bridge(new Matrix4f(modernBase).invert().mul(legacyBase), 0.5f);
	}

	private static Bridge bridge(Matrix4f matrix, float postScale) {
		Vector3f scales = matrix.getScale(new Vector3f());
		if (Math.abs(scales.x - scales.y) > UNIFORM_EPSILON || Math.abs(scales.x - scales.z) > UNIFORM_EPSILON
			|| scales.x <= 0.0f) {
			throw new IllegalStateException("item renderer bridge is not a positive uniform TRS: " + scales);
		}
		return new Bridge(matrix.getTranslation(new Vector3f()),
			matrix.getUnnormalizedRotation(new Quaternionf()).normalize(), scales.x, postScale);
	}

	enum Geometry {
		FLAT_GENERATED,
		CUBOID_3D
	}

	private enum Context {
		THIRD_PERSON("thirdperson", "thirdperson_righthand"),
		FIRST_PERSON("firstperson", "firstperson_righthand"),
		HEAD("head", "head"),
		GUI("gui", "gui"),
		GROUND("ground", "ground"),
		FIXED("fixed", "fixed");

		private final String legacyName;
		private final String modernName;

		Context(String legacyName, String modernName) {
			this.legacyName = legacyName;
			this.modernName = modernName;
		}
	}

	record Result(@Nullable JsonObject display, @Nullable Geometry geometry, @Nullable String failure, boolean alreadyModern) {
		static Result success(JsonObject display, Geometry geometry) {
			return new Result(display, geometry, null, false);
		}

		static Result modernResult() {
			return new Result(null, null, null, true);
		}

		static Result failure(String failure) {
			return new Result(null, null, failure, false);
		}

		boolean succeeded() {
			return display != null || alreadyModern;
		}
	}

	private record LegacyTransform(Vector3f translation, Quaternionf rotation, Vector3f scale) {
	}

	private record Bridge(Vector3f translation, Quaternionf rotation, float scale, float postScale) {
		Converted apply(LegacyTransform old) {
			Vector3f convertedTranslation = rotation.transform(
				new Vector3f(old.translation).mul(scale), new Vector3f()
			).add(translation);
			Quaternionf convertedRotation = new Quaternionf(rotation).mul(old.rotation).normalize();
			Vector3f convertedScale = new Vector3f(old.scale).mul(scale * postScale);
			return new Converted(convertedTranslation, convertedRotation, convertedScale);
		}
	}

	private record Converted(Vector3f translation, Quaternionf rotation, Vector3f scale) {
		@Nullable String invalidReason() {
			if (!translation.isFinite() || !rotation.isFinite() || !scale.isFinite()) return "conversion is non-finite";
			if (Math.max(Math.abs(translation.x), Math.max(Math.abs(translation.y), Math.abs(translation.z))) > 5.0f) {
				return "translation exceeds the current +/-5 limit";
			}
			if (Math.max(Math.abs(scale.x), Math.max(Math.abs(scale.y), Math.abs(scale.z))) > 4.0f) {
				return "scale exceeds the current +/-4 limit";
			}
			Vector3f euler = eulerXYZ(rotation);
			Quaternionf serializedRotation = new Quaternionf().rotationXYZ(euler.x, euler.y, euler.z);
			Matrix4f expected = new Matrix4f().translation(translation).rotate(rotation).scale(scale);
			Matrix4f serialized = new Matrix4f().translation(translation).rotate(serializedRotation).scale(scale);
			return expected.equals(serialized, MATRIX_EPSILON) ? null : "rotation cannot be serialized without matrix loss";
		}

		JsonObject toJson() {
			Vector3f euler = eulerXYZ(rotation).mul(RAD_TO_DEG);
			JsonObject json = new JsonObject();
			json.add("rotation", array(clean(euler.x), clean(euler.y), clean(euler.z)));
			json.add("translation", array(clean(translation.x * 16.0f), clean(translation.y * 16.0f), clean(translation.z * 16.0f)));
			json.add("scale", array(clean(scale.x), clean(scale.y), clean(scale.z)));
			return json;
		}

		private static float clean(float value) {
			return Math.abs(value) < 1.0e-4f ? 0.0f : value;
		}

		private static JsonArray array(float x, float y, float z) {
			JsonArray result = new JsonArray();
			result.add(x);
			result.add(y);
			result.add(z);
			return result;
		}

		/** Inverse of JOML's {@link Quaternionf#rotationXYZ(float, float, float)}, including gimbal lock. */
		private static Vector3f eulerXYZ(Quaternionf quaternion) {
			Matrix4f matrix = new Matrix4f().rotation(quaternion);
			float sinY = clamp(matrix.m20(), -1.0f, 1.0f);
			// Quaternion multiplication leaves a conceptual +/-90-degree rotation a few ULPs
			// short of one. Testing cos(asin(sinY)) magnifies that rounding error enough to miss
			// the singular branch (vanilla's fence-gate transforms exercise this exact case).
			if (Math.abs(sinY) > 1.0f - 1.0e-5f) {
				// At +/-90 degrees only z+x or z-x is observable. Choosing x=0 preserves the matrix.
				return new Vector3f(0.0f, Math.copySign((float) Math.PI / 2.0f, sinY),
					(float) Math.atan2(matrix.m01(), matrix.m11()));
			}
			float y = (float) Math.asin(sinY);
			float x = (float) Math.atan2(-matrix.m21(), matrix.m22());
			float z = (float) Math.atan2(-matrix.m10(), matrix.m00());
			return new Vector3f(x, y, z);
		}
	}
}
