package dev.oery.legacyresources.client.derive;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The values a {@link Derivation} runs with: whatever overrides were supplied, falling back to each
 * {@link Param}'s declared default.
 * <p>
 * In the mod there are never any overrides - {@link #defaults} is what runs. The lab supplies them
 * from its sliders. A derivation therefore reads its constants the same way in both, and cannot
 * accidentally behave differently in-game than it did while being tuned.
 */
public final class Params {
	private final Map<String, Double> declared;
	private final Map<String, Double> overrides;

	private Params(Map<String, Double> declared, Map<String, Double> overrides) {
		this.declared = declared;
		this.overrides = overrides;
	}

	public static Params defaults(List<Param> params) {
		return of(params, Map.of());
	}

	public static Params of(List<Param> params, Map<String, Double> overrides) {
		Map<String, Double> declared = new HashMap<>();
		for (Param param : params) {
			declared.put(param.name(), param.defaultValue());
		}
		return new Params(Map.copyOf(declared), Map.copyOf(overrides));
	}

	/**
	 * @throws IllegalArgumentException if {@code name} is not a declared {@link Param} - a typo in a
	 *                                  derivation would otherwise read as a silent 0
	 */
	public double get(String name) {
		Double override = overrides.get(name);
		if (override != null) {
			return override;
		}
		Double value = declared.get(name);
		if (value == null) {
			throw new IllegalArgumentException("Undeclared derivation param: " + name);
		}
		return value;
	}

	public int getInt(String name) {
		return (int) Math.round(get(name));
	}

	public boolean getBool(String name) {
		return get(name) >= 0.5;
	}
}
