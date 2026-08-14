package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The four frost stages retain a pack's packed-ice pattern, falling back to ordinary ice when that
 * is all the pack provides. Crack overlays are new artwork and are deliberately not faked.
 */
final class FrostedIce implements Derivation {
	private static final String PACKED = "block/packed_ice";
	private static final String ICE = "block/ice";
	private static final List<String> OUTPUTS = List.of(
		"block/frosted_ice_0", "block/frosted_ice_1", "block/frosted_ice_2", "block/frosted_ice_3"
	);

	@Override public String id() { return "frosted_ice"; }
	@Override public List<String> sources() { return List.of(PACKED, ICE); }
	@Override public List<String> outputs() { return OUTPUTS; }
	@Override public List<Param> params() { return List.of(); }

	@Override
	public Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		BufferedImage ice = sources.get(PACKED);
		if (ice == null) ice = sources.get(ICE);
		if (ice == null || Ops.scaleOf(ice) == 0) return Map.of();
		Map<String, BufferedImage> derived = new LinkedHashMap<>();
		for (String output : OUTPUTS) derived.put(output, Ops.copy(ice));
		return derived;
	}
}
