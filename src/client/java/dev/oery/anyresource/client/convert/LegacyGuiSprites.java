package dev.oery.anyresource.client.convert;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

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

	private static final int BUTTON_WIDTH = 200;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_DISABLED_ROW = 46;
	private static final int BUTTON_NORMAL_ROW = 66;
	private static final int BUTTON_HOVERED_ROW = 86;
	/** Width of each of the two button-edge slices 1.8.9 stitches into the slider knob. */
	private static final int SLIDER_KNOB_HALF = 4;

	/** Key: path relative to {@code textures/gui/sprites/}. */
	static final Map<String, SheetCrop> SPRITES = build();

	/**
	 * Sprite metadata that has to travel with the sprite, keyed the same way as {@link #SPRITES}.
	 * <p>
	 * Modern stretches a GUI sprite to whatever size it is drawn at, unless a {@code .png.mcmeta}
	 * declares nine-slice scaling - which is how a 200x20 button texture survives being drawn 98 or
	 * 310 wide. Vanilla ships exactly such a file for each sprite below, but it does <em>not</em>
	 * get applied to ours: {@code FallbackResourceManager.listResources} only honours metadata whose
	 * pack sits at or above the pack supplying the image ({@code metaResource.packIndex >=
	 * resource.packIndex}), and vanilla is always below a resource pack. So overriding the PNG alone
	 * silently drops the nine-slice and stretches every non-200px-wide button. These copies of
	 * vanilla's values are served and listed alongside the images to keep it.
	 * <p>
	 * The widths/heights here are the sprite's <em>nominal</em> size, independent of the pack's own
	 * resolution, so they stay 200x20 even when the synthesized image is 400x40 or larger. Sprites
	 * absent from this map (the lock icons, and every HUD sprite) have no vanilla {@code .mcmeta}
	 * either, so plain stretch scaling is already correct for them.
	 */
	static final Map<String, String> SPRITE_METADATA = buildMetadata();

	private LegacyGuiSprites() {
	}

	enum Sheet {
		ICONS("textures/gui/icons.png"),
		WIDGETS("textures/gui/widgets.png"),
		CREATIVE_TABS("textures/gui/container/creative_inventory/tabs.png");

		private final String legacyPath;

		Sheet(String legacyPath) {
			this.legacyPath = legacyPath;
		}

		String legacyPath() {
			return legacyPath;
		}
	}

	/**
	 * A cell of {@code sheet}, in 1.8.9's {@value #SHEET_BASE_SIZE}px sheet coordinates.
	 * <p>
	 * {@code second}, when present, is a further cell butted against the right edge of the first:
	 * 1.8.9 had no nine-slice scaling, so where a widget needed to stretch it was drawn as two
	 * separate blits of the sheet's left and right edges (buttons via
	 * {@code GuiButton.java:68-69}, the slider knob via {@code GuiSlider.java:85-86}), and the
	 * modern single sprite has to be stitched back together from both.
	 */
	record SheetCrop(Sheet sheet, int u, int v, int w, int h, @Nullable SheetCrop second) {
		SheetCrop(Sheet sheet, int u, int v, int w, int h) {
			this(sheet, u, v, w, h, null);
		}

		/** Total width of the stitched sprite. */
		int totalWidth() {
			return second == null ? w : w + second.w();
		}
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

		putWidgets(map);

		// Creative inventory tabs. 1.8.9 paints every tab button and the scroller onto one
		// 256x256 sheet (textures/gui/container/creative_inventory/tabs.png); modern split it into
		// 30 individual sprites under textures/gui/sprites/container/creative_inventory/. Each legacy
		// cell is 28x32 (GuiContainerCreative.func_147051_a: j = i * 28, drawTexturedModalRect(...,28,32)),
		// but modern's sprites are 26x32, so the crop is offset +1 to centre the tab point (same
		// 1px-each-side call as the crosshair's 16->15). The four v-bands are: top unselected v=0,
		// top selected v=32, bottom unselected v=64, bottom selected v=96. 1.8.9 has only six tab
		// columns (0-5); _7 (column 6) is blank in legacy packs and falls back to vanilla's sprite.
		// The scroller is a pixel-exact 12x15 match at u=232/244, v=0
		// (GuiContainerCreative: drawTexturedModalRect(..., 232 + (needsScrollBars ? 0 : 12), 0, 12, 15)).
		putCreativeTabs(map);

		return Collections.unmodifiableMap(map);
	}

	/**
	 * Menu widgets, all from {@code widgets.png}. {@code GuiButton.java:68} draws the button as
	 * {@code 0, 46 + state * 20, 200, 20}, where the state is 0 when the button is disabled, 1
	 * normally and 2 while hovered ({@code GuiButton.getHoverState}); the disabled and normal rows
	 * are pixel-identical to modern's own {@code button_disabled}/{@code button} sprites, which
	 * pins the mapping. The hovered row is not - 1.8.9 tinted the whole button blue where modern
	 * draws a white outline instead - but it is unambiguously the same state, so a pack's hover art
	 * lands where a pack author expects it.
	 * <p>
	 * The slider is the same texture wearing two hats: {@code GuiSlider.getHoverState} is
	 * hardcoded to 0, so its track is always the *disabled* button row, and its 8x20 knob is
	 * stitched from the leftmost and rightmost 4px of the *normal* row
	 * ({@code GuiSlider.java:85-86}). 1.8.9 had no focused-slider state, so modern's
	 * {@code _highlighted} variants reuse the same art rather than falling back to vanilla's and
	 * flickering between two styles.
	 */
	private static void putWidgets(Map<String, SheetCrop> map) {
		map.put("widget/button.png", button(BUTTON_NORMAL_ROW));
		map.put("widget/button_highlighted.png", button(BUTTON_HOVERED_ROW));
		map.put("widget/button_disabled.png", button(BUTTON_DISABLED_ROW));

		map.put("widget/slider.png", button(BUTTON_DISABLED_ROW));
		map.put("widget/slider_highlighted.png", button(BUTTON_DISABLED_ROW));
		SheetCrop knob = new SheetCrop(
			Sheet.WIDGETS, 0, BUTTON_NORMAL_ROW, SLIDER_KNOB_HALF, BUTTON_HEIGHT,
			new SheetCrop(Sheet.WIDGETS, BUTTON_WIDTH - SLIDER_KNOB_HALF, BUTTON_NORMAL_ROW, SLIDER_KNOB_HALF, BUTTON_HEIGHT)
		);
		map.put("widget/slider_handle.png", knob);
		map.put("widget/slider_handle_highlighted.png", knob);

		// The world-options difficulty lock, a 2x3 block of 20x20 icons per
		// GuiLockIconButton.Icon's (u, v) pairs.
		map.put("widget/locked_button.png", lockIcon(0, 146));
		map.put("widget/locked_button_highlighted.png", lockIcon(0, 166));
		map.put("widget/locked_button_disabled.png", lockIcon(0, 186));
		map.put("widget/unlocked_button.png", lockIcon(20, 146));
		map.put("widget/unlocked_button_highlighted.png", lockIcon(20, 166));
		map.put("widget/unlocked_button_disabled.png", lockIcon(20, 186));
	}

	private static void putCreativeTabs(Map<String, SheetCrop> map) {
		// Six tab columns map directly; _7 (column 6) is blank in legacy but announced so atlas
		// discovery finds it — it just won't resolve from a legacy pack, falling back to vanilla's.
		for (int col = 0; col < 7; col++) {
			int u = col * 28 + 1; // +1: centre the 26-wide crop in the 28-wide cell
			String suffix = "_" + (col + 1) + ".png";
			map.put("container/creative_inventory/tab_top_unselected" + suffix, creativeTab(u, 0));
			map.put("container/creative_inventory/tab_top_selected" + suffix, creativeTab(u, 32));
			map.put("container/creative_inventory/tab_bottom_unselected" + suffix, creativeTab(u, 64));
			map.put("container/creative_inventory/tab_bottom_selected" + suffix, creativeTab(u, 96));
		}
		map.put("container/creative_inventory/scroller.png", new SheetCrop(Sheet.CREATIVE_TABS, 232, 0, 12, 15));
		map.put("container/creative_inventory/scroller_disabled.png", new SheetCrop(Sheet.CREATIVE_TABS, 244, 0, 12, 15));
	}

	private static SheetCrop creativeTab(int u, int v) {
		return new SheetCrop(Sheet.CREATIVE_TABS, u, v, 26, 32);
	}

	/** See {@link #SPRITE_METADATA}; values copied from 26.2's own {@code widget/*.png.mcmeta}. */
	private static Map<String, String> buildMetadata() {
		Map<String, String> map = new LinkedHashMap<>();
		map.put("widget/button.png", nineSlice(BUTTON_WIDTH, BUTTON_HEIGHT, 3));
		map.put("widget/button_highlighted.png", nineSlice(BUTTON_WIDTH, BUTTON_HEIGHT, 3));
		map.put("widget/button_disabled.png", nineSlice(BUTTON_WIDTH, BUTTON_HEIGHT, 1));
		map.put("widget/slider.png", nineSlice(BUTTON_WIDTH, BUTTON_HEIGHT, 1));
		map.put("widget/slider_highlighted.png", nineSlice(BUTTON_WIDTH, BUTTON_HEIGHT, 1));
		String knob = "{\"gui\":{\"scaling\":{\"type\":\"nine_slice\",\"width\":8,\"height\":20,"
			+ "\"border\":{\"left\":2,\"top\":2,\"right\":2,\"bottom\":3}}}}";
		map.put("widget/slider_handle.png", knob);
		map.put("widget/slider_handle_highlighted.png", knob);
		return Collections.unmodifiableMap(map);
	}

	private static String nineSlice(int width, int height, int border) {
		return "{\"gui\":{\"scaling\":{\"type\":\"nine_slice\",\"width\":" + width
			+ ",\"height\":" + height + ",\"border\":" + border + "}}}";
	}

	private static SheetCrop button(int v) {
		return new SheetCrop(Sheet.WIDGETS, 0, v, BUTTON_WIDTH, BUTTON_HEIGHT);
	}

	private static SheetCrop lockIcon(int u, int v) {
		return new SheetCrop(Sheet.WIDGETS, u, v, 20, 20);
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

	/**
	 * The title screen logo, which needs relaying rather than cropping - and unlike everything
	 * above is a plain texture, not an atlas sprite, so {@link LegacyPackResources} only has to
	 * intercept {@code getResource} for it.
	 * <p>
	 * Both versions call the file {@code textures/gui/title/minecraft.png}, which is exactly why it
	 * renders wrong today: the legacy file passes straight through and modern reads a completely
	 * different layout out of it. 1.8.9 stores the wordmark as two stacked 155x44 rows - at (0,0)
	 * and (0,45) of a square sheet - and draws them side by side ({@code GuiMainMenu.java:437-438}),
	 * so the logo only exists as a 310-wide strip once assembled. Modern instead blits a single
	 * {@code (0,0,256,44)} region out of a nominally 256x64 texture
	 * ({@code LogoRenderer.extractRenderState}), so what the game currently shows is the top-left
	 * corner of the legacy sheet: the first half of the wordmark, and nothing else.
	 * <p>
	 * The two canvases also disagree on width. 1.8.9's own {@code i = 274} centering constant says
	 * the artwork occupies 274 of those 310 columns (the remainder is padding), and measuring all
	 * 57 real packs that ship a logo confirms it - none paints beyond 274. Modern's slot is 256
	 * wide. So the assembled 274x44 artwork is fitted onto the 256x44 slot, which costs a 6.6%
	 * horizontal squeeze; the alternative, preserving the aspect ratio by letterboxing it to
	 * 256x41, leaves the logo visibly smaller than every other pack's and than vanilla's own.
	 */
	static final String LOGO_PATH = "textures/gui/title/minecraft.png";
	/** Modern's easter-egg logo, a 1-in-10000 roll in {@code LogoRenderer}. */
	static final String EASTER_EGG_LOGO_PATH = "textures/gui/title/minceraft.png";
	/** Width of the assembled legacy strip that actually holds artwork; see {@link #LOGO_PATH}. */
	static final int LOGO_LEGACY_WIDTH = 274;
	/** Nominal canvas modern reads the logo out of, and the region of it that gets drawn. */
	static final int LOGO_CANVAS_WIDTH = 256;
	static final int LOGO_CANVAS_HEIGHT = 64;
	static final int LOGO_HEIGHT = 44;

	/** A piece of the legacy logo sheet, copied to {@code dx} in the assembled strip. */
	record LogoPiece(int u, int v, int w, int dx) {
	}

	/** {@code GuiMainMenu.java:437-438} - the plain wordmark, two rows laid side by side. */
	static final List<LogoPiece> LOGO_PIECES = List.of(
		new LogoPiece(0, 0, 155, 0),
		new LogoPiece(0, 45, 155, 155)
	);

	/**
	 * {@code GuiMainMenu.java:431-435} - the "MINCERAFT" easter egg, which 1.8.9 produced by
	 * re-cutting the same sheet rather than shipping a second file (modern gave it its own
	 * {@link #EASTER_EGG_LOGO_PATH}). Reproduced piece for piece, including vanilla's own 1px
	 * overlap between the third and fourth blits, so a legacy pack's art keeps the joke.
	 */
	static final List<LogoPiece> EASTER_EGG_LOGO_PIECES = List.of(
		new LogoPiece(0, 0, 99, 0),
		new LogoPiece(129, 0, 27, 99),
		new LogoPiece(126, 0, 3, 125),
		new LogoPiece(99, 0, 26, 128),
		new LogoPiece(0, 45, 155, 155)
	);
}
