package dev.oery.legacyresources.client.convert;

import java.io.IOException;
import java.io.InputStream;
import net.minecraft.SharedConstants;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.InclusiveRange;
import org.jspecify.annotations.Nullable;

/** A legacy pack's metadata probe, reporting the running version's pack_format so the game accepts it. */
final class LegacyPackMetadata implements PackMetadataResources {
	/** Rewrites a pack's own format into the running version's; every other section passes through. */
	@SuppressWarnings("unchecked")
	static <T> T currentFormat(MetadataSectionType<T> type, T value) {
		if (value instanceof PackMetadataSection section
			&& (type == PackMetadataSection.CLIENT_TYPE || type == PackMetadataSection.FALLBACK_TYPE)) {
			PackFormat current = SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES);
			return (T) new PackMetadataSection(section.description(), new InclusiveRange<>(current));
		}
		return value;
	}

	private final PackMetadataResources delegate;

	LegacyPackMetadata(PackMetadataResources delegate) {
		this.delegate = delegate;
	}

	@Override public PackLocationInfo location() { return delegate.location(); }

	@Override public @Nullable IoSupplier<InputStream> getRootResource(String... path) { return delegate.getRootResource(path); }

	@Override public <T> T getMetadataSection(MetadataSectionType<T> type) throws IOException {
		return currentFormat(type, delegate.getMetadataSection(type));
	}

	@Override public void close() { delegate.close(); }
}
