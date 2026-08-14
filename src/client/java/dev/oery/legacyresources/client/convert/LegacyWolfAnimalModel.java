package dev.oery.legacyresources.client.convert;

import net.minecraft.client.model.animal.wolf.AdultWolfModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.WolfRenderState;

/**
 * The 1.8.9 {@code ModelWolf} animation, on top of the {@link LegacyWolfModel} geometry.
 * Two things differ from the modern {@code AdultWolfModel}: the sitting pose (hind legs
 * pivot at 22 rather than 22.7, both front legs shift +0.01 in X rather than toward each
 * other, and the mane lift scales with the baby factor), and the wet shake, which in 1.8.9
 * is a plain sine of the age rather than the saturating roll progress.
 */
public final class LegacyWolfAnimalModel extends AdultWolfModel {
	private final ModelPart realHead;
	private final ModelPart upperBody;
	private final ModelPart realTail;

	public LegacyWolfAnimalModel(ModelPart root) {
		super(root);
		realHead = this.head.getChild("real_head");
		upperBody = root.getChild("upper_body");
		realTail = this.tail.getChild("real_tail");
	}

	@Override
	protected void setSittingPose(WolfRenderState state) {
		float as = state.ageScale;
		this.body.y += 4.0f * as;
		this.body.z -= 2.0f * as;
		this.body.xRot = 0.7853982f;
		this.tail.y += 9.0f * as;
		this.tail.z -= 2.0f * as;
		this.rightHindLeg.y += 6.0f * as;
		this.rightHindLeg.z -= 5.0f * as;
		this.rightHindLeg.xRot = 4.712389f;
		this.leftHindLeg.y += 6.0f * as;
		this.leftHindLeg.z -= 5.0f * as;
		this.leftHindLeg.xRot = 4.712389f;
		this.rightFrontLeg.xRot = 5.811947f;
		this.rightFrontLeg.x += 0.01f * as;
		this.rightFrontLeg.y += 1.0f * as;
		this.leftFrontLeg.xRot = 5.811947f;
		this.leftFrontLeg.x += 0.01f * as;
		this.leftFrontLeg.y += 1.0f * as;
		upperBody.y += 2.0f * as;
		upperBody.xRot = 1.2566371f;
		upperBody.yRot = 0.0f;
	}

	@Override
	protected void shakeOffWater(WolfRenderState state) {
		if (state.shakeAnim == 0.0f) {
			super.shakeOffWater(state);
			return;
		}
		realHead.zRot = state.headRollAngle + (float) Math.sin(state.shakeAnim) * 0.25f;
		upperBody.zRot = (float) Math.sin(state.shakeAnim - 0.08f) * 0.25f;
		this.body.zRot = (float) Math.sin(state.shakeAnim - 0.16f) * 0.25f;
		realTail.zRot = (float) Math.sin(state.shakeAnim - 0.2f) * 0.25f;
	}
}
