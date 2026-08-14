package dev.oery.legacyresources.client.convert;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Legacy-to-modern entity texture paths whose existing renderer/model still uses the same UV
 * layout. More involved entries live in the entity compatibility registry instead: their texture
 * cannot safely be exposed until its classic model and render layers are available too.
 *
 * <p>Paths include {@code textures/} and the {@code .png} suffix because these resources are
 * loaded directly by renderers rather than through one of Minecraft's texture atlases.
 */
public final class EntityTextureMappings {
	private static final Map<String, String> VANILLA_COMPATIBLE = Map.ofEntries(
		Map.entry("textures/entity/armorstand/armorstand.png", "textures/entity/armorstand/wood.png"),
		Map.entry("textures/entity/projectiles/arrow.png", "textures/entity/arrow.png"),
		Map.entry("textures/entity/beacon/beacon_beam.png", "textures/entity/beacon_beam.png"),
		Map.entry("textures/entity/blaze/blaze.png", "textures/entity/blaze.png"),
		Map.entry("textures/entity/cow/cow_temperate_baby.png", "textures/entity/cow/cow.png"),
		Map.entry("textures/entity/cow/mooshroom_red.png", "textures/entity/cow/mooshroom.png"),
		Map.entry("textures/entity/cow/mooshroom_red_baby.png", "textures/entity/cow/mooshroom.png"),
		// Temperate variants retain the 1.8.9 adult model/UV layout. Warm/cold variants are new art.
		Map.entry("textures/entity/chicken/chicken_temperate.png", "textures/entity/chicken.png"),
		Map.entry("textures/entity/pig/pig_temperate.png", "textures/entity/pig/pig.png"),
		// 26.2 selects dedicated puppy sheets. The classic puppy model instead scales the adult
		// 64x32 layout, so each default-wolf baby pass must keep sampling its 1.8.9 adult sheet.
		Map.entry("textures/entity/wolf/wolf_baby.png", "textures/entity/wolf/wolf.png"),
		Map.entry("textures/entity/wolf/wolf_tame_baby.png", "textures/entity/wolf/wolf_tame.png"),
		Map.entry("textures/entity/wolf/wolf_angry_baby.png", "textures/entity/wolf/wolf_angry.png"),
		Map.entry("textures/entity/wolf/wolf_collar_baby.png", "textures/entity/wolf/wolf_collar.png"),
		// The cat renderer selects the classic feline mesh before consuming these adult/baby aliases.
		Map.entry("textures/entity/cat/cat_black.png", "textures/entity/cat/black.png"),
		Map.entry("textures/entity/cat/cat_black_baby.png", "textures/entity/cat/black.png"),
		Map.entry("textures/entity/cat/cat_red.png", "textures/entity/cat/red.png"),
		Map.entry("textures/entity/cat/cat_red_baby.png", "textures/entity/cat/red.png"),
		Map.entry("textures/entity/cat/cat_siamese.png", "textures/entity/cat/siamese.png"),
		Map.entry("textures/entity/cat/cat_siamese_baby.png", "textures/entity/cat/siamese.png"),
		Map.entry("textures/entity/enchantment/enchanting_table_book.png", "textures/entity/enchanting_table_book.png"),
		Map.entry("textures/entity/end_crystal/end_crystal.png", "textures/entity/endercrystal/endercrystal.png"),
		Map.entry("textures/entity/end_crystal/end_crystal_beam.png", "textures/entity/endercrystal/endercrystal_beam.png"),
		Map.entry("textures/entity/end_portal/end_portal.png", "textures/entity/end_portal.png"),
		Map.entry("textures/entity/endermite/endermite.png", "textures/entity/endermite.png"),
		Map.entry("textures/entity/experience/experience_orb.png", "textures/entity/experience_orb.png"),
		Map.entry("textures/entity/guardian/guardian.png", "textures/entity/guardian.png"),
		Map.entry("textures/entity/guardian/guardian_beam.png", "textures/entity/guardian_beam.png"),
		Map.entry("textures/entity/guardian/guardian_elder.png", "textures/entity/guardian_elder.png"),
		Map.entry("textures/entity/iron_golem/iron_golem.png", "textures/entity/iron_golem.png"),
		Map.entry("textures/entity/lead_knot/lead_knot.png", "textures/entity/lead_knot.png"),
		Map.entry("textures/entity/minecart/minecart.png", "textures/entity/minecart.png"),
		Map.entry("textures/entity/silverfish/silverfish.png", "textures/entity/silverfish.png"),
		Map.entry("textures/entity/snow_golem/snow_golem.png", "textures/entity/snowman.png"),
		Map.entry("textures/entity/spider/spider_eyes.png", "textures/entity/spider_eyes.png"),
		Map.entry("textures/entity/squid/squid.png", "textures/entity/squid.png"),
		Map.entry("textures/entity/witch/witch.png", "textures/entity/witch.png")
	);

	private EntityTextureMappings() {
	}

	/** Returns the legacy file for a path that is safe to render with vanilla's current model. */
	public static @Nullable String vanillaCompatibleLegacyPath(String modernPath) {
		return VANILLA_COMPATIBLE.get(modernPath);
	}

	/** Complete table for the lab verifier; callers must not mutate it. */
	public static Map<String, String> vanillaCompatibleMappings() {
		return VANILLA_COMPATIBLE;
	}
}
