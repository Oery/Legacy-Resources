package dev.oery.legacyresources.client.derive;

import java.util.List;
import java.util.Map;

/**
 * The copper armour set - the four item icons plus the layers drawn on the player - recoloured from
 * the pack's iron armour. See {@link MetalRecolor} for how the recolour works.
 * <p>
 * Iron is the right source for copper for the same reason diamond is for netherite: vanilla drew the
 * second set by repainting the first. In 26.2 the iron and copper icons agree on the alpha of all 256
 * pixels of every piece, and the worn layers on all 2048 and 4096 of theirs, so nothing but palette
 * separates them - and unlike diamond and netherite, the agreement here is total rather than
 * near-total.
 * <p>
 * The armour set carries no mask, having no wood on it; {@link CopperTools} is the half that does.
 */
final class CopperArmor extends MetalRecolor {
	private static final Map<String, String> PIECES = armor("iron", "copper");

	@Override
	public String id() {
		return "copper_armor";
	}

	@Override
	protected Map<String, String> pieces() {
		return PIECES;
	}

	/** Tuned in the lab across the pack corpus; see {@link MetalRecolor#params} for what each does. */
	@Override
	public List<Param> params() {
		return List.of(
			Param.of("shadow", 0, 255, 36),
			Param.of("highlight", 0, 255, 221),
			Param.of("gamma", 0.25, 3, 1.63),
			Param.of("auto_level", 0, 1, 0.7),
			Param.of("target_mean", 0, 255, 107),
			Param.of("hue", 0, 360, 13),
			Param.of("saturation_shadow", 0, 1, 0.73),
			Param.of("saturation_highlight", 0, 1, 0.62),
			Param.of("keep_hue", 0, 1, 0)
		);
	}
}
