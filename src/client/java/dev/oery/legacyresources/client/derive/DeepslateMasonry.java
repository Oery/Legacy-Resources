package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The cut deepslate family, retaining the pack's cobble and stone-brick masonry. */
final class DeepslateMasonry implements Derivation {
	private record Piece(String source, String output, String band) { }

	private static final List<Piece> PIECES = List.of(
		new Piece("block/cobblestone", "block/cobbled_deepslate", "cobbled"),
		new Piece("block/stone_bricks", "block/polished_deepslate", "polished"),
		new Piece("block/stone_bricks", "block/deepslate_bricks", "bricks"),
		new Piece("block/cracked_stone_bricks", "block/cracked_deepslate_bricks", "cracked_bricks"),
		new Piece("block/stone_bricks", "block/deepslate_tiles", "tiles"),
		new Piece("block/cracked_stone_bricks", "block/cracked_deepslate_tiles", "cracked_tiles"),
		new Piece("block/chiseled_stone_bricks", "block/chiseled_deepslate", "chiseled")
	);

	@Override public String id() { return "deepslate_masonry"; }
	@Override public List<String> sources() { return PIECES.stream().map(Piece::source).distinct().toList(); }
	@Override public List<String> outputs() { return PIECES.stream().map(Piece::output).toList(); }
	@Override public String animationSource(String output) {
		return PIECES.stream().filter(piece -> piece.output().equals(output)).map(Piece::source).findFirst().orElse(null);
	}
	@Override public List<Param> params() {
		return List.of(
			Param.of("hue", 0, 1, .70), Param.of("saturation", 0, 1, .01),
			Param.of("cobbled_brightness", 0, 255, 61), Param.of("cobbled_spread", 0, 64, 22),
			Param.of("polished_brightness", 0, 255, 72.6), Param.of("polished_spread", 0, 64, 20.2),
			Param.of("bricks_brightness", 0, 255, 71), Param.of("bricks_spread", 0, 64, 22),
			Param.of("cracked_bricks_brightness", 0, 255, 64.9), Param.of("cracked_bricks_spread", 0, 64, 22.6),
			Param.of("tiles_brightness", 0, 255, 54.8), Param.of("tiles_spread", 0, 64, 14.9),
			Param.of("cracked_tiles_brightness", 0, 255, 52.6), Param.of("cracked_tiles_spread", 0, 64, 14.7),
			Param.of("chiseled_brightness", 0, 255, 54.4), Param.of("chiseled_spread", 0, 64, 15.9),
			Param.of("max_gain", .25, 4, 1.5), Param.ofInt("levels", 0, 8, 0)
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
		return out;
	}
}
