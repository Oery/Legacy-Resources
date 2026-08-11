package dev.oery.legacyresources.client;

import dev.oery.legacyresources.LegacyResources;
import dev.oery.legacyresources.client.entity.LegacyEntityRenderers;
import net.fabricmc.api.ClientModInitializer;

public class LegacyResourcesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		LegacyEntityRenderers.register();
		LegacyResources.LOGGER.info("Legacy pack conversion active (PackMixin installed)");
	}
}
