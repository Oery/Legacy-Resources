package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Command-block families from the legacy command-block mechanism texture.
 *
 * <p>The front/back/side glyphs are new artwork, but all twelve faces share the same underlying
 * material. Repainting the pack's animated mechanical pattern gives chain and repeating blocks the
 * distinct teal and violet material cues they otherwise lose to the old orange-only texture.</p>
 */
final class CommandBlocks implements Derivation {
	private record Family(String prefix, double hue, double saturation, double brightness, double spread) {
	}

	private static final String SOURCE = "block/command_block";
	private static final List<Family> FAMILIES = List.of(
		new Family("command_block", .058, .36, 140, 30),
		new Family("chain_command_block", .455, .25, 147, 30),
		new Family("repeating_command_block", .724, .34, 135, 32)
	);
	private static final List<String> FACES = List.of("back", "front", "side", "conditional");
	private static final List<String> OUTPUTS = FAMILIES.stream()
		.flatMap(family -> FACES.stream().map(face -> "block/" + family.prefix() + "_" + face)).toList();

	@Override public String id() { return "command_blocks"; }
	@Override public List<String> sources() { return List.of(SOURCE); }
	@Override public List<String> outputs() { return OUTPUTS; }

	@Override
	public List<Param> params() {
		return FAMILIES.stream().flatMap(family -> java.util.stream.Stream.of(
			Param.of(family.prefix() + "_hue", 0, 1, family.hue()),
			Param.of(family.prefix() + "_saturation", 0, 1, family.saturation()),
			Param.of(family.prefix() + "_brightness", 0, 255, family.brightness()),
			Param.of(family.prefix() + "_spread", 0, 64, family.spread())
		)).collect(java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toList(), params -> {
			params.add(Param.of("max_gain", .5, 4, 2));
			params.add(Param.ofInt("levels", 0, 8, 0));
			return List.copyOf(params);
		}));
	}

	@Override
	public Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		BufferedImage source = sources.get(SOURCE);
		if (source == null || Ops.scaleOf(source) == 0) return Map.of();
		Map<String, BufferedImage> derived = new LinkedHashMap<>();
		for (Family family : FAMILIES) {
			Palette palette = new Palette(
				params.get(family.prefix() + "_hue"), params.get(family.prefix() + "_saturation"), params.get(family.prefix() + "_brightness")
			);
			BufferedImage material = palette.repaint(source, params.get(family.prefix() + "_spread"), params.get("max_gain"), params.getInt("levels"));
			for (String face : FACES) derived.put("block/" + family.prefix() + "_" + face, Ops.copy(material));
		}
		return derived;
	}
}
