package dev.oery.legacyresources.client.derive;

import java.util.List;

/**
 * Suspicious gravel's four brushing stages, from the pack's own gravel. See {@link SuspiciousBlock} for
 * how the stages are built.
 */
final class SuspiciousGravel extends SuspiciousBlock {
	@Override
	public String id() {
		return "suspicious_gravel";
	}

	@Override
	protected String base() {
		return "block/gravel";
	}

	@Override
	protected String stem() {
		return "block/suspicious_gravel_";
	}

	/** Tuned in the lab across the pack corpus; see {@link SuspiciousBlock#params} for what each does. */
	@Override
	public List<Param> params() {
		return List.of(
			// The ramp starts at 2 and takes every second stage from there, ending at 8: gravel's
			// lowest destroy stages are a couple of isolated cracks that all but vanish once blurred.
			Param.ofInt("stage_base", 0, 9, 2),
			Param.ofInt("stage_step", 0, 3, 2),
			Param.of("blur_radius", 0, 4, 1.0),
			Param.of("mask_gamma", 0.25, 4, 1.4),
			Param.of("opacity_first", 0, 1, 0.35),
			Param.of("opacity_last", 0, 1, 0.57)
		);
	}
}
