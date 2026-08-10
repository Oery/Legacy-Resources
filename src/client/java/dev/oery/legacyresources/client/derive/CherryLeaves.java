package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cherry leaves from the pack's oak canopy. Cherry's blossom-cluster silhouette is new art and
 * cannot be learned from an oak sheet, but its pale pink material can: the pack retains its own
 * cutout, branch gaps and foliage density instead of receiving a foreign vanilla cluster pattern.
 */
final class CherryLeaves implements Derivation {
	private static final String SOURCE = "block/oak_leaves";
	private static final String OUTPUT = "block/cherry_leaves";
	/** The green leaf clusters in vanilla's 16x cherry-leaves texture, read from reference/26.2. */
	private static final int[] GREEN_CELLS = {
		3, 0, 8, 2, 9, 2, 7, 3, 8, 3, 9, 3, 8, 4, 13, 4, 14, 4, 14, 5, 15, 5, 10, 6,
		3, 9, 3, 10, 4, 10, 13, 13, 3, 14, 4, 14, 12, 14, 13, 14, 2, 15, 3, 15, 4, 15, 12, 15, 13, 15
	};

	@Override
	public String id() {
		return "cherry_leaves";
	}

	@Override
	public List<String> sources() {
		return List.of(SOURCE);
	}

	@Override
	public List<String> outputs() {
		return List.of(OUTPUT);
	}

	@Override
	public List<Param> params() {
		return List.of(
			Param.of("hue", 0, 1, 0.94),
			Param.of("saturation", 0, 1, 0.42),
			Param.of("brightness", 0, 255, 190),
			Param.of("spread", 0, 64, 24),
			Param.of("max_gain", 0.5, 4, 2),
			Param.ofInt("levels", 0, 8, 0),
			Param.of("green_hue", 0, 1, 0.20),
			Param.of("green_saturation", 0, 1, 0.70),
			Param.of("green_brightness", 0, 255, 140)
		);
	}

	@Override
	public Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		BufferedImage oak = sources.get(SOURCE);
		if (oak == null || Ops.scaleOf(oak) == 0) {
			return Map.of();
		}
		Palette palette = new Palette(params.get("hue"), params.get("saturation"), params.get("brightness"));
		BufferedImage pink = palette.repaint(oak, params.get("spread"), params.get("max_gain"), params.getInt("levels"));
		return Map.of(OUTPUT, hasAtLeastThreeColors(oak) ? addGreenClusters(pink, oak, params) : pink);
	}

	/** Minimal packs often use one or two deliberate flat leaf colours; extra clusters only add noise there. */
	private static boolean hasAtLeastThreeColors(BufferedImage image) {
		Set<Integer> colors = new HashSet<>();
		for (int pixel : Ops.pixels(image)) {
			if (Ops.alpha(pixel) == 0) {
				continue;
			}
			colors.add(pixel & 0x00FFFFFF);
			if (colors.size() >= 3) {
				return true;
			}
		}
		return false;
	}

	/** Paints vanilla's small olive clusters where the pack has an opaque leaf pixel, never filling a hole. */
	private static BufferedImage addGreenClusters(BufferedImage pink, BufferedImage source, Params params) {
		int[] out = Ops.pixels(pink);
		int[] sourcePixels = Ops.pixels(source);
		int scale = Ops.scaleOf(source);
		int size = source.getWidth();
		int green = Ops.atLuminance(params.get("green_hue"), params.get("green_saturation"), params.get("green_brightness"));
		for (int cell = 0; cell < GREEN_CELLS.length; cell += 2) {
			int left = GREEN_CELLS[cell] * scale;
			int top = GREEN_CELLS[cell + 1] * scale;
			for (int y = top; y < top + scale; y++) {
				for (int x = left; x < left + scale; x++) {
					int index = y * size + x;
					if (Ops.alpha(sourcePixels[index]) != 0) {
						out[index] = Ops.withAlpha(green, Ops.alpha(sourcePixels[index]));
					}
				}
			}
		}
		return Ops.image(out, size, size);
	}
}
