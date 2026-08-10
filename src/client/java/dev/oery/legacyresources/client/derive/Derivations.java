package dev.oery.legacyresources.client.derive;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Every {@link Derivation} the mod knows, and the reverse index from a produced texture back to the
 * derivation that produces it.
 * <p>
 * Two consumers: the derivation lab enumerates {@link #ALL} to fill its picker, and the pack wrapper
 * will consult {@link #byOutput} when a modern texture has no legacy counterpart. Registering a
 * derivation here is the only step needed to make it appear in both.
 */
public final class Derivations {
	public static final List<Derivation> ALL = List.of(
		new SuspiciousGravel(),
		new SuspiciousSand(),
		new NetheriteArmor(),
		new NetheriteTools(),
		new CopperArmor(),
		new CopperTools(),
		new Beds(),
		new DirtPath(),
		new ConcretePowder(),
		new Concrete(),
		new HoneyBlock(),
		new CherryWood(),
		new CherryLeaves(),
		new PaleOak(),
		new StrippedLogs(),
		new WhiteDye(),
		new BlackDye(),
		new BlueDye(),
		new BrownDye()
	);

	private static final Map<String, Derivation> BY_OUTPUT = indexByOutput();

	private Derivations() {
	}

	/** @param texturePath modern path relative to {@code textures/}, without extension */
	public static @Nullable Derivation byOutput(String texturePath) {
		return BY_OUTPUT.get(texturePath);
	}

	public static @Nullable Derivation byId(String id) {
		return ALL.stream().filter(derivation -> derivation.id().equals(id)).findFirst().orElse(null);
	}

	private static Map<String, Derivation> indexByOutput() {
		Map<String, Derivation> map = new LinkedHashMap<>();
		for (Derivation derivation : ALL) {
			for (String output : derivation.outputs()) {
				Derivation previous = map.put(output, derivation);
				if (previous != null) {
					throw new IllegalStateException(
						"Two derivations both produce " + output + ": " + previous.id() + " and " + derivation.id()
					);
				}
			}
		}
		return Map.copyOf(map);
	}
}
