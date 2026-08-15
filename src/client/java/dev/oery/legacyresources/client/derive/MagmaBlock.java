package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Cobblestone whose darkest joints are filled with the pack's own animated lava. */
final class MagmaBlock implements Derivation {
	private static final String COBBLE = "block/cobblestone";
	private static final String LAVA = "block/lava_still";
	private static final String OUTPUT = "block/magma";

	@Override public String id() { return "magma_block"; }
	@Override public List<String> sources() { return List.of(COBBLE, LAVA); }
	@Override public List<String> outputs() { return List.of(OUTPUT); }
	@Override public String animationSource(String output) { return LAVA; }
	@Override public List<Param> params() {
		return List.of(
			Param.of("stone_hue", 0, 1, .035), Param.of("stone_saturation", 0, 1, .62),
			Param.of("stone_brightness", 0, 255, 53), Param.of("stone_spread", 0, 64, 18),
			Param.of("max_gain", .25, 4, 1.25), Param.of("gap_fraction", .04, .45, .20),
			Param.ofInt("joint_radius", 1, 4, 1), Param.of("min_joint_contrast", 0, 32, 2),
			Param.of("lava_target", 0, 255, 145), Param.of("lava_reference", 0, 255, 122.8),
			Param.of("lava_follow_source", 0, 1, .35), Param.of("min_lava_contrast", 0, 96, 18)
		);
	}

	@Override public Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		BufferedImage cobble = sources.get(COBBLE);
		BufferedImage lava = sources.get(LAVA);
		int scale = cobble == null ? 0 : Ops.scaleOf(cobble);
		if (scale == 0 || lava == null || lava.getWidth() < Ops.BASE_SIZE || lava.getWidth() % Ops.BASE_SIZE != 0
			|| lava.getHeight() % lava.getWidth() != 0) return Map.of();
		BufferedImage blurred = Ops.boxBlur(cobble, params.getInt("joint_radius") * scale);
		int[] sourcePixels = Ops.pixels(cobble);
		int[] blurPixels = Ops.pixels(blurred);
		double[] contrast = new double[sourcePixels.length];
		for (int i = 0; i < contrast.length; i++) contrast[i] = Ops.luminance(sourcePixels[i]) - Ops.luminance(blurPixels[i]);
		double[] ordered = contrast.clone();
		Arrays.sort(ordered);
		if (ordered[ordered.length - 1] - ordered[0] < params.get("min_joint_contrast")) return Map.of();
		int cut = Math.clamp((int) Math.round(ordered.length * params.get("gap_fraction")), 1, ordered.length - 1);
		double threshold = ordered[cut - 1];
		boolean[] gap = new boolean[contrast.length];
		int selected = 0;
		for (int i = 0; i < gap.length; i++) {
			if (contrast[i] < threshold) { gap[i] = true; selected++; }
		}
		for (int i = 0; i < gap.length && selected < cut; i++) {
			if (contrast[i] == threshold) { gap[i] = true; selected++; }
		}

		Palette stoneBand = new Palette(params.get("stone_hue"), params.get("stone_saturation"), params.get("stone_brightness"));
		int[] stone = Ops.pixels(stoneBand.repaint(cobble, params.get("stone_spread"), params.get("max_gain"), 0));
		int frames = lava.getHeight() / lava.getWidth();
		BufferedImage out = Ops.blank(cobble.getWidth(), cobble.getHeight() * frames);
		for (int frame = 0; frame < frames; frame++) {
			BufferedImage lavaFrame = lava.getSubimage(0, frame * lava.getWidth(), lava.getWidth(), lava.getWidth());
			int[] lavaPixels = Ops.pixels(Ops.resizeNearest(lavaFrame, cobble.getWidth()));
			double sourceMean = Ops.meanLuminance(lavaFrame);
			double targetMean = params.get("lava_target")
				+ (sourceMean - params.get("lava_reference")) * params.get("lava_follow_source");
			double lavaGain = sourceMean <= 0 ? 1 : targetMean / sourceMean;
			double lavaLuminance = 0, stoneLuminance = 0;
			int gapPixels = 0;
			for (int i = 0; i < lavaPixels.length; i++) if (gap[i] && Ops.alpha(lavaPixels[i]) != 0) {
				int levelled = Ops.argb(255,
					(int) Math.round(Ops.red(lavaPixels[i]) * lavaGain),
					(int) Math.round(Ops.green(lavaPixels[i]) * lavaGain),
					(int) Math.round(Ops.blue(lavaPixels[i]) * lavaGain));
				lavaLuminance += Ops.luminance(levelled);
				stoneLuminance += Ops.luminance(stone[i]);
				gapPixels++;
			}
			if (gapPixels == 0 || (lavaLuminance - stoneLuminance) / gapPixels < params.get("min_lava_contrast")) return Map.of();
			int[] framePixels = stone.clone();
			for (int i = 0; i < framePixels.length; i++) {
				if (gap[i] && Ops.alpha(sourcePixels[i]) != 0 && Ops.alpha(lavaPixels[i]) != 0) {
					framePixels[i] = Ops.argb(
						Ops.alpha(sourcePixels[i]),
						(int) Math.round(Ops.red(lavaPixels[i]) * lavaGain),
						(int) Math.round(Ops.green(lavaPixels[i]) * lavaGain),
						(int) Math.round(Ops.blue(lavaPixels[i]) * lavaGain)
					);
				}
			}
			out.setRGB(0, frame * cobble.getHeight(), cobble.getWidth(), cobble.getHeight(), framePixels, 0, cobble.getWidth());
		}
		return Map.of(OUTPUT, out);
	}
}
