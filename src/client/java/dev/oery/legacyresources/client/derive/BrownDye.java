package dev.oery.legacyresources.client.derive;

import java.util.List;

/**
 * Brown dye, from the pack's magenta. See {@link DyeRecolor} for why it is not simply the pack's
 * {@code dye_powder_brown}, which is its cocoa beans.
 * <p>
 * Brown is orange held down: at 21 degrees and 0.71 saturation it would be a bright orange were its band
 * not capped at 165, and the whole of the difference between the two dyes is that ceiling. Which makes
 * it the one of the four where {@code highlight} is doing the naming, and where letting it drift up
 * costs the pack a distinguishable dye rather than merely a shade.
 */
final class BrownDye extends DyeRecolor {
	BrownDye() {
		super("magenta", "brown");
	}

	/**
	 * Tuned in the lab across the pack corpus; see {@link RampRecolor#params} for the nine shared ones
	 * and {@link DyeRecolor#metal} for {@code dye_change} below them.
	 */
	@Override
	public List<Param> params() {
		return List.of(
			Param.of("shadow", 0, 255, 35),
			Param.of("highlight", 0, 255, 165),
			Param.of("gamma", 0.25, 3, 1.0),
			Param.of("auto_level", 0, 1, 1.0),
			Param.of("target_mean", 0, 255, 84),
			Param.of("hue", 0, 360, 21),
			Param.of("saturation_shadow", 0, 1, 0.71),
			Param.of("saturation_highlight", 0, 1, 0.52),
			Param.of("keep_hue", 0, 1, 0),
			Param.of("dye_change", 0, 255, 80)
		);
	}
}
