package dev.oery.legacyresources.client.derive;

import java.util.List;

/**
 * The sixteen hardened concrete blocks, from the pack's own sand. See {@link ConcreteBlock} for how they
 * are built and {@link ConcreteColor} for where the colours come from.
 * <p>
 * Vanilla draws these flat - a standard deviation of 0.5 to 1.0, four shades at most - so reproducing it
 * exactly would mean sixteen fills whose colours are vanilla's own, which is a derivation that derives
 * nothing. {@code spread_scale} therefore defaults above 1: keeping a little of the pack's sand grain is
 * what makes the block belong to the pack, and it is the same call a pack author drawing at 128x would
 * make, since a flat fill is a 16x convention rather than a property of concrete.
 */
final class Concrete extends ConcreteBlock {
	@Override
	public String id() {
		return "concrete";
	}

	@Override
	protected List<ConcreteColor> colors() {
		return ConcreteColor.HARDENED;
	}

	@Override
	protected String suffix() {
		return "_concrete";
	}

	/** Tuned in the lab across the pack corpus; see {@link ConcreteBlock#params} for what each does. */
	@Override
	public List<Param> params() {
		return List.of(
			// 1 is vanilla's near-flat fill; above that is the pack's own grain showing through. See the
			// class note - this is the one constant here set by eye rather than by measurement.
			Param.of("spread_scale", 0, 6, 2.5),
			Param.of("max_gain", 0.5, 4, 1.5),
			Param.of("saturation_scale", 0, 2, 1.0),
			Param.of("follow_source", 0, 1, 0.35),
			Param.ofInt("levels", 0, 8, 0)
		);
	}
}
