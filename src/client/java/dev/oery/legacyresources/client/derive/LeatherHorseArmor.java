package dev.oery.legacyresources.client.derive;

import java.util.List;
import java.util.Map;

/** Leather horse armour retains iron armour's pack-specific plates on a warm leather ramp. */
final class LeatherHorseArmor extends RampRecolor {
	private static final Map<String, String> PIECES = Map.of(
		"item/iron_horse_armor", "item/leather_horse_armor",
		"entity/horse/armor/horse_armor_iron", "entity/horse/armor/horse_armor_leather"
	);
	@Override public String id() { return "leather_horse_armor"; }
	@Override protected Map<String, String> pieces() { return PIECES; }
	@Override public List<Param> params() {
		return List.of(
			Param.of("shadow", 0, 255, 39), Param.of("highlight", 0, 255, 168), Param.of("gamma", .25, 3, 1),
			Param.of("auto_level", 0, 1, .7), Param.of("target_mean", 0, 255, 103), Param.of("hue", 0, 360, 27),
			Param.of("saturation_shadow", 0, 1, .62), Param.of("saturation_highlight", 0, 1, .42), Param.of("keep_hue", 0, 1, 0)
		);
	}
}
