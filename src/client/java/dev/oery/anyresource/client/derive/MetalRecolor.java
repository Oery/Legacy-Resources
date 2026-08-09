package dev.oery.anyresource.client.derive;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Recolours a set of the pack's textures of one metal into the matching set in another. Subclassed per
 * set: {@link NetheriteArmor}, {@link NetheriteTools}, {@link CopperArmor}, {@link CopperTools}.
 * <p>
 * Both pairs vanilla ships are the same artwork twice, which is what makes the transform learnable
 * rather than something to eyeball. Comparing 26.2's files pixel for pixel, diamond and netherite agree
 * on 246 of 256 alpha values for the armour icons and 1953 of 2048 for the worn layer; iron and copper
 * agree on <em>every</em> one of them, across all nine icons and all three layers. What is left in each
 * case is palette. Netherite takes diamond's luminance ramp of roughly 30-255 and compresses it into
 * 18-110, at very low saturation with a warm shadow - diamond's darkest {@code (8, 37, 32)} becomes
 * {@code (23, 17, 17)}, its white highlight {@code (118, 106, 118)}. Copper barely compresses iron's at
 * all, 25-255 into 38-223, but swings it hard in saturation: 0.74 at the shadow down to 0.20 at the
 * highlight, at a hue that holds between 11 and 16 degrees the whole way up.
 * <p>
 * Rather than hard-coding those colours - which would produce vanilla's metal on top of a pack's
 * silhouette, throwing away the pack's own shading - the ramp is applied <em>relatively</em>: each
 * pixel's position within the pack's own luminance range is preserved, and only the range's endpoints,
 * its curve and its tint are set. A pack whose diamond armour is pale lilac and one whose diamond
 * armour is deep teal both come out as the same near-black metal, each keeping its own internal
 * contrast and detail.
 * <p>
 * The range is measured across <em>all</em> the set's pieces at once, not per texture, so a helmet and
 * a pair of boots stay in step with each other instead of each being stretched to fill the ramp alone.
 * By default every pixel goes through it, including a tool's wooden handle - which is what vanilla's
 * netherite does too, its handles landing on warm browns like {@code (115, 69, 67)} while the metal
 * goes neutral. A set whose metal does not do that overrides {@link #metal}; see {@link CopperTools}.
 * <p>
 * Unlike most derivations this one has no geometry, so it imposes no size or squareness requirement -
 * which it could not anyway, since the worn armour layers are 64x32.
 */
abstract class MetalRecolor implements Derivation {
	/** Source texture to the texture it produces. Subclasses should return a cached, ordered map. */
	protected abstract Map<String, String> pieces();

	/**
	 * Textures the set reads but produces nothing from - a reference the mask is measured against.
	 * <p>
	 * Kept apart from {@link #pieces()} rather than being a piece with no output, because everything
	 * else here treats a piece as something to level and recolour: a reference must stay out of the
	 * ramp's histogram, out of the outputs, and out of the recolour itself.
	 */
	protected List<String> references() {
		return List.of();
	}

	@Override
	public final List<String> sources() {
		List<String> sources = new ArrayList<>(pieces().keySet());
		sources.addAll(references());
		return List.copyOf(sources);
	}

	@Override
	public final List<String> outputs() {
		return List.copyOf(pieces().values());
	}

	/**
	 * The nine constants every subclass declares, and what each one does. The values themselves live
	 * in the subclasses - each set is tuned on its own, and no two of them agree.
	 * <p>
	 * Each set's list is written to be replaced wholesale by the lab's "Copy as Java" button, so keep
	 * these notes here rather than interleaving them with the numbers.
	 * <ul>
	 *   <li>{@code shadow}, {@code highlight} - the luminance the pack's darkest and brightest pixels
	 *   are remapped onto. Vanilla's own netherite spans 18 to 110 against diamond's 30 to 255, but both
	 *   netherite sets are tuned to a far lower highlight than that: real packs draw brighter, busier
	 *   diamond art than vanilla does, so the measured 110 reads as washed out across the corpus even
	 *   with auto-levelling pulling it down per pack. Copper's measured 38 to 223 survives tuning much
	 *   better, because copper is a bright metal and iron is drawn about as brightly as vanilla's.</li>
	 *   <li>{@code gamma} - shape of the ramp between them. Above 1 holds more of the piece in shadow,
	 *   which is what makes netherite read as heavy rather than merely dark.</li>
	 *   <li>{@code auto_level}, {@code target_mean} - see {@link #ramp}: brings this pack's highlight
	 *   down until its average lands on {@code target_mean}, which is what stops a pack with bright
	 *   source art producing a washed-out output. 0 disables it and hands control back to {@code gamma}.
	 *   The target is the vanilla metal's own mean.</li>
	 *   <li>{@code hue}, {@code saturation_shadow}, {@code saturation_highlight} - the tint, and how
	 *   much of it survives at each end of the ramp. Netherite is almost neutral, and what little cast
	 *   it has is violet: vanilla's brightest netherite pixel is {@code (118, 106, 118)}, which is
	 *   roughly where both netherite sets' hue sits. Copper is the opposite case - saturated at the
	 *   shadow and washing out towards the highlight, which is what reads as polished metal.</li>
	 *   <li>{@code keep_hue} - how much of the pack's own hue to keep instead of the tint above. 0
	 *   gives every pack the same metal whatever colour its source set is; holding some of it back keeps
	 *   each pack recognisably itself - a red set stays faintly warm, a blue one faintly cool - which is
	 *   the point of deriving from the pack rather than shipping art. It also costs a little against the
	 *   vanilla control, necessarily: vanilla has no pack hue to keep. How much a set can afford depends
	 *   on how nameable its metal is: netherite holds a little under half, while copper, which stops
	 *   being copper the moment it drifts, keeps almost none.</li>
	 * </ul>
	 */
	@Override
	public abstract List<Param> params();

	/**
	 * Which of a piece's pixels the recolour applies to; the rest are copied through untouched.
	 * <p>
	 * Asked per pixel rather than per colour, because the two are not the same question. A pack that
	 * paints its handle and part of its blade in one grey has that grey as metal in one place and not in
	 * the other, and a mask that has to answer for the colour as a whole gets one of them wrong: judged
	 * by colour, 13 of the corpus's 70 packs bled copper into the handle and Occult lost its handle
	 * entirely.
	 */
	@FunctionalInterface
	protected interface Metal {
		/**
		 * @param source the {@link #pieces()} key the pixel came from
		 * @param index  its index within that image
		 */
		boolean test(String source, int index, int argb);
	}

	/**
	 * The default: every pixel, which is the whole-piece recolour both netherite sets want.
	 * <p>
	 * A subclass that overrides this must answer all-or-nothing when it cannot tell. A mask that is
	 * <em>partly</em> right speckles a tool with two metals, which is worse than either answer given
	 * consistently.
	 *
	 * @param sources every {@link #sources()} that resolved, including {@link #references()}
	 */
	protected Metal metal(Map<String, BufferedImage> sources, Params params) {
		return (source, index, argb) -> true;
	}

	@Override
	public final Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		Metal metal = metal(sources, params);
		Map<String, BufferedImage> pieces = new LinkedHashMap<>();
		for (String source : pieces().keySet()) {
			BufferedImage image = sources.get(source);
			if (image != null) {
				pieces.put(source, image);
			}
		}
		// A mask that leaves nothing to recolour reaches ramp() with an empty histogram, which declines
		// the whole set rather than emitting the untouched source under a copper name.
		Ramp ramp = ramp(pieces, metal, params);
		if (ramp == null) {
			return Map.of();
		}
		Map<String, BufferedImage> derived = new LinkedHashMap<>();
		pieces().forEach((source, output) -> {
			BufferedImage image = pieces.get(source);
			if (image != null) {
				derived.put(output, recolor(source, image, ramp, metal, params));
			}
		});
		return derived;
	}

	/**
	 * The luminance mapping for this pack: the span the set occupies, and the highlight that lands it
	 * at the requested average brightness.
	 * <p>
	 * Measured across all the pieces at once, so they stay in step with each other rather than each
	 * being stretched and levelled on its own - and over the masked-in pixels only, so that what the
	 * recolour never touches cannot set the range it is stretched over either.
	 *
	 * @return {@code null} if the pack's set is a single flat colour, which has no ramp to remap
	 */
	private static @Nullable Ramp ramp(Map<String, BufferedImage> images, Metal metal, Params params) {
		// A 256-bin histogram rather than the pixels themselves: meanPosition walks the whole
		// population, and an HD pack's textures run to millions of pixels but still only 256 distinct
		// luminances.
		long[] histogram = new long[256];
		for (Map.Entry<String, BufferedImage> entry : images.entrySet()) {
			int[] pixels = Ops.pixels(entry.getValue());
			for (int i = 0; i < pixels.length; i++) {
				if (Ops.alpha(pixels[i]) != 0 && metal.test(entry.getKey(), i, pixels[i])) {
					histogram[Ops.luminance(pixels[i])]++;
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

	private static BufferedImage recolor(String path, BufferedImage source, Ramp ramp, Metal metal, Params params) {
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
			if (!metal.test(path, i, argb)) {
				out[i] = argb;
				continue;
			}
			double position = ramp.position(Ops.luminance(argb));
			double saturation = saturationShadow + (saturationHighlight - saturationShadow) * position;
			float[] hsb = Color.RGBtoHSB(Ops.red(argb), Ops.green(argb), Ops.blue(argb), null);
			out[i] = Ops.withAlpha(
				Ops.atLuminance(blendHue(hsb[0], hue, keepHue), saturation, ramp.luminance(position)),
				Ops.alpha(argb)
			);
		}
		return Ops.image(out, source.getWidth(), source.getHeight());
	}

	/** Interpolates around the colour wheel the short way, so a blend never sweeps through the spectrum. */
	private static double blendHue(double from, double to, double weight) {
		double difference = from - to;
		difference -= Math.floor(difference + 0.5);
		double hue = to + difference * weight;
		return hue - Math.floor(hue);
	}

	/**
	 * The four armour icons plus the three layers drawn on the player, {@code from} metal to {@code to}.
	 * <p>
	 * The {@code entity/equipment/**} entries read as modern paths, but a legacy pack has no such files
	 * - {@code LegacyPackResources} resolves them back to the pre-1.13
	 * {@code textures/models/armor/<metal>_layer_*.png}, so the derivation gets the pack's real art
	 * without knowing anything about the old layout. {@code humanoid} and {@code humanoid_baby} both
	 * come from {@code _layer_1}, which is why they are separate outputs fed by separate (identical)
	 * sources.
	 */
	protected static Map<String, String> armor(String from, String to) {
		Map<String, String> map = new LinkedHashMap<>(pieces("item/", from, to, "helmet", "chestplate", "leggings", "boots"));
		for (String layer : new String[] { "humanoid", "humanoid_baby", "humanoid_leggings" }) {
			map.put("entity/equipment/" + layer + "/" + from, "entity/equipment/" + layer + "/" + to);
		}
		return Collections.unmodifiableMap(map);
	}

	/** The five tool icons, {@code from} metal to {@code to}. */
	protected static Map<String, String> tools(String from, String to) {
		return pieces("item/", from, to, "sword", "pickaxe", "axe", "shovel", "hoe");
	}

	/**
	 * Builds a {@link #pieces()} map from stems, pairing {@code <directory>/<from>_<stem>} with
	 * {@code <directory>/<to>_<stem>}.
	 * <p>
	 * Insertion-ordered, unlike {@code Map.copyOf}, so a set lists in a sensible order in the lab.
	 */
	protected static Map<String, String> pieces(String directory, String from, String to, String... stems) {
		Map<String, String> map = new LinkedHashMap<>();
		for (String stem : stems) {
			map.put(directory + from + "_" + stem, directory + to + "_" + stem);
		}
		return Collections.unmodifiableMap(map);
	}
}
