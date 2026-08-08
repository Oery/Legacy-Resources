package dev.oery.anyresource.client.derive;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Recolours a set of the pack's diamond textures into the matching netherite ones. Subclassed per set:
 * {@link NetheriteArmor}, {@link NetheriteTools}.
 * <p>
 * Vanilla's own two sets are the same artwork twice: comparing 26.2's diamond and netherite files
 * pixel for pixel, the silhouettes agree on 246 of 256 pixels for the armour icons and 1953 of 2048 for
 * the worn layer, so the only real difference is palette. That makes the transform learnable, and what
 * it turns out to be is simple: netherite takes diamond's luminance ramp of roughly 30-255 and
 * compresses it into 18-110, at very low saturation with a warm shadow. Diamond's darkest
 * {@code (8, 37, 32)} becomes {@code (23, 17, 17)}; its white highlight becomes {@code (118, 106, 118)}.
 * <p>
 * Rather than hard-coding those colours - which would produce vanilla's netherite on top of a pack's
 * silhouette, throwing away the pack's own shading - the ramp is applied <em>relatively</em>: each
 * pixel's position within the pack's own diamond luminance range is preserved, and only the range's
 * endpoints, its curve and its tint are set. A pack whose diamond armour is pale lilac and one whose
 * diamond armour is deep teal both come out as the same near-black metal, each keeping its own
 * internal contrast and detail.
 * <p>
 * The range is measured across <em>all</em> the set's pieces at once, not per texture, so a helmet and
 * a pair of boots stay in step with each other instead of each being stretched to fill the ramp alone.
 * Every pixel goes through it, including a tool's wooden handle - which is what vanilla does too, its
 * handles landing on warm browns like {@code (115, 69, 67)} while the metal goes neutral.
 * <p>
 * Unlike most derivations this one has no geometry, so it imposes no size or squareness requirement -
 * which it could not anyway, since the worn armour layers are 64x32.
 */
abstract class NetheriteRecolor implements Derivation {
	/** Source texture to the texture it produces. Subclasses should return a cached, ordered map. */
	protected abstract Map<String, String> pieces();

	@Override
	public final List<String> sources() {
		return List.copyOf(pieces().keySet());
	}

	@Override
	public final List<String> outputs() {
		return List.copyOf(pieces().values());
	}

	/**
	 * The nine constants every subclass declares, and what each one does. The values themselves live
	 * in the subclasses - armour and tools are tuned separately, and they do not agree.
	 * <p>
	 * Each set's list is written to be replaced wholesale by the lab's "Copy as Java" button, so keep
	 * these notes here rather than interleaving them with the numbers.
	 * <ul>
	 *   <li>{@code shadow}, {@code highlight} - the luminance the pack's darkest and brightest pixels
	 *   are remapped onto. Vanilla's own netherite spans 18 to 110 against diamond's 30 to 255, but
	 *   both sets are tuned to a far lower highlight than that: real packs draw brighter, busier
	 *   diamond art than vanilla does, so the measured 110 reads as washed out across the corpus even
	 *   with auto-levelling pulling it down per pack.</li>
	 *   <li>{@code gamma} - shape of the ramp between them. Above 1 holds more of the piece in shadow,
	 *   which is what makes netherite read as heavy rather than merely dark.</li>
	 *   <li>{@code auto_level}, {@code target_mean} - see {@link #ramp}: brings this pack's highlight
	 *   down until its average lands on {@code target_mean}, which is what stops a pack with bright
	 *   diamond art producing washed-out netherite. 0 disables it and hands control back to
	 *   {@code gamma}. The target is vanilla netherite's own mean.</li>
	 *   <li>{@code hue}, {@code saturation_shadow}, {@code saturation_highlight} - the tint, and how
	 *   much of it survives at each end of the ramp. Netherite is almost neutral, and what little cast
	 *   it has is violet: vanilla's brightest netherite pixel is {@code (118, 106, 118)}, which is
	 *   roughly where both sets' hue sits.</li>
	 *   <li>{@code keep_hue} - how much of the pack's own hue to keep instead of the tint above. 0
	 *   gives every pack the same neutral metal whatever colour its diamond set is; holding a little
	 *   under half keeps each pack recognisably itself - a red set stays faintly warm, a blue one
	 *   faintly cool - which is the point of deriving from the pack rather than shipping art. It also
	 *   costs a little against the vanilla control, necessarily: vanilla has no pack hue to keep.</li>
	 * </ul>
	 */
	@Override
	public abstract List<Param> params();

	@Override
	public final Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		Ramp ramp = ramp(sources.values(), params);
		if (ramp == null) {
			return Map.of();
		}
		Map<String, BufferedImage> derived = new LinkedHashMap<>();
		pieces().forEach((source, output) -> {
			BufferedImage image = sources.get(source);
			if (image != null) {
				derived.put(output, recolor(image, ramp, params));
			}
		});
		return derived;
	}

	/**
	 * The luminance mapping for this pack: the span the set occupies, and the highlight that lands it
	 * at the requested average brightness.
	 * <p>
	 * Measured across all the pieces at once, so they stay in step with each other rather than each
	 * being stretched and levelled on its own.
	 *
	 * @return {@code null} if the pack's set is a single flat colour, which has no ramp to remap
	 */
	private static @Nullable Ramp ramp(Iterable<BufferedImage> images, Params params) {
		// A 256-bin histogram rather than the pixels themselves: meanPosition walks the whole
		// population, and an HD pack's textures run to millions of pixels but still only 256 distinct
		// luminances.
		long[] histogram = new long[256];
		for (BufferedImage image : images) {
			for (int argb : Ops.pixels(image)) {
				if (Ops.alpha(argb) != 0) {
					histogram[Ops.luminance(argb)]++;
				}
			}
		}
		int min = 0;
		while (min < histogram.length && histogram[min] == 0) {
			min++;
		}
		int max = histogram.length - 1;
		while (max >= 0 && histogram[max] == 0) {
			max--;
		}
		if (max <= min) {
			return null;
		}

		double exponent = params.get("gamma");
		double shadow = params.get("shadow");
		double highlight = params.get("highlight");
		double autoLevel = params.get("auto_level");
		if (autoLevel <= 0) {
			return new Ramp(min, max, exponent, shadow, highlight);
		}

		// Where this pack's set sits within its own range, on average. A pack drawn mostly in
		// bright pixels lands near 1 and would otherwise produce netherite of that same average
		// brightness; a mostly-dark one lands near 0 and needs no help.
		double meanPosition = meanPosition(histogram, min, max, exponent);
		if (meanPosition <= 0) {
			return new Ramp(min, max, exponent, shadow, highlight);
		}
		// The highlight at which this pack's average output luminance is exactly target_mean.
		// Curve-shaping cannot achieve this on its own: a pack whose diamond art has a large flat
		// bright area has those pixels *at* the top of its range, and no exponent moves a value that
		// is already 1 - only bringing the endpoint down does.
		double levelled = shadow + (params.get("target_mean") - shadow) / meanPosition;
		// Only ever darker. A pack already below the target is not brightened up to meet it - that
		// would undo the deliberately heavy look on exactly the packs that got it right.
		double capped = Math.min(highlight, levelled);
		return new Ramp(min, max, exponent, shadow, highlight + (capped - highlight) * autoLevel);
	}

	/** Average of {@code position^exponent} over every pixel the histogram counts. */
	private static double meanPosition(long[] histogram, int min, int max, double exponent) {
		double total = 0;
		double weighted = 0;
		for (int luminance = min; luminance <= max; luminance++) {
			if (histogram[luminance] == 0) {
				continue;
			}
			total += histogram[luminance];
			weighted += histogram[luminance] * Math.pow((double) (luminance - min) / (max - min), exponent);
		}
		return total == 0 ? 0 : weighted / total;
	}

	/** The finished mapping: source luminance in, output luminance out. */
	private record Ramp(int min, int max, double exponent, double shadow, double highlight) {
		/** Where a source luminance falls along the curve, 0 at the pack's darkest and 1 at its brightest. */
		double position(int sourceLuminance) {
			double t = Math.clamp((double) (sourceLuminance - min) / (max - min), 0, 1);
			return Math.pow(t, exponent);
		}

		double luminance(double position) {
			return shadow + (highlight - shadow) * position;
		}
	}

	private static BufferedImage recolor(BufferedImage source, Ramp ramp, Params params) {
		double hue = params.get("hue") / 360.0;
		double saturationShadow = params.get("saturation_shadow");
		double saturationHighlight = params.get("saturation_highlight");
		double keepHue = params.get("keep_hue");

		int[] pixels = Ops.pixels(source);
		int[] out = new int[pixels.length];
		for (int i = 0; i < pixels.length; i++) {
			int argb = pixels[i];
			if (Ops.alpha(argb) == 0) {
				out[i] = 0;
				continue;
			}
			double position = ramp.position(Ops.luminance(argb));
			double saturation = saturationShadow + (saturationHighlight - saturationShadow) * position;
			float[] hsb = Color.RGBtoHSB(Ops.red(argb), Ops.green(argb), Ops.blue(argb), null);
			out[i] = Ops.withAlpha(
				atLuminance(blendHue(hsb[0], hue, keepHue), saturation, ramp.luminance(position)),
				Ops.alpha(argb)
			);
		}
		return Ops.image(out, source.getWidth(), source.getHeight());
	}

	/**
	 * The fully bright form of {@code hue}/{@code saturation}, scaled down until its luminance is
	 * {@code target}.
	 * <p>
	 * Scaling a bright colour is what keeps the tint honest at low light levels. Feeding the target
	 * straight into HSB brightness would leave the saturation applied to a value that is already dark,
	 * and every shadow pixel would come out closer to flat black than the tint asks for.
	 */
	private static int atLuminance(double hue, double saturation, double target) {
		int bright = Color.HSBtoRGB((float) hue, (float) Math.clamp(saturation, 0, 1), 1);
		double luminance = Ops.luminance(bright);
		double scale = luminance <= 0 ? 0 : target / luminance;
		return Ops.argb(
			255,
			(int) Math.round(Ops.red(bright) * scale),
			(int) Math.round(Ops.green(bright) * scale),
			(int) Math.round(Ops.blue(bright) * scale)
		);
	}

	/** Interpolates around the colour wheel the short way, so a blend never sweeps through the spectrum. */
	private static double blendHue(double from, double to, double weight) {
		double difference = from - to;
		difference -= Math.floor(difference + 0.5);
		double hue = to + difference * weight;
		return hue - Math.floor(hue);
	}

	/**
	 * Builds a {@link #pieces()} map from stems, pairing {@code <directory>/diamond_<stem>} with
	 * {@code <directory>/netherite_<stem>}.
	 * <p>
	 * Insertion-ordered, unlike {@code Map.copyOf}, so a set lists in a sensible order in the lab.
	 */
	protected static Map<String, String> pieces(String directory, String... stems) {
		Map<String, String> map = new LinkedHashMap<>();
		for (String stem : stems) {
			map.put(directory + "diamond_" + stem, directory + "netherite_" + stem);
		}
		return Collections.unmodifiableMap(map);
	}
}
