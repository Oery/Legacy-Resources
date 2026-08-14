package dev.oery.legacyresources.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.oery.legacyresources.client.convert.LegacyOcelotModel;
import dev.oery.legacyresources.client.convert.LegacyPackResources;
import net.minecraft.client.model.animal.feline.AbstractFelineModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.OcelotRenderer;
import net.minecraft.client.renderer.entity.state.FelineRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.animal.feline.Ocelot;

/** Uses the classic 64×32 ocelot sheet and its scaled adult geometry for legacy-pack cubs. */
final class HybridOcelotRenderer extends MobRenderer<Ocelot, FelineRenderState, AbstractFelineModel<FelineRenderState>> {
	private static final Identifier ADULT_TEXTURE = Identifier.withDefaultNamespace("textures/entity/cat/ocelot.png");
	private static final Identifier BABY_TEXTURE = Identifier.withDefaultNamespace("textures/entity/cat/ocelot_baby.png");
	private final ResourceManager resources;
	private final OcelotRenderer modern;
	private final AbstractFelineModel<FelineRenderState> adultModel;

	HybridOcelotRenderer(EntityRendererProvider.Context context) {
		super(context, new LegacyOcelotModel(LegacyOcelotModel.createBodyLayer().bakeRoot()), .4f);
		adultModel = model;
		resources = context.getResourceManager();
		modern = new OcelotRenderer(context);
	}

	@Override public Identifier getTextureLocation(FelineRenderState state) {
		return state.isBaby && !usesLegacy() ? BABY_TEXTURE : ADULT_TEXTURE;
	}
	@Override public FelineRenderState createRenderState() { return new FelineRenderState(); }
	@Override public void extractRenderState(Ocelot ocelot, FelineRenderState state, float partial) { super.extractRenderState(ocelot, state, partial); }
	@Override public void submit(FelineRenderState state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
		if (usesLegacy()) {
			model = adultModel;
			super.submit(state, poses, collector, camera);
		} else modern.submit(state, poses, collector, camera);
	}

	private boolean usesLegacy() {
		return resources.getResource(ADULT_TEXTURE).map(Resource::source).filter(LegacyPackResources.class::isInstance).isPresent();
	}

	@Override protected void scale(FelineRenderState state, PoseStack poses) {
		if (state.isBaby && usesLegacy()) {
			// The outer legacy cub body-group scale; the 1.5× head group is applied by
			// LegacyOcelotModel.setupAnim once the modern pose reset has run.
			LegacyEntityRenderPlan.applyOuter(LegacyEntityRenderPlan.Family.OCELOT, true, poses);
		}
	}
}
