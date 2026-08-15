package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Re-packs the classic 64×32 sign UV sheet into current 32×32 sign-model UVs. */
final class Signs implements Derivation {
	private static final String SHEET = "entity/sign";
	private static final String ITEM = "item/sign";

	@Override public String id() { return "signs"; }
	@Override public List<String> sources() { return List.of(SHEET, ITEM); }
	@Override public List<String> outputs() { return List.of("block/oak_sign", "item/oak_sign"); }
	@Override public List<Param> params() { return List.of(); }
	@Override public Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		Map<String, BufferedImage> out = new LinkedHashMap<>();
		BufferedImage sheet = sources.get(SHEET);
		BufferedImage item = sources.get(ITEM);
		if (sheet != null) {
			BufferedImage sign = repack(sheet);
			if (sign != null) out.put("block/oak_sign", sign);
		}
		if (item != null) out.put("item/oak_sign", Ops.copy(item));
		return out;
	}

	private static BufferedImage repack(BufferedImage source) {
		if (source.getWidth() != source.getHeight() * 2 || source.getWidth() < 64 || source.getWidth() % 64 != 0) return null;
		int scale = source.getWidth() / 64;
		BufferedImage out = Ops.blank(32 * scale, 32 * scale);
		// Block-model UVs use a logical 0..16 grid, so every coordinate in the 32×32 sign
		// texture is twice the value written in template_sign_rot_0.json. The old renderer's
		// (2/3, -2/3, -2/3) scale also turns ModelBox north into world south and vice versa.
		copy(source, out, scale, 28, 2, 52, 14, 0, 16, 24, 28);  // board north
		copy(source, out, scale, 2, 2, 26, 14, 0, 2, 24, 14);    // board south
		copy(source, out, scale, 26, 2, 28, 14, 24, 2, 26, 14);  // board east
		copy(source, out, scale, 0, 2, 2, 14, 24, 16, 26, 28);   // board west
		copy(source, out, scale, 2, 0, 26, 2, 0, 0, 24, 2);      // board top
		copy(source, out, scale, 26, 0, 50, 2, 0, 28, 24, 30, true); // board bottom
		copy(source, out, scale, 6, 16, 8, 30, 28, 16, 30, 30);  // post north
		copy(source, out, scale, 4, 16, 6, 30, 30, 0, 32, 14);   // post east
		copy(source, out, scale, 2, 16, 4, 30, 28, 0, 30, 14);   // post south
		copy(source, out, scale, 0, 16, 2, 30, 30, 16, 32, 30);  // post west
		copy(source, out, scale, 4, 14, 6, 16, 28, 30, 30, 32, true); // post bottom
		return out;
	}

	private static void copy(BufferedImage source, BufferedImage target, int scale, int sx0, int sy0, int sx1, int sy1, int dx0, int dy0, int dx1, int dy1) {
		copy(source, target, scale, sx0, sy0, sx1, sy1, dx0, dy0, dx1, dy1, false);
	}

	private static void copy(BufferedImage source, BufferedImage target, int scale, int sx0, int sy0, int sx1, int sy1, int dx0, int dy0, int dx1, int dy1, boolean flipY) {
		int[] pixels = source.getRGB(sx0 * scale, sy0 * scale, (sx1 - sx0) * scale, (sy1 - sy0) * scale, null, 0, (sx1 - sx0) * scale);
		int width = (dx1 - dx0) * scale, height = (dy1 - dy0) * scale, sourceWidth = (sx1 - sx0) * scale, sourceHeight = (sy1 - sy0) * scale;
		int[] scaled = new int[width * height];
		for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
			int sourceY = y * sourceHeight / height;
			if (flipY) sourceY = sourceHeight - 1 - sourceY;
			scaled[y * width + x] = pixels[sourceY * sourceWidth + x * sourceWidth / width];
		}
		target.setRGB(dx0 * scale, dy0 * scale, width, height, scaled, 0, width);
	}
}
