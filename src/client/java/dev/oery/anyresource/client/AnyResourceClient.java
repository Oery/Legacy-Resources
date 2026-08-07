package dev.oery.anyresource.client;

import dev.oery.anyresource.AnyResource;
import net.fabricmc.api.ClientModInitializer;

public class AnyResourceClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		AnyResource.LOGGER.info("Legacy pack conversion active (PackMixin installed)");
	}
}