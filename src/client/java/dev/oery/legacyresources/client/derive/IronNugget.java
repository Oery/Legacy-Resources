package dev.oery.legacyresources.client.derive;

import java.util.List;

/** An iron nugget is the pack's gold-nugget faceting on vanilla's cool silver ramp. */
final class IronNugget extends NuggetRecolor {
	IronNugget() {
		super("iron");
	}

	@Override
	public List<Param> params() {
		return ramp(58, 205, 142, 220, .08, .03);
	}
}
