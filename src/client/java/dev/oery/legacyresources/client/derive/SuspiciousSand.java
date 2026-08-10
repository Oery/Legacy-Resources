package dev.oery.legacyresources.client.derive;

import java.util.List;

/**
 * Suspicious sand's four brushing stages, from the pack's own sand. See {@link SuspiciousBlock} for how
 * the stages are built.
 * <p>
 * The same treatment gravel gets, one block over, and on the same numbers: vanilla perturbs 66, 78, 99
 * and 125 pixels across the four sand stages against gravel's 61, 78, 99 and 130, and darkens both by
 * the same 6.4% of their own mean by the last stage. Since the overlay is a multiply it is proportional
 * too, so gravel's tuned ramp reproduces on sand exactly what it does on gravel - 13.3% of the block's
 * own mean either way - and a ramp of sand's own would only be the same ramp written twice. What does
 * differ is that sand is the flatter field of the two, so the hollow has less grain to hide in and the
 * mask's shape shows more plainly; that is a reason to look at it in game, not a number to change.
 */
final class SuspiciousSand extends SuspiciousBlock {
	@Override
	public String id() {
		return "suspicious_sand";
	}

	@Override
	protected String base() {
		return "block/sand";
	}

	@Override
	protected String stem() {
		return "block/suspicious_sand_";
	}

	/** Tuned in the lab across the pack corpus; see {@link SuspiciousBlock#params} for what each does. */
	@Override
	public List<Param> params() {
		return List.of(
			Param.ofInt("stage_base", 0, 9, 2),
			Param.ofInt("stage_step", 0, 3, 2),
			Param.of("blur_radius", 0, 4, 1.0),
			Param.of("mask_gamma", 0.25, 4, 1.4),
			Param.of("opacity_first", 0, 1, 0.35),
			Param.of("opacity_last", 0, 1, 0.57)
		);
	}
}
