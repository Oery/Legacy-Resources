package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;

/**
 * A band of colour to repaint a texture onto: one hue, one saturation, and the luminance the result
 * averages to.
 * <p>
 * The operation this exists for is {@link #repaint}: take the pack's own art for something, keep its
 * luminance <em>structure</em> - its grain, its noise, wherever it happens to be light and dark - and
 * move that structure wholesale onto a different material's colours. It is what lets a derivation
 * produce a texture the pack never drew that still looks like the pack drew it, rather than a tinted
 * copy of vanilla's.
 * <p>
 * Shared by {@link DirtPath}, which takes its band off the pack's own dirt and warms it, and
 * {@link ConcretePowder} and {@link Concrete}, whose bands are the sixteen dye colours measured out of
 * vanilla (see {@link ConcreteColor}).
 *
 * @param hue as a fraction of the colour wheel, the form {@link Ops#atLuminance} takes
 */
record Palette(double hue, double saturation, double mean) {
	/**
	 * {@code source}'s luminance structure, re-levelled onto this band and painted in its colour.
	 * <p>
	 * Levelled against the source's own mean and spread rather than a fixed range, which is what lets
	 * one operation serve sources of wholly different contrast: whatever a pack's grass top, its dirt or
	 * its sand measures at, all three come out on the band asked for, so faces derived from different
	 * sources still agree with each other.
	 *
	 * @param spread  the standard deviation the output should have, in luminance counts. 0 flattens the
	 *                source to a single colour
	 * @param maxGain ceiling on the amplification reaching {@code spread} may ask for. A near-flat
	 *                source blown up to meet a spread turns a faint gradient - or a resave artefact -
	 *                into speckle, and a flatter output than asked for is the better answer
	 * @param levels  optional posterisation of the finished band, in shades, or {@code <= 0} for none.
	 *                Right for 16x art and wrong for a 128x pack's gradients, so callers default it off
	 */
	BufferedImage repaint(BufferedImage source, double spread, double maxGain, int levels) {
		int[] pixels = Ops.pixels(source);
		double sourceMean = 0;
		double sourceSquares = 0;
		long count = 0;
		for (int argb : pixels) {
			if (Ops.alpha(argb) != 0) {
				double value = Ops.luminance(argb);
				sourceMean += value;
				sourceSquares += value * value;
				count++;
			}
		}
		double deviation = 0;
		if (count > 0) {
			sourceMean /= count;
			deviation = Math.sqrt(Math.max(0, sourceSquares / count - sourceMean * sourceMean));
		}
		// A flat source has no structure to re-level, and every pixel simply lands on the mean.
		double gain = deviation <= 0 ? 0 : Math.min(spread / deviation, maxGain);
		// The band runs roughly two deviations either side of the mean, so that is what the requested
		// number of shades divides up.
		double step = levels <= 0 ? 0 : 4 * spread / levels;

		int[] out = new int[pixels.length];
		for (int i = 0; i < pixels.length; i++) {
			int argb = pixels[i];
			if (Ops.alpha(argb) == 0) {
				out[i] = 0;
				continue;
			}
			double value = mean + (Ops.luminance(argb) - sourceMean) * gain;
			if (step > 0) {
				value = mean + Math.round((value - mean) / step) * step;
			}
			out[i] = Ops.withAlpha(
				Ops.atLuminance(hue, saturation, Math.clamp(value, 0, 255)),
				Ops.alpha(argb)
			);
		}
		return Ops.image(out, source.getWidth(), source.getHeight());
	}
}
