package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Blackstone and its cut variants, based on the pack's diorite and masonry patterns. */
final class Blackstone implements Derivation {
	private record Piece(String source, String output, String band) { }
	private static final List<Piece> PIECES = List.of(
		new Piece("block/diorite", "block/blackstone", "raw"),
		new Piece("block/diorite", "block/blackstone_top", "raw_top"),
		new Piece("block/polished_diorite", "block/polished_blackstone", "polished"),
		new Piece("block/stone_bricks", "block/polished_blackstone_bricks", "bricks"),
		new Piece("block/cracked_stone_bricks", "block/cracked_polished_blackstone_bricks", "cracked"),
		new Piece("block/chiseled_stone_bricks", "block/chiseled_polished_blackstone", "chiseled")
	);

	@Override public String id() { return "blackstone"; }
	@Override public List<String> sources() {
		return java.util.stream.Stream.concat(PIECES.stream().map(Piece::source), java.util.stream.Stream.of("block/stone", "block/gold_ore"))
			.distinct().toList();
	}
	@Override public List<String> outputs() {
		return java.util.stream.Stream.concat(PIECES.stream().map(Piece::output), java.util.stream.Stream.of("block/gilded_blackstone")).toList();
	}
	@Override public String animationSource(String output) {
		return PIECES.stream().filter(piece -> piece.output().equals(output)).map(Piece::source).findFirst().orElse(null);
	}
	@Override public List<Param> params() {
		return List.of(
			Param.of("hue", 0, 1, .82), Param.of("saturation", 0, 1, .08),
			Param.of("raw_brightness", 0, 255, 37.3), Param.of("raw_spread", 0, 64, 15.8),
			Param.of("raw_top_brightness", 0, 255, 38.1), Param.of("raw_top_spread", 0, 64, 14.8),
			Param.of("polished_brightness", 0, 255, 50.2), Param.of("polished_spread", 0, 64, 14.8),
			Param.of("bricks_brightness", 0, 255, 44.3), Param.of("bricks_spread", 0, 64, 16.8),
			Param.of("cracked_brightness", 0, 255, 39.4), Param.of("cracked_spread", 0, 64, 18.2),
			Param.of("chiseled_brightness", 0, 255, 50.4), Param.of("chiseled_spread", 0, 64, 19.5),
			Param.of("max_gain", .25, 4, 1.25), Param.ofInt("levels", 0, 8, 0),
			Param.ofInt("ore_host_difference", 0, 64, 8), Param.of("min_ore_host", .2, .95, .35),
			Param.of("min_gold_fraction", 0, .25, .02), Param.of("max_gold_fraction", .1, .75, .35),
			Param.ofInt("gold_edge_rows", 1, 4, 2), Param.of("max_edge_gold", .2, 1, .50),
			Param.of("gold_hue", 0, 1, .093), Param.of("gold_saturation", 0, 1, .86),
			Param.of("gold_brightness", 0, 255, 79.5), Param.of("gold_spread", 0, 96, 59.3)
		);
	}

	@Override public Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		Map<String, BufferedImage> out = new LinkedHashMap<>();
		for (Piece piece : PIECES) {
			BufferedImage source = sources.get(piece.source());
			if (source == null || Ops.scaleOf(source) == 0) continue;
			Palette palette = new Palette(params.get("hue"), params.get("saturation"), params.get(piece.band() + "_brightness"));
			out.put(piece.output(), palette.repaint(source, params.get(piece.band() + "_spread"), params.get("max_gain"), params.getInt("levels")));
		}
		BufferedImage stone = sources.get("block/stone");
		BufferedImage gold = sources.get("block/gold_ore");
		BufferedImage blackstone = out.get("block/blackstone");
		if (stone != null && gold != null && blackstone != null && Ops.scaleOf(gold) > 0) {
			stone = Ops.resizeNearest(stone, gold.getWidth());
			blackstone = Ops.resizeNearest(blackstone, gold.getWidth());
			int[] host = Ops.pixels(stone), ore = Ops.pixels(gold), dark = Ops.pixels(blackstone);
			int[] gilded = dark.clone();
			boolean[] vein = new boolean[ore.length];
			int hostCount = 0, veinCount = 0, edgeVeinCount = 0, opaqueCount = 0;
			int limit = params.getInt("ore_host_difference");
			for (int i = 0; i < ore.length; i++) {
				int dr = Ops.red(ore[i]) - Ops.red(host[i]);
				int dg = Ops.green(ore[i]) - Ops.green(host[i]);
				int db = Ops.blue(ore[i]) - Ops.blue(host[i]);
				if (Ops.alpha(ore[i]) != 0) opaqueCount++;
				if (Ops.alpha(ore[i]) != 0 && Ops.alpha(host[i]) != 0 && dr * dr + dg * dg + db * db <= limit * limit) hostCount++;
				else if (Ops.alpha(ore[i]) != 0) {
					vein[i] = true; veinCount++;
					int x = i % gold.getWidth(), y = i / gold.getWidth();
					int margin = params.getInt("gold_edge_rows") * gold.getWidth() / Ops.BASE_SIZE;
					if (x < margin || y < margin || x >= gold.getWidth() - margin || y >= gold.getHeight() - margin) edgeVeinCount++;
				}
			}
			double veinFraction = opaqueCount == 0 ? 0 : (double) veinCount / opaqueCount;
			double edgeFraction = veinCount == 0 ? 1 : (double) edgeVeinCount / veinCount;
			if ((double) hostCount / ore.length >= params.get("min_ore_host")
				&& veinFraction >= params.get("min_gold_fraction") && veinFraction <= params.get("max_gold_fraction")
				&& edgeFraction <= params.get("max_edge_gold")) {
				repaintGold(ore, gilded, vein, params);
				out.put("block/gilded_blackstone", Ops.image(gilded, gold.getWidth(), gold.getHeight()));
			}
		}
		return out;
	}

	private static void repaintGold(int[] source, int[] output, boolean[] selected, Params params) {
		double sum = 0, squares = 0;
		int count = 0;
		for (int i = 0; i < source.length; i++) if (selected[i]) {
			double luminance = Ops.luminance(source[i]);
			sum += luminance; squares += luminance * luminance; count++;
		}
		if (count == 0) return;
		double mean = sum / count;
		double deviation = Math.sqrt(Math.max(0, squares / count - mean * mean));
		double gain = deviation == 0 ? 0 : Math.min(params.get("gold_spread") / deviation, params.get("max_gain"));
		for (int i = 0; i < source.length; i++) if (selected[i]) {
			double luminance = params.get("gold_brightness") + (Ops.luminance(source[i]) - mean) * gain;
			output[i] = Ops.withAlpha(Ops.atLuminance(params.get("gold_hue"), params.get("gold_saturation"), luminance), Ops.alpha(source[i]));
		}
	}
}
