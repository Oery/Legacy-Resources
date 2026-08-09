package dev.oery.legacyresources.lab;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import javax.imageio.ImageIO;

/** Turning derivation output into something a browser can show, and into a number. */
final class LabImages {
	/**
	 * Largest edge, in pixels, of an image embedded in the batch response.
	 * <p>
	 * The corpus reaches 512px, and a full-resolution PNG of every output of every pack, base64'd,
	 * runs to double-digit megabytes for a single drag of a slider. Everything is therefore shrunk for
	 * the grid; {@code /api/texture} still serves the untouched original for the click-through view.
	 */
	static final int PREVIEW_MAX = 128;

	private LabImages() {
	}

	/**
	 * Shrinks by a whole-number factor with a box average, or returns the image untouched.
	 * <p>
	 * Whole-number only, and averaging rather than sampling: an arbitrary ratio drops pixels
	 * unevenly, which shows up as a limp or a shimmer in exactly the fine detail being judged.
	 */
	static BufferedImage preview(BufferedImage source) {
		int size = Math.max(source.getWidth(), source.getHeight());
		if (size <= PREVIEW_MAX) {
			return source;
		}
		int factor = Math.max(2, (int) Math.ceil((double) size / PREVIEW_MAX));
		int width = Math.max(1, source.getWidth() / factor);
		int height = Math.max(1, source.getHeight() / factor);
		int[] in = source.getRGB(0, 0, source.getWidth(), source.getHeight(), null, 0, source.getWidth());
		int[] out = new int[width * height];
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				long a = 0;
				long r = 0;
				long g = 0;
				long b = 0;
				int samples = 0;
				for (int dy = 0; dy < factor; dy++) {
					int sy = y * factor + dy;
					if (sy >= source.getHeight()) {
						continue;
					}
					for (int dx = 0; dx < factor; dx++) {
						int sx = x * factor + dx;
						if (sx >= source.getWidth()) {
							continue;
						}
						int argb = in[sy * source.getWidth() + sx];
						int alpha = argb >>> 24 & 0xFF;
						a += alpha;
						// Premultiplied, for the same reason Ops.boxBlur premultiplies: averaging the
						// colour of transparent pixels darkens every soft edge.
						r += (long) (argb >>> 16 & 0xFF) * alpha;
						g += (long) (argb >>> 8 & 0xFF) * alpha;
						b += (long) (argb & 0xFF) * alpha;
						samples++;
					}
				}
				if (samples == 0 || a == 0) {
					out[y * width + x] = 0;
					continue;
				}
				out[y * width + x] = (int) (a / samples) << 24
					| (int) (r / a) << 16
					| (int) (g / a) << 8
					| (int) (b / a);
			}
		}
		BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		result.setRGB(0, 0, width, height, out, 0, width);
		return result;
	}

	static byte[] png(BufferedImage image) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			ImageIO.write(image, "png", bytes);
			return bytes.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	static String dataUrl(BufferedImage image) {
		return "data:image/png;base64," + Base64.getEncoder().encodeToString(png(preview(image)));
	}

	/**
	 * Mean absolute per-channel difference between two images, as a percentage - 0 is pixel-identical.
	 * <p>
	 * Only meaningful for the vanilla control, which is the one input whose correct output is known.
	 * It is a blunt instrument (it cannot tell a slightly-wrong hue from a badly misplaced shape), so
	 * it belongs beside the images rather than in place of them - but while dragging a slider, a
	 * number that moves is worth a great deal.
	 *
	 * @return {@code -1} when the two cannot be compared
	 */
	static double difference(BufferedImage a, BufferedImage b) {
		if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
			return -1;
		}
		int[] left = a.getRGB(0, 0, a.getWidth(), a.getHeight(), null, 0, a.getWidth());
		int[] right = b.getRGB(0, 0, b.getWidth(), b.getHeight(), null, 0, b.getWidth());
		long total = 0;
		for (int i = 0; i < left.length; i++) {
			for (int shift = 0; shift < 32; shift += 8) {
				total += Math.abs((left[i] >>> shift & 0xFF) - (right[i] >>> shift & 0xFF));
			}
		}
		return 100.0 * total / (left.length * 4L * 255L);
	}
}
