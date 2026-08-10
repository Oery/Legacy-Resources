package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Soul fire retains the pack's animated flame silhouette and is moved onto vanilla's cyan band. */
final class SoulFire implements Derivation {
	private static final Map<String, String> FACES = Map.of(
		"block/fire_0", "block/soul_fire_0",
		"block/fire_1", "block/soul_fire_1"
	);

	@Override
	public String id() {
		return "soul_fire";
	}

	@Override
	public List<String> sources() {
		return List.copyOf(FACES.keySet());
	}

	@Override
	public List<String> outputs() {
		return List.copyOf(FACES.values());
	}

	@Override
	public List<Param> params() {
		return List.of(
			// Measured from reference/26.2's two soul-fire sheets: 0.505 / 0.788 / 163.6 / 50.0.
			Param.of("hue", 0, 1, .505), Param.of("saturation", 0, 1, .788),
			Param.of("brightness", 0, 255, 163.6), Param.of("spread", 0, 64, 50),
			Param.of("max_gain", .5, 4, 2), Param.ofInt("levels", 0, 8, 0)
		);
	}

	@Override
	public String animationSource(String output) {
		return FACES.entrySet().stream().filter(entry -> entry.getValue().equals(output)).map(Map.Entry::getKey).findFirst().orElse(null);
	}

	@Override
	public Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		Palette palette = new Palette(params.get("hue"), params.get("saturation"), params.get("brightness"));
		Map<String, BufferedImage> derived = new LinkedHashMap<>();
		for (Map.Entry<String, String> face : FACES.entrySet()) {
			BufferedImage fire = sources.get(face.getKey());
			if (fire != null && Ops.scaleOf(fire) != 0) {
				derived.put(face.getValue(), palette.repaint(fire, params.get("spread"), params.get("max_gain"), params.getInt("levels")));
			}
		}
		return derived;
	}
}
