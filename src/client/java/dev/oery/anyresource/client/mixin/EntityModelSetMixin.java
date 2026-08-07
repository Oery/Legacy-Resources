package dev.oery.anyresource.client.mixin;

import dev.oery.anyresource.client.convert.LegacyCowModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Legacy (pre-1.13) cow textures are 64x32 and were never painted with the muzzle/horn UV
 * regions the modern cow model added (which needs a 64x64 canvas) - baking them against that
 * model scrambles every UV coordinate (see {@link LegacyCowModel}). When a legacy pack supplies
 * the classic single-file {@code textures/entity/cow/cow.png} - a filename only such a pack
 * would ever provide, since vanilla no longer ships it - swap in the classic geometry instead.
 * Models are rebaked on every resource reload, so this stays in sync with pack changes/F3+T.
 */
@Mixin(EntityModelSet.class)
public class EntityModelSetMixin {
	private static final Identifier LEGACY_COW_TEXTURE = Identifier.withDefaultNamespace("textures/entity/cow/cow.png");

	@Inject(method = "bakeLayer", at = @At("HEAD"), cancellable = true)
	private void anyresource$useLegacyCowModel(ModelLayerLocation id, CallbackInfoReturnable<ModelPart> cir) {
		Minecraft client = Minecraft.getInstance();
		if (client != null && id.equals(ModelLayers.COW) && client.getResourceManager().getResource(LEGACY_COW_TEXTURE).isPresent()) {
			cir.setReturnValue(LegacyCowModel.createBodyLayer().bakeRoot());
		}
	}
}
