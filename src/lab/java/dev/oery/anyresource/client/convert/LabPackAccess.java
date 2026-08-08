package dev.oery.anyresource.client.convert;

import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import org.jspecify.annotations.Nullable;

/**
 * Lab-only access to the two package-private pieces of the conversion the derivation lab needs.
 * <p>
 * This class lives in the {@code lab} source set but declares the {@code convert} package, purely so
 * it can reach {@link LegacyPackDetector} and {@link LegacyPackResources} without either of them
 * having to become public API of the mod for a development harness's benefit. Nothing in the shipped
 * jar sees this file - the {@code lab} source set is excluded from it (see {@code build.gradle}).
 */
public final class LabPackAccess {
	private LabPackAccess() {
	}

	/**
	 * Opens a pack converted, or {@code null} if it isn't a legacy one.
	 * <p>
	 * Line for line what {@link LegacyResourcesSupplier#openPrimary} does, and it has to be: the
	 * detection must run against the <em>unconverted</em> resources. A converted pack deliberately
	 * reports the current {@code pack_format} rather than its real one (that is how the game accepts
	 * it as compatible), so asking one whether it is legacy always answers no.
	 */
	public static @Nullable PackResources openIfLegacy(Pack.ResourcesSupplier supplier, PackLocationInfo location) {
		PackResources resources = supplier.openPrimary(location);
		if (!LegacyPackDetector.isLegacy(resources)) {
			resources.close();
			return null;
		}
		return new LegacyPackResources(resources);
	}

	/**
	 * Converts {@code resources} whether or not it announces itself as legacy.
	 * <p>
	 * Only for the lab's vanilla control, which is 1.8.9's own {@code assets/} tree extracted out of
	 * the game jar and therefore has no {@code pack.mcmeta} to be detected by - but is, of course,
	 * the most legacy pack there is. Real packs go through {@link LegacyResourcesSupplier}, which
	 * gates on {@link #isLegacy}.
	 */
	public static PackResources convert(PackResources resources) {
		return new LegacyPackResources(resources);
	}
}
