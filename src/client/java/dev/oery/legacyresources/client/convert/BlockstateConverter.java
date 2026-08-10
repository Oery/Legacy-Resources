package dev.oery.legacyresources.client.convert;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

/**
 * Converts a legacy pack's own {@code blockstates/<block>.json} into the modern format, or refuses
 * to and lets vanilla's own file stand.
 * <p>
 * Two things changed in the format that {@link JsonRewriter}'s texture-path rewriting cannot touch,
 * and either one alone is enough to turn a block into a missing-model cube:
 * <ul>
 *   <li><b>Model references were bare names relative to {@code models/block/}.</b> 1.8-1.12 resolved
 *   {@code "model": "cobblestone"} by prefixing {@code block/} to the path
 *   ({@code ModelBlockDefinition.Variant.Deserializer.makeModelLocation}); modern parses the same
 *   string as a plain {@code Identifier}, i.e. {@code minecraft:cobblestone}, and looks for
 *   {@code models/cobblestone.json} - which no pack has ever contained.</li>
 *   <li><b>"every state" was spelled {@code "normal"}</b> (or {@code "all"}), where modern spells it
 *   with an empty key. A leftover {@code normal} key is not ignored: modern hands the key to
 *   {@code VariantSelector.predicate}, which throws on the unknown property, and the states it would
 *   have covered end up with no model at all.</li>
 * </ul>
 * Conversion is deliberately all-or-nothing, per file. A blockstate that cannot be converted
 * completely - an unknown block, a selector naming a property that no longer exists, a model that
 * exists in neither the pack nor modern vanilla, a state left unclaimed - is rejected outright
 * ({@code null}), so {@link LegacyPackResources} never announces it and the game falls back to
 * vanilla's own blockstate. That trade is worth making: vanilla's blockstate still picks up all of
 * the pack's <em>textures</em> through the rest of this mod, so rejecting costs at most a block's
 * custom geometry, while accepting a half-convertible file costs the block entirely. It is the same
 * call the {@code redstone_wire} special case in {@link LegacyPackResources#computeBlockstate} makes,
 * generalized.
 */
final class BlockstateConverter {
	private static final String VARIANTS_KEY = "variants";
	private static final String MULTIPART_KEY = "multipart";
	private static final String WHEN_KEY = "when";
	private static final String APPLY_KEY = "apply";
	private static final String MODEL_KEY = "model";
	private static final String WEIGHT_KEY = "weight";
	private static final String UVLOCK_KEY = "uvlock";
	/** Rotation fields, in degrees; modern reads them as a {@code Quadrant}, so only these four values parse. */
	private static final Set<String> ROTATION_KEYS = Set.of("x", "y", "z");
	private static final Set<Integer> ROTATION_DEGREES = Set.of(0, 90, 180, 270);
	/** Selector keys meaning "every state of this block". Modern only knows the empty one. */
	private static final Set<String> WILDCARD_SELECTORS = Set.of("", "normal", "all");
	/** The two {@code multipart} condition combinators, which take a list of nested conditions. */
	private static final Set<String> CONDITION_COMBINATORS = Set.of("AND", "OR");
	private static final String NEGATED_TERM_PREFIX = "!";
	private static final String TERM_SEPARATOR = "\\|";
	/** What legacy blockstate model references are relative to. */
	private static final String LEGACY_MODEL_DIR = "block/";

	private BlockstateConverter() {
	}

	/**
	 * @param modelResolves whether a model reference (e.g. {@code minecraft:block/cobblestone}) can be
	 *     loaded from either the pack or modern vanilla
	 * @return the converted blockstate, or {@code null} to keep vanilla's own
	 */
	static @Nullable JsonObject convert(String namespace, String stem, JsonElement source, Predicate<Identifier> modelResolves) {
		if (!source.isJsonObject()) {
			return null;
		}
		Block block = block(namespace, stem);
		if (block == null) {
			// A blockstate for a block that no longer exists under this name - 1.8's lit_furnace,
			// double_stone_slab, ... Nothing will ever ask for it; announcing it would only invite the
			// game to log a warning about a block it cannot find.
			return null;
		}
		StateDefinition<Block, BlockState> states = block.getStateDefinition();
		JsonObject root = source.getAsJsonObject();
		boolean variants = root.has(VARIANTS_KEY);
		boolean multipart = root.has(MULTIPART_KEY);
		if (variants == multipart) {
			// Neither (nothing to convert), or both - which modern accepts but no legacy pack writes,
			// so it is far more likely a hand-broken file than something worth interpreting.
			return null;
		}
		JsonObject converted = new JsonObject();
		if (variants) {
			JsonObject selectors = convertVariants(namespace, stem, root.get(VARIANTS_KEY), states, modelResolves);
			if (selectors == null) {
				return null;
			}
			converted.add(VARIANTS_KEY, selectors);
		} else {
			JsonArray parts = convertMultipart(namespace, stem, root.get(MULTIPART_KEY), states, modelResolves);
			if (parts == null) {
				return null;
			}
			converted.add(MULTIPART_KEY, parts);
		}
		return converted;
	}

	private static @Nullable Block block(String namespace, String stem) {
		if (!Identifier.isValidNamespace(namespace) || !Identifier.isValidPath(stem)) {
			return null;
		}
		Identifier id = Identifier.fromNamespaceAndPath(namespace, stem);
		// getValue() on the block registry answers air rather than nothing for an unknown id, so ask
		// whether the key is there first.
		return BuiltInRegistries.BLOCK.containsKey(id) ? BuiltInRegistries.BLOCK.getValue(id) : null;
	}

	/**
	 * Requires every state of the block to be claimed by exactly one selector. An unclaimed state gets
	 * the missing model, and an overlapping one makes modern abort that selector partway through
	 * ({@code BlockStateModelDispatcher.instantiate} throws on the duplicate), leaving an arbitrary
	 * subset of states behind - neither is worth shipping over vanilla's own working file.
	 */
	private static @Nullable JsonObject convertVariants(
		String namespace, String blockstateStem, JsonElement element, StateDefinition<Block, BlockState> states, Predicate<Identifier> modelResolves
	) {
		if (!element.isJsonObject() || element.getAsJsonObject().isEmpty()) {
			return null;
		}
		JsonObject out = new JsonObject();
		Set<BlockState> claimed = Collections.newSetFromMap(new IdentityHashMap<>());
		for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
			String selector = WILDCARD_SELECTORS.contains(entry.getKey()) ? "" : entry.getKey();
			Predicate<BlockState> matches = selectorPredicate(states, selector);
			if (matches == null || out.has(selector)) {
				return null;
			}
			JsonElement variant = convertVariant(namespace, blockstateStem, entry.getValue(), modelResolves);
			if (variant == null) {
				return null;
			}
			for (BlockState state : states.getPossibleStates()) {
				if (matches.test(state) && !claimed.add(state)) {
					return null;
				}
			}
			out.add(selector, variant);
		}
		return claimed.size() == states.getPossibleStates().size() ? out : null;
	}

	/** Mirrors {@code VariantSelector.predicate}, but answering "would this throw?" instead of throwing. */
	private static @Nullable Predicate<BlockState> selectorPredicate(StateDefinition<Block, BlockState> states, String selector) {
		Predicate<BlockState> predicate = state -> true;
		if (selector.isEmpty()) {
			return predicate;
		}
		for (String term : selector.split(",", -1)) {
			int equals = term.indexOf('=');
			if (equals < 0) {
				return null;
			}
			Property<?> property = states.getProperty(term.substring(0, equals));
			if (property == null) {
				return null;
			}
			Predicate<BlockState> matchesValue = valuePredicate(property, term.substring(equals + 1));
			if (matchesValue == null) {
				return null;
			}
			predicate = predicate.and(matchesValue);
		}
		return predicate;
	}

	private static <T extends Comparable<T>> @Nullable Predicate<BlockState> valuePredicate(Property<T> property, String name) {
		Optional<T> value = property.getValue(name);
		if (value.isEmpty()) {
			return null;
		}
		T expected = value.get();
		return state -> state.getValue(property).equals(expected);
	}

	/** A variant is either one model, or a weighted list of them to pick between at random. */
	private static @Nullable JsonElement convertVariant(String namespace, String blockstateStem, JsonElement element, Predicate<Identifier> modelResolves) {
		if (!element.isJsonArray()) {
			return convertVariantModel(namespace, blockstateStem, element, modelResolves, false);
		}
		JsonArray in = element.getAsJsonArray();
		if (in.isEmpty()) {
			return null;
		}
		JsonArray out = new JsonArray();
		for (JsonElement weighted : in) {
			JsonObject converted = convertVariantModel(namespace, blockstateStem, weighted, modelResolves, true);
			if (converted == null) {
				return null;
			}
			out.add(converted);
		}
		return out;
	}

	/**
	 * Rebuilt field by field rather than copied and patched: modern's variant codec has a fixed, small
	 * set of fields, so anything else a legacy file carries is inert at best, and validating what is
	 * kept means a file that would have failed to parse as a whole (a rotation of 45, a weight of 0)
	 * is caught here, where the answer is still "use vanilla's", rather than at load time, where it is
	 * "this block has no model".
	 */
	private static @Nullable JsonObject convertVariantModel(
		String namespace, String blockstateStem, JsonElement element, Predicate<Identifier> modelResolves, boolean weighted
	) {
		if (!element.isJsonObject()) {
			return null;
		}
		JsonObject in = element.getAsJsonObject();
		JsonElement model = in.get(MODEL_KEY);
		if (model == null || !isString(model)) {
			return null;
		}
		Identifier modelId = modelId(namespace, blockstateStem, model.getAsString());
		if (modelId == null || !modelResolves.test(modelId)) {
			return null;
		}
		JsonObject out = new JsonObject();
		out.addProperty(MODEL_KEY, modelId.toString());
		for (Map.Entry<String, JsonElement> field : in.entrySet()) {
			JsonElement value = field.getValue();
			if (ROTATION_KEYS.contains(field.getKey())) {
				if (!isRotation(value)) {
					return null;
				}
				out.add(field.getKey(), value);
			} else if (field.getKey().equals(UVLOCK_KEY)) {
				if (!isBoolean(value)) {
					return null;
				}
				out.add(UVLOCK_KEY, value);
			} else if (weighted && field.getKey().equals(WEIGHT_KEY)) {
				if (!isPositiveInteger(value)) {
					return null;
				}
				out.add(WEIGHT_KEY, value);
			}
		}
		return out;
	}

	/**
	 * Unlike {@code variants}, a {@code multipart} definition needs no coverage check: modern applies
	 * it to every state of the block regardless, so a state matched by no part renders as nothing
	 * rather than as a missing model. Only the conditions and the models have to hold up.
	 */
	private static @Nullable JsonArray convertMultipart(
		String namespace, String blockstateStem, JsonElement element, StateDefinition<Block, BlockState> states, Predicate<Identifier> modelResolves
	) {
		if (!element.isJsonArray() || element.getAsJsonArray().isEmpty()) {
			return null;
		}
		JsonArray out = new JsonArray();
		for (JsonElement element1 : element.getAsJsonArray()) {
			if (!element1.isJsonObject()) {
				return null;
			}
			JsonObject part = element1.getAsJsonObject();
			JsonElement apply = part.get(APPLY_KEY);
			if (apply == null) {
				return null;
			}
			JsonElement variant = convertVariant(namespace, blockstateStem, apply, modelResolves);
			if (variant == null) {
				return null;
			}
			JsonObject converted = new JsonObject();
			JsonElement when = part.get(WHEN_KEY);
			if (when != null) {
				if (!isValidCondition(states, when)) {
					return null;
				}
				// Conditions name properties and values, never models or textures, so they carry across
				// verbatim - modern's condition syntax (including "a|b" and "!a" terms) is 1.9's.
				converted.add(WHEN_KEY, when);
			}
			converted.add(APPLY_KEY, variant);
			out.add(converted);
		}
		return out;
	}

	private static boolean isValidCondition(StateDefinition<Block, BlockState> states, JsonElement element) {
		if (!element.isJsonObject() || element.getAsJsonObject().isEmpty()) {
			return false;
		}
		JsonObject condition = element.getAsJsonObject();
		for (Map.Entry<String, JsonElement> test : condition.entrySet()) {
			if (CONDITION_COMBINATORS.contains(test.getKey())) {
				if (condition.size() != 1 || !test.getValue().isJsonArray() || test.getValue().getAsJsonArray().isEmpty()) {
					return false;
				}
				for (JsonElement term : test.getValue().getAsJsonArray()) {
					if (!isValidCondition(states, term)) {
						return false;
					}
				}
				continue;
			}
			Property<?> property = states.getProperty(test.getKey());
			if (property == null || !isValidConditionTerms(property, test.getValue())) {
				return false;
			}
		}
		return true;
	}

	private static boolean isValidConditionTerms(Property<?> property, JsonElement element) {
		// Bare booleans and numbers are as accepted here as strings are ("north": true), by modern and
		// by 1.9 alike, and mean the same thing spelled out.
		if (!element.isJsonPrimitive()) {
			return false;
		}
		String terms = element.getAsString();
		if (terms.isEmpty()) {
			return false;
		}
		for (String term : terms.split(TERM_SEPARATOR, -1)) {
			String name = term.startsWith(NEGATED_TERM_PREFIX) ? term.substring(NEGATED_TERM_PREFIX.length()) : term;
			if (property.getValue(name).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Resolves a legacy blockstate's model reference the way the legacy game did: as an identifier
	 * whose path is relative to {@code models/block/}. Deliberately unconditional - a legacy pack that
	 * wrote {@code "model": "block/stone"} really did mean {@code block/block/stone} and was broken in
	 * its own version too, so second-guessing it here would invent a model reference the pack never
	 * had.
	 */
	private static @Nullable Identifier modelId(String namespace, String blockstateStem, String reference) {
		int colon = reference.indexOf(':');
		String modelNamespace = colon < 0 ? namespace : reference.substring(0, colon);
		String modelStem = colon < 0 ? reference : reference.substring(colon + 1);
		String path = bedModelPath(blockstateStem, modelStem);
		if (!Identifier.isValidNamespace(modelNamespace) || !Identifier.isValidPath(path)) {
			return null;
		}
		return Identifier.fromNamespaceAndPath(modelNamespace, path);
	}

	/**
	 * A coloured modern bed keeps the legacy bed blockstate's rotations (they differ by 180 degrees),
	 * but points at that colour's rewritten custom model so its derived sheets are used.
	 */
	private static String bedModelPath(String blockstateStem, String modelStem) {
		if ((modelStem.equals("bed_head") || modelStem.equals("bed_foot")) && blockstateStem.endsWith("_bed")) {
			return LEGACY_MODEL_DIR + blockstateStem + "_" + modelStem.substring("bed_".length());
		}
		return LEGACY_MODEL_DIR + modelStem;
	}

	private static boolean isString(JsonElement element) {
		return element.isJsonPrimitive() && element.getAsJsonPrimitive().isString();
	}

	private static boolean isBoolean(JsonElement element) {
		return element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean();
	}

	private static boolean isRotation(JsonElement element) {
		Integer degrees = asInteger(element);
		return degrees != null && ROTATION_DEGREES.contains(degrees);
	}

	private static boolean isPositiveInteger(JsonElement element) {
		Integer value = asInteger(element);
		return value != null && value > 0;
	}

	private static @Nullable Integer asInteger(JsonElement element) {
		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return null;
		}
		double value = element.getAsDouble();
		return value == Math.rint(value) ? (int) value : null;
	}
}
