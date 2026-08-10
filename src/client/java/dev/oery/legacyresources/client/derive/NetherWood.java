package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The core crimson and warped wood families, made from legacy jungle wood and netherrack.
 *
 * <p>Pre-1.16 packs have no Nether-tree relative. Jungle supplies the dense vertical/end grain a
 * stem needs, while netherrack supplies the dense organic noise of both wart blocks. Each modern
 * material still has its own measured band, so crimson and warped never collapse into the same
 * recolour.</p>
 */
final class NetherWood implements Derivation {
	private record Band(double hue, double saturation, double brightness, double spread) {
	}
	private record Face(String source, String output, Band band) {
		String paramPrefix() {
			return output.substring("block/".length());
		}
	}

	private static final String JUNGLE_LOG = "block/jungle_log";
	private static final String JUNGLE_LOG_TOP = "block/jungle_log_top";
	private static final String JUNGLE_PLANKS = "block/jungle_planks";
	private static final String NETHERRACK = "block/netherrack";

	private static final List<Face> FACES = List.of(
		new Face(JUNGLE_LOG, "block/crimson_stem", new Band(.986, .713, 40.3, 8.7)),
		new Face(JUNGLE_LOG_TOP, "block/crimson_stem_top", new Band(.946, .559, 64.4, 14.5)),
		new Face(JUNGLE_PLANKS, "block/crimson_planks", new Band(.930, .514, 61.4, 13.8)),
		new Face(NETHERRACK, "block/nether_wart_block", new Band(.001, .986, 26.5, 9.0)),
		new Face(JUNGLE_LOG, "block/stripped_crimson_stem", new Band(.932, .583, 76.4, 6.2)),
		new Face(JUNGLE_LOG_TOP, "block/stripped_crimson_stem_top", new Band(.933, .534, 71.9, 7.8)),
		// A red-magenta bark band keeps warped wood distinct from crimson without forcing vanilla's
		// cyan strip layout onto packs such as PureBDcraft that author their own broad grain.
		new Face(JUNGLE_LOG, "block/warped_stem", new Band(.870, .550, 60.0, 14.0)),
		new Face(JUNGLE_LOG_TOP, "block/warped_stem_top", new Band(.506, .581, 97.7, 27.2)),
		new Face(JUNGLE_PLANKS, "block/warped_planks", new Band(.483, .594, 91.3, 25.0)),
		new Face(NETHERRACK, "block/warped_wart_block", new Band(.503, .812, 99.2, 13.0)),
		new Face(JUNGLE_LOG, "block/stripped_warped_stem", new Band(.494, .620, 130.8, 9.2)),
		new Face(JUNGLE_LOG_TOP, "block/stripped_warped_stem_top", new Band(.489, .592, 112.3, 15.9))
	);

	@Override
	public String id() {
		return "nether_wood";
	}

	@Override
	public List<String> sources() {
		return List.of(JUNGLE_LOG, JUNGLE_LOG_TOP, JUNGLE_PLANKS, NETHERRACK);
	}

	@Override
	public List<String> outputs() {
		return FACES.stream().map(Face::output).toList();
	}

	@Override
	public List<Param> params() {
		List<Param> params = new ArrayList<>();
		for (Face face : FACES) {
			String prefix = face.paramPrefix();
			params.add(Param.of(prefix + "_hue", 0, 1, face.band().hue()));
			params.add(Param.of(prefix + "_saturation", 0, 1, face.band().saturation()));
			params.add(Param.of(prefix + "_brightness", 0, 255, face.band().brightness()));
			params.add(Param.of(prefix + "_spread", 0, 64, face.band().spread()));
		}
		params.add(Param.of("max_gain", .5, 4, 2));
		params.add(Param.ofInt("levels", 0, 8, 0));
		params.add(Param.ofInt("top_bark_search_width", 1, 8, 4));
		return List.copyOf(params);
	}

	@Override
	public Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		Map<String, BufferedImage> derived = new LinkedHashMap<>();
		for (Face face : FACES) {
			BufferedImage source = sources.get(face.source());
			if (source == null || Ops.scaleOf(source) == 0) continue;
			String prefix = face.paramPrefix();
			Palette palette = new Palette(params.get(prefix + "_hue"), params.get(prefix + "_saturation"), params.get(prefix + "_brightness"));
			BufferedImage painted = palette.repaint(source, params.get(prefix + "_spread"), params.get("max_gain"), params.getInt("levels"));
			if (face.output().equals("block/warped_stem_top")) {
				Palette barkPalette = new Palette(
					params.get("warped_stem_hue"), params.get("warped_stem_saturation"), params.get("warped_stem_brightness")
				);
				BufferedImage bark = barkPalette.repaint(source, params.get("warped_stem_spread"), params.get("max_gain"), params.getInt("levels"));
				painted = LogTop.withBarkMask(painted, bark, source, params.getInt("top_bark_search_width"));
			}
			derived.put(face.output(), painted);
		}
		return derived;
	}

}
