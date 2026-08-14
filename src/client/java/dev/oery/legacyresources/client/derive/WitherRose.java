package dev.oery.legacyresources.client.derive;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

/** Turns the red bloom in a legacy poppy into the dark, withered bloom of a wither rose. */
final class WitherRose implements Derivation {
	private static final String SOURCE = "block/poppy";
	private static final String OUTPUT = "block/wither_rose";

	@Override public String id() { return "wither_rose"; }
	@Override public List<String> sources() { return List.of(SOURCE); }
	@Override public List<String> outputs() { return List.of(OUTPUT); }
	@Override public List<Param> params() {
		return List.of(
			Param.of("min_saturation", 0, 1, .22), Param.of("red_hue_window", 0, .5, .12),
			Param.of("brightness", 0, 1, .16), Param.of("saturation", 0, 1, .08)
		);
	}

	@Override public Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		BufferedImage source = sources.get(SOURCE);
		if (source == null) return Map.of();
		int[] pixels = Ops.pixels(source);
		for (int i = 0; i < pixels.length; i++) {
			int pixel = pixels[i];
			if (Ops.alpha(pixel) == 0) continue;
			float[] hsb = Color.RGBtoHSB(Ops.red(pixel), Ops.green(pixel), Ops.blue(pixel), null);
			// Keep the green stem intact; only red flower petals become the nearly-black bloom.
			if (hsb[1] >= params.get("min_saturation") && Math.min(hsb[0], 1 - hsb[0]) <= params.get("red_hue_window")) {
				int rgb = Color.HSBtoRGB(.78f, (float) params.get("saturation"), (float) Math.min(hsb[2], params.get("brightness")));
				pixels[i] = Ops.argb(Ops.alpha(pixel), (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255);
			}
		}
		return Map.of(OUTPUT, Ops.image(pixels, source.getWidth(), source.getHeight()));
	}
}
