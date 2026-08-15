package dev.oery.legacyresources.client.convert;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Exact crops that unpack pre-1.13 particle sheets into the sprites used by the current atlas.
 * <p>
 * The indices come from every 1.8.9 {@code EntityFX} implementation: generic animation 0-7,
 * rain/fishing wake 19-22, bubble 32, flame/lava 48-49, note/crit 64-66, villager/heart 80-82,
 * drops 112-114, spells 128-151, firework sparks 160-167, and enchantment glyphs 225-250.
 * Firework's overlay used a direct 32x32 UV crop, while large explosions used their own 4x4
 * entity sheet. Comparing those regions against the targeted vanilla assets confirms the modern
 * names below pixel-for-pixel (apart from three tiny vanilla art edits, whose old pixels we must
 * deliberately preserve for a custom pack).
 * <p>
 * Block/item breaking particles already sample the block and item atlases converted elsewhere.
 * The only other standalone legacy particle file is {@code footprint.png}; current Minecraft has
 * no footprint particle or sprite consumer, so there is no modern resource to serve it as. Later
 * particles intentionally retain vanilla art rather than being guessed from unrelated old cells.
 */
final class LegacyParticleSprites {
	static final String MODERN_TEXTURE_DIR = "textures/particle/";
	static final String MODERN_TEXTURE_ROOT = "textures/particle";
	static final String LEGACY_PARTICLE_SHEET = "textures/particle/particles.png";
	static final String LEGACY_EXPLOSION_SHEET = "textures/entity/explosion.png";
	static final String FISHING_HOOK_TEXTURE = "textures/entity/fishing/fishing_hook.png";

	private static final int PARTICLE_GRID = 16;
	private static final int EXPLOSION_GRID = 4;
	private static final Map<String, Crop> CROPS = buildCrops();

	private LegacyParticleSprites() { }

	static @Nullable Crop crop(String modernPath) {
		return CROPS.get(modernPath);
	}

	static Set<String> modernPaths() {
		return CROPS.keySet();
	}

	private static Map<String, Crop> buildCrops() {
		Map<String, Crop> crops = new LinkedHashMap<>();
		sequence(crops, "generic_", 8, 0);
		sequence(crops, "splash_", 4, 19);
		cell(crops, "bubble", 32);
		cell(crops, "flame", 48);
		cell(crops, "lava", 49);
		cell(crops, "note", 64);
		cell(crops, "critical_hit", 65);
		cell(crops, "enchanted_hit", 66);
		cell(crops, "heart", 80);
		cell(crops, "angry", 81);
		cell(crops, "glint", 82);
		cell(crops, "drip_fall", 112);
		cell(crops, "drip_hang", 113);
		cell(crops, "drip_land", 114);
		sequence(crops, "effect_", 8, 128);
		sequence(crops, "spell_", 8, 144);
		sequence(crops, "spark_", 8, 160);
		for (int letter = 0; letter < 26; letter++) {
			cell(crops, "sga_" + (char) ('a' + letter), 225 + letter);
		}

		// EntityFirework.OverlayFX sampled this 32x32 region directly from particles.png.
		crops.put(MODERN_TEXTURE_DIR + "flash.png", new Crop(LEGACY_PARTICLE_SHEET, PARTICLE_GRID, PARTICLE_GRID, 4, 2, 4, 4));
		for (int frame = 0; frame < 16; frame++) {
			crops.put(MODERN_TEXTURE_DIR + "explosion_" + frame + ".png",
				new Crop(LEGACY_EXPLOSION_SHEET, EXPLOSION_GRID, EXPLOSION_GRID, frame % 4, frame / 4, 1, 1));
		}
		// The old fishing renderer sampled particle index 33 instead of loading an entity texture.
		crops.put(FISHING_HOOK_TEXTURE, particleCell(33));
		return Map.copyOf(crops);
	}

	private static void sequence(Map<String, Crop> crops, String stem, int count, int firstIndex) {
		for (int frame = 0; frame < count; frame++) {
			cell(crops, stem + frame, firstIndex + frame);
		}
	}

	private static void cell(Map<String, Crop> crops, String stem, int index) {
		crops.put(MODERN_TEXTURE_DIR + stem + ".png", particleCell(index));
	}

	private static Crop particleCell(int index) {
		return new Crop(LEGACY_PARTICLE_SHEET, PARTICLE_GRID, PARTICLE_GRID, index % PARTICLE_GRID, index / PARTICLE_GRID, 1, 1);
	}

	record Crop(String legacyPath, int columns, int rows, int column, int row, int width, int height) { }
}
