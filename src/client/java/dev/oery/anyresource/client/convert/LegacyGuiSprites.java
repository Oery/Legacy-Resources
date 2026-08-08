package dev.oery.anyresource.client.convert;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where each modern {@code textures/gui/sprites/**} HUD sprite lives inside a legacy pack's two
 * monolithic GUI sheets.
 * <p>
 * Pre-1.13 Minecraft painted the entire HUD onto {@code textures/gui/icons.png} (hearts, armor,
 * food, air, the XP/jump/boss bars, the crosshair, the tab-list ping bars) and
 * {@code textures/gui/widgets.png} (hotbar and its selection box), cropping cells out of them at
 * draw time. Modern Minecraft deleted both files: every element became its own PNG discovered by
 * a {@code minecraft:directory} sprite source over {@code gui/sprites} (see
 * {@code assets/minecraft/atlases/gui.json}). There is no path to alias one onto the other, so -
 * exactly like the fishing hook, the redstone dust textures and the compass frames - each modern
 * sprite is synthesized by cropping the legacy sheet at load time.
 * <p>
 * Every coordinate below is read out of {@code reference/1.8.9}'s own draw calls rather than
 * eyeballed, and was then confirmed by pixel-diffing the resulting crop of <em>vanilla</em>
 * 1.8.9's sheets against 26.2's own vanilla sprite files: all but five come out at zero differing
 * pixels, and those five differ by 2-27 px out of 81 purely because Mojang retouched the art
 * (their coordinates are still pinned by the source code). The draw calls:
 * <ul>
 *   <li>crosshair {@code GuiIngame.java:147}, bars {@code :301-327}, boss bar {@code :640-652}</li>
 *   <li>armor {@code :494-506}, hearts {@code :507-552}, food {@code :556-586},
 *   mount hearts {@code :600-616}, air {@code :617-632}</li>
 *   <li>hotbar + selection {@code :279-285}</li>
 *   <li>ping bars {@code GuiPlayerTabOverlay.java:170-177}</li>
 * </ul>
 * <p>
 * Coordinates are expressed at 1.8.9's own {@value #SHEET_BASE_SIZE}px sheet resolution and get
 * scaled by the pack's multiplier at crop time (HD packs ship 512/1024/2048px sheets), the same
 * way {@code CHEST_BASE_CANVAS_SIZE} works for chests in {@link LegacyPackResources}.
 * <p>
 * Sprites with no 1.8.9 equivalent are deliberately absent so they fall back to vanilla's own
 * art, the same call already made for the biome cow variants in {@code ENTITY_TEXTURE_ALIASES}:
 * {@code hud/heart/frozen_*} (powder snow,
 * 1.17), {@code hud/air_empty}, {@code hud/effect_background*}, {@code hud/hotbar_offhand_*} and
 * every attack-indicator sprite (1.9), {@code hud/locator_bar_*} (1.21.6),
 * {@code hud/jump_bar_cooldown}, and {@code boss_bar/notched_*} (transparent notch overlays drawn
 * <em>on top of</em> the color bar, per {@code BossHealthOverlay.java:84-86} - not bars in their
 * own right).
 */
final class LegacyGuiSprites {
	/** Sheet resolution vanilla 1.8.9 ships; a pack's own multiplier is its width divided by this. */
	static final int SHEET_BASE_SIZE = 256;

	/**
	 * The modern sprite whose canvas genuinely grew: 1.8.9 draws the hotbar selection box as
	 * 24x22 ({@code GuiIngame.java:285}), modern draws it 24x23 - one extra bottom bevel row.
	 * Vanilla's own {@code widgets.png} does carry that row at y=44 (the crop below is a
	 * pixel-exact match against modern's sprite), but 26 of the 70 real legacy packs surveyed
	 * cleared it, having only ever authored the 22 rows their game used. The sprite has to be 23
	 * rows tall or modern stretches it, so {@link LegacyPackResources} fills a fully transparent
	 * bottom row by repeating the row above it - a 1px thicker bottom border on those packs,
	 * rather than a selection box with no bottom edge at all.
	 */
	static final String HOTBAR_SELECTION = "hud/hotbar_selection.png";

	/**
	 * 1.8.9 reserves a 16x16 cell for the crosshair but modern's sprite is 15x15, drawn at a
	 * hardcoded 15x15 ({@code Hud.java:485}). Cropping 15x15 keeps vanilla-shaped crosshairs
	 * pixel-exact and evenly scaled; the 24-of-69 surveyed packs that do paint into the 16th
	 * row/column lose 1px there. The alternative - serving the full 16x16 cell - would preserve
	 * their art but squeeze every crosshair by 15/16, giving uneven pixel steps on the majority
	 * that don't need it.
	 */
	private static final int CROSSHAIR_SIZE = 15;

	/** Row offset added to every heart sprite's {@code v} for its hardcore variant ({@code 9 * 5}). */
	private static final int HARDCORE_ROW_OFFSET = 45;

	/** Boss bar colors modern renders, all fed from 1.8.9's single bar - see {@link #putBossBars}. */
	private static final List<String> BOSS_BAR_COLORS =
		List.of("pink", "blue", "red", "green", "yellow", "purple", "white");

	/** Key: path relative to {@code textures/gui/sprites/}. */
	static final Map<String, SheetCrop> SPRITES = build();

	private LegacyGuiSprites() {
	}

	enum Sheet {
		ICONS("textures/gui/icons.png"),
		WIDGETS("textures/gui/widgets.png");

		private final String legacyPath;

		Sheet(String legacyPath) {
			this.legacyPath = legacyPath;
		}

		String legacyPath() {
			return legacyPath;
		}
	}

	/** A cell of {@code sheet}, in 1.8.9's {@value #SHEET_BASE_SIZE}px sheet coordinates. */
	record SheetCrop(Sheet sheet, int u, int v, int w, int h) {
	}

	private static Map<String, SheetCrop> build() {
		Map<String, SheetCrop> map = new LinkedHashMap<>();

		map.put("hud/crosshair.png", icons(0, 0, CROSSHAIR_SIZE, CROSSHAIR_SIZE));

		// 182x5 bars. The XP and jump bars share a layout; the progress row sits 5px under its
		// background row in every case.
		map.put("hud/experience_bar_background.png", bar(64));
		map.put("hud/experience_bar_progress.png", bar(69));
		map.put("hud/jump_bar_background.png", bar(84));
		map.put("hud/jump_bar_progress.png", bar(89));

		map.put("hud/armor_empty.png", icon(16, 9));
		map.put("hud/armor_half.png", icon(25, 9));
		map.put("hud/armor_full.png", icon(34, 9));

		map.put("hud/air.png", icon(16, 18));
		map.put("hud/air_bursting.png", icon(25, 18));

		// Food shares the heart layout's shape one row down (v=27): a base column for the empty
		// slot, +36/+45 for full/half, and a second column base 36 further right for the
		// hunger-effect (rotten) variants.
		map.put("hud/food_empty.png", icon(16, 27));
		map.put("hud/food_full.png", icon(52, 27));
		map.put("hud/food_half.png", icon(61, 27));
		map.put("hud/food_empty_hunger.png", icon(133, 27));
		map.put("hud/food_full_hunger.png", icon(88, 27));
		map.put("hud/food_half_hunger.png", icon(97, 27));

		// The empty heart outline, which 1.8.9 draws at "16 + blinking * 9" regardless of any
		// status effect, unlike the filled hearts below.
		map.put("hud/heart/container.png", icon(16, 0));
		map.put("hud/heart/container_blinking.png", icon(25, 0));
		map.put("hud/heart/container_hardcore.png", icon(16, HARDCORE_ROW_OFFSET));
		map.put("hud/heart/container_hardcore_blinking.png", icon(25, HARDCORE_ROW_OFFSET));

		// 1.8.9 picks a column base per status effect (16 normal, +36 poison, +72 wither) and then
		// offsets within it: +36 full, +45 half, +54/+63 for their blinking (recently-damaged)
		// counterparts, +144/+153 for absorption.
		putHeartVariant(map, "", 52, 61, 70, 79);
		putHeartVariant(map, "poisoned_", 88, 97, 106, 115);
		putHeartVariant(map, "withered_", 124, 133, 142, 151);
		// Absorption's +144/+153 offsets are only ever taken off the base (non-status) column, and
		// 1.8.9 has no blinking absorption art at all - its blink and absorption branches are
		// independent draws over the same heart. Mapping the blinking names onto the static cells
		// keeps golden hearts on the pack's own art instead of flickering to vanilla's on damage.
		putHeartVariant(map, "absorbing_", 160, 169, 160, 169);

		// Mount/vehicle health, drawn from its own trio on the armor row ("ae = 52", +36/+45).
		map.put("hud/heart/vehicle_container.png", icon(52, 9));
		map.put("hud/heart/vehicle_full.png", icon(88, 9));
		map.put("hud/heart/vehicle_half.png", icon(97, 9));

		putBossBars(map);

		// Tab-list and server-list ping bars, one 10x8 row per quality band. 1.8.9 orders them
		// best-first (m=0 is the full 5 bars) and puts "no connection" last; modern names them
		// worst-first, so the rows reverse.
		map.put("icon/ping_5.png", ping(176));
		map.put("icon/ping_4.png", ping(184));
		map.put("icon/ping_3.png", ping(192));
		map.put("icon/ping_2.png", ping(200));
		map.put("icon/ping_1.png", ping(208));
		map.put("icon/ping_unknown.png", ping(216));

		map.put("hud/hotbar.png", new SheetCrop(Sheet.WIDGETS, 0, 0, 182, 22));
		map.put(HOTBAR_SELECTION, new SheetCrop(Sheet.WIDGETS, 0, 22, 24, 23));

		return Collections.unmodifiableMap(map);
	}

	/**
	 * Adds the eight {@code full}/{@code half} x {@code blinking} x {@code hardcore} sprites of one
	 * heart status group. Hardcore is the same column one row band down, per
	 * {@link #HARDCORE_ROW_OFFSET}.
	 */
	private static void putHeartVariant(
		Map<String, SheetCrop> map, String prefix, int fullU, int halfU, int fullBlinkingU, int halfBlinkingU
	) {
		for (boolean hardcore : new boolean[] { false, true }) {
			String name = prefix + (hardcore ? "hardcore_" : "");
			int v = hardcore ? HARDCORE_ROW_OFFSET : 0;
			map.put("hud/heart/" + name + "full.png", icon(fullU, v));
			map.put("hud/heart/" + name + "half.png", icon(halfU, v));
			map.put("hud/heart/" + name + "full_blinking.png", icon(fullBlinkingU, v));
			map.put("hud/heart/" + name + "half_blinking.png", icon(halfBlinkingU, v));
		}
	}

	/**
	 * 1.8.9 has exactly one boss bar - the wither/dragon one, which is pixel-identical to modern's
	 * {@code boss_bar/pink_*} - because boss bar colors only arrived with the 1.9 boss bar API. Every
	 * modern color is fed from it, so a pack's bar shows up for every boss rather than only the
	 * ender dragon; the cost is that modern's per-boss color coding is lost while such a pack is on.
	 */
	private static void putBossBars(Map<String, SheetCrop> map) {
		for (String color : BOSS_BAR_COLORS) {
			map.put("boss_bar/" + color + "_background.png", bar(74));
			map.put("boss_bar/" + color + "_progress.png", bar(79));
		}
	}

	/** A full-width 182x5 bar row of {@code icons.png}. */
	private static SheetCrop bar(int v) {
		return icons(0, v, 182, 5);
	}

	/** One of the 9x9 heart/armor/food/air cells of {@code icons.png}. */
	private static SheetCrop icon(int u, int v) {
		return icons(u, v, 9, 9);
	}

	/** One 10x8 ping-bar row of {@code icons.png}. */
	private static SheetCrop ping(int v) {
		return icons(0, v, 10, 8);
	}

	private static SheetCrop icons(int u, int v, int w, int h) {
		return new SheetCrop(Sheet.ICONS, u, v, w, h);
	}
}
