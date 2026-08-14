package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

/**
 * Pillager skin from the pack's generic villager skin.
 *
 * <p>Both use a 64px biped skin canvas. Illager-specific uniform and crossbow details did not
 * exist in a legacy pack, so this deliberately retains the pack's villager face and robe rather
 * than inserting incompatible vanilla pixels. Packs that already ship a modern pillager texture
 * still win before this fallback is considered.</p>
 */
final class Pillager implements Derivation {
	private static final String SOURCE = "entity/villager/villager";
	private static final String OUTPUT = "entity/illager/pillager";

	@Override public String id() { return "pillager"; }
	@Override public List<String> sources() { return List.of(SOURCE); }
	@Override public List<String> outputs() { return List.of(OUTPUT); }
	@Override public List<Param> params() { return List.of(); }

	@Override
	public Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		BufferedImage villager = sources.get(SOURCE);
		return villager == null || villager.getWidth() != 64 || villager.getHeight() != 64
			? Map.of() : Map.of(OUTPUT, Ops.copy(villager));
	}
}
