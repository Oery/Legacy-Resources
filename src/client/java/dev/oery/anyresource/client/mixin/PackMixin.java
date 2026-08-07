package dev.oery.anyresource.client.mixin;

import dev.oery.anyresource.client.convert.LegacyResourcesSupplier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Every {@link Pack} (client resource pack or data pack) is created through
 * {@link Pack#readMetaAndCreate}, both for its metadata probe and for the supplier stored on
 * the resulting {@link Pack}. Wrapping the {@link Pack.ResourcesSupplier} right here - before
 * metadata is even read - means a legacy pack is transparently upgraded everywhere: in the
 * resource pack selection screen (compatibility check) and at actual load time.
 */
@Mixin(Pack.class)
public abstract class PackMixin {
	@ModifyVariable(method = "readMetaAndCreate", at = @At("HEAD"), argsOnly = true)
	private static Pack.ResourcesSupplier anyresource$wrapLegacyResources(
		Pack.ResourcesSupplier resources, PackLocationInfo location, Pack.ResourcesSupplier resourcesArg, PackType packType, PackSelectionConfig selectionConfig
	) {
		return packType == PackType.CLIENT_RESOURCES ? new LegacyResourcesSupplier(resources) : resources;
	}
}
