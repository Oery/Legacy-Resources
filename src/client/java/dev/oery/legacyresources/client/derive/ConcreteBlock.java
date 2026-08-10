package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The sixteen concrete blocks of one kind, painted out of the pack's own sand. Subclassed per kind:
 * {@link ConcretePowder}, {@link Concrete}.
 * <p>
 * Sand is the right source because concrete powder <em>is</em> sand that has been dyed - it falls like
 * sand, and vanilla draws it as the same kind of granular field, at a standard deviation of 6 to 14
 * against sand's 9.7. So the pack's own sand supplies the grain and {@link ConcreteColor} supplies the
 * band it is painted onto, which is the whole derivation: a 128x pack's finely stippled sand becomes
 * finely stippled concrete, and a 16x pack's four-shade sand becomes four-shade concrete.
 * <p>
 * All sixteen come from the one source, so a pack either gets the whole set or none of it - unlike the
 * brushing stages of a {@link SuspiciousBlock}, there is no way for one colour to be derivable and
 * another not.
 */
abstract class ConcreteBlock implements Derivation {
	private static final String SAND = "block/sand";

	/** The measured bands for this kind of block, i.e. one of {@link ConcreteColor}'s two tables. */
	protected abstract List<ConcreteColor> colors();

	/** Appended to the colour name to make the output stem, e.g. {@code _concrete_powder}. */
	protected abstract String suffix();

	@Override
	public final List<String> sources() {
		return List.of(SAND);
	}

	@Override
	public final List<String> outputs() {
		List<String> outputs = new ArrayList<>();
		for (ConcreteColor color : colors()) {
			outputs.add(texture(color));
		}
		return List.copyOf(outputs);
	}

	/**
	 * The five constants both kinds declare, and what each one does. The values live in the subclasses,
	 * since a granular powder and a block that is flat to within a deviation of 1 want different
	 * answers from every one of them.
	 * <ul>
	 *   <li>{@code spread_scale} - multiplier on the colour's measured standard deviation, i.e. how much
	 *   grain the finished block carries. 1 reproduces vanilla's own contrast.</li>
	 *   <li>{@code max_gain} - ceiling on the amplification reaching that spread may ask for. A pack with
	 *   nearly flat sand would otherwise have a resave artefact or a faint gradient blown up into
	 *   speckle across all sixteen blocks; see {@link Palette#repaint}.</li>
	 *   <li>{@code saturation_scale} - multiplier on the colour's measured saturation. The one knob that
	 *   is a matter of taste rather than measurement: vanilla's concrete is more saturated than most
	 *   16x packs draw anything else, and a pack of muted earth tones can want it pulled back.</li>
	 *   <li>{@code follow_source} - how far the band's brightness tracks the pack's own sand instead of
	 *   vanilla's absolute value. At 0 every pack gets vanilla's exact luminance, which is right in the
	 *   sense that white concrete is white in any pack; at 1 a pack whose sand is drawn dark gets
	 *   concrete darkened in the same proportion, which is right in the sense that it then belongs
	 *   beside the pack's other blocks. The default splits the difference, and cannot invert an
	 *   ordering - every colour moves by the same factor.</li>
	 *   <li>{@code levels} - optional posterisation, in shades, off by default. Right for 16x art and
	 *   wrong for a 128x pack's gradients, the same call {@link DirtPath} leaves to the lab.</li>
	 * </ul>
	 */
	@Override
	public abstract List<Param> params();

	@Override
	public final Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		BufferedImage sand = sources.get(SAND);
		// Non-square sand is an animation strip, and one that is not a multiple of 16 is not art this
		// can read; either way there is no second source to fall back on.
		if (sand == null || Ops.scaleOf(sand) == 0) {
			return Map.of();
		}
		double mean = meanLuminance(sand);
		// A wholly transparent sand has no brightness to follow and no structure to repaint.
		if (mean <= 0) {
			return Map.of();
		}
		double follow = params.get("follow_source");
		double brightness = 1 + follow * (mean / ConcreteColor.VANILLA_SAND_MEAN - 1);
		double saturationScale = params.get("saturation_scale");
		double spreadScale = params.get("spread_scale");
		double maxGain = params.get("max_gain");
		int levels = params.getInt("levels");

		Map<String, BufferedImage> derived = new LinkedHashMap<>();
		for (ConcreteColor color : colors()) {
			Palette palette = color.palette(saturationScale, brightness);
			derived.put(texture(color), palette.repaint(sand, color.spread() * spreadScale, maxGain, levels));
		}
		return derived;
	}

	private String texture(ConcreteColor color) {
		return "block/" + color.colour() + suffix();
	}

	/** Mean luminance over the opaque pixels, or 0 if there are none. */
	private static double meanLuminance(BufferedImage image) {
		double total = 0;
		long count = 0;
		for (int argb : Ops.pixels(image)) {
			if (Ops.alpha(argb) != 0) {
				total += Ops.luminance(argb);
				count++;
			}
		}
		return count == 0 ? 0 : total / count;
	}
}
