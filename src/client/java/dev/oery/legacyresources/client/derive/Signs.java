package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Re-packs the classic 64×32 sign UV sheet into current 32×32 sign-model UVs. */
final class Signs implements Derivation {
	private static final String SHEET = "entity/sign";
	private static final String ITEM = "item/sign";
	private static final List<String> WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "crimson", "warped", "pale_oak");

	@Override public String id() { return "signs"; }
	@Override public List<String> sources() { return List.of(SHEET, ITEM); }
	@Override public List<String> outputs() {
		return WOODS.stream().flatMap(wood -> java.util.stream.Stream.of("block/" + wood + "_sign", "item/" + wood + "_sign")).toList();
	}
	@Override public List<Param> params() { return List.of(); }
	@Override public Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		Map<String, BufferedImage> out = new LinkedHashMap<>();
		BufferedImage sheet = sources.get(SHEET);
		BufferedImage item = sources.get(ITEM);
		if (sheet != null) {
			BufferedImage sign = repack(sheet);
			if (sign != null) WOODS.forEach(wood -> out.put("block/" + wood + "_sign", Ops.copy(sign)));
		}
		if (item != null) WOODS.forEach(wood -> out.put("item/" + wood + "_sign", Ops.copy(item)));
		return out;
	}

	private static BufferedImage repack(BufferedImage source) {
		if (source.getWidth() != source.getHeight() * 2 || source.getWidth() < 64 || source.getWidth() % 64 != 0) return null;
		int scale = source.getWidth() / 64;
		BufferedImage out = Ops.blank(32 * scale, 32 * scale);
		// Old ModelSign uses 24×12×2 and 2×14×2 boxes. Current templates are their 2/3-sized
		// counterparts; each destination UV cell is exactly the corresponding old region at half scale.
		copy(source, out, scale, 2, 2, 26, 14, 0, 8, 12, 14);   // board north
		copy(source, out, scale, 28, 2, 52, 14, 0, 1, 12, 7);   // board south
		copy(source, out, scale, 0, 2, 2, 14, 12, 1, 13, 7);    // board east
		copy(source, out, scale, 26, 2, 28, 14, 12, 8, 13, 14); // board west
		copy(source, out, scale, 2, 0, 26, 2, 0, 0, 12, 1);     // board top
		copy(source, out, scale, 26, 0, 50, 2, 0, 14, 12, 15);  // board bottom
		copy(source, out, scale, 2, 16, 4, 30, 14, 8, 15, 15);  // post north
		copy(source, out, scale, 0, 16, 2, 30, 15, 0, 16, 7);   // post east
		copy(source, out, scale, 6, 16, 8, 30, 14, 0, 15, 7);   // post south
		copy(source, out, scale, 4, 16, 6, 30, 15, 8, 16, 15);  // post west
		copy(source, out, scale, 2, 14, 4, 16, 14, 15, 15, 16); // post bottom
		return out;
	}

	private static void copy(BufferedImage source, BufferedImage target, int scale, int sx0, int sy0, int sx1, int sy1, int dx0, int dy0, int dx1, int dy1) {
		int[] pixels = source.getRGB(sx0 * scale, sy0 * scale, (sx1 - sx0) * scale, (sy1 - sy0) * scale, null, 0, (sx1 - sx0) * scale);
		int width = (dx1 - dx0) * scale, height = (dy1 - dy0) * scale, sourceWidth = (sx1 - sx0) * scale, sourceHeight = (sy1 - sy0) * scale;
		int[] scaled = new int[width * height];
		for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) scaled[y * width + x] = pixels[(y * sourceHeight / height) * sourceWidth + x * sourceWidth / width];
		target.setRGB(dx0 * scale, dy0 * scale, width, height, scaled, 0, width);
	}
}
