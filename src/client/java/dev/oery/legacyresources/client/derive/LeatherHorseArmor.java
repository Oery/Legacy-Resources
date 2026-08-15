package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Splits the pack's iron horse armour into the two layers leather rendering expects.
 *
 * <p>The first layer must be neutral grey: Minecraft multiplies it by the item's dye colour at render
 * time. Baking brown into that layer, as the old derivation did, applies the default leather colour
 * twice and makes the plates much darker and redder than ordinary leather armour. The second layer is
 * submitted without a dye tint and supplies the warm harness/highlight colour.</p>
 */
final class LeatherHorseArmor implements Derivation {
	private static final String ITEM = "item/iron_horse_armor";
	private static final String BODY = "entity/horse/armor/horse_armor_iron";
	private static final String ITEM_BASE = "item/leather_horse_armor";
	private static final String ITEM_OVERLAY = "item/leather_horse_armor_overlay";
	private static final String BODY_BASE = "entity/horse/armor/horse_armor_leather";
	private static final String BODY_OVERLAY = "entity/horse/armor/horse_armor_leather_overlay";

	// Modern leather horse armour partitions its 16px icon between dyeable plates and an untinted
	// harness. The legacy icon has the same orientation but a slightly older silhouette, so these rows
	// classify coordinates rather than replace any of the pack's pixels. Extra legacy pixels remain in
	// the dyeable base instead of being discarded.
	private static final int[] ITEM_OVERLAY_ROWS = {
		0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x7000,
		0x09E0, 0x0FE0, 0x03F0, 0x03F8, 0x03E0, 0x1FFE, 0x0000, 0x0000
	};

	@Override public String id() { return "leather_horse_armor"; }
	@Override public List<String> sources() { return List.of(ITEM, BODY); }
	@Override public List<String> outputs() { return List.of(ITEM_BASE, ITEM_OVERLAY, BODY_BASE, BODY_OVERLAY); }

	@Override
	public List<Param> params() {
		return List.of(
			// Vanilla's dyeable layer is deliberately pale; the renderer's #a06540 multiplication is
			// what brings it onto the same chestnut band as ordinary leather armour.
			Param.of("item_base_shadow", 0, 255, 154), Param.of("item_base_highlight", 0, 255, 233),
			Param.of("body_base_shadow", 0, 255, 70), Param.of("body_base_highlight", 0, 255, 199),
			Param.of("gamma", .25, 3, 1),
			Param.of("overlay_shadow", 0, 255, 39), Param.of("overlay_highlight", 0, 255, 149),
			Param.of("overlay_hue", 0, 360, 27), Param.of("overlay_saturation", 0, 1, .67),
			// Legacy horse sheets distinguish their leather harness from iron plates by colour. Keep
			// the threshold tunable because packs range from nearly-grey straps to saturated red ones.
			Param.ofInt("body_overlay_chroma", 0, 128, 18)
		);
	}

	@Override
	public Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		Map<String, BufferedImage> out = new LinkedHashMap<>();
		BufferedImage item = sources.get(ITEM);
		if (item != null && Ops.scaleOf(item) > 0) {
			boolean[] overlay = itemOverlayMask(item);
			out.put(ITEM_BASE, base(item, null, params.get("item_base_shadow"), params.get("item_base_highlight"), params));
			out.put(ITEM_OVERLAY, overlay(item, overlay, params));
		}
		BufferedImage body = sources.get(BODY);
		if (body != null && body.getWidth() == body.getHeight() && body.getWidth() % 64 == 0) {
			boolean[] overlay = bodyOverlayMask(body, params.getInt("body_overlay_chroma"));
			out.put(BODY_BASE, base(body, null, params.get("body_base_shadow"), params.get("body_base_highlight"), params));
			out.put(BODY_OVERLAY, overlay(body, overlay, params));
		}
		return out;
	}

	private static boolean[] itemOverlayMask(BufferedImage image) {
		boolean[] mask = new boolean[image.getWidth() * image.getHeight()];
		for (int y = 0; y < image.getHeight(); y++) {
			int baseY = y * Ops.BASE_SIZE / image.getHeight();
			for (int x = 0; x < image.getWidth(); x++) {
				int baseX = x * Ops.BASE_SIZE / image.getWidth();
				mask[y * image.getWidth() + x] = (ITEM_OVERLAY_ROWS[baseY] & 1 << baseX) != 0;
			}
		}
		return mask;
	}

	private static boolean[] bodyOverlayMask(BufferedImage image, int chromaFloor) {
		int[] pixels = Ops.pixels(image);
		boolean[] mask = new boolean[pixels.length];
		for (int y = 0; y < image.getHeight(); y++) {
			int modelY = y * 128 / image.getHeight();
			for (int x = 0; x < image.getWidth(); x++) {
				int i = y * image.getWidth() + x;
				int pixel = pixels[i];
				int max = Math.max(Ops.red(pixel), Math.max(Ops.green(pixel), Ops.blue(pixel)));
				int min = Math.min(Ops.red(pixel), Math.min(Ops.green(pixel), Ops.blue(pixel)));
				int modelX = x * 128 / image.getWidth();
				// These fixed UVs are the harness strips in 1.8.9's 128px horse-armour model. They
				// provide the semantic split for packs such as PureBDcraft that draw both straps and
				// plates in neutral grey; chroma additionally preserves custom coloured harness detail.
				mask[i] = Ops.alpha(pixel) != 0 && (max - min >= chromaFloor || harnessUv(modelX, modelY));
			}
		}
		return mask;
	}

	private static boolean harnessUv(int x, int y) {
		if (y == 40 || y == 49) return x >= 24 && x <= 33;
		if (y >= 58 && y <= 62) return x == 6 || x == 15 || x == 42 || x == 51;
		if (y == 63) return x >= 6 && x <= 10 || x == 15 || x == 42 || x >= 47 && x <= 51;
		return y == 64 && (x >= 10 && x <= 15 || x >= 42 && x <= 47);
	}

	private static BufferedImage base(BufferedImage source, boolean[] selected, double shadow, double highlight, Params params) {
		Range range = range(source, selected);
		int[] pixels = Ops.pixels(source);
		int[] out = new int[pixels.length];
		for (int i = 0; i < pixels.length; i++) {
			int pixel = pixels[i];
			if (Ops.alpha(pixel) == 0) continue;
			double position = range.position(Ops.luminance(pixel), params.get("gamma"));
			int grey = (int) Math.round(shadow + (highlight - shadow) * position);
			out[i] = Ops.argb(Ops.alpha(pixel), grey, grey, grey);
		}
		return Ops.image(out, source.getWidth(), source.getHeight());
	}

	private static BufferedImage overlay(BufferedImage source, boolean[] selected, Params params) {
		Range range = range(source, selected);
		int[] pixels = Ops.pixels(source);
		int[] out = new int[pixels.length];
		for (int i = 0; i < pixels.length; i++) {
			int pixel = pixels[i];
			if (!selected[i] || Ops.alpha(pixel) == 0) continue;
			double position = range.position(Ops.luminance(pixel), params.get("gamma"));
			double luminance = params.get("overlay_shadow")
				+ (params.get("overlay_highlight") - params.get("overlay_shadow")) * position;
			out[i] = Ops.withAlpha(Ops.atLuminance(
				params.get("overlay_hue") / 360, params.get("overlay_saturation"), luminance
			), Ops.alpha(pixel));
		}
		return Ops.image(out, source.getWidth(), source.getHeight());
	}

	private static Range range(BufferedImage image, boolean[] selected) {
		int min = 255;
		int max = 0;
		int[] pixels = Ops.pixels(image);
		for (int i = 0; i < pixels.length; i++) {
			if ((selected == null || selected[i]) && Ops.alpha(pixels[i]) != 0) {
				int luminance = Ops.luminance(pixels[i]);
				min = Math.min(min, luminance);
				max = Math.max(max, luminance);
			}
		}
		return new Range(min, max);
	}

	private record Range(int min, int max) {
		double position(int luminance, double gamma) {
			return max <= min ? .5 : Math.pow(Math.clamp((double) (luminance - min) / (max - min), 0, 1), gamma);
		}
	}
}
