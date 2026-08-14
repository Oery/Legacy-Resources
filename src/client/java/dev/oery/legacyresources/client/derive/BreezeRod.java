package dev.oery.legacyresources.client.derive;

import java.util.List;
import java.util.Map;

/** The breeze rod is the blaze rod's silhouette on a cool, desaturated wind-colour ramp. */
final class BreezeRod extends RampRecolor {
	private static final Map<String, String> PIECES = Map.of("item/blaze_rod", "item/breeze_rod");

	@Override
	public String id() {
		return "breeze_rod";
	}

	@Override
	protected Map<String, String> pieces() {
		return PIECES;
	}

	@Override
	public List<Param> params() {
		return List.of(
			Param.of("shadow", 0, 255, 56), Param.of("highlight", 0, 255, 218),
			Param.of("gamma", .25, 3, 1), Param.of("auto_level", 0, 1, .7),
			Param.of("target_mean", 0, 255, 151), Param.of("hue", 0, 360, 216),
			Param.of("saturation_shadow", 0, 1, .35), Param.of("saturation_highlight", 0, 1, .14),
			Param.of("keep_hue", 0, 1, 0)
		);
	}
}
