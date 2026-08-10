package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A suspicious block's four brushing stages, derived from the pack's own art for the block it is a
 * disturbed form of, plus its own block-breaking overlays. Subclassed per block:
 * {@link SuspiciousGravel}, {@link SuspiciousSand}.
 * <p>
 * Modern Minecraft wants {@code suspicious_gravel_0..3} - one texture per brushing stage, not one
 * texture - and no 1.8.9 pack has any of them. What every pack does have is {@code destroy_stage_0..9},
 * which survives the legacy conversion under its own name (both versions call it that), and which is
 * exactly the right raw material: a set of ten progressively heavier "this block has been disturbed"
 * patterns, already drawn in the pack's own style. Blurred, they stop reading as cracks and start
 * reading as ground that has been dug into, which is what the stage art needs to say.
 * <p>
 * Deriving from the pack rather than recolouring vanilla's own suspicious gravel matters because the
 * result inherits both the pack's gravel palette <em>and</em> its crack style, so a stylized pack gets
 * a stylized result instead of a vanilla-looking block sitting among its restyled neighbours.
 * <p>
 * Vanilla's own art takes a different route - it perturbs individual pixels of the base block rather
 * than overlaying anything, and does so identically for both blocks, growing from 61 changed pixels at
 * stage 0 to 130 at stage 3 for gravel and from 66 to 125 for sand - so the derivation is not trying to
 * reproduce it pixel for pixel, only to read the same way at a glance.
 * <p>
 * Each block declares its own {@link #params()}, but the two blocks in fact want the same numbers, and
 * it is worth saying why since the raw figures suggest otherwise. Vanilla's last stage takes 13.2
 * luminance off sand and only 8.3 off gravel - but sand is a far brighter field, and as a proportion of
 * each block's own mean those are 6.40% and 6.44%, the same darkening twice. The overlay here is a
 * multiply, so it is proportional in the same way: gravel's shipped ramp lands both blocks at 13.3% of
 * their own mean. Nothing about the ramp needs to know which block it is on.
 */
abstract class SuspiciousBlock implements Derivation {
	/** How many brushing stages modern renders, i.e. {@code suspicious_gravel_0..3}. */
	private static final int STAGES = 4;
	/** How many block-breaking overlays exist to draw from, i.e. {@code destroy_stage_0..9}. */
	private static final int DESTROY_STAGES = 10;

	/** The pack's art for the undisturbed block, e.g. {@code block/gravel}. */
	protected abstract String base();

	/** Output path up to the stage number, e.g. {@code block/suspicious_gravel_}. */
	protected abstract String stem();

	@Override
	public final List<String> sources() {
		List<String> sources = new ArrayList<>();
		sources.add(base());
		for (int stage = 0; stage < DESTROY_STAGES; stage++) {
			sources.add(destroyStage(stage));
		}
		return List.copyOf(sources);
	}

	@Override
	public final List<String> outputs() {
		List<String> outputs = new ArrayList<>();
		for (int stage = 0; stage < STAGES; stage++) {
			outputs.add(stem() + stage);
		}
		return List.copyOf(outputs);
	}

	/**
	 * The six constants every subclass declares, and what each one does. The values live in the
	 * subclasses, each tuned on its own block.
	 * <ul>
	 *   <li>{@code stage_base}, {@code stage_step} - which destroy stage feeds brushing stage 0, and how
	 *   far apart the four are. The lowest stages are a couple of isolated cracks that all but vanish
	 *   once blurred.</li>
	 *   <li>{@code blur_radius} - in 16px units, scaled to the pack's resolution: 1 means a 3x3 kernel on
	 *   a 16x pack and a 9x9 one on a 48x. This is the constant that turns cracks into a dug hollow.</li>
	 *   <li>{@code mask_gamma} - contrast of the blurred mask. Above 1 pulls the soft halo back and keeps
	 *   a defined pit; below 1 spreads the disturbance over the whole face.</li>
	 *   <li>{@code opacity_first}, {@code opacity_last} - how hard the overlay darkens, ramped across the
	 *   four stages.</li>
	 * </ul>
	 */
	@Override
	public abstract List<Param> params();

	@Override
	public final Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		BufferedImage base = sources.get(base());
		if (base == null || Ops.scaleOf(base) == 0) {
			return Map.of();
		}
		int size = base.getWidth();
		int radius = (int) Math.round(params.get("blur_radius") * Ops.scaleOf(base));
		double gamma = params.get("mask_gamma");
		int[] pixels = Ops.pixels(base);

		Map<String, BufferedImage> derived = new LinkedHashMap<>();
		for (int stage = 0; stage < STAGES; stage++) {
			BufferedImage overlay = sources.get(destroyStage(destroyStageFor(stage, params)));
			// A pack missing this particular overlay yields no texture for this stage, so the game
			// falls back to vanilla's; the other three stages still derive.
			if (overlay == null || Ops.scaleOf(overlay) == 0) {
				continue;
			}
			int[] mask = Ops.pixels(Ops.boxBlur(Ops.resizeNearest(overlay, size), radius));
			double opacity = ramp(params.get("opacity_first"), params.get("opacity_last"), stage);
			int[] out = new int[pixels.length];
			for (int i = 0; i < pixels.length; i++) {
				out[i] = Ops.multiply(pixels[i], Ops.withAlpha(mask[i], contrast(Ops.alpha(mask[i]), gamma)), opacity);
			}
			derived.put(stem() + stage, Ops.image(out, size, size));
		}
		return derived;
	}

	private static int destroyStageFor(int stage, Params params) {
		int index = params.getInt("stage_base") + params.getInt("stage_step") * stage;
		return Math.clamp(index, 0, DESTROY_STAGES - 1);
	}

	private static String destroyStage(int stage) {
		return "block/destroy_stage_" + stage;
	}

	/** Linear interpolation from {@code first} at stage 0 to {@code last} at the final stage. */
	private static double ramp(double first, double last, int stage) {
		return first + (last - first) * stage / (STAGES - 1.0);
	}

	/**
	 * Gamma curve on the mask's alpha.
	 * <p>
	 * Note that vanilla's own {@code destroy_stage_*} paints its background at alpha 1 rather than 0,
	 * so a "is this pixel a crack" threshold would call the entire texture a crack. Nothing here
	 * thresholds: alpha is carried through as a weight, and alpha 1 simply contributes 0.4% of
	 * nothing - which is also why packs that do use a true alpha 0 need no special case.
	 */
	private static int contrast(int alpha, double gamma) {
		return (int) Math.round(255 * Math.pow(alpha / 255.0, gamma));
	}
}
