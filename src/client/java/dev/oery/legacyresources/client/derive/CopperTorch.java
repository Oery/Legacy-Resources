package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

/** Copper torch: the lit torch's timber stays intact while its two-row flame becomes pale green. */
final class CopperTorch implements Derivation {
	private static final String SOURCE = "block/torch";
	private static final String OUTPUT = "block/copper_torch";
	static final String CUSTOM_FIRE_SOURCE = "block/bdc_torch";
	static final String CUSTOM_FIRE_OUTPUT = "block/legacy_copper_torch_fire";

	@Override
	public String id() {
		return "copper_torch";
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
	public String animationSource(String output) {
		return output.equals(CUSTOM_FIRE_OUTPUT) ? CUSTOM_FIRE_SOURCE : SOURCE;
	}

	@Override
	public List<Param> params() {
		return List.of(
			// The four opaque copper-torch flame pixels in reference/26.2: green, mean 183, spread 52.
			Param.of("hue", 0, 1, .344), Param.of("saturation", 0, 1, .38),
			Param.of("brightness", 0, 255, 183), Param.of("spread", 0, 64, 52),
			Param.of("max_gain", .5, 4, 2), Param.ofInt("levels", 0, 8, 0),
			Param.ofInt("flame_start", 0, 15, 6), Param.ofInt("flame_rows", 1, 4, 2)
		);
	}

	@Override
	public Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		BufferedImage torch = sources.get(SOURCE);
		int scale = torch == null ? 0 : Ops.scaleOf(torch);
		if (scale == 0) return Map.of();
		int width = torch.getWidth();
		int start = params.getInt("flame_start") * scale;
		int end = Math.min(width, start + params.getInt("flame_rows") * scale);
		int[] input = Ops.pixels(torch);
		boolean[] flame = new boolean[input.length];
		double sum = 0, squares = 0;
		int count = 0;
		for (int y = start; y < end; y++) for (int x = 0; x < width; x++) {
			int index = y * width + x;
			if (Ops.alpha(input[index]) == 0) continue;
			flame[index] = true;
			double luminance = Ops.luminance(input[index]);
			sum += luminance;
			squares += luminance * luminance;
			count++;
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
			Palette flamePalette = new Palette(params.get("hue"), params.get("saturation"), params.get("brightness"));
			derived.put(CUSTOM_FIRE_OUTPUT, flamePalette.repaint(customFire, params.get("spread"), params.get("max_gain"), params.getInt("levels")));
		}
		return derived;
	}
}
