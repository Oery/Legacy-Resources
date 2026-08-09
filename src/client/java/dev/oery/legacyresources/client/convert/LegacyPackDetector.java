package dev.oery.legacyresources.client.convert;

import dev.oery.legacyresources.LegacyResources;
import java.io.IOException;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;

/**
 * A pack declaring pack_format 1-3 predates the 1.13 "flattening" (Minecraft 1.6.1-1.12.2)
 * and therefore uses the pre-flattening texture/model layout that {@link LegacyPackResources}
 * knows how to translate. See PLAN.md.
 */
final class LegacyPackDetector {
	private static final int MIN_LEGACY_FORMAT = 1;
	private static final int MAX_LEGACY_FORMAT = 3;

	private LegacyPackDetector() {
	}

	static boolean isLegacy(PackResources resources) {
		try {
			PackMetadataSection section = resources.getMetadataSection(PackMetadataSection.CLIENT_TYPE);
			if (section == null) {
				return false;
			}
			int format = section.supportedFormats().minInclusive().major();
			return format >= MIN_LEGACY_FORMAT && format <= MAX_LEGACY_FORMAT;
		} catch (IOException e) {
			LegacyResources.LOGGER.debug("Failed to read pack metadata for {}, treating as non-legacy", resources.location().id(), e);
			return false;
		}
	}
}
