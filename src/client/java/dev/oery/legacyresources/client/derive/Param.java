package dev.oery.legacyresources.client.derive;

/**
 * One tunable constant of a {@link Derivation}.
 * <p>
 * The point of declaring constants rather than inlining them is the derivation lab (see
 * {@code src/lab}): it renders a slider per param across every legacy pack in the corpus at once, so
 * a value can be judged on 79 real packs instead of the one that happened to be open. The shipped mod
 * never varies them - it always runs on {@link #defaultValue()} - so what gets tuned in the lab is
 * exactly what ships, provided the tuned value is pasted back into the declaration here.
 *
 * @param name          identifier, also the slider's label and its query-parameter name
 * @param min           lowest value the slider offers
 * @param max           highest value the slider offers
 * @param defaultValue  the value the mod actually uses
 * @param step          slider granularity; exactly {@code 1} marks the param as an integer, which the
 *                      lab renders as a stepped slider and {@link Params#getInt} reads back
 */
public record Param(String name, double min, double max, double defaultValue, double step) {
	/** A continuous param, with the slider divided into 100 steps across its range. */
	public static Param of(String name, double min, double max, double defaultValue) {
		return new Param(name, min, max, defaultValue, (max - min) / 100.0);
	}

	/** An integer param, e.g. a frame index or a pixel count. */
	public static Param ofInt(String name, int min, int max, int defaultValue) {
		return new Param(name, min, max, defaultValue, 1);
	}

	public boolean isInteger() {
		return step == 1;
	}
}
