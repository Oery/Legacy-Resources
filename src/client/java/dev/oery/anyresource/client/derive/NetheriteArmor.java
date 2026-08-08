package dev.oery.anyresource.client.derive;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The netherite armour set - the four item icons plus the layers drawn on the player - recoloured
 * from the pack's diamond armour. See {@link NetheriteRecolor} for how the recolour works.
 */
final class NetheriteArmor extends NetheriteRecolor {
	/**
	 * The three {@code entity/equipment/**} entries are the layers drawn on the player. They read as
	 * modern paths, but a legacy pack has no such files - {@code LegacyPackResources} resolves them
	 * back to the pre-1.13 {@code textures/models/armor/diamond_layer_*.png}, so the derivation gets
	 * the pack's real art without knowing anything about the old layout. {@code humanoid} and
	 * {@code humanoid_baby} both come from {@code _layer_1}, which is why they are separate outputs
	 * fed by separate (identical) sources.
	 */
	private static final Map<String, String> PIECES = build();

	@Override
	public String id() {
		return "netherite_armor";
	}

	@Override
	protected Map<String, String> pieces() {
		return PIECES;
	}

	/** Tuned in the lab across the pack corpus; see {@link NetheriteRecolor#params} for what each does. */
	@Override
	public List<Param> params() {
		return List.of(
			Param.of("shadow", 0, 255, 18),
			Param.of("highlight", 0, 255, 63.75),
			Param.of("gamma", 0.25, 3, 1),
			Param.of("auto_level", 0, 1, 1),
			Param.of("target_mean", 0, 255, 53),
			Param.of("hue", 0, 360, 306),
			Param.of("saturation_shadow", 0, 0.6, 0.22),
			Param.of("saturation_highlight", 0, 0.6, 0.1),
			Param.of("keep_hue", 0, 1, 0.4)
		);
	}

	private static Map<String, String> build() {
		Map<String, String> map = new LinkedHashMap<>(pieces("item/", "helmet", "chestplate", "leggings", "boots"));
		for (String layer : new String[] { "humanoid", "humanoid_baby", "humanoid_leggings" }) {
			map.put("entity/equipment/" + layer + "/diamond", "entity/equipment/" + layer + "/netherite");
		}
		return Collections.unmodifiableMap(map);
	}
}
