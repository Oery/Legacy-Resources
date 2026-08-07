package dev.oery.anyresource.client.convert;

import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;

/**
 * Wraps a {@link Pack.ResourcesSupplier} so that every {@link PackResources} it opens is
 * transparently upgraded with {@link LegacyPackResources} if (and only if) it turns out to be
 * a legacy (pre-1.13) pack. Non-legacy packs pass through completely untouched.
 */
public final class LegacyResourcesSupplier implements Pack.ResourcesSupplier {
	private final Pack.ResourcesSupplier delegate;

	public LegacyResourcesSupplier(Pack.ResourcesSupplier delegate) {
		this.delegate = delegate;
	}

	@Override
	public PackResources openPrimary(PackLocationInfo location) {
		return wrapIfLegacy(delegate.openPrimary(location));
	}

	@Override
	public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
		return wrapIfLegacy(delegate.openFull(location, metadata));
	}

	private static PackResources wrapIfLegacy(PackResources resources) {
		return LegacyPackDetector.isLegacy(resources) ? new LegacyPackResources(resources) : resources;
	}
}
