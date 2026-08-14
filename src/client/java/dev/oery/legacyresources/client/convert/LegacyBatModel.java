package dev.oery.legacyresources.client.convert;

import java.util.List;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.BatRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * Placeholder for the 1.8.9 bat.  The oracle-derived model was reverted because it was
 * built from reading legacy source instead of blackbox reverse engineering; until it is
 * redriven from the oracle traces alone this renders nothing.
 */
public final class LegacyBatModel extends EntityModel<BatRenderState> {
	public LegacyBatModel(ModelPart root) {
		super(root, RenderTypes::entityCutoutCull);
	}

	public static ModelPart createRoot() {
		return new ModelPart(List.of(), Map.of());
	}

	@Override
	public void setupAnim(BatRenderState state) {
		super.setupAnim(state);
	}
}