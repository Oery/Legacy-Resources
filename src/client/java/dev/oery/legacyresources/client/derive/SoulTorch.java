package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

/** A soul torch is the pack's lit torch with only its two-row flame changed to soul-fire cyan. */
final class SoulTorch implements Derivation {
	private static final String SOURCE = "block/torch";
	private static final String OUTPUT = "block/soul_torch";
	/** Optional custom billboard flame used by PureBDcraft's torch model. */
	static final String CUSTOM_FIRE_SOURCE = "block/bdc_torch";
	static final String CUSTOM_FIRE_OUTPUT = "block/legacy_soul_torch_fire";

	@Override
	public String id() {
		return "soul_torch";
	}

	@Override
	public List<String> sources() {
		return List.of(SOURCE, CUSTOM_FIRE_SOURCE);
	}

	@Override
	public List<String> outputs() {
		return List.of(OUTPUT, CUSTOM_FIRE_OUTPUT);
	}

	@Override
	public List<Param> params() {
		return List.of(
			// The four opaque flame pixels in vanilla soul_torch: cyan at a mean luminance of 205.
			Param.of("hue", 0, 1, .505), Param.of("saturation", 0, 1, .79),
			Param.of("brightness", 0, 255, 205), Param.of("spread", 0, 64, 43),
			Param.of("max_gain", .5, 4, 2), Param.ofInt("levels", 0, 8, 0),
			Param.ofInt("flame_start", 0, 15, 6), Param.ofInt("flame_rows", 1, 4, 2)
		);
	}

	@Override
	public String animationSource(String output) {
		return output.equals(CUSTOM_FIRE_OUTPUT) ? CUSTOM_FIRE_SOURCE : SOURCE;
	}

	@Override
	public Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		BufferedImage torch = sources.get(SOURCE);
		int scale = torch == null ? 0 : Ops.scaleOf(torch);
		if (scale == 0) return Map.of();
		int width = torch.getWidth();
		int flameStart = params.getInt("flame_start") * scale;
		int flameHeight = params.getInt("flame_rows") * scale;
		int[] input = Ops.pixels(torch);
		boolean[] flame = new boolean[input.length];
		double sum = 0;
		double squares = 0;
		int count = 0;
		for (int y = flameStart; y < Math.min(width, flameStart + flameHeight); y++) {
			for (int x = 0; x < width; x++) {
				int index = y * width + x;
				if (Ops.alpha(input[index]) == 0) continue;
				flame[index] = true;
				double luminance = Ops.luminance(input[index]);
				sum += luminance;
				squares += luminance * luminance;
				count++;
			}
		}
		if (count == 0) return Map.of();
		double mean = sum / count;
		double deviation = Math.sqrt(Math.max(0, squares / count - mean * mean));
		double gain = deviation == 0 ? 0 : Math.min(params.get("spread") / deviation, params.get("max_gain"));
		double step = params.getInt("levels") == 0 ? 0 : 4 * params.get("spread") / params.getInt("levels");
		int[] output = input.clone();
		for (int i = 0; i < output.length; i++) {
			if (!flame[i]) continue;
			double luminance = params.get("brightness") + (Ops.luminance(input[i]) - mean) * gain;
			if (step > 0) luminance = params.get("brightness") + Math.round((luminance - params.get("brightness")) / step) * step;
			output[i] = Ops.withAlpha(Ops.atLuminance(params.get("hue"), params.get("saturation"), luminance), Ops.alpha(input[i]));
		}
		Map<String, BufferedImage> derived = new java.util.LinkedHashMap<>();
		derived.put(OUTPUT, Ops.image(output, width, width));
		BufferedImage customFire = sources.get(CUSTOM_FIRE_SOURCE);
		if (customFire != null && Ops.scaleOf(customFire) != 0) {
			// Its full 16x16 canvas is the flame, unlike the normal torch where only rows 6-7 burn.
			Palette flamePalette = new Palette(params.get("hue"), params.get("saturation"), params.get("brightness"));
			derived.put(CUSTOM_FIRE_OUTPUT, flamePalette.repaint(customFire, params.get("spread"), params.get("max_gain"), params.getInt("levels")));
		}
		return derived;
	}
}
