package dev.oery.legacyresources.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HorseRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.equine.Markings;

/** The old marking sheets use the same 128x128 classic horse UV layout as the base coat. */
final class ClassicHorseMarkingLayer extends RenderLayer<HorseRenderState, ClassicHorseModel> {
	private static final Identifier INVISIBLE = Identifier.withDefaultNamespace("invisible");
	private static final Map<Markings, Identifier> TEXTURES = Map.of(
		Markings.NONE, INVISIBLE,
		Markings.WHITE, Identifier.withDefaultNamespace("textures/entity/horse/horse_markings_white.png"),
		Markings.WHITE_FIELD, Identifier.withDefaultNamespace("textures/entity/horse/horse_markings_whitefield.png"),
		Markings.WHITE_DOTS, Identifier.withDefaultNamespace("textures/entity/horse/horse_markings_whitedots.png"),
		Markings.BLACK_DOTS, Identifier.withDefaultNamespace("textures/entity/horse/horse_markings_blackdots.png")
	);

	ClassicHorseMarkingLayer(RenderLayerParent<HorseRenderState, ClassicHorseModel> parent) { super(parent); }

	@Override public void submit(PoseStack pose, SubmitNodeCollector collector, int light, HorseRenderState state, float yRot, float xRot) {
		Identifier texture = TEXTURES.get(state.markings);
		if (texture == INVISIBLE || state.isInvisible) return;
		collector.order(1).submitModel(getParentModel(), state, pose, RenderTypes.entityTranslucent(texture), light,
			LivingEntityRenderer.getOverlayCoords(state, 0), state.outlineColor, (ModelFeatureRenderer.CrumblingOverlay)null);
	}
}
