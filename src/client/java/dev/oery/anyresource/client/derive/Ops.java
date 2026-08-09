package dev.oery.anyresource.client.derive;

import java.awt.Color;
import java.awt.image.BufferedImage;
import org.jspecify.annotations.Nullable;

/**
 * Image primitives shared by {@link Derivation} implementations.
 * <p>
 * Deliberately small: it holds only what more than one derivation needs, or what is easy to get
 * subtly wrong (blurring across an alpha edge, blurring a tiling texture, mixing two sources a pack
 * happened to author at different resolutions). Anything specific to a single derivation belongs in
 * that derivation.
 */
public final class Ops {
	/** Texture size a derivation's measurements are authored in; see {@link Derivation}. */
	public static final int BASE_SIZE = 16;

	private Ops() {
	}

	/**
	 * The pack's resolution multiplier for {@code image}, or {@code 0} if this derivation should
	 * decline it.
	 * <p>
	 * Rejects anything non-square - which is how an animation strip presents itself
	 * ({@code LegacyPackResources.isValidLegacyTexture} makes the same call) - and anything that
	 * isn't a whole multiple of {@value #BASE_SIZE}, since every offset a derivation computes would
	 * otherwise land between pixels.
	 */
	public static int scaleOf(BufferedImage image) {
		int width = image.getWidth();
		if (width != image.getHeight() || width < BASE_SIZE || width % BASE_SIZE != 0) {
			return 0;
		}
		return width / BASE_SIZE;
	}

	public static BufferedImage blank(int width, int height) {
		return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
	}

	public static int[] pixels(BufferedImage image) {
		return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
	}

	public static BufferedImage image(int[] pixels, int width, int height) {
		BufferedImage out = blank(width, height);
		out.setRGB(0, 0, width, height, pixels, 0, width);
		return out;
	}

	/** An independent ARGB copy, so a derivation can paint over a source without mutating the cache. */
	public static BufferedImage copy(BufferedImage source) {
		return image(pixels(source), source.getWidth(), source.getHeight());
	}

	/**
	 * Nearest-neighbour resample to {@code size} x {@code size}.
	 * <p>
	 * Needed because a pack's textures are not all at one resolution: plenty ship 16x block art next
	 * to a 32x or 64x overlay (or the reverse), and a derivation combining two of them has to bring
	 * them onto a common grid first. Nearest-neighbour rather than anything smoothing - these are
	 * pixel-art sources, and interpolating invents colours that aren't in the pack's palette.
	 */
	public static BufferedImage resizeNearest(BufferedImage source, int size) {
		if (source.getWidth() == size && source.getHeight() == size) {
			return source;
		}
		int[] in = pixels(source);
		int inWidth = source.getWidth();
		int inHeight = source.getHeight();
		int[] out = new int[size * size];
		for (int y = 0; y < size; y++) {
			int sy = y * inHeight / size;
			for (int x = 0; x < size; x++) {
				out[y * size + x] = in[sy * inWidth + x * inWidth / size];
			}
		}
		return image(out, size, size);
	}

	/**
	 * Box-blurs {@code source} with a {@code (2 * radius + 1)}-wide kernel, twice - two box passes
	 * approximate a Gaussian closely enough at these sizes and stay integer-cheap.
	 * <p>
	 * Three details matter more than the kernel shape. Sampling <b>wraps</b> at the edges, because
	 * these are tiling block textures and clamping would leave a visible seam where the blur ran out
	 * of neighbours. The colour channels are <b>premultiplied</b> by alpha before being averaged and
	 * divided back out, since averaging straight colour drags the (arbitrary) RGB of transparent
	 * pixels into the result and fringes every soft edge. And each pass is <b>separable</b> with a
	 * running sum, so cost is independent of the radius: the naive form is fine at 16x but a 512x
	 * pack with a scaled-up radius would run into billions of samples.
	 *
	 * @param radius in pixels, already scaled to the pack's resolution; {@code <= 0} returns a copy
	 */
	public static BufferedImage boxBlur(BufferedImage source, int radius) {
		int width = source.getWidth();
		int height = source.getHeight();
		if (radius <= 0) {
			return copy(source);
		}
		int[] argb = pixels(source);
		int count = width * height;
		int[] a = new int[count];
		int[] r = new int[count];
		int[] g = new int[count];
		int[] b = new int[count];
		for (int i = 0; i < count; i++) {
			int pixel = argb[i];
			int alpha = alpha(pixel);
			a[i] = alpha;
			r[i] = red(pixel) * alpha;
			g[i] = green(pixel) * alpha;
			b[i] = blue(pixel) * alpha;
		}
		for (int pass = 0; pass < 2; pass++) {
			for (int[] channel : new int[][] { a, r, g, b }) {
				blurRows(channel, width, height, radius);
				blurColumns(channel, width, height, radius);
			}
		}
		int[] out = new int[count];
		for (int i = 0; i < count; i++) {
			int alpha = a[i];
			out[i] = alpha == 0 ? 0 : argb(alpha, r[i] / alpha, g[i] / alpha, b[i] / alpha);
		}
		return image(out, width, height);
	}

	/** One horizontal box pass over {@code channel}, in place, wrapping at both ends. */
	private static void blurRows(int[] channel, int width, int height, int radius) {
		int window = 2 * radius + 1;
		int[] row = new int[width];
		for (int y = 0; y < height; y++) {
			int base = y * width;
			System.arraycopy(channel, base, row, 0, width);
			long sum = 0;
			for (int dx = -radius; dx <= radius; dx++) {
				sum += row[Math.floorMod(dx, width)];
			}
			for (int x = 0; x < width; x++) {
				channel[base + x] = (int) (sum / window);
				sum -= row[Math.floorMod(x - radius, width)];
				sum += row[Math.floorMod(x + radius + 1, width)];
			}
		}
	}

	/** One vertical box pass over {@code channel}, in place, wrapping at both ends. */
	private static void blurColumns(int[] channel, int width, int height, int radius) {
		int window = 2 * radius + 1;
		int[] column = new int[height];
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				column[y] = channel[y * width + x];
			}
			long sum = 0;
			for (int dy = -radius; dy <= radius; dy++) {
				sum += column[Math.floorMod(dy, height)];
			}
			for (int y = 0; y < height; y++) {
				channel[y * width + x] = (int) (sum / window);
				sum -= column[Math.floorMod(y - radius, height)];
				sum += column[Math.floorMod(y + radius + 1, height)];
			}
		}
	}

	/**
	 * Darkens {@code base} by {@code overlay} the way Minecraft's own block-breaking overlay does -
	 * the overlay's colour multiplied in, weighted by its alpha and by {@code strength}.
	 * {@code base}'s own alpha is untouched, so a derivation cannot punch holes in an opaque block.
	 */
	public static int multiply(int base, int overlay, double strength) {
		double weight = alpha(overlay) / 255.0 * strength;
		if (weight <= 0) {
			return base;
		}
		return argb(
			alpha(base),
			mix(red(base), red(base) * red(overlay) / 255, weight),
			mix(green(base), green(base) * green(overlay) / 255, weight),
			mix(blue(base), blue(base) * blue(overlay) / 255, weight)
		);
	}

	/**
	 * Which way {@code argb} leans in colour, as a unit vector, or {@code null} if it is too near grey to
	 * lean anywhere.
	 * <p>
	 * The channels' distance from their own mean, which drops brightness entirely - a surface's pale
	 * highlight and its darkest fold are the same hue and give the same vector. That is what makes this
	 * the right tool for telling one material from another inside a texture: saturation and luminance
	 * both vary across a single material's shading, and direction does not.
	 *
	 * @param floor how far from grey, in channel counts, a pixel must be before it counts as having a
	 *              direction at all. Every derivation using this declares it as a {@code Param}, because
	 *              the right value depends on how boldly the packs in question draw.
	 */
	public static double @Nullable [] chromaOf(int argb, double floor) {
		if (alpha(argb) == 0) {
			return null;
		}
		double mean = (red(argb) + green(argb) + blue(argb)) / 3.0;
		double red = red(argb) - mean;
		double green = green(argb) - mean;
		double blue = blue(argb) - mean;
		double length = Math.sqrt(red * red + green * green + blue * blue);
		return length < floor ? null : new double[] { red / length, green / length, blue / length };
	}

	/** How nearly two {@link #chromaOf} directions point the same way, 1 for identical and -1 for opposite. */
	public static double agree(double[] one, double[] other) {
		return one[0] * other[0] + one[1] * other[1] + one[2] * other[2];
	}

	/** Perceptual brightness, 0-255, on the sRGB coefficients. */
	public static int luminance(int argb) {
		return (int) Math.round(0.2126 * red(argb) + 0.7152 * green(argb) + 0.0722 * blue(argb));
	}

	/**
	 * The fully bright form of {@code hue}/{@code saturation}, scaled down until its luminance is
	 * {@code target}.
	 * <p>
	 * Scaling a bright colour is what keeps the tint honest at low light levels. Feeding the target
	 * straight into HSB brightness would leave the saturation applied to a value that is already dark,
	 * and every shadow pixel would come out closer to flat black than the tint asks for.
	 *
	 * @param hue as a fraction of the colour wheel, the form {@link Color#HSBtoRGB} takes
	 */
	public static int atLuminance(double hue, double saturation, double target) {
		int bright = Color.HSBtoRGB((float) hue, (float) Math.clamp(saturation, 0, 1), 1);
		double luminance = luminance(bright);
		double scale = luminance <= 0 ? 0 : target / luminance;
		return argb(
			255,
			(int) Math.round(red(bright) * scale),
			(int) Math.round(green(bright) * scale),
			(int) Math.round(blue(bright) * scale)
		);
	}

	/** Replaces {@code argb}'s alpha, leaving its colour alone. */
	public static int withAlpha(int argb, int alpha) {
		return clamp(alpha) << 24 | argb & 0x00FFFFFF;
	}

	private static int mix(int from, int to, double weight) {
		return clamp((int) Math.round(from + (to - from) * weight));
	}

	public static int alpha(int argb) {
		return argb >>> 24 & 0xFF;
	}

	public static int red(int argb) {
		return argb >>> 16 & 0xFF;
	}

	public static int green(int argb) {
		return argb >>> 8 & 0xFF;
	}

	public static int blue(int argb) {
		return argb & 0xFF;
	}

	public static int argb(int a, int r, int g, int b) {
		return clamp(a) << 24 | clamp(r) << 16 | clamp(g) << 8 | clamp(b);
	}

	public static int clamp(int channel) {
		return Math.clamp(channel, 0, 255);
	}
}
