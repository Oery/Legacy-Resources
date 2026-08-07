package dev.oery.anyresource.client.mixin;

import dev.oery.anyresource.client.convert.LegacyCowModel;
import dev.oery.anyresource.client.convert.LegacyHorseModel;
import dev.oery.anyresource.client.convert.LegacyPackResources;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Swaps in hand-built classic geometry for mob models the 1.13+/variant-update rewrites broke
 * for legacy textures - see {@link LegacyCowModel} and {@link LegacyHorseModel} for the specifics
 * of what changed for each. Models are rebaked on every resource reload, so this stays in sync
 * with pack changes/F3+T.
 */
@Mixin(EntityModelSet.class)
public class EntityModelSetMixin {
	private static final Identifier LEGACY_COW_TEXTURE = Identifier.withDefaultNamespace("textures/entity/cow/cow.png");

	/**
	 * Unlike the cow, horse texture filenames never changed, so there's no "only a legacy pack
	 * would have this" filename to check. Instead, ask the resource manager which pack is
	 * actually supplying each type's representative texture right now, and check whether that's
	 * one of ours - {@link LegacyPackResources} only ever wraps a legacy pack.
	 */
	private static final Map<ModelLayerLocation, Identifier> HORSE_FAMILY_TEXTURES = Map.of(
		ModelLayers.HORSE, Identifier.withDefaultNamespace("textures/entity/horse/horse_white.png"),
		ModelLayers.SKELETON_HORSE, Identifier.withDefaultNamespace("textures/entity/horse/horse_skeleton.png"),
		ModelLayers.ZOMBIE_HORSE, Identifier.withDefaultNamespace("textures/entity/horse/horse_zombie.png")
	);

	@Inject(method = "bakeLayer", at = @At("HEAD"), cancellable = true)
	private void anyresource$useLegacyMobModels(ModelLayerLocation id, CallbackInfoReturnable<ModelPart> cir) {
		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			return;
		}
		if (id.equals(ModelLayers.COW) && client.getResourceManager().getResource(LEGACY_COW_TEXTURE).isPresent()) {
			cir.setReturnValue(LegacyCowModel.createBodyLayer().bakeRoot());
			return;
		}
		Identifier horseTexture = HORSE_FAMILY_TEXTURES.get(id);
		if (horseTexture != null && anyresource$isLegacySourced(client, horseTexture)) {
			cir.setReturnValue(LegacyHorseModel.createBodyLayer().bakeRoot());
		}
	}

	private static boolean anyresource$isLegacySourced(Minecraft client, Identifier texture) {
		return client.getResourceManager().getResource(texture)
			.map(Resource::source)
			.filter(source -> source instanceof LegacyPackResources)
			.isPresent();
	}
}
