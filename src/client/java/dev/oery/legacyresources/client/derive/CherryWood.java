package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cherry's first three block textures, built from the pack's oak grain. Cherry was added long after
 * legacy packs stopped receiving art: it is not a renamed oak texture but a dark purple bark, a pale
 * pink end grain, and pale pink planks. The source therefore contributes pattern and resolution while
 * the three measured modern bands supply the material's identity.
 *
 * <p>Only the plain log and planks are deliberately in scope here. Stripped wood, leaves, saplings,
 * signs and the manufactured wood set need their own source/geometry decisions.
 */
final class CherryWood implements Derivation {
	private record Face(String source, String output, String prefix, double hue, double saturation, double mean, double spread) {
	}

	/** Values measured from {@code reference/26.2}; hue is HSB's circular 0..1 form. */
	private static final List<Face> FACES = List.of(
		new Face("block/oak_log", "block/cherry_log", "bark", 0.91, 0.40, 38.4, 15),
		new Face("block/oak_log_top", "block/cherry_log_top", "end", 0.98, 0.30, 150.1, 18),
		new Face("block/oak_planks", "block/cherry_planks", "planks", 0.02, 0.24, 188.5, 13)
	);

	@Override
	public String id() {
		return "cherry_wood";
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
			Param.of("bark_hue", 0, 1, 0.91), Param.of("bark_saturation", 0, 1, 0.40),
			Param.of("bark_brightness", 0, 255, 38.4), Param.of("bark_spread", 0, 64, 15),
			Param.of("end_hue", 0, 1, 0.98), Param.of("end_saturation", 0, 1, 0.30),
			Param.of("end_brightness", 0, 255, 150.1), Param.of("end_spread", 0, 64, 64),
			Param.of("planks_hue", 0, 1, 0.95), Param.of("planks_saturation", 0, 1, 0.24),
			Param.of("planks_brightness", 0, 255, 188.5), Param.of("planks_spread", 0, 64, 13),
			Param.of("max_gain", 0.5, 4, 2.0),
			Param.ofInt("levels", 0, 8, 0)
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
			derived.put(face.output(), palette.repaint(source, params.get(face.prefix() + "_spread"), params.get("max_gain"), params.getInt("levels")));
		}
		return derived;
	}
}
