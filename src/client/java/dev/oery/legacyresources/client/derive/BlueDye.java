package dev.oery.legacyresources.client.derive;

import java.util.List;

/**
 * Blue dye, from the pack's magenta. See {@link DyeRecolor} for why it is not simply the pack's
 * {@code dye_powder_blue}, which is its lapis lazuli.
 * <p>
 * The most saturated of the four, at 0.73 in shadow falling to 0.48, on a band of 34 to 165 averaging
 * 77. Blue is a colour a pack can be recognisably its own about and still be blue, but at 218 degrees it
 * sits close enough to purple that keeping any of the source's hue is not worth the risk of the two
 * dyes becoming hard to tell apart in a hotbar.
 */
final class BlueDye extends DyeRecolor {
	BlueDye() {
		super("magenta", "blue");
	}

	/**
	 * Tuned in the lab across the pack corpus; see {@link RampRecolor#params} for the nine shared ones
	 * and {@link DyeRecolor#metal} for {@code dye_change} below them.
	 */
	@Override
	public List<Param> params() {
		return List.of(
			Param.of("shadow", 0, 255, 34),
			Param.of("highlight", 0, 255, 165),
			Param.of("gamma", 0.25, 3, 1.0),
			Param.of("auto_level", 0, 1, 0.75),
			Param.of("target_mean", 0, 255, 77),
			Param.of("hue", 0, 360, 218),
			Param.of("saturation_shadow", 0, 1, 0.73),
			Param.of("saturation_highlight", 0, 1, 0.48),
			Param.of("keep_hue", 0, 1, 0),
			Param.of("dye_change", 0, 255, 80)
		);
	}
}
