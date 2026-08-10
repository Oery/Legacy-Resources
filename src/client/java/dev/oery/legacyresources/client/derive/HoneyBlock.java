package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The honey block's three faces, derived from the pack's own slime block.
 * <p>
 * Slime is the right source because the two blocks are the same idea drawn twice: a translucent gel
 * cube, mottled rather than patterned, with a shell you can see into. Vanilla's own art says as much.
 * Neither block has any structure to speak of - the honey faces correlate with the slime block at
 * |r| &lt; 0.10 and with <em>each other</em> at no more than 0.27, so there is nothing to reproduce
 * geometrically, no drip or seam or directional motif. What separates them is entirely palette and
 * transparency, and both of those are flat constants that can simply be read off:
 * <ul>
 *   <li>{@code honey_block_top} and {@code honey_block_side} are all but the same band - hue 40.1 and
 *   40.6 degrees, saturation 0.79 and 0.77, mean luminance 189.7 and 192.3, and the identical
 *   luminance range of 162.6 to 225.0. They are two shufflings of one palette.</li>
 *   <li>{@code honey_block_bottom} is that palette darkened to a mean of 157, pushed to 0.93
 *   saturation, and made markedly more transparent. It is the outer shell rather than a face: the
 *   model wraps all six sides of the 16x16 cube in it and paints the 14x14 core inside with the other
 *   two, which is why it alone is drawn to be seen through.</li>
 *   <li>Alpha is one value per texture, not a pattern - 191, 189 and 129 against the slime block's
 *   180.</li>
 * </ul>
 * <p>
 * So the pack's slime supplies the mottle and {@link Palette} moves it onto each band, the same
 * operation {@link ConcretePowder} performs with sand. The three faces come out sharing one pattern,
 * since there is only one source to take it from - which is what the pack itself does, its slime block
 * being a single texture worn on all six sides.
 * <p>
 * <b>Colour is absolute and transparency is relative</b>, which is the one asymmetry here worth
 * stating. Honey is amber in every pack, so the band is vanilla's own and {@code follow_source} only
 * lets it drift towards the pack's brightness; but how far through its gel a pack lets you see is a
 * style the pack is entitled to - 2 of the 63 packs with slime art draw it fully opaque and one draws
 * it at alpha 130 - so alpha is carried as a <em>ratio</em> of the pack's own and no absolute value is
 * imposed.
 */
final class HoneyBlock implements Derivation {
	private static final String SLIME = "block/slime_block";

	/**
	 * Mean luminance of 1.8.9's own slime block, which {@code follow_source} measures a pack's against.
	 * <p>
	 * The legacy figure rather than 26.2's 167.7, because a legacy pack is drawing a variant of the
	 * block it shipped beside, not of the one that replaced it.
	 */
	private static final double LEGACY_SLIME_MEAN = 176.0;

	/** Alpha of 26.2's slime block, which the faces' alpha below is measured as a ratio of. */
	private static final double MODERN_SLIME_ALPHA = 180;

	private static final List<Face> FACES = List.of(
		new Face("top", 40.1, 0.792, 189.7, 18.1, 191 / MODERN_SLIME_ALPHA),
		new Face("side", 40.6, 0.769, 192.3, 18.6, 189 / MODERN_SLIME_ALPHA),
		new Face("bottom", 34.3, 0.927, 157.0, 19.7, 129 / MODERN_SLIME_ALPHA)
	);

	/**
	 * One face of vanilla's own honey block, measured off {@code reference/26.2}.
	 *
	 * @param hue        in degrees
	 * @param saturation 0-1
	 * @param mean       luminance the face averages to, 0-255
	 * @param spread     standard deviation of that luminance
	 * @param alpha      how transparent the face is <em>relative to the slime block</em>, so that a pack
	 *                   drawing opaque slime gets opaque honey
	 */
	private record Face(String name, double hue, double saturation, double mean, double spread, double alpha) {
		String texture() {
			return "block/honey_block_" + name;
		}
	}

	@Override
	public String id() {
		return "honey_block";
	}

	@Override
	public List<String> sources() {
		return List.of(SLIME);
	}

	@Override
	public List<String> outputs() {
		return FACES.stream().map(Face::texture).toList();
	}

	@Override
	public List<Param> params() {
		return List.of(
			// Multiplier on each face's measured standard deviation - how much of the pack's own mottle
			// survives. 1 reproduces vanilla's contrast, which is roughly twice the slime block's own
			// (18-20 against 9.8), so the median pack is amplified rather than flattened here.
			Param.of("spread_scale", 0, 3, 1.0),
			// Ceiling on the gain reaching that spread may ask for, since amplifying a near-flat gel
			// turns a resave artefact into speckle; see Palette#repaint. Set above DirtPath's 1.5
			// because the honest gain here is around 1.9 and capping at 1.5 would flatten every pack.
			Param.of("max_gain", 0.5, 4, 2.2),
			Param.of("saturation_scale", 0, 2, 1.0),
			// How far the band's brightness tracks the pack's own slime rather than holding vanilla's
			// absolute value; the same trade ConcreteBlock makes, and the same default.
			Param.of("follow_source", 0, 1, 0.35),
			// Multiplier on the face alpha ratios above. 1 keeps vanilla's relationship between the
			// three faces and the pack's gel; below 1 makes the whole block more see-through.
			Param.of("alpha_scale", 0, 2, 1.0),
			Param.ofInt("levels", 0, 8, 0)
		);
	}

	@Override
	public Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		BufferedImage slime = sources.get(SLIME);
		// Non-square slime is an animation strip, and one that is not a whole multiple of 16 - the 8x8
		// one pack ships - is not art this can read. There is no second source to fall back on.
		if (slime == null || Ops.scaleOf(slime) == 0) {
			return Map.of();
		}
		double mean = Ops.meanLuminance(slime);
		// A wholly transparent slime block has no brightness to follow and no mottle to repaint.
		if (mean <= 0) {
			return Map.of();
		}
		double brightness = 1 + params.get("follow_source") * (mean / LEGACY_SLIME_MEAN - 1);
		double saturationScale = params.get("saturation_scale");
		double spreadScale = params.get("spread_scale");
		double maxGain = params.get("max_gain");
		double alphaScale = params.get("alpha_scale");
		int levels = params.getInt("levels");

		Map<String, BufferedImage> derived = new LinkedHashMap<>();
		for (Face face : FACES) {
			Palette palette = new Palette(face.hue() / 360.0, face.saturation() * saturationScale, face.mean() * brightness);
			BufferedImage repainted = palette.repaint(slime, face.spread() * spreadScale, maxGain, levels);
			derived.put(face.texture(), scaleAlpha(repainted, face.alpha() * alphaScale));
		}
		return derived;
	}

	/**
	 * {@code image} with every pixel's alpha multiplied by {@code scale}.
	 * <p>
	 * Multiplied rather than replaced, so that a pack drawing alpha into its gel - 6 of the 63 vary it
	 * across the texture - keeps whatever it drew, only more or less of it.
	 */
	private static BufferedImage scaleAlpha(BufferedImage image, double scale) {
		int[] pixels = Ops.pixels(image);
		for (int i = 0; i < pixels.length; i++) {
			pixels[i] = Ops.withAlpha(pixels[i], (int) Math.round(Ops.alpha(pixels[i]) * scale));
		}
		return Ops.image(pixels, image.getWidth(), image.getHeight());
	}
}
