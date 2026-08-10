package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The core mangrove tree textures from the pack's jungle tree. Mangrove's red bark and wood are new
 * material bands, but its legacy relative supplies the vertical grain, end-grain structure, planks
 * and dense foliage cutout that make the result belong to the pack.
 */
final class Mangrove implements Derivation {
	private record Face(String source, String output, String prefix) {
	}

	private static final List<Face> FACES = List.of(
		new Face("block/jungle_log", "block/mangrove_log", "bark"),
		new Face("block/jungle_log_top", "block/mangrove_log_top", "end"),
		new Face("block/jungle_planks", "block/mangrove_planks", "planks"),
		new Face("block/jungle_leaves", "block/mangrove_leaves", "leaves")
	);

	@Override
	public String id() {
		return "mangrove";
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
			Param.of("bark_hue", 0, 1, .10), Param.of("bark_saturation", 0, 1, .505),
			Param.of("bark_brightness", 0, 255, 68.4), Param.of("bark_spread", 0, 64, 10.2),
			Param.of("end_hue", 0, 1, .019), Param.of("end_saturation", 0, 1, .597),
			Param.of("end_brightness", 0, 255, 59.9), Param.of("end_spread", 0, 64, 11.9),
			Param.ofInt("top_bark_search_width", 1, 8, 4),
			Param.of("planks_hue", 0, 1, .011), Param.of("planks_saturation", 0, 1, .602),
			Param.of("planks_brightness", 0, 255, 67.4), Param.of("planks_spread", 0, 64, 14.3),
			Param.of("leaves_hue", 0, 1, 0), Param.of("leaves_saturation", 0, 1, 0),
			Param.of("leaves_brightness", 0, 255, 128.9), Param.of("leaves_spread", 0, 64, 35.7),
			Param.of("max_gain", .5, 4, 2), Param.ofInt("levels", 0, 8, 0)
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
