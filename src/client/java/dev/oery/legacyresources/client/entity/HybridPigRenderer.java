package dev.oery.legacyresources.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.oery.legacyresources.client.convert.LegacyPackResources;
import net.minecraft.client.model.animal.pig.PigModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.PigRenderer;
import net.minecraft.client.renderer.entity.state.PigRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.pig.PigVariant;

/** Uses the 64x32 classic model only for a legacy-sourced adult pig texture. */
final class HybridPigRenderer extends MobRenderer<Pig, PigRenderState, PigModel> {
	private final ResourceManager resources;
	private final PigRenderer modern;
	private final PigModel adultModel;
	private final PigModel babyModel;

	HybridPigRenderer(EntityRendererProvider.Context context) {
		super(context, new PigModel(ClassicPigModel.createLayer(0).bakeRoot()), .7f);
		adultModel = model;
		babyModel = new PigModel(ClassicPigModel.createBabyLayer(0).bakeRoot());
		resources = context.getResourceManager();
		modern = new PigRenderer(context);
		addLayer(new ClassicPigSaddleLayer(this));
	}
	@Override public Identifier getTextureLocation(PigRenderState state) { return state.variant.modelAndTexture().asset().texturePath(); }
	@Override public PigRenderState createRenderState() { return new PigRenderState(); }
	@Override public void extractRenderState(Pig pig, PigRenderState state, float partial) {
		super.extractRenderState(pig, state, partial);
		state.saddle = pig.getItemBySlot(EquipmentSlot.SADDLE).copy();
		state.variant = (PigVariant)pig.getVariant().value();
	}
	@Override public void submit(PigRenderState state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.variant != null && resources.getResource(getTextureLocation(state)).map(Resource::source).filter(LegacyPackResources.class::isInstance).isPresent()) { model = state.isBaby ? babyModel : adultModel; super.submit(state, poses, collector, camera); }
		else modern.submit(state, poses, collector, camera);
	}
}
