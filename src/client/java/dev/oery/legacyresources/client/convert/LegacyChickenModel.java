package dev.oery.legacyresources.client.convert;

import net.minecraft.client.model.animal.chicken.AdultChickenModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.ChickenRenderState;

/**
 * The 1.8.9 chicken wing flap.  Legacy feeding the wing angle straight from the
 * entity (an accurate double sine); the modern model quantizes it through
 * {@code Mth.sin}'s lookup table, which drifts by up to ~5e-5 rad and shows up as
 * mismatched wing normals once the baby transform is applied.  Everything else
 * (mesh, head, legs) is identical to the modern chicken.
 */
public final class LegacyChickenModel extends AdultChickenModel {
	private final ModelPart rightWing;
	private final ModelPart leftWing;

	public LegacyChickenModel(ModelPart root) {
		super(root);
		rightWing = root.getChild("right_wing");
		leftWing = root.getChild("left_wing");
	}

	@Override
	public void setupAnim(ChickenRenderState state) {
		super.setupAnim(state);
		float flapAngle = (float) ((Math.sin(state.flap) + 1.0f) * state.flapSpeed);
		rightWing.zRot = flapAngle;
		leftWing.zRot = -flapAngle;
	}
}
