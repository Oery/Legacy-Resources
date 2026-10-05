package dev.oery.legacyresources.client.convert;

import java.util.stream.Stream;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;

/**
 * Wraps a {@link Pack.ResourcesSupplier} so that every {@link PackResources} it opens is
 * transparently upgraded with {@link LegacyPackResources} if (and only if) it turns out to be
 * a legacy (pre-1.13) pack. Non-legacy packs pass through completely untouched.
 * <p>
 * 26.3 reads a pack's format from a metadata-only object ({@link #openMetadata}) that it closes
 * again before any resources are opened ({@link #openResources}), so the probe and the resources
 * the game goes on to open are wrapped separately: the probe is decorated with
 * {@link LegacyPackMetadata} to report the running pack_format, the resources with
 * {@link LegacyPackResources} to translate their contents.
 */
public final class LegacyResourcesSupplier implements Pack.ResourcesSupplier {
	private final Pack.ResourcesSupplier delegate;

	public LegacyResourcesSupplier(Pack.ResourcesSupplier delegate) {
		this.delegate = delegate;
	}

	@Override
	public PackMetadataResources openMetadata(PackLocationInfo location) {
		PackMetadataResources metadata = delegate.openMetadata(location);
		return LegacyPackDetector.isLegacy(metadata) ? new LegacyPackMetadata(metadata) : metadata;
	}

	@Override
	public Stream<PackResources> openResources(PackLocationInfo location, Pack.Metadata metadata) {
		return delegate.openResources(location, metadata).map(LegacyResourcesSupplier::wrapIfLegacy);
	}

	private static PackResources wrapIfLegacy(PackResources resources) {
		return LegacyPackDetector.isLegacy(resources) ? new LegacyPackResources(resources) : resources;
	}
}
