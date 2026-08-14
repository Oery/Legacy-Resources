package dev.oery.legacyresources.client.entity;

import dev.oery.legacyresources.client.convert.LegacyOcelotModel;
import net.minecraft.client.model.animal.feline.AdultCatModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.state.CatRenderState;

/** Cat-state adapter around the oracle-verified classic feline mesh. */
final class LegacyCatModel extends AdultCatModel {
	LegacyCatModel(ModelPart root) { super(root); }

	static LayerDefinition createBodyLayer() { return LegacyOcelotModel.createBodyLayer(); }

	@Override public void setupAnim(CatRenderState state) {
		super.setupAnim(state);
		if (state.isBaby) {
			head.x = 0;
			head.y = 13.5f;
			head.z = -7.5f;
			head.xScale = head.yScale = head.zScale = 1.5f;
		}
	}
}
