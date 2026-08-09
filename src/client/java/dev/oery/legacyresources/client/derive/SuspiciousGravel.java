package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Suspicious gravel's four brushing stages, derived from the pack's own gravel and its own
 * block-breaking overlays.
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
 * Vanilla's own art takes a different route - it perturbs individual gravel pixels rather than
 * overlaying anything, growing from 61 changed pixels at stage 0 to 130 at stage 3 - so the
 * derivation is not trying to reproduce it pixel for pixel, only to read the same way at a glance.
 */
final class SuspiciousGravel implements Derivation {
	/** How many brushing stages modern renders, i.e. {@code suspicious_gravel_0..3}. */
	private static final int STAGES = 4;
	/** How many block-breaking overlays exist to draw from, i.e. {@code destroy_stage_0..9}. */
	private static final int DESTROY_STAGES = 10;

	private static final String GRAVEL = "block/gravel";

	@Override
	public String id() {
		return "suspicious_gravel";
	}

	@Override
	public List<String> sources() {
		List<String> sources = new ArrayList<>();
		sources.add(GRAVEL);
		for (int stage = 0; stage < DESTROY_STAGES; stage++) {
			sources.add(destroyStage(stage));
		}
		return List.copyOf(sources);
	}

	@Override
	public List<String> outputs() {
		List<String> outputs = new ArrayList<>();
		for (int stage = 0; stage < STAGES; stage++) {
			outputs.add("block/suspicious_gravel_" + stage);
		}
		return List.copyOf(outputs);
	}

	@Override
	public List<Param> params() {
		return List.of(
			// Which destroy stage feeds brushing stage 0, and how far apart the four are. The lowest
			// stages are a couple of isolated cracks that all but vanish once blurred, so the ramp
			// starts at 2 and takes every second stage from there, ending at 8.
			Param.ofInt("stage_base", 0, 9, 2),
			Param.ofInt("stage_step", 0, 3, 2),
			// In 16px units, scaled to the pack's resolution: 1 means a 3x3 kernel on a 16x pack and
			// a 9x9 one on a 48x. This is the constant that turns cracks into a dug hollow.
			Param.of("blur_radius", 0, 4, 1.0),
			// Contrast of the blurred mask. Above 1 pulls the soft halo back and keeps a defined pit;
			// below 1 spreads the disturbance over the whole face.
			Param.of("mask_gamma", 0.25, 4, 1.4),
			// How hard the overlay darkens, ramped across the four stages.
			Param.of("opacity_first", 0, 1, 0.35),
			Param.of("opacity_last", 0, 1, 0.57)
		);
	}

	@Override
	public Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		BufferedImage gravel = sources.get(GRAVEL);
		if (gravel == null || Ops.scaleOf(gravel) == 0) {
			return Map.of();
		}
		int size = gravel.getWidth();
		int radius = (int) Math.round(params.get("blur_radius") * Ops.scaleOf(gravel));
		double gamma = params.get("mask_gamma");
		int[] base = Ops.pixels(gravel);

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
			int[] out = new int[base.length];
			for (int i = 0; i < base.length; i++) {
				out[i] = Ops.multiply(base[i], Ops.withAlpha(mask[i], contrast(Ops.alpha(mask[i]), gamma)), opacity);
			}
			derived.put("block/suspicious_gravel_" + stage, Ops.image(out, size, size));
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
