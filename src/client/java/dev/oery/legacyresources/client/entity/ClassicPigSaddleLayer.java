package dev.oery.legacyresources.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.animal.pig.PigModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.PigRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** The pre-equipment-renderer saddle uses the old pig's 64x32 texture and inflated mesh. */
final class ClassicPigSaddleLayer extends RenderLayer<PigRenderState, PigModel> {
	private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/pig/pig_saddle.png");
	private final PigModel adultModel = new PigModel(ClassicPigModel.createLayer(.5f).bakeRoot());
	private final PigModel babyModel = new PigModel(ClassicPigModel.createBabyLayer(.5f).bakeRoot());
	ClassicPigSaddleLayer(RenderLayerParent<PigRenderState, PigModel> parent) { super(parent); }
	@Override public void submit(PoseStack poses, SubmitNodeCollector collector, int light, PigRenderState state, float yRot, float xRot) {
		if (!state.saddle.isEmpty() && !state.isInvisible) collector.order(1).submitModel(state.isBaby ? babyModel : adultModel, state, poses, RenderTypes.entityCutout(TEXTURE), light, LivingEntityRenderer.getOverlayCoords(state, 0), -1, null, state.outlineColor, null);
	}
}
