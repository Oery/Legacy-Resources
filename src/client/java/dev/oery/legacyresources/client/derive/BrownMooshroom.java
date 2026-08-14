package dev.oery.legacyresources.client.derive;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

/** Repaints only the red cap/coat material of the legacy red mooshroom into its brown variant. */
final class BrownMooshroom implements Derivation {
	private static final String SOURCE = "entity/cow/mooshroom_red";
	private static final List<String> OUTPUTS = List.of("entity/cow/mooshroom_brown", "entity/cow/mooshroom_brown_baby");

	@Override public String id() { return "brown_mooshroom"; }
	@Override public List<String> sources() { return List.of(SOURCE); }
	@Override public List<String> outputs() { return OUTPUTS; }
	@Override public List<Param> params() {
		return List.of(
			Param.of("hue", 0, 1, .075), Param.of("saturation", 0, 1, .52),
			Param.of("min_saturation", 0, 1, .25), Param.of("red_hue_window", 0, .5, .11)
		);
	}

	@Override
	public Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		BufferedImage source = sources.get(SOURCE);
		if (source == null) return Map.of();
		int[] pixels = Ops.pixels(source);
		for (int i = 0; i < pixels.length; i++) {
			int pixel = pixels[i];
			if (Ops.alpha(pixel) == 0) continue;
			float[] hsb = Color.RGBtoHSB(Ops.red(pixel), Ops.green(pixel), Ops.blue(pixel), null);
			// Red lies on both sides of hue zero. Neutral body pixels must stay untouched.
			if (hsb[1] >= params.get("min_saturation") && Math.min(hsb[0], 1 - hsb[0]) <= params.get("red_hue_window")) {
				float saturation = (float) Math.min(1, hsb[1] * params.get("saturation") / .52);
				int rgb = Color.HSBtoRGB((float) params.get("hue"), saturation, hsb[2]);
				pixels[i] = Ops.argb(Ops.alpha(pixel), (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255);
			}
		}
		BufferedImage brown = Ops.image(pixels, source.getWidth(), source.getHeight());
		return Map.of(OUTPUTS.getFirst(), brown, OUTPUTS.getLast(), Ops.copy(brown));
	}
}
