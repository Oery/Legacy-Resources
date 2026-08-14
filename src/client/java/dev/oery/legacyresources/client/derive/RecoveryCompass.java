package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Recovery-compass frames from the converted legacy compass frames.
 *
 * <p>A compass already supplies the answer to the difficult question here: comparing all 32 frames
 * identifies its moving needle pixel-for-pixel. The unchanged dial, rim and highlights pass through;
 * only pixels that differ from their coordinate's most common value are recoloured cyan. Requiring a
 * complete, same-sized frame set avoids a partly recoloured animated needle.</p>
 */
final class RecoveryCompass implements Derivation {
	private static final int FRAMES = 32;
	private static final List<String> SOURCES = frames("item/compass_");
	private static final List<String> OUTPUTS = frames("item/recovery_compass_");

	@Override public String id() { return "recovery_compass"; }
	@Override public List<String> sources() { return SOURCES; }
	@Override public List<String> outputs() { return OUTPUTS; }

	@Override
	public List<Param> params() {
		return List.of(Param.of("hue", 0, 1, .53), Param.of("saturation", 0, 1, .58));
	}

	@Override
	public Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		List<BufferedImage> frames = new ArrayList<>(FRAMES);
		int width = 0;
		for (String source : SOURCES) {
			BufferedImage frame = sources.get(source);
			if (frame == null || Ops.scaleOf(frame) == 0) return Map.of();
			if (width == 0) width = frame.getWidth();
			if (frame.getWidth() != width || frame.getHeight() != width) return Map.of();
			frames.add(frame);
		}
		int[][] pixels = frames.stream().map(Ops::pixels).toArray(int[][]::new);
		int[] staticPixels = mostCommonAtEachPixel(pixels);
		Map<String, BufferedImage> derived = new LinkedHashMap<>();
		for (int frame = 0; frame < FRAMES; frame++) {
			int[] output = pixels[frame].clone();
			for (int pixel = 0; pixel < output.length; pixel++) {
				if (output[pixel] == staticPixels[pixel] || Ops.alpha(output[pixel]) == 0) continue;
				output[pixel] = Ops.withAlpha(
					Ops.atLuminance(params.get("hue"), params.get("saturation"), Ops.luminance(output[pixel])),
					Ops.alpha(output[pixel])
				);
			}
			derived.put(OUTPUTS.get(frame), Ops.image(output, width, width));
		}
		return derived;
	}

	private static int[] mostCommonAtEachPixel(int[][] frames) {
		int[] common = new int[frames[0].length];
		for (int pixel = 0; pixel < common.length; pixel++) {
			Map<Integer, Integer> counts = new HashMap<>();
			for (int[] frame : frames) counts.merge(frame[pixel], 1, Integer::sum);
			common[pixel] = counts.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
		}
		return common;
	}

	private static List<String> frames(String prefix) {
		List<String> frames = new ArrayList<>(FRAMES);
		for (int frame = 0; frame < FRAMES; frame++) frames.add(prefix + "%02d".formatted(frame));
		return List.copyOf(frames);
	}
}
