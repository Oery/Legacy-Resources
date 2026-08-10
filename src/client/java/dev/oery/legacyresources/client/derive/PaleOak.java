package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The basic pale-oak tree textures from the closest legacy wood materials. Its cracked grey bark is
 * new art, so this preserves oak's longitudinal grain rather than pretending the silhouettes match;
 * the lighter birch end grain and planks make better sources for the pale cut wood.
 */
final class PaleOak implements Derivation {
	private record Face(String source, String output, String prefix) {
	}

	private static final List<Face> FACES = List.of(
		new Face("block/oak_log", "block/pale_oak_log", "bark"),
		new Face("block/birch_log_top", "block/pale_oak_log_top", "end"),
		new Face("block/birch_planks", "block/pale_oak_planks", "planks"),
		new Face("block/oak_leaves", "block/pale_oak_leaves", "leaves")
	);

	@Override
	public String id() {
		return "pale_oak";
	}

	@Override
	public List<String> sources() {
		return FACES.stream().map(Face::source).toList();
	}

	@Override
	public List<String> outputs() {
		return FACES.stream().map(Face::output).toList();
	}

	@Override
	public List<Param> params() {
		return List.of(
			Param.of("bark_hue", 0, 1, 0.06), Param.of("bark_saturation", 0, 1, 0.15),
			Param.of("bark_brightness", 0, 255, 76), Param.of("bark_spread", 0, 64, 14),
			Param.of("end_hue", 0, 1, 0.02), Param.of("end_saturation", 0, 1, 0.06),
			Param.of("end_brightness", 0, 255, 210), Param.of("end_spread", 0, 64, 18),
			Param.ofInt("top_bark_search_width", 1, 8, 4),
			Param.of("planks_hue", 0, 1, 0.08), Param.of("planks_saturation", 0, 1, 0.08),
			Param.of("planks_brightness", 0, 255, 203), Param.of("planks_spread", 0, 64, 10),
			Param.of("leaves_hue", 0, 1, 0.31), Param.of("leaves_saturation", 0, 1, 0),
			Param.of("leaves_brightness", 0, 255, 104), Param.of("leaves_spread", 0, 64, 28),
			Param.of("max_gain", 0.5, 4, 2), Param.ofInt("levels", 0, 8, 0)
		);
	}

	@Override
	public Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		Map<String, BufferedImage> derived = new LinkedHashMap<>();
		for (Face face : FACES) {
			BufferedImage source = sources.get(face.source());
			if (source == null || Ops.scaleOf(source) == 0) {
				continue;
			}
			Palette palette = new Palette(
				params.get(face.prefix() + "_hue"), params.get(face.prefix() + "_saturation"), params.get(face.prefix() + "_brightness")
			);
			BufferedImage painted = palette.repaint(source, params.get(face.prefix() + "_spread"), params.get("max_gain"), params.getInt("levels"));
			if (face.prefix().equals("end")) {
				Palette bark = new Palette(params.get("bark_hue"), params.get("bark_saturation"), params.get("bark_brightness"));
				BufferedImage barkPainted = bark.repaint(source, params.get("bark_spread"), params.get("max_gain"), params.getInt("levels"));
				painted = LogTop.withBarkMask(painted, barkPainted, source, params.getInt("top_bark_search_width"));
			}
			derived.put(face.output(), painted);
		}
		return derived;
	}
}
