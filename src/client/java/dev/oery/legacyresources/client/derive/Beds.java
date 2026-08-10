package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Builds all sixteen dyed beds out of the one red bed a 1.8.9 pack ships.
 * <p>
 * Legacy beds are six block textures - {@code bed_{feet,head}_{end,side,top}} - and one blockstate. 1.13
 * split them into sixteen dyed variants over 115 files with a different UV layout, so today every bed in
 * a converted pack is vanilla's, sixteen foreign objects among hundreds of restyled neighbours. Two
 * things measured off {@code reference/} make deriving them exact rather than approximate:
 * <ul>
 *   <li><b>The geometry maps one to one.</b> Both eras give the mattress the same vertical UV bands -
 *   rows 7-10 cloth, row 10 its dark trim, rows 11-13 the wooden frame - because both models read the
 *   face from the same texture rows for the same block heights. Rows 0-13 of a modern face are a legacy
 *   face turned, and nothing more. Only the leg strip differs, and only in packing (see
 *   {@link #packLegs}).</li>
 *   <li><b>Vanilla's sixteen beds are one bed, palette-swapped.</b> Every dyed bed uses exactly seven
 *   cloth tones, at byte-identical pixel positions - the same 7/17/24/16 counts on the sides and
 *   43/118/68/27 on the tops, in all sixteen - over an identical pillow and identical oak frame. So
 *   {@link #RAMPS} is the whole difference between them, and applying it to a pack's own cloth is not an
 *   approximation of what vanilla did but the thing itself.</li>
 * </ul>
 * <p>
 * <b>Red keeps the pack's own pixels</b>, turned onto the modern layout and left alone - which is the
 * point of deriving at all, and means a pack whose bed is a deliberate maroon or a washed-out pink keeps
 * it. The exception is a pack whose bed is not red at all, teal or navy or aqua, which has no red art to
 * keep and would otherwise be handed fifteen correct beds and one that lies about its colour; those get
 * red built like any other dye. The other fifteen take that same art and move only its cloth onto vanilla's
 * ramp for their dye, so they keep the pack's shading, weave and contrast while reading unmistakably as
 * their colour. Beds are identified by colour at a glance and a bed that came out "faintly the pack's
 * hue" would be a bed you cannot name, so unlike {@link RampRecolor} nothing of the source hue is
 * held back.
 * <p>
 * No item texture is involved: modern {@code items/<colour>_bed.json} is a {@code minecraft:composite}
 * of the two block models, so vanilla supplies everything but these textures, and the legacy
 * {@code items/bed.png} goes unused.
 */
final class Beds implements Derivation {
	/** Row vanilla's wooden frame starts at; only a fallback, see {@link Rows}. */
	private static final int FRAME_TOP = 11;
	/** Texture row the leg strip starts at, in both eras. */
	private static final int LEG_TOP = 13;
	/** Width of one leg face in the strip; the legs are 3x3x3 boxes. */
	private static final int LEG = 3;

	/** The one colour that is the pack's own art rather than a recolour of it. */
	private static final String RED = "red";

	/** How a legacy texture is turned to land on the modern face that reads the same physical surface. */
	private enum Turn {
		KEEP,
		MIRROR,
		ROTATE
	}

	/** Which part of a finished face is cloth, and so eligible to be recoloured. */
	private enum Region {
		/** Everything above the leg strip: the mattress, its trim, and the frame under them. */
		BAND,
		/** All of it - the two tops are cloth edge to edge, pillow aside. */
		WHOLE,
		/** None: shared between all sixteen beds, so no dye can be applied to it. */
		NONE
	}

	/**
	 * One modern bed texture: where it comes from and what has to happen to it.
	 *
	 * @param output   modern path relative to {@code textures/}, with {@code %s} where the dye colour goes
	 * @param source   legacy texture it is turned from, in the same path form
	 * @param turn     how to turn it; see {@link #COLORED}
	 * @param legs     x offsets, in 16px units, of the 3-wide leg slots this texture packs into rows 13-16
	 * @param legFrom  the {@link #legs} the legacy leg art lands in once turned - a subset, and the only
	 *                 slots read rather than written
	 * @param pillowFree whether this face is all cloth above the frame, which the foot of a bed is and
	 *                 the head of one is not; see {@link #clothOf}
	 */
	private record Face(String output, String source, Turn turn, List<Integer> legs, List<Integer> legFrom,
		boolean pillowFree, Region region) {
		String output(@Nullable String color) {
			return color == null ? output : output.formatted(color);
		}
	}

	/**
	 * The seven faces a dyed bed needs, and how each is turned.
	 * <p>
	 * The turns were settled by scoring all eight dihedral transforms of each legacy texture against
	 * vanilla's own {@code red_bed_*}, classifying pixels as cloth, pillow, wood or
	 * transparent. Where it matters the winner is unambiguous: {@code bed_head_side} onto
	 * {@code head_west} scores 0.88 mirrored against 0.61 unmirrored - the pillow half moves from one
	 * end to the other - and {@code bed_head_top} onto {@code head_up} scores 0.90 rotated, the pillow
	 * moving from the right edge to the top. The rest follows from those: the two side faces of a pair
	 * are mirror images of each other, and a legacy west face is a modern east face because the two
	 * eras' blockstates sit 180 degrees apart for the same {@code facing} value (1.8.9 draws
	 * {@code facing=south} unrotated, modern draws {@code facing=north} unrotated).
	 * <p>
	 * The leg offsets are a straight reading of {@code template_bed_{foot,head}}'s UVs, and they
	 * corroborate the turns independently: {@code legFrom} - where 1.8.9's own models put each texture's
	 * painted leg, turned the same way - lands inside a slot the output declares, in all six cases.
	 */
	private static final List<Face> COLORED = List.of(
		new Face("block/%s_bed_foot_east", "block/bed_feet_side", Turn.KEEP,
			List.of(0, 3, 6), List.of(0), true, Region.BAND),
		new Face("block/%s_bed_foot_west", "block/bed_feet_side", Turn.MIRROR,
			List.of(7, 10, 13), List.of(13), true, Region.BAND),
		new Face("block/%s_bed_foot_south", "block/bed_feet_end", Turn.KEEP,
			List.of(0, 3, 10, 13), List.of(0, 13), true, Region.BAND),
		new Face("block/%s_bed_foot_up", "block/bed_feet_top", Turn.ROTATE,
			List.of(), List.of(), true, Region.WHOLE),
		new Face("block/%s_bed_head_east", "block/bed_head_side", Turn.KEEP,
			List.of(7, 10, 13), List.of(13), false, Region.BAND),
		new Face("block/%s_bed_head_west", "block/bed_head_side", Turn.MIRROR,
			List.of(0, 3, 6), List.of(0), false, Region.BAND),
		new Face("block/%s_bed_head_up", "block/bed_head_top", Turn.ROTATE,
			List.of(), List.of(), false, Region.WHOLE)
	);

	/**
	 * The two textures every bed shares, uncoloured in vanilla too - the pillow end, which is all pillow
	 * and so has no cloth to dye, and the underside, which modern beds take from a dedicated texture
	 * where 1.8.9 pointed the face straight at {@code planks_oak}.
	 * <p>
	 * Worth deriving even though they are colourless: without them a pack's bed keeps vanilla's pillow
	 * and vanilla's planks on two of its faces while the other five are the pack's own.
	 */
	private static final List<Face> SHARED = List.of(
		new Face("block/bed_head_north", "block/bed_head_end", Turn.KEEP,
			List.of(0, 3, 10, 13), List.of(0, 13), false, Region.NONE),
		new Face("block/bed_down", "block/oak_planks", Turn.KEEP,
			List.of(), List.of(), false, Region.NONE)
	);

	/**
	 * Each dye's cloth ramp, darkest first, read straight out of {@code reference/26.2}.
	 * <p>
	 * Vanilla draws a bed's cloth in seven tones - four on the sides and ends, four on the tops, sharing
	 * one where they meet. The join is exact in all sixteen dyes: the brightest tone of
	 * {@code <colour>_bed_foot_west} is byte-for-byte the darkest of {@code <colour>_bed_foot_up}. So
	 * these are not two palettes but one ramp, with the tops sitting in its upper half because a bed's
	 * top face catches more light than its sides - and nothing here has to know which face it is
	 * recolouring. A pixel's own luminance places it on the ramp, and a source's top pixels are brighter
	 * than its side pixels for the same reason vanilla's are.
	 * <p>
	 * Stored as RGB stops rather than reduced to a hue and a saturation, because that reduction is lossy
	 * exactly where beds are: averaging a hue breaks on red, which wraps 0/360, and is meaningless for
	 * white, black and the two greys. Interpolated continuously (see {@link #sample}) they reproduce
	 * vanilla's own tones on a 16px source, whose cloth luminances land on the stops, without quantising
	 * an HD one down to seven.
	 * <p>
	 * Red is here for the packs that need it. Most do not - a pack whose bed is already red keeps its own
	 * pixels untouched, which is the point of deriving at all - but a pack whose bed is teal or navy has
	 * no red art to keep, and would otherwise be given fifteen correctly dyed beds and one that is still
	 * the wrong colour. See {@link #derive}.
	 * <p>
	 * In dye order. One cosmetic gap against vanilla: the white bed alone brightens its pillow, to
	 * {@code f8f8f8} from the {@code ececec} all fifteen others share. Not worth a special case.
	 */
	private static final Map<String, int[]> RAMPS = ramps();

	private static Map<String, int[]> ramps() {
		Map<String, int[]> map = new LinkedHashMap<>();
		map.put("white", new int[] { 0xa8b0b1, 0xc2c2c2, 0xc7d3d3, 0xe0e5e5, 0xefefef, 0xf8f8f8, 0xffffff });
		map.put("orange", new int[] { 0xb44800, 0xc55500, 0xd55900, 0xe66500, 0xf67510, 0xff8118, 0xff9929 });
		map.put("magenta", new int[] { 0x791a72, 0x8b2883, 0x9c2894, 0xa42c9c, 0xb438ab, 0xbd3cb4, 0xc544b4 });
		map.put("light_blue", new int[] { 0x16689f, 0x187db4, 0x2085c5, 0x208dcd, 0x2995d5, 0x29a1d5, 0x31aede });
		map.put("yellow", new int[] { 0xde9908, 0xde9d08, 0xe6a510, 0xf6b618, 0xffc218, 0xffce29, 0xffda39 });
		map.put("lime", new int[] { 0x417910, 0x529510, 0x5aa510, 0x62b618, 0x73ba18, 0x7bc618, 0x8bce29 });
		map.put("pink", new int[] { 0x983157, 0xb04b70, 0xc55983, 0xde6594, 0xee799c, 0xf689ac, 0xf699b4 });
		map.put("gray", new int[] { 0x394441, 0x4a5052, 0x4a595a, 0x526162, 0x596b71, 0x627173, 0x6a757b });
		map.put("light_gray", new int[] { 0x53534c, 0x62655a, 0x6a6d62, 0x7d7f77, 0x888a80, 0x93958c, 0xa3a09a });
		map.put("cyan", new int[] { 0x084747, 0x0c5f5e, 0x0f6968, 0x108887, 0x129493, 0x129e9d, 0x16abaa });
		map.put("purple", new int[] { 0x431171, 0x52188b, 0x621c9c, 0x6a24a4, 0x7b28ac, 0x832cb4, 0x8b30bd });
		map.put("blue", new int[] { 0x202b61, 0x2c3b85, 0x35479a, 0x3a4da5, 0x3f53b3, 0x455abe, 0x406fc2 });
		map.put("brown", new int[] { 0x56341b, 0x5b371d, 0x643d21, 0x724728, 0x84532f, 0x8f5b35, 0x9a643d });
		map.put("green", new int[] { 0x2f3d11, 0x364612, 0x3f5312, 0x4a6313, 0x577514, 0x608116, 0x658718 });
		map.put("red", new int[] { 0x6b1213, 0x851a1a, 0x902120, 0xa22722, 0xac2922, 0xb53129, 0xbf3b33 });
		map.put("black", new int[] { 0x050507, 0x0f0f14, 0x141418, 0x1a1a1f, 0x1f1f24, 0x232328, 0x28282e });
		// Insertion-ordered, unlike Map.copyOf, whose iteration order is salted per JVM run - outputs()
		// walks this map, and a derivation's output list has to be the same list every time.
		return Collections.unmodifiableMap(map);
	}

	/**
	 * The direction vanilla's own red bed points, which a pack's bed is measured against to decide
	 * whether it is a red bed at all; see {@link #derive}.
	 */
	private static final double[] VANILLA_RED = direction(RAMPS.get(RED));

	/** The mean colour direction of a ramp's stops. */
	private static double[] direction(int[] ramp) {
		double x = 0;
		double y = 0;
		double z = 0;
		for (int stop : ramp) {
			double[] chroma = Ops.chromaOf(Ops.withAlpha(stop, 255), 0);
			if (chroma != null) {
				x += chroma[0];
				y += chroma[1];
				z += chroma[2];
			}
		}
		double length = Math.sqrt(x * x + y * y + z * z);
		return new double[] { x / length, y / length, z / length };
	}

	@Override
	public String id() {
		return "beds";
	}

	@Override
	public List<String> sources() {
		List<String> sources = new ArrayList<>();
		for (Face face : COLORED) {
			if (!sources.contains(face.source())) {
				sources.add(face.source());
			}
		}
		for (Face face : SHARED) {
			sources.add(face.source());
		}
		return List.copyOf(sources);
	}

	/**
	 * Red's seven faces first, then the two shared ones, then the other fifteen dyes - so the lab's
	 * cards open on the part that says whether the geometry is right, which everything else is built on.
	 */
	@Override
	public List<String> outputs() {
		List<String> outputs = new ArrayList<>();
		for (Face face : COLORED) {
			outputs.add(face.output(RED));
		}
		for (Face face : SHARED) {
			outputs.add(face.output(null));
		}
		for (String color : RAMPS.keySet()) {
			if (color.equals(RED)) {
				continue;
			}
			for (Face face : COLORED) {
				outputs.add(face.output(color));
			}
		}
		return List.copyOf(outputs);
	}

	/**
	 * <ul>
	 *   <li>{@code neutral_floor} - how far from grey, in channel counts, a pixel has to be before it is
	 *   taken to have a colour at all. Below it the pixel is pillow or shadow and is left alone.
	 *   <b>The one value here with a narrow window.</b> The control is flat across everything from 8 up,
	 *   so it says nothing; the corpus fixes it to 14-15. Below 14, packs that draw a faintly warm pillow
	 *   have some of it dyed - a third of it on {@code Luminous} at 13. Above 15, packs that draw a pale
	 *   blanket have some of it missed - 6% of {@code PureBDcraft}'s at 16, 40% at 18, which is the
	 *   failure that matters more, since undyed cloth is what a player sees. Move it with the corpus
	 *   open, not the control.</li>
	 *   <li>{@code cloth_agreement} - how nearly a pixel has to point the way its own surface's cloth
	 *   does to be dyed, as a cosine; see {@link #clothOf}. <b>Deliberately loose.</b> It is a backstop,
	 *   not the main test: the pillow is already excluded by {@code neutral_floor} - across the whole
	 *   corpus not one pack tints a pillow past it - and the frame by the timber comparison, which puts
	 *   oak around -0.87. What is left for this to catch is a pillow tinted <em>opposite</em> the
	 *   blanket, and nothing else, so it only wants to be on the right side of zero.
	 *   <p>
	 *   Set tight it does real harm, because a blanket is not one hue. Nebula's runs a gradient from cyan
	 *   through blue to violet, +0.51 to +0.98 against its own mean, and a threshold in the middle of
	 *   that cuts a hard seam across the bed and leaves a cyan block undyed on all sixteen. Every value
	 *   from 0 to 0.45 covers that gradient whole, and lowering it to 0.2 changes no other pack in the
	 *   corpus by a single pixel.</li>
	 *   <li>{@code timber_distinct} - how alike the pack's blanket and its own frame may be before the
	 *   two are called inseparable and the row cutoff takes over; see {@link Rows}. Near 1 by design:
	 *   the comparison it guards works on all but the pack whose bed really is the colour of wood.</li>
	 *   <li>{@code red_passthrough} - how nearly a pack's bed has to point the way vanilla's red one does
	 *   to keep its own pixels on red rather than be dyed onto the red ramp like any other colour; see
	 *   {@link #derive}. The corpus splits cleanly in two here and leaves an enormous gap between: 53
	 *   packs land at +0.996 or above, ten at -0.43 or below, and not one anywhere in the middle. Any
	 *   value between those separates them, so this is the least delicate number in the list.</li>
	 *   <li>{@code min_cloth_fraction} - below this share of the region, the cloth was not found and the
	 *   fifteen dyes are declined (see {@link #derive}).</li>
	 *   <li>{@code leg_purity} - how nearly a pixel inside a leg tile has to match the blanket before it
	 *   is taken to be mattress that leaked into the tile and painted over; see {@link #scrubMattress}.
	 *   Near 1, because it is asked of pixels that are mostly wood and a loose value would sand the
	 *   warmth off an oak leg.</li>
	 *   <li>{@code range_trim} - the share of cloth pixels ignored at each end when measuring the range
	 *   the ramp spans, so one misread pixel cannot set it for all seven faces.</li>
	 *   <li>{@code gamma} - shape of the ramp. Above 1 holds more of the mattress in its dark tones.</li>
	 *   <li>{@code contrast} - how much of the dye's four-stop range to actually use, about its middle.
	 *   Below 1 keeps a high-contrast pack off the ends of vanilla's fairly narrow ramps; above 1
	 *   stretches a flat pack across them.</li>
	 * </ul>
	 */
	@Override
	public List<Param> params() {
		return List.of(
			Param.of("neutral_floor", 0, 32, 14),
			Param.of("cloth_agreement", -1, 1, 0.2),
			Param.of("timber_distinct", 0, 1, 0.98),
			Param.of("red_passthrough", -1, 1, 0.5),
			Param.of("min_cloth_fraction", 0, 1, 0.15),
			Param.of("leg_purity", 0, 1, 0.95),
			Param.of("range_trim", 0, 0.1, 0.01),
			Param.of("gamma", 0.25, 3, 1),
			Param.of("contrast", 0, 2, 1)
		);
	}

	@Override
	public Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		Map<String, BufferedImage> derived = new LinkedHashMap<>();
		double floor = params.get("neutral_floor");

		// Turned onto the modern layout but with the leg strip not yet rebuilt, because rebuilding it
		// needs to know what this pack's blanket looks like - see packLegs.
		Map<Face, BufferedImage> base = new LinkedHashMap<>();
		for (Face face : COLORED) {
			BufferedImage built = turned(sources.get(face.source()), face);
			if (built != null) {
				base.put(face, built);
			}
		}
		Map<Face, BufferedImage> shared = new LinkedHashMap<>();
		for (Face face : SHARED) {
			BufferedImage built = turned(sources.get(face.source()), face);
			if (built != null) {
				shared.put(face, built);
			}
		}

		// Measured off rows the mattress owns in every pack, so it does not depend on the leg strip and
		// can be used while rebuilding it.
		double[] sides = clothOf(base, floor, Region.BAND);
		double[] tops = clothOf(base, floor, Region.WHOLE);

		base.forEach((face, image) -> packLegs(image, face, image.getWidth() / Ops.BASE_SIZE, sides, params));
		shared.forEach((face, image) -> packLegs(image, face, image.getWidth() / Ops.BASE_SIZE, sides, params));

		// The two shared textures stand on their own sources: a pack that ships planks but no bed still
		// wants its own planks under vanilla's beds.
		shared.forEach((face, image) -> derived.put(face.output(null), image));

		// All or nothing across the seven: a bed with two of its faces derived and the rest vanilla's
		// reads worse than one that is wholly vanilla.
		if (base.size() < COLORED.size()) {
			return derived;
		}
		// Red first, as the pack's own untouched pixels. If the cloth cannot be read at all this is the
		// only bed that comes out, so it is put in before anything can go wrong.
		base.forEach((face, image) -> derived.put(face.output(RED), image));

		Cloth cloth = measure(base, sides, tops, params);
		if (cloth == null) {
			return derived;
		}
		// Whether this pack's bed is a red bed. Most are, and those keep their own art on red - the whole
		// reason to derive rather than ship textures. But a fair few packs restyle the bed a different
		// colour outright, and handing one of those fifteen correctly dyed beds plus a teal "red" bed
		// would be worse than dyeing red too: the player picks a bed by its colour, and a red bed that is
		// not red is the one bed in the set that lies. So those get red built the same way as the rest,
		// which still keeps the pack's shading, weave and contrast - only the hue is vanilla's.
		if (Ops.agree(cloth.direction(), VANILLA_RED) < params.get("red_passthrough")) {
			base.forEach((face, image) ->
				derived.put(face.output(RED), recolor(image, cloth.mask(face), cloth, RAMPS.get(RED), params)));
		}
		RAMPS.forEach((color, ramp) -> {
			if (color.equals(RED)) {
				return;
			}
			base.forEach((face, image) ->
				derived.put(face.output(color), recolor(image, cloth.mask(face), cloth, ramp, params)));
		});
		return derived;
	}

	/** @return {@code null} where the pack has no usable source for this face, declining it */
	private static @Nullable BufferedImage turned(@Nullable BufferedImage source, Face face) {
		if (source == null || Ops.scaleOf(source) == 0) {
			return null;
		}
		return turn(source, face.turn());
	}

	/** Turns a legacy texture onto the modern face reading the same surface; see {@link #COLORED}. */
	private static BufferedImage turn(BufferedImage source, Turn turn) {
		int size = source.getWidth();
		int[] in = Ops.pixels(source);
		int[] out = new int[in.length];
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				int index = switch (turn) {
					case KEEP -> y * size + x;
					case MIRROR -> y * size + (size - 1 - x);
					// Anticlockwise: the legacy tops put the pillow at the right edge, the modern ones
					// along the top.
					case ROTATE -> (size - 1 - x) * size + y;
				};
				out[index] = in[y * size + x];
			}
		}
		return Ops.image(out, size, size);
	}

	/**
	 * Rebuilds rows 13-16, the only band the two eras disagree on.
	 * <p>
	 * 1.8.9 beds are one solid box with the legs painted on, so a legacy side texture carries its leg at
	 * the coordinates its own model read that leg from - one 3x3 tile. Modern beds give the legs real
	 * geometry, three visible faces each, and pack all three into the same three rows at offsets the
	 * template's UVs pick out. So the band is cleared and rebuilt: {@code face.legFrom()} says where
	 * 1.8.9 kept its leg, and every declared slot takes that tile, or the nearest of them where there
	 * are two. Every leg face is the same oak in vanilla too, so repeating one across the three costs
	 * nothing visible.
	 * <p>
	 * The source slots are declared rather than found by looking for opaque pixels, because plenty of
	 * packs shift the whole bed down a row or paint the mattress into rows a leg never occupied, and
	 * "whichever slot has something in it" would then stamp cloth across the legs of every bed.
	 * <p>
	 * A pack that painted no leg at all leaves the band transparent, which renders as a legless bed -
	 * exactly the silhouette that pack drew for 1.8.9, where nothing below the frame was solid.
	 */
	private static void packLegs(BufferedImage face, Face declared, int scale, double @Nullable [] cloth,
		Params params) {
		if (declared.legs().isEmpty()) {
			return;
		}
		int size = face.getWidth();
		int top = LEG_TOP * scale;
		int height = size - top;
		int width = LEG * scale;

		Map<Integer, int[]> tiles = new LinkedHashMap<>();
		for (int slot : declared.legFrom()) {
			int[] tile = face.getRGB(slot * scale, top, width, height, null, 0, width);
			if (anyOpaque(tile)) {
				scrubMattress(tile, width, height, cloth, params);
				tiles.put(slot, tile);
			}
		}
		face.setRGB(0, top, size, height, new int[size * height], 0, size);
		if (tiles.isEmpty()) {
			return;
		}
		for (int slot : declared.legs()) {
			face.setRGB(slot * scale, top, width, height, tiles.get(nearest(tiles.keySet(), slot)), 0, width);
		}
	}

	/**
	 * Paints mattress out of a leg tile, replacing it with the nearest timber in the same tile.
	 * <p>
	 * A modern leg is a 3x3 box of solid wood, but the 3-wide slot the tile is lifted from is only wood in
	 * a pack that drew its bedpost exactly 3 wide. Draill's is 2, so the third column of its slot is
	 * mattress - and since the tile is then stamped into all three leg faces, one stray column of blanket
	 * becomes three stripes down the legs of every bed, dyed along with the rest because it genuinely is
	 * cloth. Scrubbing it here rather than declining to dye the strip keeps the packs whose mattress
	 * really does overhang into these rows, which is a different thing and wants dyeing.
	 * <p>
	 * The test has to be against the pack's own blanket rather than against wood, because red cloth and
	 * oak agree to better than 0.8 and no threshold separates them - the same reason {@link #isCloth}
	 * asks its question comparatively. Held tight on purpose: this only wants pixels that are
	 * unmistakably the mattress, not merely warm.
	 */
	private static void scrubMattress(int[] tile, int width, int height, double @Nullable [] cloth,
		Params params) {
		if (cloth == null) {
			return;
		}
		double floor = params.get("neutral_floor");
		double purity = params.get("leg_purity");
		boolean[] mattress = new boolean[tile.length];
		boolean any = false;
		for (int i = 0; i < tile.length; i++) {
			double[] chroma = Ops.alpha(tile[i]) == 0 ? null : Ops.chromaOf(tile[i], floor);
			mattress[i] = chroma != null && Ops.agree(chroma, cloth) >= purity;
			any |= mattress[i];
		}
		if (!any) {
			return;
		}
		int[] original = tile.clone();
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int index = y * width + x;
				if (!mattress[index]) {
					continue;
				}
				Integer timber = nearestTimber(original, mattress, width, height, x, y);
				if (timber != null) {
					tile[index] = timber;
				}
			}
		}
	}

	/** The closest pixel to {@code (x, y)} in {@code tile} that is not mattress; along the row first. */
	private static @Nullable Integer nearestTimber(int[] tile, boolean[] mattress, int width, int height,
		int x, int y) {
		for (int radius = 1; radius < width + height; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dy = -radius; dy <= radius; dy++) {
					if (Math.abs(dx) != radius && Math.abs(dy) != radius) {
						continue;
					}
					int nx = x + dx;
					int ny = y + dy;
					if (nx < 0 || ny < 0 || nx >= width || ny >= height) {
						continue;
					}
					int index = ny * width + nx;
					if (!mattress[index] && Ops.alpha(tile[index]) != 0) {
						return tile[index];
					}
				}
			}
		}
		return null;
	}

	private static boolean anyOpaque(int[] pixels) {
		for (int argb : pixels) {
			if (Ops.alpha(argb) != 0) {
				return true;
			}
		}
		return false;
	}

	private static int nearest(Collection<Integer> slots, int slot) {
		int best = slot;
		int distance = Integer.MAX_VALUE;
		for (int candidate : slots) {
			int gap = Math.abs(candidate - slot);
			if (gap < distance) {
				distance = gap;
				best = candidate;
			}
		}
		return best;
	}

	/**
	 * Which pixels of the finished faces are cloth, and the luminance range they span.
	 * <p>
	 * The range is measured across all seven faces at once rather than per texture, so the top of a bed
	 * and its sides stay in step instead of each being stretched to fill the dye's ramp alone - the same
	 * reason {@link NetheriteRecolor} measures a whole armour set together.
	 */
	private record Cloth(Map<Face, boolean[]> masks, int min, int max, double[] direction) {
		boolean[] mask(Face face) {
			return masks.get(face);
		}

		double position(int argb, double gamma, double contrast) {
			double t = (double) (Ops.luminance(argb) - min) / (max - min);
			t = Math.pow(Math.clamp(t, 0, 1), gamma);
			return Math.clamp(0.5 + (t - 0.5) * contrast, 0, 1);
		}
	}

	/**
	 * @return {@code null} if the cloth could not be told apart from the rest of the bed, or is a single
	 *         flat colour with no ramp to remap - in which case the fifteen dyes are declined
	 */
	private static @Nullable Cloth measure(Map<Face, BufferedImage> base, double @Nullable [] sides,
		double @Nullable [] tops, Params params) {
		double floor = params.get("neutral_floor");
		double agreement = params.get("cloth_agreement");
		int wood = woodOf(base);
		double[] timber = wood == 0 ? null : Ops.chromaOf(wood, floor);

		if (sides == null && tops == null) {
			return null;
		}
		// A pack whose blanket is the same colour as its own frame has nothing here to tell them apart
		// with, and forcing the question would decide each pixel on rounding and speckle the result. Drop
		// back to the row cutoff, which at least splits them somewhere deliberate.
		if (timber != null && Ops.agree(timber, sides != null ? sides : tops) > params.get("timber_distinct")) {
			timber = null;
		}

		Map<Face, boolean[]> masks = new LinkedHashMap<>();
		long[] histogram = new long[256];
		long dyed = 0;
		long candidates = 0;
		for (Map.Entry<Face, BufferedImage> entry : base.entrySet()) {
			int[] pixels = Ops.pixels(entry.getValue());
			boolean[] mask = new boolean[pixels.length];
			Rows rows = Rows.of(entry.getKey(), entry.getValue(), timber);
			// A surface with no direction of its own - a greyscale top over a coloured bed - borrows the
			// other's rather than going undyed while the rest of the bed is dyed around it.
			double[] cloth = entry.getKey().region() == Region.WHOLE
				? (tops != null ? tops : sides)
				: (sides != null ? sides : tops);
			for (int i = rows.start(); i < rows.end(); i++) {
				if (Ops.alpha(pixels[i]) == 0) {
					continue;
				}
				candidates++;
				double[] chroma = Ops.chromaOf(pixels[i], floor);
				if (chroma == null || Ops.agree(chroma, cloth) < agreement) {
					continue;
				}
				// Nearer the frame than the blanket. Asked as a comparison rather than against a fixed
				// distance because oak and a red blanket are genuinely alike - they agree to 0.83 in
				// vanilla's own bed - so no absolute threshold separates them, while asking which of the
				// pack's own two references a pixel is closer to separates them every time.
				if (timber != null && Ops.agree(chroma, timber) >= Ops.agree(chroma, cloth)) {
					continue;
				}
				mask[i] = true;
				dyed++;
				histogram[Ops.luminance(pixels[i])]++;
			}
			masks.put(entry.getKey(), mask);
		}
		if (candidates == 0 || dyed < candidates * params.get("min_cloth_fraction")) {
			return null;
		}
		// Percentiles rather than the outright darkest and brightest. No classification of a pack's own
		// art is going to be perfect, and with a plain min/max a single stray pillow pixel that squeaks
		// past the tests sets the top of the ramp for all seven faces and washes every dye out. Trimming
		// the ends costs nothing on a clean pack - vanilla's cloth has flat tones, not tails - and caps
		// what one misread pixel can do on a messy one.
		double trim = params.get("range_trim");
		int min = percentile(histogram, dyed, trim);
		int max = percentile(histogram, dyed, 1 - trim);
		if (max <= min) {
			return null;
		}
		return new Cloth(Collections.unmodifiableMap(masks), min, max, sides != null ? sides : tops);
	}

	/** The luminance at {@code fraction} of the way through {@code histogram}'s population. */
	private static int percentile(long[] histogram, long count, double fraction) {
		long target = (long) Math.ceil(count * Math.clamp(fraction, 0, 1));
		long seen = 0;
		for (int luminance = 0; luminance < histogram.length; luminance++) {
			seen += histogram[luminance];
			if (seen >= target) {
				return luminance;
			}
		}
		return histogram.length - 1;
	}

	/**
	 * The direction one of the bed's surfaces points in colour, learned from the faces of that surface
	 * that are all cloth.
	 * <p>
	 * Telling a mattress from a pillow by how saturated it is only works on packs that draw a vivid
	 * mattress. PureBDcraft draws a pale blue blanket at 0.12-0.17 saturation against a cream pillow at
	 * 0.02: no threshold separates those two, and one that tries picks out a sliver of the darkest folds
	 * and leaves the rest of the blanket undyed. What does separate them is <em>hue</em> - the blanket
	 * holds 195-213 degrees across every one of its tones while the pillow sits at 48-60 - and that is
	 * true of vanilla too, whose red cloth and neutral pillow point opposite ways.
	 * <p>
	 * The hue to compare against is not guessed, it is measured, and the model says where to measure it:
	 * a bed's <em>foot</em> has no pillow on it, in either era and in every pack, so
	 * {@code bed_feet_side}, {@code bed_feet_end} and {@code bed_feet_top} are cloth from edge to edge
	 * above the frame. Sampling there and classifying the head faces against it is the same move
	 * {@link #woodOf} makes for the legs.
	 * <p>
	 * Measured per {@code region} - the sides and the top separately - because a pack can style them
	 * apart, and one direction covering both would sit between two hues and match neither.
	 * <p>
	 * Each pixel contributes its chroma as a <em>unit</em> vector, so the pale tones of a blanket count
	 * as much as its dark folds and the answer is a direction rather than an average colour. Pixels too
	 * close to neutral to have a direction at all are left out, and a pack that has none of them - a
	 * greyscale bed - gets {@code null} here.
	 */
	private static double @Nullable [] clothOf(Map<Face, BufferedImage> base, double floor, Region region) {
		double x = 0;
		double y = 0;
		double z = 0;
		for (Map.Entry<Face, BufferedImage> entry : base.entrySet()) {
			if (!entry.getKey().pillowFree() || entry.getKey().region() != region) {
				continue;
			}
			int[] pixels = Ops.pixels(entry.getValue());
			// Sampled with no timber declared, which stops a side face at the frame: the reference has to
			// come from rows that are mattress in every pack, since it is what everything else is judged
			// against.
			Rows rows = Rows.of(entry.getKey(), entry.getValue(), null);
			for (int i = rows.start(); i < rows.end(); i++) {
				double[] chroma = Ops.chromaOf(pixels[i], floor);
				if (chroma != null) {
					x += chroma[0];
					y += chroma[1];
					z += chroma[2];
				}
			}
		}
		double length = Math.sqrt(x * x + y * y + z * z);
		return length <= 0 ? null : new double[] { x / length, y / length, z / length };
	}

	/**
	 * The pack's own frame timber, taken over its leg tiles - or 0 where it painted no legs.
	 * <p>
	 * The legs are the one part of a bed that is unambiguously wood in every pack and both eras, which
	 * makes them the place to learn what this pack's timber looks like. {@link #packLegs} has already
	 * cleared everything in that band that is not a leg, so every opaque pixel below {@link #LEG_TOP} is
	 * a sample.
	 * <p>
	 * Per-channel median rather than mean, and via a histogram so the cost is bounded at any pack
	 * resolution. A pack that has nudged its bed down a row leaves a line of mattress along the top of
	 * its leg tile, and a mean would drag the timber a third of the way towards the cloth it is supposed
	 * to be telling apart from.
	 */
	private static int woodOf(Map<Face, BufferedImage> base) {
		int[][] histogram = new int[3][256];
		int count = 0;
		for (Map.Entry<Face, BufferedImage> entry : base.entrySet()) {
			if (entry.getKey().legs().isEmpty()) {
				continue;
			}
			BufferedImage image = entry.getValue();
			int[] pixels = Ops.pixels(image);
			for (int i = LEG_TOP * (image.getWidth() / Ops.BASE_SIZE) * image.getWidth(); i < pixels.length; i++) {
				if (Ops.alpha(pixels[i]) != 0) {
					histogram[0][Ops.red(pixels[i])]++;
					histogram[1][Ops.green(pixels[i])]++;
					histogram[2][Ops.blue(pixels[i])]++;
					count++;
				}
			}
		}
		if (count == 0) {
			return 0;
		}
		return Ops.argb(255, median(histogram[0], count), median(histogram[1], count), median(histogram[2], count));
	}

	private static int median(int[] histogram, int count) {
		int seen = 0;
		for (int value = 0; value < histogram.length; value++) {
			seen += histogram[value];
			if (seen * 2 >= count) {
				return value;
			}
		}
		return histogram.length - 1;
	}

	/**
	 * The pixel range of one face that may be dyed.
	 * <p>
	 * With the pack's timber known, a side face runs the whole way down and every pixel is judged on
	 * colour: which rows hold mattress and which hold frame differs from pack to pack - vanilla starts
	 * its frame at row 11, Eum3 at row 10 under the pillow, Deep Sky runs the mattress past row 13 - and
	 * a fixed row is wrong for all but the one it was measured on.
	 * <p>
	 * With no timber to compare against - a pack that painted no legs - there is nothing to tell a frame
	 * from a blanket by, so the range stops at the row vanilla puts its frame on and the frame is
	 * excluded by position instead. A worse guess, but the only one left.
	 */
	private record Rows(int start, int end) {
		static Rows of(Face face, BufferedImage image, double @Nullable [] timber) {
			int size = image.getWidth();
			int row = (size / Ops.BASE_SIZE) * size;
			return switch (face.region()) {
				case BAND -> new Rows(0, timber == null ? FRAME_TOP * row : size * size);
				case WHOLE -> new Rows(0, size * size);
				case NONE -> new Rows(0, 0);
			};
		}
	}

	/**
	 * The pack's face with only its cloth moved onto {@code ramp}. Everything else - pillow, frame,
	 * legs, transparency - is the pack's own pixel, untouched.
	 */
	private static BufferedImage recolor(BufferedImage base, boolean[] mask, Cloth cloth, int[] ramp, Params params) {
		double gamma = params.get("gamma");
		double contrast = params.get("contrast");
		int[] pixels = Ops.pixels(base);
		int[] out = pixels.clone();
		for (int i = 0; i < pixels.length; i++) {
			if (mask[i]) {
				out[i] = Ops.withAlpha(sample(ramp, cloth.position(pixels[i], gamma, contrast)), Ops.alpha(pixels[i]));
			}
		}
		return Ops.image(out, base.getWidth(), base.getHeight());
	}

	/**
	 * The dye's colour at {@code position} along its ramp, 0 at its darkest stop and 1 at its brightest.
	 * <p>
	 * Interpolated rather than snapped to the nearest of the four, so a 16px pack lands on vanilla's own
	 * tones - its cloth has four luminances and they fall on the stops - while a 128px pack keeps the
	 * gradients it drew between them instead of being posterised down to four.
	 */
	private static int sample(int[] ramp, double position) {
		double scaled = position * (ramp.length - 1);
		int index = Math.min((int) scaled, ramp.length - 2);
		double weight = scaled - index;
		int from = ramp[index];
		int to = ramp[index + 1];
		return Ops.argb(
			255,
			(int) Math.round(Ops.red(from) + (Ops.red(to) - Ops.red(from)) * weight),
			(int) Math.round(Ops.green(from) + (Ops.green(to) - Ops.green(from)) * weight),
			(int) Math.round(Ops.blue(from) + (Ops.blue(to) - Ops.blue(from)) * weight)
		);
	}
}
