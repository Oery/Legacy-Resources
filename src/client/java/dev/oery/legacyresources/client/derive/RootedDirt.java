package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

/** Rooted dirt, using the pack's gravel fragments as the tangled root pattern. */
final class RootedDirt implements Derivation {
	private static final String SOURCE = "block/gravel";
	private static final String OUTPUT = "block/rooted_dirt";

	@Override public String id() { return "rooted_dirt"; }
	@Override public List<String> sources() { return List.of(SOURCE); }
	@Override public List<String> outputs() { return List.of(OUTPUT); }
	@Override public String animationSource(String output) { return SOURCE; }
	@Override public List<Param> params() {
		return List.of(
			Param.of("hue", 0, 1, .0668), Param.of("saturation", 0, 1, .478),
			Param.of("brightness", 0, 255, 110.2), Param.of("spread", 0, 64, 26.1),
			Param.of("max_gain", .25, 4, 1.5), Param.ofInt("levels", 0, 8, 0)
		);
	}

	@Override public Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		BufferedImage gravel = sources.get(SOURCE);
		if (gravel == null || Ops.scaleOf(gravel) == 0) return Map.of();
		Palette palette = new Palette(params.get("hue"), params.get("saturation"), params.get("brightness"));
		return Map.of(OUTPUT, palette.repaint(gravel, params.get("spread"), params.get("max_gain"), params.getInt("levels")));
	}
}
