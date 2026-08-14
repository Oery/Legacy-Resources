package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Crimson and warped nylium from the pack's own fungal surface and untouched netherrack substrate. */
final class Nylium implements Derivation {
	private record Surface(String output, double hue, double saturation, double brightness, double spread) {
	}

	private static final String SOURCE = "block/netherrack";
	private static final String MYCELIUM_TOP = "block/mycelium_top";
	private static final String MYCELIUM_SIDE = "block/mycelium_side";
	private static final String DIRT = "block/dirt";
	private static final String GRASS_TOP_FALLBACK = "block/grass_block_top";
	private static final String GRASS_OVERLAY = "block/grass_block_side_overlay";
	private static final List<Surface> SURFACES = List.of(
		new Surface("block/crimson_nylium", 0, .752, 52.6, 17.8),
		new Surface("block/warped_nylium", .472, .613, 98.3, 16.7)
	);
	private static final List<String> OUTPUTS = List.of(
		"block/crimson_nylium", "block/crimson_nylium_side", "block/warped_nylium", "block/warped_nylium_side"
	);

	@Override
	public String id() {
		return "nylium";
	}

	@Override
	public List<String> sources() {
		return List.of(SOURCE, MYCELIUM_TOP, MYCELIUM_SIDE, DIRT, GRASS_TOP_FALLBACK, GRASS_OVERLAY);
	}

	@Override
	public List<String> outputs() {
		return OUTPUTS;
	}

	@Override
	public String animationSource(String output) {
		return output.endsWith("_side") ? SOURCE : MYCELIUM_TOP;
	}

	@Override
	public List<Param> params() {
		return SURFACES.stream().flatMap(surface -> {
			String prefix = surface.output().substring("block/".length());
			return java.util.stream.Stream.of(
				Param.of(prefix + "_hue", 0, 1, surface.hue()), Param.of(prefix + "_saturation", 0, 1, surface.saturation()),
				Param.of(prefix + "_brightness", 0, 255, surface.brightness()), Param.of(prefix + "_spread", 0, 64, surface.spread())
			);
		}).collect(java.util.stream.Collectors.collectingAndThen(
			java.util.stream.Collectors.toList(), params -> {
				params.add(Param.of("max_gain", .5, 4, 2));
				params.add(Param.ofInt("levels", 0, 8, 0));
				params.add(Param.ofInt("side_top_rows", 1, 8, 8));
				params.add(Param.of("mycelium_max_background_distance", 0, 40, 14));
				params.add(Param.of("mycelium_min_separation", 0, 64, 12));
				params.add(Param.of("mycelium_pixel_threshold", 0, 64, 12));
				params.add(Param.of("mycelium_edge_width", 1, 64, 12));
				return List.copyOf(params);
			}
		));
	}

	@Override
	public Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		BufferedImage netherrack = sources.get(SOURCE);
		if (netherrack == null || Ops.scaleOf(netherrack) == 0) return Map.of();
		// Mycelium is the legacy fungal ground cover and therefore the closest structural relative to
		// nylium. Grass remains a useful fallback for packs that omit mycelium but do author turf.
		BufferedImage surface = sources.get(MYCELIUM_TOP);
		if (surface == null || Ops.scaleOf(surface) == 0) surface = sources.get(GRASS_TOP_FALLBACK);
		Map<String, BufferedImage> derived = new LinkedHashMap<>();
		for (Surface face : SURFACES) {
			String prefix = face.output().substring("block/".length());
			if (surface == null || Ops.scaleOf(surface) == 0) continue;
			Palette palette = palette(prefix, params);
			derived.put(face.output(), palette.repaint(surface, params.get(prefix + "_spread"), params.get("max_gain"), params.getInt("levels")));
		}
		// Preserve netherrack exactly. Only the fungal/turf layer is repainted and composited over it.
		derived.put("block/crimson_nylium_side", Ops.copy(netherrack));
		derived.put("block/warped_nylium_side", Ops.copy(netherrack));
		BufferedImage overlay = extractMycelium(
			sources.get(MYCELIUM_SIDE), sources.get(DIRT), netherrack.getWidth(), params
		);
		int overlayRows = Ops.BASE_SIZE;
		if (overlay == null) {
			overlay = sources.get(GRASS_OVERLAY);
			overlayRows = params.getInt("side_top_rows");
		}
		BufferedImage selectedOverlay = overlay;
		int selectedOverlayRows = overlayRows;
		derived.computeIfPresent("block/crimson_nylium_side", (path, side) -> cap(
			side, derived.get("block/crimson_nylium"), selectedOverlay, palette("crimson_nylium", params),
			params.get("crimson_nylium_spread"), params, selectedOverlayRows
		));
		derived.computeIfPresent("block/warped_nylium_side", (path, side) -> cap(
			side, derived.get("block/warped_nylium"), selectedOverlay, palette("warped_nylium", params),
			params.get("warped_nylium_spread"), params, selectedOverlayRows
		));
		return derived;
	}

	/** Extracts a fungal foreground only when the side's lower dirt establishes a trustworthy baseline. */
	private static @Nullable BufferedImage extractMycelium(
		@Nullable BufferedImage side, @Nullable BufferedImage dirt, int size, Params params
	) {
		if (side == null || dirt == null || Ops.scaleOf(side) == 0 || Ops.scaleOf(dirt) == 0) return null;
		int[] sidePixels = Ops.pixels(Ops.resizeNearest(side, size));
		int[] dirtPixels = Ops.pixels(Ops.resizeNearest(dirt, size));
		int bottomStart = size * 3 / 4;
		double topDistance = meanDistance(sidePixels, dirtPixels, size, 0, size / 4);
		double bottomDistance = meanDistance(sidePixels, dirtPixels, size, bottomStart, size);
		if (bottomDistance > params.get("mycelium_max_background_distance")
			|| topDistance - bottomDistance < params.get("mycelium_min_separation")) return null;
		double threshold = Math.max(params.get("mycelium_pixel_threshold"), bottomDistance * 2);
		double edgeWidth = params.get("mycelium_edge_width");
		int[] overlay = new int[sidePixels.length];
		boolean visible = false;
		for (int i = 0; i < overlay.length; i++) {
			double distance = distance(sidePixels[i], dirtPixels[i]);
			int alpha = (int) Math.round(Math.clamp((distance - threshold) / edgeWidth, 0, 1) * Ops.alpha(sidePixels[i]));
			if (alpha != 0) visible = true;
			overlay[i] = sidePixels[i] & 0x00ffffff | alpha << 24;
		}
		return visible ? Ops.image(overlay, size, size) : null;
	}

	private static double meanDistance(int[] side, int[] dirt, int width, int firstRow, int lastRow) {
		double total = 0;
		for (int y = firstRow; y < lastRow; y++) for (int x = 0; x < width; x++) {
			total += distance(side[y * width + x], dirt[y * width + x]);
		}
		return total / ((lastRow - firstRow) * width);
	}

	private static double distance(int one, int other) {
		return (Math.abs(Ops.red(one) - Ops.red(other)) + Math.abs(Ops.green(one) - Ops.green(other))
			+ Math.abs(Ops.blue(one) - Ops.blue(other))) / 3.0;
	}

	private static Palette palette(String prefix, Params params) {
		return new Palette(
			params.get(prefix + "_hue"), params.get(prefix + "_saturation"), params.get(prefix + "_brightness")
		);
	}

	private static BufferedImage cap(
		BufferedImage side, @Nullable BufferedImage top, @Nullable BufferedImage overlay,
		Palette palette, double spread, Params params, int rows
	) {
		int scale = Ops.scaleOf(side);
		if (scale == 0) return side;
		int width = side.getWidth();
		int height = Math.min(width, rows * scale);
		int[] output = Ops.pixels(side);
		BufferedImage resized = overlay == null || Ops.scaleOf(overlay) == 0 ? null : Ops.resizeNearest(overlay, width);
		int[] painted = resized == null ? null : Ops.pixels(
			palette.repaint(resized, spread, params.get("max_gain"), params.getInt("levels"))
		);
		if (painted != null && java.util.Arrays.stream(painted).noneMatch(pixel -> Ops.alpha(pixel) != 0)) painted = null;
		int[] topPixels = top == null || Ops.scaleOf(top) != scale ? null : Ops.pixels(top);
		if (painted == null && topPixels == null) return side;
		for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
			int index = y * width + x;
			// A missing or empty overlay still receives the simple cap this derivation used before.
			output[index] = painted == null ? topPixels[index] : sourceOver(output[index], painted[index]);
		}
		return Ops.image(output, width, width);
	}

	/** Alpha-composites {@code foreground} over {@code background}, preserving antialiased blade edges. */
	private static int sourceOver(int background, int foreground) {
		int foregroundAlpha = Ops.alpha(foreground);
		if (foregroundAlpha == 0) return background;
		if (foregroundAlpha == 255) return foreground;
		int backgroundAlpha = Ops.alpha(background);
		int inverse = 255 - foregroundAlpha;
		int outputAlpha = foregroundAlpha + (backgroundAlpha * inverse + 127) / 255;
		if (outputAlpha == 0) return 0;
		int red = compositeChannel(foreground >>> 16, background >>> 16, foregroundAlpha, backgroundAlpha, inverse, outputAlpha);
		int green = compositeChannel(foreground >>> 8, background >>> 8, foregroundAlpha, backgroundAlpha, inverse, outputAlpha);
		int blue = compositeChannel(foreground, background, foregroundAlpha, backgroundAlpha, inverse, outputAlpha);
		return Ops.argb(outputAlpha, red, green, blue);
	}

	private static int compositeChannel(
		int foreground, int background, int foregroundAlpha, int backgroundAlpha, int inverse, int outputAlpha
	) {
		int premultiplied = (foreground & 255) * foregroundAlpha
			+ ((background & 255) * backgroundAlpha * inverse + 127) / 255;
		return Math.clamp((premultiplied + outputAlpha / 2) / outputAlpha, 0, 255);
	}
}
