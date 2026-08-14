package dev.oery.legacyresources.client.derive;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

/** Recolours the pack's warm book cover into the knowledge book's green cover, preserving pages. */
final class KnowledgeBook implements Derivation {
	private static final String SOURCE = "item/book";
	private static final String OUTPUT = "item/knowledge_book";
	@Override public String id() { return "knowledge_book"; }
	@Override public List<String> sources() { return List.of(SOURCE); }
	@Override public List<String> outputs() { return List.of(OUTPUT); }
	@Override public List<Param> params() {
		return List.of(Param.of("hue", 0, 1, .31), Param.of("saturation", 0, 1, .62), Param.of("min_saturation", 0, 1, .15));
	}
	@Override public Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		BufferedImage source = sources.get(SOURCE);
		if (source == null) return Map.of();
		int[] pixels = Ops.pixels(source);
		for (int i = 0; i < pixels.length; i++) {
			int pixel = pixels[i];
			if (Ops.alpha(pixel) == 0) continue;
			float[] hsb = Color.RGBtoHSB(Ops.red(pixel), Ops.green(pixel), Ops.blue(pixel), null);
			// Covers are warm; pale pages, black outlines and any intentionally cool pack details remain.
			if (hsb[1] >= params.get("min_saturation") && (hsb[0] <= .17f || hsb[0] >= .93f)) {
				int rgb = Color.HSBtoRGB((float) params.get("hue"), (float) Math.min(1, hsb[1] * params.get("saturation") / .62), hsb[2]);
				pixels[i] = Ops.argb(Ops.alpha(pixel), (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255);
			}
		}
		return Map.of(OUTPUT, Ops.image(pixels, source.getWidth(), source.getHeight()));
	}
}
