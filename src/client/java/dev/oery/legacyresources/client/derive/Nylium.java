package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Crimson and warped nylium from the pack's own netherrack grain. */
final class Nylium implements Derivation {
	private record Face(String output, double hue, double saturation, double brightness, double spread) {
	}

	private static final String SOURCE = "block/netherrack";
	private static final String GRASS_OVERLAY = "block/grass_block_side_overlay";
	private static final List<Face> FACES = List.of(
		new Face("block/crimson_nylium", 0, .752, 52.6, 17.8),
		new Face("block/crimson_nylium_side", .001, .747, 43.9, 17.3),
		new Face("block/warped_nylium", .472, .613, 98.3, 16.7),
		new Face("block/warped_nylium_side", .020, .589, 64.1, 26.2)
	);

	@Override
	public String id() {
		return "nylium";
	}

	@Override
	public List<String> sources() {
		return List.of(SOURCE, GRASS_OVERLAY);
	}

	@Override
	public List<String> outputs() {
		return FACES.stream().map(Face::output).toList();
	}

	@Override
	public List<Param> params() {
		return FACES.stream().flatMap(face -> {
			String prefix = face.output().substring("block/".length());
			return java.util.stream.Stream.of(
				Param.of(prefix + "_hue", 0, 1, face.hue()), Param.of(prefix + "_saturation", 0, 1, face.saturation()),
				Param.of(prefix + "_brightness", 0, 255, face.brightness()), Param.of(prefix + "_spread", 0, 64, face.spread())
			);
		}).collect(java.util.stream.Collectors.collectingAndThen(
			java.util.stream.Collectors.toList(), params -> {
				params.add(Param.of("max_gain", .5, 4, 2));
				params.add(Param.ofInt("levels", 0, 8, 0));
				params.add(Param.ofInt("side_top_rows", 1, 8, 8));
				return List.copyOf(params);
			}
		));
	}

	@Override
	public Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		BufferedImage netherrack = sources.get(SOURCE);
		if (netherrack == null || Ops.scaleOf(netherrack) == 0) return Map.of();
		Map<String, BufferedImage> derived = new LinkedHashMap<>();
		for (Face face : FACES) {
			String prefix = face.output().substring("block/".length());
			Palette palette = new Palette(params.get(prefix + "_hue"), params.get(prefix + "_saturation"), params.get(prefix + "_brightness"));
			derived.put(face.output(), palette.repaint(netherrack, params.get(prefix + "_spread"), params.get("max_gain"), params.getInt("levels")));
		}
		// Nylium sides are netherrack below, with the same nylium material spilling down from the top
		// face. Keeping the cap from the already-derived top preserves the pack's own netherrack grain
		// and makes the two visible faces read as one block.
		BufferedImage overlay = sources.get(GRASS_OVERLAY);
		derived.computeIfPresent("block/crimson_nylium_side", (path, side) -> cap(side, derived.get("block/crimson_nylium"), overlay, params.getInt("side_top_rows")));
		derived.computeIfPresent("block/warped_nylium_side", (path, side) -> cap(side, derived.get("block/warped_nylium"), overlay, params.getInt("side_top_rows")));
		return derived;
	}

	private static BufferedImage cap(BufferedImage side, BufferedImage top, BufferedImage overlay, int rows) {
		int scale = Ops.scaleOf(side);
		if (scale == 0 || Ops.scaleOf(top) != scale) return side;
		int width = side.getWidth();
		int height = Math.min(width, rows * scale);
		int[] output = Ops.pixels(side);
		int[] topPixels = Ops.pixels(top);
		int[] mask = overlay == null || Ops.scaleOf(overlay) == 0 ? null : Ops.pixels(Ops.resizeNearest(overlay, width));
		for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
			int index = y * width + x;
			// A missing grass overlay still receives a simple cap; the overlay is solely what gives
			// packs that authored a ragged fringe their own dents and protrusions.
			if (mask == null || Ops.alpha(mask[index]) != 0) output[index] = topPixels[index];
		}
		return Ops.image(output, width, width);
	}
}
