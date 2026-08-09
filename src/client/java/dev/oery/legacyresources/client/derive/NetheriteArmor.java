package dev.oery.legacyresources.client.derive;

import java.util.List;
import java.util.Map;

/**
 * The netherite armour set - the four item icons plus the layers drawn on the player - recoloured
 * from the pack's diamond armour. See {@link MetalRecolor} for how the recolour works.
 */
final class NetheriteArmor extends MetalRecolor {
	private static final Map<String, String> PIECES = armor("diamond", "netherite");

	@Override
	public String id() {
		return "netherite_armor";
	}

	@Override
	protected Map<String, String> pieces() {
		return PIECES;
	}

	/** Tuned in the lab across the pack corpus; see {@link MetalRecolor#params} for what each does. */
	@Override
	public List<Param> params() {
		return List.of(
			Param.of("shadow", 0, 255, 18),
			Param.of("highlight", 0, 255, 63.75),
			Param.of("gamma", 0.25, 3, 1),
			Param.of("auto_level", 0, 1, 1),
			Param.of("target_mean", 0, 255, 53),
			Param.of("hue", 0, 360, 306),
			Param.of("saturation_shadow", 0, 0.6, 0.22),
			Param.of("saturation_highlight", 0, 0.6, 0.1),
			Param.of("keep_hue", 0, 1, 0.4)
		);
	}
}
