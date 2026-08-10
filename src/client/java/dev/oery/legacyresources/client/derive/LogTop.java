package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;

/** Separates bark from end grain on a source log-top texture. */
final class LogTop {
	private LogTop() {
	}

	static BufferedImage withBarkMask(BufferedImage end, BufferedImage bark, BufferedImage source, int searchCells) {
		if (end.getWidth() != bark.getWidth() || end.getWidth() != source.getWidth() || searchCells <= 0) {
			return end;
		}
		int scale = Ops.scaleOf(source);
		if (scale == 0) {
			return end;
		}
		int width = end.getWidth();
		int search = Math.min(searchCells * scale, width / 2);
		int[] sourcePixels = Ops.pixels(source);
		double[] barkMean = mean(sourcePixels, width, 0, width, 0, width, scale, true);
		int centreStart = width / 4;
		int centreEnd = width - centreStart;
		double[] endMean = mean(sourcePixels, width, centreStart, centreEnd, centreStart, centreEnd, 0, false);
		if (barkMean == null || endMean == null) {
			return end;
		}
		int[] out = Ops.pixels(end);
		int[] barkPixels = Ops.pixels(bark);
		for (int y = 0; y < width; y++) {
			for (int x = 0; x < width; x++) {
				if (Math.min(Math.min(x, y), Math.min(width - 1 - x, width - 1 - y)) < search
					&& closerTo(sourcePixels[y * width + x], barkMean, endMean)) {
					out[y * width + x] = barkPixels[y * width + x];
				}
			}
		}
		return Ops.image(out, width, width);
	}

	/** Mean RGB of either the outer {@code edge}-wide frame or an interior rectangle. */
	private static double[] mean(int[] pixels, int width, int left, int right, int top, int bottom, int edge, boolean frame) {
		double red = 0;
		double green = 0;
		double blue = 0;
		int count = 0;
		for (int y = top; y < bottom; y++) {
			for (int x = left; x < right; x++) {
				if (frame && x >= edge && y >= edge && x < width - edge && y < width - edge) {
					continue;
				}
				int pixel = pixels[y * width + x];
				if (Ops.alpha(pixel) == 0) {
					continue;
				}
				red += Ops.red(pixel);
				green += Ops.green(pixel);
				blue += Ops.blue(pixel);
				count++;
			}
		}
		return count == 0 ? null : new double[] { red / count, green / count, blue / count };
	}

	private static boolean closerTo(int pixel, double[] bark, double[] end) {
		if (Ops.alpha(pixel) == 0) {
			return false;
		}
		double barkDistance = distance(pixel, bark);
		return barkDistance < distance(pixel, end);
	}

	private static double distance(int pixel, double[] color) {
		double red = Ops.red(pixel) - color[0];
		double green = Ops.green(pixel) - color[1];
		double blue = Ops.blue(pixel) - color[2];
		return red * red + green * green + blue * blue;
	}
}
