package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deepslate and the ores embedded in it, made from the pack's stone and ore art.
 *
 * <p>Every legacy ore is its corresponding stone texture with the mineral painted over selected
 * pixels.  That relationship survives in resource packs much more reliably than a particular ore
 * palette: comparing an ore to the pack's own stone therefore identifies the host material without
 * mistaking a blue lapis or a black coal vein for something to repaint.  The resulting deepslate ore
 * keeps the pack's vein intact and changes only the inferred stone pixels.</p>
 */
final class Deepslate implements Derivation {
	private record Ore(String source, String output, boolean copper) {
	}

	private static final String STONE = "block/stone";
	private static final String DEEPSLATE = "block/deepslate";
	private static final String DEEPSLATE_TOP = "block/deepslate_top";
	private static final String COPPER_ORE = "block/copper_ore";
	private static final List<Ore> ORES = List.of(
		new Ore("block/coal_ore", "block/deepslate_coal_ore", false),
		new Ore("block/iron_ore", "block/deepslate_iron_ore", false),
		new Ore("block/gold_ore", "block/deepslate_gold_ore", false),
		new Ore("block/redstone_ore", "block/deepslate_redstone_ore", false),
		new Ore("block/lapis_ore", "block/deepslate_lapis_ore", false),
		new Ore("block/diamond_ore", "block/deepslate_diamond_ore", false),
		new Ore("block/emerald_ore", "block/deepslate_emerald_ore", false),
		// Copper did not exist in 1.8.9. Iron is its closest legacy material: both are pale metal
		// veins, so its pixels supply the pack-specific shape and shading before the copper repaint.
		new Ore("block/iron_ore", "block/deepslate_copper_ore", true)
	);

	@Override
	public String id() {
		return "deepslate";
	}

	@Override
	public List<String> sources() {
		return List.of(
			STONE, "block/coal_ore", "block/iron_ore", "block/gold_ore", "block/redstone_ore",
			"block/lapis_ore", "block/diamond_ore", "block/emerald_ore"
		);
	}

	@Override
	public List<String> outputs() {
		return List.of(
			DEEPSLATE, DEEPSLATE_TOP, COPPER_ORE, "block/deepslate_coal_ore", "block/deepslate_iron_ore", "block/deepslate_gold_ore",
			"block/deepslate_redstone_ore", "block/deepslate_lapis_ore", "block/deepslate_diamond_ore",
			"block/deepslate_emerald_ore", "block/deepslate_copper_ore"
		);
	}

	@Override
	public List<Param> params() {
		return List.of(
			// Tuned against real packs: retain a much wider slice of the pack's stone contrast while
			// landing on deepslate's dark blue-grey material band.
			Param.of("hue", 0, 1, .667), Param.of("saturation", 0, 1, .039),
			Param.of("brightness", 0, 255, 40.8), Param.of("spread", 0, 64, 64),
			Param.of("max_gain", .5, 4, .78), Param.ofInt("levels", 0, 8, 0),
			// Stone and ore pixels are byte-identical in vanilla. A little slack accepts pack-side
			// antialiasing or a minor resave difference, while still being far below an ore vein.
			Param.ofInt("host_difference", 0, 64, 8), Param.of("min_host_fraction", .25, .95, .25),
			// Many packs shade the stone surrounding a vein without changing its material colour. It
			// should become deepslate too; this wider tolerance accepts only neutral-grey differences,
			// leaving a mineral's coloured pixels alone.
			Param.ofInt("shadow_difference", 0, 128, 64), Param.ofInt("shadow_chroma", 0, 64, 16),
			// The copper vein's modern material band. Its shape and luminance structure remain iron's.
			Param.of("copper_hue", 0, 1, .065), Param.of("copper_saturation", 0, 1, .61),
			Param.of("copper_brightness", 0, 255, 137), Param.of("copper_spread", 0, 64, 25)
		);
	}

	@Override
	public Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		BufferedImage stone = sources.get(STONE);
		if (stone == null || Ops.scaleOf(stone) == 0) {
			return Map.of();
		}
		Map<String, BufferedImage> derived = new LinkedHashMap<>();
		// Vanilla uses a separate top sprite, but it is the same material. Legacy packs only supply
		// one stone texture, so using this exact derived side keeps both faces pack-consistent.
		BufferedImage deepslate = repaint(stone, null, palette(params), params.get("spread"), params);
		derived.put(DEEPSLATE, deepslate);
		derived.put(DEEPSLATE_TOP, deepslate);
		for (Ore ore : ORES) {
			BufferedImage source = sources.get(ore.source());
			if (source == null || Ops.scaleOf(source) == 0) {
				continue;
			}
			boolean[] host = hostMask(
				stone, source, params.getInt("host_difference"), params.getInt("shadow_difference"), params.getInt("shadow_chroma")
			);
			if (hostFraction(host) < params.get("min_host_fraction")) {
				continue;
			}
			if (ore.copper()) {
				// A regular copper ore is simply the legacy stone host with iron's pack-specific vein
				// shape repainted. Do this before replacing the host for its deepslate sibling.
				derived.put(COPPER_ORE, repaint(source, invert(host), copperPalette(params), params.get("copper_spread"), params));
			}
			BufferedImage derivedOre = repaint(source, host, palette(params), params.get("spread"), params);
			if (ore.copper()) {
				derivedOre = repaint(derivedOre, invert(host), copperPalette(params), params.get("copper_spread"), params);
			}
			derived.put(ore.output(), derivedOre);
		}
		return derived;
	}

	private static Palette palette(Params params) {
		return new Palette(params.get("hue"), params.get("saturation"), params.get("brightness"));
	}

	private static Palette copperPalette(Params params) {
		return new Palette(params.get("copper_hue"), params.get("copper_saturation"), params.get("copper_brightness"));
	}

	/** Repaints only selected opaque pixels, measuring their luminance range independently. */
	private static BufferedImage repaint(BufferedImage source, boolean[] selected, Palette palette, double spread, Params params) {
		int[] pixels = Ops.pixels(source);
		double sum = 0;
		double squares = 0;
		int count = 0;
		for (int i = 0; i < pixels.length; i++) {
			if ((selected == null || selected[i]) && Ops.alpha(pixels[i]) != 0) {
				double luminance = Ops.luminance(pixels[i]);
				sum += luminance;
				squares += luminance * luminance;
				count++;
			}
		}
		if (count == 0) {
			return Ops.copy(source);
		}
		double mean = sum / count;
		double deviation = Math.sqrt(Math.max(0, squares / count - mean * mean));
		double gain = deviation == 0 ? 0 : Math.min(spread / deviation, params.get("max_gain"));
		double step = params.getInt("levels") <= 0 ? 0 : 4 * spread / params.getInt("levels");
		int[] out = pixels.clone();
		for (int i = 0; i < out.length; i++) {
			if ((selected == null || selected[i]) && Ops.alpha(out[i]) != 0) {
				double luminance = palette.mean() + (Ops.luminance(out[i]) - mean) * gain;
				if (step > 0) {
					luminance = palette.mean() + Math.round((luminance - palette.mean()) / step) * step;
				}
				out[i] = Ops.withAlpha(Ops.atLuminance(palette.hue(), palette.saturation(), luminance), Ops.alpha(out[i]));
			}
		}
		return Ops.image(out, source.getWidth(), source.getHeight());
	}

	private static boolean[] hostMask(BufferedImage stone, BufferedImage ore, int difference, int shadowDifference, int shadowChroma) {
		BufferedImage alignedStone = Ops.resizeNearest(stone, ore.getWidth());
		int[] stonePixels = Ops.pixels(alignedStone);
		int[] orePixels = Ops.pixels(ore);
		boolean[] host = new boolean[orePixels.length];
		int limit = difference * difference;
		int shadowLimit = shadowDifference * shadowDifference;
		for (int i = 0; i < host.length; i++) {
			int a = orePixels[i];
			int b = stonePixels[i];
			int red = Ops.red(a) - Ops.red(b);
			int green = Ops.green(a) - Ops.green(b);
			int blue = Ops.blue(a) - Ops.blue(b);
			int distance = red * red + green * green + blue * blue;
			int chroma = Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue));
			host[i] = Ops.alpha(a) != 0 && Ops.alpha(b) != 0 && (
				distance <= limit || distance <= shadowLimit && chroma <= shadowChroma
			);
		}
		return host;
	}

	private static double hostFraction(boolean[] host) {
		int count = 0;
		for (boolean value : host) {
			if (value) count++;
		}
		return (double) count / host.length;
	}

	private static boolean[] invert(boolean[] mask) {
		boolean[] inverted = new boolean[mask.length];
		for (int i = 0; i < mask.length; i++) {
			inverted[i] = !mask[i];
		}
		return inverted;
	}
}
