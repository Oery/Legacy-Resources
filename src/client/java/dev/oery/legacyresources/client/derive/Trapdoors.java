package dev.oery.legacyresources.client.derive;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wood-specific trapdoors from the pack's oak-trapdoor construction and each wood's own planks.
 *
 * <p>Pre-flattening packs have one wooden trapdoor, but do have six distinct plank palettes. The
 * common trapdoor supplies the joinery and cut-out shape; its material band is read from each
 * target wood's planks, including planks produced by the existing cherry, mangrove, pale-oak and
 * nether-wood derivations. A missing template or matching planks declines just that target.</p>
 */
final class Trapdoors implements Derivation {
	private record Wood(String name) {
		String planks() { return "block/" + name + "_planks"; }
		String output() { return "block/" + name + "_trapdoor"; }
	}

	private static final String TEMPLATE = "block/oak_trapdoor";
	private static final List<Wood> WOODS = List.of(
		new Wood("spruce"), new Wood("birch"), new Wood("jungle"), new Wood("acacia"), new Wood("dark_oak"),
		new Wood("mangrove"), new Wood("cherry"), new Wood("crimson"), new Wood("warped"), new Wood("pale_oak")
	);

	@Override public String id() { return "wood_trapdoors"; }
	@Override public List<String> sources() {
		return java.util.stream.Stream.concat(java.util.stream.Stream.of(TEMPLATE), WOODS.stream().map(Wood::planks)).toList();
	}
	@Override public List<String> outputs() { return WOODS.stream().map(Wood::output).toList(); }

	@Override
	public List<Param> params() {
		return List.of(
			Param.of("brightness_scale", .5, 1.5, .92), Param.of("saturation_scale", 0, 2, 1.05),
			Param.of("spread", 0, 64, 18), Param.of("max_gain", .5, 4, 2), Param.ofInt("levels", 0, 8, 0)
		);
	}

	@Override
	public Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		BufferedImage template = sources.get(TEMPLATE);
		if (template == null || Ops.scaleOf(template) == 0) return Map.of();
		Map<String, BufferedImage> derived = new LinkedHashMap<>();
		for (Wood wood : WOODS) {
			BufferedImage planks = sources.get(wood.planks());
			if (planks == null || Ops.meanLuminance(planks) <= 0) continue;
			double[] colour = averageColour(planks);
			if (colour == null) continue;
			Palette palette = new Palette(colour[0], Math.min(1, colour[1] * params.get("saturation_scale")),
				Ops.meanLuminance(planks) * params.get("brightness_scale"));
			derived.put(wood.output(), palette.repaint(template, params.get("spread"), params.get("max_gain"), params.getInt("levels")));
		}
		return derived;
	}

	/** Average opaque RGB, then its HSB colour direction; transparent pixels are not material. */
	private static double[] averageColour(BufferedImage image) {
		long red = 0, green = 0, blue = 0, count = 0;
		for (int pixel : Ops.pixels(image)) {
			if (Ops.alpha(pixel) == 0) continue;
			red += Ops.red(pixel);
			green += Ops.green(pixel);
			blue += Ops.blue(pixel);
			count++;
		}
		if (count == 0) return null;
		float[] hsb = Color.RGBtoHSB((int) (red / count), (int) (green / count), (int) (blue / count), null);
		return new double[] { hsb[0], hsb[1] };
	}
}
