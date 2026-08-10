package dev.oery.legacyresources.client.derive;

import java.util.List;

/**
 * White dye, from the pack's magenta. See {@link DyeRecolor} for why it is not simply the pack's
 * {@code dye_powder_white}, which is its bone meal.
 * <p>
 * The one dye whose highlight has to reach the top of the range - vanilla's runs 35 to 253 where the
 * other fifteen stop at 165 to 240 - and the one that all but abandons its tint on the way there,
 * falling from 0.44 saturation in shadow to 0.02 at the highlight. What is left is a cool cast at 234
 * degrees, which is what keeps it from reading as flat grey.
 */
final class WhiteDye extends DyeRecolor {
	WhiteDye() {
		super("magenta", "white");
	}

	/**
	 * Tuned in the lab across the pack corpus; see {@link RampRecolor#params} for the nine shared ones
	 * and {@link DyeRecolor#metal} for {@code dye_change} below them.
	 */
	@Override
	public List<Param> params() {
		return List.of(
			Param.of("shadow", 0, 255, 35),
			Param.of("highlight", 0, 255, 253),
			Param.of("gamma", 0.25, 3, 1.0),
			Param.of("auto_level", 0, 1, 1.0),
			Param.of("target_mean", 0, 255, 150),
			Param.of("hue", 0, 360, 234),
			Param.of("saturation_shadow", 0, 1, 0.44),
			Param.of("saturation_highlight", 0, 1, 0.02),
			// A white dye that keeps any of the pack's hue is not a white dye.
			Param.of("keep_hue", 0, 1, 0),
			Param.of("dye_change", 0, 255, 80)
		);
	}
}
