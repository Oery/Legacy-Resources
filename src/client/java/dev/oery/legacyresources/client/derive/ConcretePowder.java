package dev.oery.legacyresources.client.derive;

import java.util.List;

/**
 * The sixteen concrete powders, from the pack's own sand. See {@link ConcreteBlock} for how they are
 * built and {@link ConcreteColor} for where the colours come from.
 */
final class ConcretePowder extends ConcreteBlock {
	@Override
	public String id() {
		return "concrete_powder";
	}

	@Override
	protected List<ConcreteColor> colors() {
		return ConcreteColor.POWDER;
	}

	@Override
	protected String suffix() {
		return "_concrete_powder";
	}

	/** Tuned in the lab across the pack corpus; see {@link ConcreteBlock#params} for what each does. */
	@Override
	public List<Param> params() {
		return List.of(
			// Vanilla's own contrast, which sand transplants onto at close to 1:1 gain - the two are
			// drawn at much the same grain (sand's deviation is 9.7, the powders' 6 to 14).
			Param.of("spread_scale", 0, 3, 1.0),
			Param.of("max_gain", 0.5, 4, 1.5),
			Param.of("saturation_scale", 0, 2, 1.0),
			Param.of("follow_source", 0, 1, 0.35),
			Param.ofInt("levels", 0, 8, 0)
		);
	}
}
