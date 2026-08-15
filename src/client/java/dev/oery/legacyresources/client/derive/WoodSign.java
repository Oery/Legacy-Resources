package dev.oery.legacyresources.client.derive;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Repaints the pack's corrected oak sign block sheet and item silhouette into one of its other wood
 * palettes. Board colour is learned from this pack's own planks and post colour from its log or
 * Nether stem; oak remains the source of the sign-specific grain, edge shading and item shape that
 * plain material crops cannot supply.
 *
 * <p>The transfer is relative to the same pack's oak planks. If its spruce planks are half as bright
 * as its oak, for example, its spruce sign becomes half as bright as its oak sign. Hue and saturation
 * move by the corresponding oak-to-target-plank difference rather than being replaced with vanilla's
 * fixed colours, so unusual resource-pack palettes survive.</p>
 */
final class WoodSign implements Derivation {
	private static final String OAK_BLOCK = "block/oak_sign";
	private static final String OAK_ITEM = "item/oak_sign";
	private static final String OAK_PLANKS = "block/oak_planks";
	private static final String OAK_LOG = "block/oak_log";

	private final String wood;
	private final String planks;
	private final String log;
	private final String blockOutput;
	private final String itemOutput;
	private final String guiOutput;

	WoodSign(String wood, String log) {
		this.wood = wood;
		planks = "block/" + wood + "_planks";
		this.log = "block/" + log;
		blockOutput = "block/" + wood + "_sign";
		itemOutput = "item/" + wood + "_sign";
		guiOutput = "gui/signs/" + wood;
	}

	@Override public String id() { return wood + "_signs"; }
	@Override public List<String> sources() { return List.of(OAK_BLOCK, OAK_ITEM, OAK_PLANKS, planks, OAK_LOG, log); }
	@Override public List<String> outputs() { return List.of(blockOutput, itemOutput, guiOutput); }
	@Override public String animationSource(String output) { return output.equals(itemOutput) ? OAK_ITEM : OAK_BLOCK; }

	@Override
	public List<Param> params() {
		return List.of(
			Param.of("hue_transfer", 0, 1, 1),
			Param.of("saturation_transfer", 0, 1, 1),
			Param.of("brightness_transfer", 0, 2, 1),
			Param.of("contrast_transfer", 0, 2, 1),
			Param.of("max_gain", .5, 4, 2),
			Param.ofInt("levels", 0, 8, 0)
		);
	}

	@Override
	public Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		BufferedImage oakPlanksImage = sources.get(OAK_PLANKS);
		BufferedImage woodPlanksImage = sources.get(planks);
		BufferedImage oakLogImage = sources.get(OAK_LOG);
		BufferedImage woodLogImage = sources.get(log);
		if (oakPlanksImage == null || woodPlanksImage == null
			|| oakLogImage == null || woodLogImage == null
			|| Ops.scaleOf(oakPlanksImage) == 0 || Ops.scaleOf(woodPlanksImage) == 0
			|| Ops.scaleOf(oakLogImage) == 0 || Ops.scaleOf(woodLogImage) == 0) return Map.of();
		Band oakPlanks = measure(oakPlanksImage);
		Band woodPlanks = measure(woodPlanksImage);
		Band oakLog = measure(oakLogImage);
		Band woodLog = measure(woodLogImage);
		if (oakPlanks == null || woodPlanks == null || oakLog == null || woodLog == null
			|| oakPlanks.mean() <= 0 || oakLog.mean() <= 0) return Map.of();

		Map<String, BufferedImage> out = new LinkedHashMap<>();
		deriveBlock(sources.get(OAK_BLOCK), oakPlanks, woodPlanks, oakLog, woodLog, params, out);
		deriveItem(sources.get(OAK_ITEM), oakPlanks, woodPlanks, oakLog, woodLog, params, out);
		return out;
	}

	private void deriveBlock(@Nullable BufferedImage source, Band oakPlanks, Band woodPlanks,
		Band oakLog, Band woodLog, Params params, Map<String, BufferedImage> out) {
		if (source == null || source.getWidth() != source.getHeight() || source.getWidth() % 32 != 0) return;
		int postStart = 28 * source.getWidth() / 32;
		BufferedImage board = masked(source, (x, y) -> x < postStart);
		BufferedImage post = masked(source, (x, y) -> x >= postStart);
		BufferedImage block = combine(
			repaint(board, oakPlanks, woodPlanks, params), repaint(post, oakLog, woodLog, params)
		);
		out.put(blockOutput, block);
		out.put(guiOutput, Signs.editorTexture(block));
	}

	private void deriveItem(@Nullable BufferedImage source, Band oakPlanks, Band woodPlanks,
		Band oakLog, Band woodLog, Params params, Map<String, BufferedImage> out) {
		if (source == null || Ops.scaleOf(source) == 0) return;
		int postStart = 11 * source.getHeight() / 16;
		BufferedImage board = masked(source, (x, y) -> y < postStart);
		BufferedImage post = masked(source, (x, y) -> y >= postStart);
		out.put(itemOutput, combine(
			repaint(board, oakPlanks, woodPlanks, params), repaint(post, oakLog, woodLog, params)
		));
	}

	private static BufferedImage repaint(BufferedImage source, Band oak, Band wood, Params params) {
		Band sign = measure(source);
		if (sign == null) return source;
		double brightnessRatio = wood.mean() / oak.mean();
		double hue = shiftHue(sign.hue(), hueDifference(oak.hue(), wood.hue()) * params.get("hue_transfer"));
		double saturation = Math.clamp(
			sign.saturation() + (wood.saturation() - oak.saturation()) * params.get("saturation_transfer"), 0, 1
		);
		double mean = sign.mean() * Math.pow(brightnessRatio, params.get("brightness_transfer"));
		double spread = sign.spread() * Math.pow(brightnessRatio, params.get("contrast_transfer"));
		return new Palette(hue, saturation, mean).repaint(
			source, spread, params.get("max_gain"), params.getInt("levels")
		);
	}

	private static BufferedImage masked(BufferedImage source, PixelPart part) {
		int width = source.getWidth(), height = source.getHeight();
		int[] pixels = Ops.pixels(source);
		for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
			if (!part.includes(x, y)) pixels[y * width + x] = 0;
		}
		return Ops.image(pixels, width, height);
	}

	private static BufferedImage combine(BufferedImage board, BufferedImage post) {
		int[] boardPixels = Ops.pixels(board);
		int[] postPixels = Ops.pixels(post);
		for (int i = 0; i < boardPixels.length; i++) {
			if (Ops.alpha(postPixels[i]) != 0) boardPixels[i] = postPixels[i];
		}
		return Ops.image(boardPixels, board.getWidth(), board.getHeight());
	}

	private static @Nullable Band measure(BufferedImage image) {
		double hueSin = 0, hueCos = 0, saturation = 0, luminance = 0, squares = 0;
		long count = 0;
		for (int argb : Ops.pixels(image)) {
			if (Ops.alpha(argb) == 0) continue;
			float[] hsb = Color.RGBtoHSB(Ops.red(argb), Ops.green(argb), Ops.blue(argb), null);
			double angle = hsb[0] * Math.PI * 2;
			hueSin += Math.sin(angle) * hsb[1];
			hueCos += Math.cos(angle) * hsb[1];
			saturation += hsb[1];
			double value = Ops.luminance(argb);
			luminance += value;
			squares += value * value;
			count++;
		}
		if (count == 0) return null;
		double mean = luminance / count;
		double hue = shiftHue(Math.atan2(hueSin, hueCos) / (Math.PI * 2), 0);
		return new Band(hue, saturation / count, mean, Math.sqrt(Math.max(0, squares / count - mean * mean)));
	}

	private static double hueDifference(double from, double to) {
		double difference = to - from;
		return difference - Math.floor(difference + .5);
	}

	private static double shiftHue(double hue, double difference) {
		double shifted = hue + difference;
		return shifted - Math.floor(shifted);
	}

	private record Band(double hue, double saturation, double mean, double spread) {
	}

	@FunctionalInterface
	private interface PixelPart {
		boolean includes(int x, int y);
	}
}
