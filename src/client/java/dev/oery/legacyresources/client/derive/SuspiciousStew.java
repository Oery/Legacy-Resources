package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

/**
 * Suspicious stew retains the pack's mushroom-stew bowl and contents.
 *
 * <p>Vanilla's two icons have the same 16px silhouette; the only added cue is new art with no
 * legacy counterpart. Reusing the pack's soup is consequently preferable to inserting foreign
 * pixels, and still prevents the modern icon falling back to vanilla.</p>
 */
final class SuspiciousStew implements Derivation {
	private static final String SOURCE = "item/mushroom_stew";
	private static final String OUTPUT = "item/suspicious_stew";

	@Override
	public String id() {
		return "suspicious_stew";
	}

	@Override
	public List<String> sources() {
		return List.of(SOURCE);
	}

	@Override
	public List<String> outputs() {
		return List.of(OUTPUT);
	}

	@Override
	public List<Param> params() {
		return List.of();
	}

	@Override
	public Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		BufferedImage soup = sources.get(SOURCE);
		return soup == null ? Map.of() : Map.of(OUTPUT, Ops.copy(soup));
	}
}
