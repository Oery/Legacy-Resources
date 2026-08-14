package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modern variants whose only legacy predecessor is the same object before the variant existed.
 *
 * <p>The pack's silhouette, resolution and shading are deliberately retained.  These are copies,
 * not palette guesses: a missing modern cue is preferable to a vanilla icon in an otherwise legacy
 * pack, and callers still fall back to vanilla when the predecessor is absent.</p>
 */
final class LegacyVariants implements Derivation {
	private static final Map<String, List<String>> VARIANTS = Map.ofEntries(
		Map.entry("item/ink_sac", List.of("item/glow_ink_sac")),
		Map.entry("item/item_frame", List.of("item/glow_item_frame", "block/glow_item_frame")),
		Map.entry("entity/squid/squid", List.of("entity/squid/glow_squid", "entity/squid/glow_squid_baby"))
	);

	@Override public String id() { return "legacy_variants"; }
	@Override public List<String> sources() { return List.copyOf(VARIANTS.keySet()); }
	@Override public List<String> outputs() { return VARIANTS.values().stream().flatMap(List::stream).toList(); }
	@Override public List<Param> params() { return List.of(); }

	@Override
	public Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		Map<String, BufferedImage> out = new LinkedHashMap<>();
		VARIANTS.forEach((source, outputs) -> {
			BufferedImage image = sources.get(source);
			if (image != null) outputs.forEach(output -> out.put(output, Ops.copy(image)));
		});
		return out;
	}
}
