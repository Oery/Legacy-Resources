package dev.oery.legacyresources.client.derive;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

/** The dragon fireball retains the pack's grey core while shifting its orange flame to purple. */
final class DragonFireball implements Derivation {
	private static final String SOURCE = "item/fire_charge";
	private static final String OUTPUT = "entity/enderdragon/dragon_fireball";

	@Override public String id() { return "dragon_fireball"; }
	@Override public List<String> sources() { return List.of(SOURCE); }
	@Override public List<String> outputs() { return List.of(OUTPUT); }
	@Override public List<Param> params() {
		return List.of(
			Param.of("hue", 0, 1, .76), Param.of("saturation", 0, 1, .62),
			Param.of("min_saturation", 0, 1, .28), Param.of("orange_min_hue", 0, 1, .02), Param.of("orange_max_hue", 0, 1, .16)
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
			if (hsb[1] >= params.get("min_saturation") && hsb[0] >= params.get("orange_min_hue") && hsb[0] <= params.get("orange_max_hue")) {
				float saturation = (float) Math.min(1, hsb[1] * params.get("saturation") / .62);
				int rgb = Color.HSBtoRGB((float) params.get("hue"), saturation, hsb[2]);
				pixels[i] = Ops.argb(Ops.alpha(pixel), (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255);
			}
		}
		return Map.of(OUTPUT, Ops.image(pixels, source.getWidth(), source.getHeight()));
	}
}
