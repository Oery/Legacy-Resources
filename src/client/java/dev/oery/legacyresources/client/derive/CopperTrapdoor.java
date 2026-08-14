package dev.oery.legacyresources.client.derive;

import java.util.List;
import java.util.Map;

/** The copper trapdoor keeps the pack's iron-trapdoor perforations on a warm copper ramp. */
final class CopperTrapdoor extends RampRecolor {
	private static final Map<String, String> PIECES = Map.of("block/iron_trapdoor", "block/copper_trapdoor");

	@Override public String id() { return "copper_trapdoor"; }
	@Override protected Map<String, String> pieces() { return PIECES; }

	@Override
	public List<Param> params() {
		return List.of(
			Param.of("shadow", 0, 255, 38), Param.of("highlight", 0, 255, 223),
			Param.of("gamma", .25, 3, 1), Param.of("auto_level", 0, 1, .7),
			Param.of("target_mean", 0, 255, 142), Param.of("hue", 0, 360, 15),
			Param.of("saturation_shadow", 0, 1, .74), Param.of("saturation_highlight", 0, 1, .20),
			Param.of("keep_hue", 0, 1, 0)
		);
	}
}
