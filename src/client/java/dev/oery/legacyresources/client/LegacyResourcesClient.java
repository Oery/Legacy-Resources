package dev.oery.legacyresources.client;

import dev.oery.legacyresources.LegacyResources;
import net.fabricmc.api.ClientModInitializer;

public class LegacyResourcesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		LegacyResources.LOGGER.info("Legacy pack conversion active (PackMixin installed)");
	}
}