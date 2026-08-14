package dev.oery.legacyresources.client.derive;

import java.util.List;
import java.util.Map;

/** Netherite horse armour uses the legacy diamond plates on the same dark violet-grey ramp as netherite tools. */
final class NetheriteHorseArmor extends RampRecolor {
	private static final Map<String, String> PIECES = Map.of(
		"item/diamond_horse_armor", "item/netherite_horse_armor",
		"entity/horse/armor/horse_armor_diamond", "entity/horse/armor/horse_armor_netherite"
	);
	@Override public String id() { return "netherite_horse_armor"; }
	@Override protected Map<String, String> pieces() { return PIECES; }
	@Override public List<Param> params() {
		return List.of(
			Param.of("shadow", 0, 255, 18), Param.of("highlight", 0, 255, 74), Param.of("gamma", .25, 3, 1.15),
			Param.of("auto_level", 0, 1, 1), Param.of("target_mean", 0, 255, 54), Param.of("hue", 0, 360, 302),
			Param.of("saturation_shadow", 0, 1, .22), Param.of("saturation_highlight", 0, 1, .19), Param.of("keep_hue", 0, 1, .42)
		);
	}
}
