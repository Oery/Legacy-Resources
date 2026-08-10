package dev.oery.legacyresources.client.derive;

import java.util.List;

/**
 * Black dye, from the pack's magenta. See {@link DyeRecolor} for why it is not simply the pack's
 * {@code dye_powder_black}, which is its ink sac.
 * <p>
 * Black is the dye that needs {@code auto_level} most. Its band runs 5 to 165 like the darker half of
 * the set, but vanilla's own art averages 47 - far below the middle of that band - so a source dye drawn
 * with an ordinary spread of shades comes out grey unless the highlight is pulled down to meet the mean.
 * The tint it does carry is a blue-grey rim at 242 degrees, held at much the same saturation from
 * shadow to highlight rather than washing out.
 */
final class BlackDye extends DyeRecolor {
	BlackDye() {
		super("magenta", "black");
	}

	/**
	 * Tuned in the lab across the pack corpus; see {@link RampRecolor#params} for the nine shared ones
	 * and {@link DyeRecolor#metal} for {@code dye_change} below them.
	 */
	@Override
	public List<Param> params() {
		return List.of(
			Param.of("shadow", 0, 255, 5),
			Param.of("highlight", 0, 255, 165),
			Param.of("gamma", 0.25, 3, 1.0),
			Param.of("auto_level", 0, 1, 1.0),
			Param.of("target_mean", 0, 255, 47),
			Param.of("hue", 0, 360, 242),
			Param.of("saturation_shadow", 0, 1, 0.38),
			Param.of("saturation_highlight", 0, 1, 0.37),
			Param.of("keep_hue", 0, 1, 0),
			Param.of("dye_change", 0, 255, 80)
		);
	}
}
