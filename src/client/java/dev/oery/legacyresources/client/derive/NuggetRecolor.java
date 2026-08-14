package dev.oery.legacyresources.client.derive;

import java.util.List;
import java.util.Map;

/** Shared one-piece ramp remap for nuggets made from a legacy gold nugget. */
abstract class NuggetRecolor extends RampRecolor {
	private static final String SOURCE = "item/gold_nugget";
	private final String material;
	private final Map<String, String> pieces;

	NuggetRecolor(String material) {
		this.material = material;
		this.pieces = Map.of(SOURCE, "item/" + material + "_nugget");
	}

	@Override
	public final String id() {
		return material + "_nugget";
	}

	@Override
	protected final Map<String, String> pieces() {
		return pieces;
	}

	protected final List<Param> ramp(double shadow, double highlight, double targetMean, double hue, double shadowSaturation, double highlightSaturation) {
		return List.of(
			Param.of("shadow", 0, 255, shadow),
			Param.of("highlight", 0, 255, highlight),
			Param.of("gamma", .25, 3, 1.0),
			Param.of("auto_level", 0, 1, .7),
			Param.of("target_mean", 0, 255, targetMean),
			Param.of("hue", 0, 360, hue),
			Param.of("saturation_shadow", 0, 1, shadowSaturation),
			Param.of("saturation_highlight", 0, 1, highlightSaturation),
			Param.of("keep_hue", 0, 1, 0)
		);
	}
}
