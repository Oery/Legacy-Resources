package dev.oery.legacyresources.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.oery.legacyresources.client.convert.LegacyPackResources;
import net.minecraft.client.model.animal.feline.AbstractFelineModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.CatRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.CatRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.feline.Cat;

/** Uses the verified 1.8.9 feline mesh for the three cat variants supplied by legacy packs. */
final class HybridCatRenderer extends MobRenderer<Cat, CatRenderState, AbstractFelineModel<CatRenderState>> {
	private final ResourceManager resources;
	private final CatRenderer modern;

	HybridCatRenderer(EntityRendererProvider.Context context) {
		super(context, new LegacyCatModel(LegacyCatModel.createBodyLayer().bakeRoot()), .4f);
		resources = context.getResourceManager();
		modern = new CatRenderer(context);
	}

	@Override public Identifier getTextureLocation(CatRenderState state) { return state.texture; }
	@Override public CatRenderState createRenderState() { return new CatRenderState(); }

	@Override public void extractRenderState(Cat cat, CatRenderState state, float partial) {
		super.extractRenderState(cat, state, partial);
		state.texture = cat.getVariant().value().assetInfo(state.isBaby).texturePath();
		state.isCrouching = cat.isCrouching();
		state.isSprinting = cat.isSprinting();
		state.isSitting = cat.isInSittingPose();
		state.lieDownAmount = cat.getLieDownAmount(partial);
		state.lieDownAmountTail = cat.getLieDownAmountTail(partial);
		state.relaxStateOneAmount = cat.getRelaxStateOneAmount(partial);
		state.isLyingOnTopOfSleepingPlayer = cat.isLyingOnTopOfSleepingPlayer();
		state.collarColor = cat.isTame() ? cat.getCollarColor() : null;
	}

	@Override public void submit(CatRenderState state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
		if (usesLegacy(state.texture)) super.submit(state, poses, collector, camera);
		else modern.submit(state, poses, collector, camera);
	}

	@Override protected void setupRotations(CatRenderState state, PoseStack poses, float bodyRot, float entityScale) {
		super.setupRotations(state, poses, bodyRot, entityScale);
		float lying = state.lieDownAmount;
		if (lying > 0) {
			poses.translate(.4f * lying, .15f * lying, .1f * lying);
			poses.mulPose(Axis.ZP.rotationDegrees(Mth.rotLerp(lying, 0, 90)));
			if (state.isLyingOnTopOfSleepingPlayer) poses.translate(.15f * lying, 0, 0);
		}
	}

	@Override protected void scale(CatRenderState state, PoseStack poses) {
		if (state.isBaby) LegacyEntityRenderPlan.applyOuter(LegacyEntityRenderPlan.Family.CAT, true, poses);
	}

	private boolean usesLegacy(Identifier texture) {
		return resources.getResource(texture).map(Resource::source).filter(LegacyPackResources.class::isInstance).isPresent();
	}
}
