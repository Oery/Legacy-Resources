package dev.oery.legacyresources.client.derive;

import java.util.List;

/** A copper nugget is the pack's gold-nugget faceting on the copper equipment ramp. */
final class CopperNugget extends NuggetRecolor {
	CopperNugget() {
		super("copper");
	}

	@Override
	public List<Param> params() {
		return ramp(42, 185, 107, 13, .73, .55);
	}
}
