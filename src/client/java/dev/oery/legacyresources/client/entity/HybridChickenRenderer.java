package dev.oery.legacyresources.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.oery.legacyresources.client.convert.LegacyChickenModel;
import dev.oery.legacyresources.client.convert.LegacyPackResources;
import java.util.Set;
import net.minecraft.client.model.BabyModelTransform;
import net.minecraft.client.model.animal.chicken.AdultChickenModel;
import net.minecraft.client.model.animal.chicken.ChickenModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ChickenRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.ChickenRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.chicken.ChickenVariant;

/** Restores the 1.8.9 chick transform and adult texture sheet for legacy-sourced chickens. */
final class HybridChickenRenderer extends MobRenderer<Chicken, ChickenRenderState, ChickenModel> {
	private final ResourceManager resources;
	private final ChickenRenderer modern;
	private final ChickenModel adultModel;
	private final ChickenModel babyModel;

	HybridChickenRenderer(EntityRendererProvider.Context context) {
		super(context, new LegacyChickenModel(AdultChickenModel.createBodyLayer().bakeRoot()), .3f);
		adultModel = model;
		babyModel = new LegacyChickenModel(AdultChickenModel.createBodyLayer().apply(new BabyModelTransform(false, 5, 2, Set.of("head"))).bakeRoot());
		resources = context.getResourceManager();
		modern = new ChickenRenderer(context);
	}
	@Override public Identifier getTextureLocation(ChickenRenderState state) { return state.variant.modelAndTexture().asset().texturePath(); }
	@Override public ChickenRenderState createRenderState() { return new ChickenRenderState(); }
	@Override public void extractRenderState(Chicken chicken, ChickenRenderState state, float partial) {
		super.extractRenderState(chicken, state, partial);
		state.flap = Mth.lerp(partial, chicken.oFlap, chicken.flap);
		state.flapSpeed = Mth.lerp(partial, chicken.oFlapSpeed, chicken.flapSpeed);
		state.variant = (ChickenVariant)chicken.getVariant().value();
	}
	@Override public void submit(ChickenRenderState state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.variant != null && resources.getResource(getTextureLocation(state)).map(Resource::source).filter(LegacyPackResources.class::isInstance).isPresent()) { model = state.isBaby ? babyModel : adultModel; super.submit(state, poses, collector, camera); }
		else modern.submit(state, poses, collector, camera);
	}
}
