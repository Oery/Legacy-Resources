package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import org.jspecify.annotations.Nullable;

/**
 * The copper tools, recoloured from the pack's iron tools. See {@link MetalRecolor} for how the
 * recolour works, and {@link CopperArmor} for why iron is the source.
 * <p>
 * A separate derivation from {@link CopperArmor} rather than more entries in the same map, so the two
 * can be levelled independently - the same split {@link NetheriteTools} makes, for the same reason.
 * <p>
 * <b>The wooden handle is not recoloured</b>, which is where copper parts company with netherite.
 * Vanilla is unambiguous about this: its copper tools carry the iron tool's handle tones -
 * {@code (40, 30, 11)}, {@code (73, 54, 21)}, {@code (104, 78, 30)}, {@code (137, 103, 39)} - across
 * byte for byte, and those same four are still what 1.8.9 painted a decade earlier. Only the head
 * changes metal. Netherite goes the other way and burns its handles warm, so the two sets need
 * opposite rules rather than one shared one.
 */
final class CopperTools extends MetalRecolor {
	private static final Map<String, String> PIECES = tools("iron", "copper");

	/**
	 * The wood reference: a tool's handle <em>is</em> a stick, so a pack that draws a wooden handle has
	 * already drawn what its own timber looks like.
	 */
	private static final String STICK = "item/stick";

	/**
	 * The tier reference: the same five tools in the pack's other metals, each keyed by the iron texture
	 * it pairs with. Gold, diamond and stone; the wooden tier changes nothing either of these three
	 * does not already catch, in any pack in the corpus, so it is not asked for.
	 */
	private static final Map<String, Map<String, String>> TIERS = tiers("gold", "diamond", "stone");

	@Override
	public String id() {
		return "copper_tools";
	}

	@Override
	protected Map<String, String> pieces() {
		return PIECES;
	}

	@Override
	protected List<String> references() {
		List<String> references = new ArrayList<>();
		references.add(STICK);
		TIERS.values().forEach(pairs -> references.addAll(pairs.values()));
		return List.copyOf(references);
	}

	/**
	 * Tuned in the lab across the pack corpus; see {@link MetalRecolor#params} for the nine shared ones
	 * and {@link #metal} for the two below them.
	 */
	@Override
	public List<Param> params() {
		return List.of(
			Param.of("shadow", 0, 255, 38),
			Param.of("highlight", 0, 255, 183),
			Param.of("gamma", 0.25, 3, 1.49),
			Param.of("auto_level", 0, 1, 1),
			Param.of("target_mean", 0, 255, 107),
			Param.of("hue", 0, 360, 13),
			Param.of("saturation_shadow", 0, 1, 0.77),
			Param.of("saturation_highlight", 0, 1, 0.53),
			Param.of("keep_hue", 0, 1, 0),
			Param.of("neutral_floor", 0, 32, 14),
			Param.of("wood_agreement", -1, 1, 0.6)
		);
	}

	/**
	 * Splits the head from the handle. Both halves of the test are the pack's own answer rather than a
	 * threshold, because a threshold does not survive the corpus.
	 * <p>
	 * <b>What the tiers say.</b> Every pack in the corpus that ships more than one metal draws its tool
	 * tiers the same way vanilla does: one silhouette, the head repainted per metal, <em>the handle left
	 * alone</em>. So the pack has already answered "which pixels are metal here?" - they are the ones it
	 * changed between iron and gold, or iron and diamond, or iron and stone. {@link #keptAcrossTiers}
	 * reads that off directly, <em>per pixel</em> - see {@link Metal} for why the same question asked per
	 * colour gets a fifth of the corpus wrong. Vanilla comes out at 38.3%, which is exactly its handle;
	 * the corpus runs on a median of 43%, and where it goes higher the pack really does keep that much -
	 * Blue 128x repaints barely a tenth of its tool between iron and gold, the rest being handle and ink.
	 * <p>
	 * The tiers are pooled rather than intersected, because a pack can restyle one tier more deeply than
	 * the rest: Occult and majesta both give their <em>gold</em> tool a different handle from their iron
	 * one, and keep the diamond one identical. Pooling is safe because the two tiers otherwise agree on
	 * 99% of pixels, and where they disagree it is one tier keeping what the other repainted, not noise.
	 * <p>
	 * <b>What the stick says.</b> A handle drawn as wood leans the same way in colour as the pack's own
	 * {@code items/stick.png}, and {@link Ops#chromaOf} reads that direction independently of how the
	 * pack shades it. Saturation cannot make this call - a handful of packs (FurfSky, Absolute,
	 * Luminous) draw a <em>saturated</em> metal, so any fixed threshold catching their handles also eats
	 * their blades - but direction can, and it is nowhere near a close call: vanilla classifies the same
	 * 38.3% whether {@code wood_agreement} is 0.3 or 0.9, and so does every classifying pack bar two.
	 * <p>
	 * <b>Neither alone is enough</b>, which is why both are asked and either one is decisive. The stick
	 * misses the whole PvP half of the corpus, which draws the handle as flat black ink and gets no
	 * chroma direction at all - 31 packs, every one of which had its handle turned copper and its
	 * contrast flattened before the tier test was added. It also misses pax10, whose handle is wood but
	 * whose stick is drawn nothing like it (8% against the tiers' 84%). The tiers in turn miss
	 * PureBDcraft, whose 128x handle is softly re-shaded per tier and so is never byte-identical, and
	 * whose stick reads cleanly. On vanilla the two agree exactly, at 38.3% each.
	 * <p>
	 * Note that the tier test also keeps the black outline a pack draws <em>around</em> the head, since
	 * that ink does not change with the metal either. That is the pack's own styling and worth keeping:
	 * it is what stops a restyled copper tool reading as a foreign object next to the pack's own items.
	 * <p>
	 * <b>A pack that answers neither way gets no mask and has its whole tool recoloured</b> -
	 * {@link NetheriteTools}'s behaviour, and the decline rule applied to the mask rather than to the
	 * output. No corpus pack ends up there any more, though ALFaithful comes close at 1%: it genuinely
	 * repaints all but a sliver of its tool between tiers. A pack that answers <em>everything</em> is
	 * not-metal - one whose other tiers are unrestyled copies of its iron - leaves
	 * {@link MetalRecolor}'s ramp no pixels to measure, and the whole set declines to vanilla. No corpus
	 * pack does that either.
	 */
	@Override
	protected Metal metal(Map<String, BufferedImage> sources, Params params) {
		double floor = params.get("neutral_floor");
		double agreement = params.get("wood_agreement");
		Kept kept = keptAcrossTiers(sources);
		double[] wood = woodOf(sources.get(STICK), floor);
		return (source, index, argb) -> {
			boolean[] pixels = kept.pixels().get(source);
			if (pixels != null && index < pixels.length && pixels[index]) {
				return false;
			}
			if (pixels == null && kept.colors().contains(rgb(argb))) {
				return false;
			}
			if (wood == null) {
				return true;
			}
			double[] chroma = Ops.chromaOf(argb, floor);
			return chroma == null || Ops.agree(chroma, wood) < agreement;
		};
	}

	/**
	 * What the tiers said: the pixels of each iron tool the pack keeps, and - for a tool it shipped no
	 * other tier of - the colours it keeps everywhere else.
	 *
	 * @param pixels keyed by the {@code item/iron_*} path, {@code true} where that pixel is kept
	 * @param colors the {@link #pixels} answer generalised to a tool that has no tier to compare against
	 */
	private record Kept(Map<String, boolean[]> pixels, Set<Integer> colors) {
	}

	/**
	 * What the pack keeps when it changes a tool's metal - its handle, the ink it outlines with, and
	 * whatever else it styles per tool rather than per tier.
	 * <p>
	 * Compared on colour, with alpha dropped. Alpha on these textures is coverage rather than material -
	 * it is how a soft-edged pack antialiases its outline - and treating a change in it as a change of
	 * metal splits one handle colour into a long tail of near-unique shades.
	 * <p>
	 * The colour generalisation is deliberately the strict one: a colour only carries over to an
	 * uncomparable tool if it is kept <em>everywhere</em> it appears in the tools that could be compared.
	 * Asked loosely it would freeze whatever the blade shares with the handle. It is the per-pixel answer
	 * that does the work; this is only for a pack that ships, say, an iron sword and no other sword.
	 */
	private static Kept keptAcrossTiers(Map<String, BufferedImage> sources) {
		Map<String, boolean[]> kept = new LinkedHashMap<>();
		Set<Integer> seen = new HashSet<>();
		Set<Integer> moved = new HashSet<>();
		for (Map<String, String> pairs : TIERS.values()) {
			pairs.forEach((ironPath, tierPath) -> {
				BufferedImage iron = sources.get(ironPath);
				BufferedImage tier = sources.get(tierPath);
				if (iron == null || tier == null
					|| iron.getWidth() != tier.getWidth() || iron.getHeight() != tier.getHeight()) {
					return;
				}
				int[] before = Ops.pixels(iron);
				int[] after = Ops.pixels(tier);
				boolean[] pixels = kept.computeIfAbsent(ironPath, path -> new boolean[before.length]);
				for (int i = 0; i < before.length; i++) {
					if (Ops.alpha(before[i]) == 0) {
						continue;
					}
					seen.add(rgb(before[i]));
					if (rgb(before[i]) == rgb(after[i])) {
						pixels[i] = true;
					} else {
						moved.add(rgb(before[i]));
					}
				}
			});
		}
		seen.removeAll(moved);
		return new Kept(Map.copyOf(kept), Set.copyOf(seen));
	}

	private static int rgb(int argb) {
		return argb & 0x00FFFFFF;
	}

	/** The five tools in each named metal, keyed by tier and then by the iron texture each pairs with. */
	private static Map<String, Map<String, String>> tiers(String... metals) {
		Map<String, Map<String, String>> map = new LinkedHashMap<>();
		for (String metal : metals) {
			map.put(metal, tools("iron", metal));
		}
		return Map.copyOf(map);
	}

	/**
	 * The direction the pack's stick leans in colour, or {@code null} if it has none to lean.
	 * <p>
	 * Each pixel contributes its chroma as a <em>unit</em> vector, so a stick's pale edge counts as much
	 * as its dark core and the answer is a direction rather than an average colour.
	 */
	private static double @Nullable [] woodOf(@Nullable BufferedImage stick, double floor) {
		if (stick == null) {
			return null;
		}
		double x = 0;
		double y = 0;
		double z = 0;
		for (int argb : Ops.pixels(stick)) {
			double[] chroma = Ops.chromaOf(argb, floor);
			if (chroma != null) {
				x += chroma[0];
				y += chroma[1];
				z += chroma[2];
			}
		}
		double length = Math.sqrt(x * x + y * y + z * z);
		return length <= 0 ? null : new double[] { x / length, y / length, z / length };
	}
}
