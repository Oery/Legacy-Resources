package dev.oery.legacyresources.client.derive;

import java.util.List;
import java.util.Map;

/**
 * The netherite tools, recoloured from the pack's diamond tools. See {@link MetalRecolor} for how the
 * recolour works.
 * <p>
 * A separate derivation from {@link NetheriteArmor} rather than more entries in the same map, so the
 * two can be levelled independently: a pack's diamond tools and its diamond armour often occupy
 * different brightness ranges, and sharing one range would let whichever set is more extreme drag the
 * other off. The cost is that the two sets can end up slightly different metals - if that shows, the
 * fix is to match their {@code target_mean}.
 * <p>
 * The wooden handle goes through the same ramp as the head, which is also what vanilla does: its
 * netherite handles are warm browns rather than the diamond tool's original wood. {@link CopperTools}
 * is the same set under the opposite rule, and carries the note on why.
 */
final class NetheriteTools extends MetalRecolor {
	private static final Map<String, String> PIECES = tools("diamond", "netherite");

	@Override
	public String id() {
		return "netherite_tools";
	}

	@Override
	protected Map<String, String> pieces() {
		return PIECES;
	}

	/**
	 * Tuned in the lab across the pack corpus; see {@link MetalRecolor#params} for what each does.
	 * Close to {@link NetheriteArmor}'s but not identical - the tools carry slightly more of the
	 * pack's own hue and a warmer highlight, which is what the two sets were judged to need
	 * side by side.
	 */
	@Override
	public List<Param> params() {
		return List.of(
			Param.of("shadow", 0, 255, 18),
			Param.of("highlight", 0, 255, 63.75),
			Param.of("gamma", 0.25, 3, 1),
			Param.of("auto_level", 0, 1, 1),
			Param.of("target_mean", 0, 255, 53),
			Param.of("hue", 0, 360, 302.4),
			Param.of("saturation_shadow", 0, 0.6, 0.22),
			Param.of("saturation_highlight", 0, 0.6, 0.198),
			Param.of("keep_hue", 0, 1, 0.46)
		);
	}
}
