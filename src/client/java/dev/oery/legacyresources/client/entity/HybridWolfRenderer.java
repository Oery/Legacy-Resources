package dev.oery.legacyresources.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.oery.legacyresources.client.convert.LegacyPackResources;
import dev.oery.legacyresources.client.convert.LegacyWolfAnimalModel;
import dev.oery.legacyresources.client.convert.LegacyWolfModel;
import net.minecraft.client.model.animal.wolf.WolfModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.minecraft.client.renderer.entity.layers.WolfArmorLayer;
import net.minecraft.client.renderer.entity.layers.WolfCollarLayer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.animal.wolf.Wolf;

/** Uses the 1.8.9 wolf geometry and sit/shake animation for legacy-sourced wolves. */
final class HybridWolfRenderer extends AgeableMobRenderer<Wolf, WolfRenderState, WolfModel> {
	private static final Identifier[] WOLF_TEXTURES = {
		Identifier.withDefaultNamespace("textures/entity/wolf/wolf.png"),
		Identifier.withDefaultNamespace("textures/entity/wolf/wolf_tame.png"),
		Identifier.withDefaultNamespace("textures/entity/wolf/wolf_angry.png"),
		Identifier.withDefaultNamespace("textures/entity/wolf/wolf_collar.png")
	};
	private final ResourceManager resources;
	private final WolfRenderer modern;

	HybridWolfRenderer(EntityRendererProvider.Context context) {
		super(context,
			new LegacyWolfAnimalModel(LegacyWolfModel.createBodyLayer().bakeRoot()),
			new LegacyWolfAnimalModel(LegacyWolfModel.createBabyLayer().bakeRoot()),
			0.5f);
		addLayer(new WolfArmorLayer(this, context.getModelSet(), context.getEquipmentRenderer()));
		addLayer(new WolfCollarLayer(this));
		resources = context.getResourceManager();
		modern = new WolfRenderer(context);
	}

	@Override protected int getModelTint(WolfRenderState state) {
		float wetShade = state.wetShade;
		if (wetShade == 1.0f) return -1;
		return ARGB.colorFromFloat(1.0f, wetShade, wetShade, wetShade);
	}
	@Override public Identifier getTextureLocation(WolfRenderState state) { return state.texture; }
	@Override public WolfRenderState createRenderState() { return new WolfRenderState(); }
	@Override public void extractRenderState(Wolf entity, WolfRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.isAngry = entity.isAngry();
		state.isSitting = entity.isInSittingPose();
		state.tailAngle = entity.getTailAngle();
		state.headRollAngle = entity.getHeadRollAngle(partialTicks);
		state.shakeAnim = entity.getShakeAnim(partialTicks);
		state.texture = entity.getTexture();
		state.wetShade = entity.getWetShade(partialTicks);
		state.collarColor = entity.isTame() ? entity.getCollarColor() : null;
		state.bodyArmorItem = entity.getBodyArmorItem().copy();
	}
	@Override public void submit(WolfRenderState state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
		if (usesLegacy()) super.submit(state, poses, collector, camera);
		else modern.submit(state, poses, collector, camera);
	}

	private boolean usesLegacy() {
		for (Identifier texture : WOLF_TEXTURES) if (resources.getResource(texture).map(Resource::source).filter(LegacyPackResources.class::isInstance).isPresent()) return true;
		return false;
	}
}