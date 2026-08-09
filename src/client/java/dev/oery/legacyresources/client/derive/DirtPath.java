package dev.oery.legacyresources.client.derive;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The dirt path's two faces, derived from the pack's own grass block and its own dirt.
 * <p>
 * Vanilla's own pair says exactly how the block is built, and both halves turn out to be learnable
 * rather than matters of taste. {@code dirt_path_side} is {@code dirt} with the grass block's fringe
 * painted over it: its trodden crust falls on precisely the pixels
 * {@code grass_block_side_overlay} marks, shifted <em>down one pixel</em> - 240 of 240 agree, no
 * exceptions - while 193 of the 195 pixels outside that mask are {@code dirt} itself at the same
 * coordinate. Row 0 is transparent, matching {@code models/block/dirt_path.json}: the block stands
 * 15/16 tall and its side UV starts at {@code v=1}, so that row is never sampled.
 * <p>
 * {@code dirt_path_top} is dirt's palette flattened and warmed. Measured against vanilla's dirt, its
 * mean luminance is 1.21x higher, its luminance spread is cut to a standard deviation of 9.2 from
 * 22.9, its hue is rotated 14.7 degrees towards yellow and its saturation is 1.11x. Seven packs in the
 * corpus drew a path of their own for 1.9-1.12, and independently agree: 14 to 21 degrees of hue and a
 * 1.15x to 1.24x lift. One of them draws in greyscale throughout, which is why saturation here is a
 * multiplier on the pack's own rather than a target to reach - a grey pack has to stay grey.
 * <p>
 * What the pattern is made of is the one choice vanilla does not settle, since its path top is noise
 * unrelated to any other texture. This takes it from {@code grass_block_top}: a pack's grass and its
 * dirt are two different noise fields, and sourcing the top from the grass one keeps the path from
 * reading as a recoloured dirt block. Flattening the spread to {@code spread} is what stops it reading
 * as grass either - it is applied hard enough to take a pack's blades down to a mottle.
 */
final class DirtPath implements Derivation {
	private static final String DIRT = "block/dirt";
	private static final String GRASS_TOP = "block/grass_block_top";
	private static final String OVERLAY = "block/grass_block_side_overlay";

	private static final String TOP = "block/dirt_path_top";
	private static final String SIDE = "block/dirt_path_side";

	@Override
	public String id() {
		return "dirt_path";
	}

	@Override
	public List<String> sources() {
		return List.of(DIRT, GRASS_TOP, OVERLAY);
	}

	@Override
	public List<String> outputs() {
		return List.of(TOP, SIDE);
	}

	@Override
	public List<Param> params() {
		return List.of(
			// How much brighter than the pack's own dirt the path lands, and how much of the source's
			// luminance variation survives - a target standard deviation rather than a proportion, so
			// that grass (vanilla: 17.7) and dirt (22.9) reach the same palette without either being
			// special-cased. Both measured off vanilla's own path: 123.28/102.25 and 9.2.
			Param.of("mean_lift", 0.8, 1.8, 1.21),
			Param.of("spread", 0, 30, 9.2),
			// Ceiling on the gain that reaching `spread` may ask for. The median pack needs 0.79, i.e.
			// its grass is scaled down, which is the direction that does the work; but 24 of 65 draw
			// flatter grass than vanilla and 13 would be amplified past 1.5, one of them by 5.15.
			// Blowing up a near-flat source turns a faint gradient or a resave artefact into speckle, so
			// a path flatter than asked for is the better answer.
			Param.of("max_gain", 0.5, 4, 1.5),
			// The warm shift off the pack's dirt, 40.70 - 26.02 degrees and 0.559/0.502 on the control.
			Param.of("hue_shift", -30, 60, 14.7),
			Param.of("saturation_scale", 0.5, 2, 1.11),
			// Optional posterisation of the finished band, in shades. Vanilla's path top is 4 shades
			// against grass top's 66, so the derived top is a smoother field than the control even
			// though it matches its statistics to two decimals. Off by default: quantising is right for
			// 16x art and wrong for a 128x pack's gradients, and the lab is where that gets settled.
			Param.ofInt("levels", 0, 8, 0),
			// How far down the side the crust may reach, in 16px units. This is a clamp, not a
			// description: corpus overlays are nothing like uniform - vanilla's fringe ends at overlay
			// row 3, i.e. side row 4, but 13 packs run to row 5, 11 to rows 6-7, and 4 tint the whole
			// side, which unclamped would repaint every pixel and leave a block that is solid crust.
			// Set from how it reads in game rather than from the control, which wants 4: at 8 a pack's
			// own fringe is what shapes the crust nearly everywhere, and only the packs that would
			// have swallowed the face are held back.
			Param.ofInt("crust_rows", 1, 8, 8)
		);
	}

	@Override
	public Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		BufferedImage dirt = sources.get(DIRT);
		// Dirt sets the palette for both faces, so without it there is nothing to key either one on.
		if (dirt == null || Ops.scaleOf(dirt) == 0) {
			return Map.of();
		}
		Palette palette = Palette.of(dirt, params);
		if (palette == null) {
			return Map.of();
		}

		Map<String, BufferedImage> derived = new LinkedHashMap<>();
		BufferedImage grassTop = sources.get(GRASS_TOP);
		// A pack with dirt but no usable grass top still gets its side; the top falls back to vanilla's
		// rather than being invented out of the only other thing to hand, which is the dirt itself.
		if (grassTop != null && Ops.scaleOf(grassTop) != 0) {
			derived.put(TOP, palette.repaint(grassTop, params));
		}
		derived.put(SIDE, side(dirt, sources.get(OVERLAY), palette, params));
		return derived;
	}

	/**
	 * The pack's dirt, with a crust of {@link Palette#repaint} across its top rows and its first row
	 * cleared.
	 *
	 * @param overlay the pack's grass fringe, at any resolution, or {@code null} to use a flat band
	 */
	private static BufferedImage side(BufferedImage dirt, @Nullable BufferedImage overlay, Palette palette, Params params) {
		int size = dirt.getWidth();
		int scale = size / Ops.BASE_SIZE;
		int depth = params.getInt("crust_rows") * scale;
		int[] base = Ops.pixels(dirt);
		int[] crust = Ops.pixels(palette.repaint(dirt, params));
		int[] mask = maskOf(overlay, size);

		int[] out = new int[base.length];
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				int index = y * size + x;
				// The row the 15/16-tall model leaves unsampled. Vanilla clears it; anything drawn here
				// would only ever show up in something that reads the sprite by hand.
				if (y < scale) {
					out[index] = 0;
					continue;
				}
				// Shifted down by exactly the one pixel the block has lost off its top.
				boolean masked = mask == null || Ops.alpha(mask[(y - scale) * size + x]) != 0;
				out[index] = masked && y < scale + depth ? crust[index] : base[index];
			}
		}
		return Ops.image(out, size, size);
	}

	/**
	 * {@code overlay} on {@code size}'s grid, or {@code null} for the flat band.
	 * <p>
	 * A pack whose overlay is missing, oddly sized or drawn entirely transparent (5 of 83 in the
	 * corpus, between them) is not a pack to decline over: a crust of {@code crust_rows} straight rows
	 * is what a trodden edge is, and the fringe only ever ragged it. Nothing is being guessed at.
	 */
	private static int @Nullable [] maskOf(@Nullable BufferedImage overlay, int size) {
		if (overlay == null || Ops.scaleOf(overlay) == 0) {
			return null;
		}
		int[] mask = Ops.pixels(Ops.resizeNearest(overlay, size));
		for (int pixel : mask) {
			if (Ops.alpha(pixel) != 0) {
				return mask;
			}
		}
		return null;
	}

	/**
	 * Where the path's colours sit for this pack: the dirt's own hue and saturation, warmed, and the
	 * luminance its faces average to.
	 * <p>
	 * Hue is a saturation-weighted circular mean, not an average of angles - dirt sits close enough to
	 * the wheel's origin that a pack straddling it would otherwise average its reds and its magentas
	 * into the blue-green on the far side.
	 */
	private record Palette(double hue, double saturation, double mean) {
		static @Nullable Palette of(BufferedImage dirt, Params params) {
			int[] pixels = Ops.pixels(dirt);
			double x = 0;
			double y = 0;
			double saturation = 0;
			double luminance = 0;
			long count = 0;
			for (int argb : pixels) {
				if (Ops.alpha(argb) == 0) {
					continue;
				}
				float[] hsb = Color.RGBtoHSB(Ops.red(argb), Ops.green(argb), Ops.blue(argb), null);
				double angle = 2 * Math.PI * hsb[0];
				x += hsb[1] * Math.cos(angle);
				y += hsb[1] * Math.sin(angle);
				saturation += hsb[1];
				luminance += Ops.luminance(argb);
				count++;
			}
			// A dirt texture that is entirely transparent has no palette to take.
			if (count == 0) {
				return null;
			}
			// A wholly grey dirt leaves the circular mean at the origin, where the angle is arbitrary;
			// its saturation is 0 too, so the hue it lands on can never be seen.
			double hue = x == 0 && y == 0 ? 0 : Math.atan2(y, x) / (2 * Math.PI);
			return new Palette(
				hue + params.get("hue_shift") / 360.0,
				saturation / count * params.get("saturation_scale"),
				luminance / count * params.get("mean_lift")
			);
		}

		/**
		 * {@code source}'s luminance structure, re-levelled onto this palette's band and painted in its
		 * colour.
		 * <p>
		 * Levelled against the source's own mean and spread rather than a fixed range, which is what
		 * lets the same operation serve a grass top and a dirt: whatever either one's own contrast is,
		 * both come out on the same palette, so the top face and the side's crust agree.
		 */
		BufferedImage repaint(BufferedImage source, Params params) {
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
			double gain = deviation <= 0 ? 0 : Math.min(params.get("spread") / deviation, params.get("max_gain"));
			int levels = params.getInt("levels");
			// The band runs roughly two deviations either side of the mean, so that is what the
			// requested number of shades divides up.
			double step = levels <= 0 ? 0 : 4 * params.get("spread") / levels;

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
}
