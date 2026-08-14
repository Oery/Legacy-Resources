package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

/** Powder snow, made from the pack's snow while keeping its fine grain and opacity. */
final class PowderSnow implements Derivation {
	private static final String SOURCE = "block/snow";
	private static final String OUTPUT = "block/powder_snow";

	@Override public String id() { return "powder_snow"; }
	@Override public List<String> sources() { return List.of(SOURCE); }
	@Override public List<String> outputs() { return List.of(OUTPUT); }

	@Override
	public List<Param> params() {
		return List.of(
			Param.of("hue", 0, 1, .58), Param.of("saturation", 0, 1, .06),
			Param.of("brightness", 0, 255, 228), Param.of("spread", 0, 64, 5),
			Param.of("max_gain", .5, 4, 1.5), Param.ofInt("levels", 0, 8, 0)
		);
	}

	@Override
	public Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		BufferedImage snow = sources.get(SOURCE);
		if (snow == null || Ops.scaleOf(snow) == 0) return Map.of();
		Palette palette = new Palette(params.get("hue"), params.get("saturation"), params.get("brightness"));
		return Map.of(OUTPUT, palette.repaint(snow, params.get("spread"), params.get("max_gain"), params.getInt("levels")));
	}
}
