package dev.oery.legacyresources.client.mixin;

import dev.oery.legacyresources.client.convert.LegacyCowModel;
import dev.oery.legacyresources.client.convert.LegacyEndermanModel;
import dev.oery.legacyresources.client.convert.LegacyPackResources;
import dev.oery.legacyresources.client.convert.LegacyWolfModel;
import dev.oery.legacyresources.client.convert.LegacyMagmaCubeModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.animal.cow.CowModel;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Swaps the small cow geometry delta that can still be driven by the modern quadruped animation.
 * More involved families (notably horses) are selected through reload-aware renderer providers,
 * because their classic animation and render layers are inseparable from their geometry.
 */
@Mixin(EntityModelSet.class)
public class EntityModelSetMixin {
	private static final Identifier CLASSIC_COW_TEXTURE = Identifier.withDefaultNamespace("textures/entity/cow/cow_temperate.png");
	private static final Identifier CLASSIC_MOOSHROOM_TEXTURE = Identifier.withDefaultNamespace("textures/entity/cow/mooshroom_red.png");
	private static final Identifier CLASSIC_ENDERMAN_TEXTURE = Identifier.withDefaultNamespace("textures/entity/enderman/enderman.png");
	private static final Identifier CLASSIC_MAGMA_CUBE_TEXTURE = Identifier.withDefaultNamespace("textures/entity/slime/magmacube.png");
	private static final Identifier[] CLASSIC_WOLF_TEXTURES = {
		Identifier.withDefaultNamespace("textures/entity/wolf/wolf.png"),
		Identifier.withDefaultNamespace("textures/entity/wolf/wolf_tame.png"),
		Identifier.withDefaultNamespace("textures/entity/wolf/wolf_angry.png"),
		Identifier.withDefaultNamespace("textures/entity/wolf/wolf_collar.png")
	};

	@Inject(method = "bakeLayer", at = @At("HEAD"), cancellable = true)
	private void legacyresources$useLegacyMobModels(ModelLayerLocation id, CallbackInfoReturnable<ModelPart> cir) {
		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			return;
		}
		if (id.equals(ModelLayers.COW) && legacyresources$isLegacySourced(client, CLASSIC_COW_TEXTURE)) {
			cir.setReturnValue(LegacyCowModel.createBodyLayer().bakeRoot());
			return;
		}
		if (id.equals(ModelLayers.COW_BABY) && legacyresources$isLegacySourced(client, CLASSIC_COW_TEXTURE)) {
			cir.setReturnValue(LegacyCowModel.createBodyLayer().apply(CowModel.BABY_TRANSFORMER).bakeRoot());
			return;
		}
		if (id.equals(ModelLayers.MOOSHROOM) && legacyresources$isLegacySourced(client, CLASSIC_MOOSHROOM_TEXTURE)) {
			cir.setReturnValue(LegacyCowModel.createBodyLayer().bakeRoot());
			return;
		}
		if (id.equals(ModelLayers.MOOSHROOM_BABY) && legacyresources$isLegacySourced(client, CLASSIC_MOOSHROOM_TEXTURE)) {
			cir.setReturnValue(LegacyCowModel.createBodyLayer().apply(CowModel.BABY_TRANSFORMER).bakeRoot());
			return;
		}
		if (id.equals(ModelLayers.ENDERMAN) && legacyresources$isLegacySourced(client, CLASSIC_ENDERMAN_TEXTURE)) cir.setReturnValue(LegacyEndermanModel.createBodyLayer().bakeRoot());
		if (id.equals(ModelLayers.MAGMA_CUBE) && legacyresources$isLegacySourced(client, CLASSIC_MAGMA_CUBE_TEXTURE)) cir.setReturnValue(LegacyMagmaCubeModel.createBodyLayer().bakeRoot());
		if (legacyresources$hasLegacySource(client, CLASSIC_WOLF_TEXTURES)) {
			if (id.equals(ModelLayers.WOLF)) cir.setReturnValue(LegacyWolfModel.createBodyLayer().bakeRoot());
			if (id.equals(ModelLayers.WOLF_BABY)) cir.setReturnValue(LegacyWolfModel.createBabyLayer().bakeRoot());
			if (id.equals(ModelLayers.WOLF_ARMOR)) cir.setReturnValue(LegacyWolfModel.createArmorLayer().bakeRoot());
		}
	}

	private static boolean legacyresources$isLegacySourced(Minecraft client, Identifier texture) {
		return client.getResourceManager().getResource(texture).map(Resource::source).filter(LegacyPackResources.class::isInstance).isPresent();
	}

	private static boolean legacyresources$hasLegacySource(Minecraft client, Identifier[] textures) {
		for (Identifier texture : textures) if (legacyresources$isLegacySourced(client, texture)) return true;
		return false;
	}
}
